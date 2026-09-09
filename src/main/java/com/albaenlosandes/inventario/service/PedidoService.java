package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.*;
import com.albaenlosandes.inventario.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Corazon del e-commerce. Aca viven las reglas de negocio principales:
 *  - crear un pedido valida stock, lo descuenta y deja trazabilidad;
 *  - cancelar un pedido devuelve el stock con su movimiento de AJUSTE.
 *
 * @Transactional: si algo falla a mitad de camino, TODO se revierte
 * (no puede quedar un pedido guardado con stock sin descontar).
 */
@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoStockRepository movimientoRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProductoRepository productoRepository,
                         UsuarioRepository usuarioRepository,
                         MovimientoStockRepository movimientoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
    }

    /**
     * Trae los pedidos con sus renglones EN UNA SOLA CONSULTA.
     * Antes se usaba findAll() y Hibernate disparaba una consulta extra por
     * cada pedido para traer sus detalles (el problema conocido como "N+1").
     */
    public List<Pedido> obtenerTodos() {
        return pedidoRepository.buscarTodosConDetalle();
    }

    public Optional<Pedido> obtenerPorId(Integer id) {
        return pedidoRepository.buscarPorIdConDetalle(id);
    }

    public List<Pedido> obtenerPorUsuario(Integer idUsuario) {
        return pedidoRepository.buscarPorUsuarioConDetalle(idUsuario);
    }

    // ==================================================================
    //  CHECKOUT
    // ==================================================================

    /**
     * CHECKOUT (tras el pago simulado). El JSON de entrada solo necesita:
     * usuario.idUsuario, medioPago y detalles[{producto.idProducto, cantidad}].
     *
     * ORDEN DE LOS PASOS, Y POR QUE ESTE Y NO OTRO:
     *
     *  1. Se valida el usuario.
     *  2. Se AGRUPAN los renglones por producto. Si alguien manda el mismo
     *     vino en dos renglones, se suman: antes cada renglon se validaba por
     *     separado contra el mismo stock y se podia vender de mas.
     *  3. Se bloquean los productos EN ORDEN DE ID. Bloquear siempre en el
     *     mismo orden es lo que evita los interbloqueos: si un pedido toma
     *     primero el vino 3 y otro toma primero el vino 7, y despues cada uno
     *     quiere el del otro, los dos quedan esperando para siempre. Con un
     *     orden fijo eso no puede pasar.
     *  4. Recien con los productos bloqueados se valida el stock. El numero
     *     que se lee ya no puede cambiar hasta que termine la transaccion.
     *  5. Se guarda el pedido, se descuenta y se registran los movimientos.
     */
    @Transactional
    public Pedido crearPedido(Pedido pedido) {

        // ---- 1. Validar el usuario ----
        Integer idUsuario = pedido.getUsuario() != null ? pedido.getUsuario().getIdUsuario() : null;
        if (idUsuario == null) {
            throw new IllegalArgumentException("El pedido debe indicar usuario.idUsuario");
        }
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + idUsuario));
        pedido.setUsuario(usuario);

        if (pedido.getDetalles() == null || pedido.getDetalles().isEmpty()) {
            throw new IllegalArgumentException("El pedido debe tener al menos un producto");
        }

        // ---- 2. Agrupar por producto (el mismo vino puede venir repetido) ----
        Map<Integer, Integer> cantidadPorProducto = new TreeMap<>();   // TreeMap = queda ordenado por id
        for (DetallePedido detalle : pedido.getDetalles()) {
            Integer idProducto = detalle.getProducto() != null ? detalle.getProducto().getIdProducto() : null;
            if (idProducto == null) {
                throw new IllegalArgumentException("Cada detalle debe indicar producto.idProducto");
            }
            if (detalle.getCantidad() == null || detalle.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad de cada renglon debe ser mayor a cero");
            }
            cantidadPorProducto.merge(idProducto, detalle.getCantidad(), Integer::sum);
        }

        // ---- 3. Bloquear los productos, siempre en orden de id ----
        Map<Integer, Producto> bloqueados = new LinkedHashMap<>();
        for (Integer idProducto : cantidadPorProducto.keySet()) {
            Producto producto = productoRepository.buscarParaActualizar(idProducto)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el producto con id " + idProducto));
            bloqueados.put(idProducto, producto);
        }

        // ---- 4. Validar el stock ya bloqueado ----
        for (Map.Entry<Integer, Integer> e : cantidadPorProducto.entrySet()) {
            Producto producto = bloqueados.get(e.getKey());
            int pedida = e.getValue();
            if (producto.getStockActual() < pedida) {
                throw new IllegalArgumentException("Stock insuficiente de " + producto.getNombre()
                        + " (disponible: " + producto.getStockActual() + ", pedido: " + pedida + ")");
            }
        }

        // ---- 5. Completar los renglones y calcular el total ----
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedido detalle : pedido.getDetalles()) {
            Producto producto = bloqueados.get(detalle.getProducto().getIdProducto());
            detalle.setProducto(producto);
            detalle.setPrecioUnitario(producto.getPrecio());   // foto del precio actual
            detalle.setPedido(pedido);                         // enlaza el renglon con su pedido
            total = total.add(producto.getPrecio().multiply(BigDecimal.valueOf(detalle.getCantidad())));
        }

        pedido.setTotal(total);
        pedido.setEstado(EstadoPedido.CONFIRMADO);
        Pedido guardado = pedidoRepository.save(pedido);       // cascade guarda los detalles

        // ---- 6. Descontar stock y registrar trazabilidad (inventario perpetuo) ----
        for (Map.Entry<Integer, Integer> e : cantidadPorProducto.entrySet()) {
            Producto producto = bloqueados.get(e.getKey());
            int cantidad = e.getValue();
            producto.setStockActual(producto.getStockActual() - cantidad);
            productoRepository.save(producto);
            movimientoRepository.save(new MovimientoStock(
                    producto, usuario, TipoMovimiento.SALIDA,
                    -cantidad,
                    "Venta - pedido #" + guardado.getIdPedido(),
                    guardado));
        }
        return guardado;
    }

    // ==================================================================
    //  CAMBIO DE ESTADO
    // ==================================================================

    /**
     * Cambio de estado desde el panel de gestion.
     * Regla: CANCELAR devuelve el stock automaticamente, con AJUSTE registrado.
     *
     * Devolver stock tambien toca las mismas filas que una venta, asi que
     * se bloquean igual y en el mismo orden por id. Si no, una cancelacion
     * y una compra simultaneas del mismo vino podrian trabarse entre si.
     */
    @Transactional
    public Pedido cambiarEstado(Integer idPedido, EstadoPedido nuevoEstado) {
        Pedido pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new IllegalArgumentException("No existe el pedido con id " + idPedido));

        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new IllegalArgumentException("El pedido #" + idPedido + " ya esta cancelado");
        }

        if (nuevoEstado == EstadoPedido.CANCELADO) {
            // Agrupar por producto y ordenar por id, igual que en el checkout
            Map<Integer, Integer> aDevolver = new TreeMap<>();
            for (DetallePedido detalle : pedido.getDetalles()) {
                aDevolver.merge(detalle.getProducto().getIdProducto(), detalle.getCantidad(), Integer::sum);
            }
            for (Map.Entry<Integer, Integer> e : aDevolver.entrySet()) {
                Producto producto = productoRepository.buscarParaActualizar(e.getKey())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No existe el producto con id " + e.getKey()));
                producto.setStockActual(producto.getStockActual() + e.getValue());
                productoRepository.save(producto);
                movimientoRepository.save(new MovimientoStock(
                        producto, pedido.getUsuario(), TipoMovimiento.AJUSTE,
                        e.getValue(),
                        "Cancelacion - pedido #" + idPedido,
                        pedido));
            }
        }

        pedido.setEstado(nuevoEstado);
        return pedidoRepository.save(pedido);
    }
}
