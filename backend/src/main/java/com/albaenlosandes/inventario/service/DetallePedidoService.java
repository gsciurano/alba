package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.*;
import com.albaenlosandes.inventario.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * ==========================================================================
 *  LOS RENGLONES DE UN PEDIDO.
 * ==========================================================================
 *
 * Permite modificar un pedido YA CONFIRMADO: agregarle un vino, cambiarle
 * la cantidad a uno, o sacarle uno. Es lo que necesita la bodega cuando el
 * cliente llama y dice "agregame dos botellas mas antes de que salga".
 *
 * TODAS las operaciones de esta clase tienen que hacer cuatro cosas juntas,
 * y por eso todas son @Transactional:
 *
 *   1. ajustar el stock del vino afectado,
 *   2. dejar el movimiento que explica ese ajuste,
 *   3. recalcular el total del pedido,
 *   4. guardar el renglon.
 *
 * Si alguna fallara sin las otras, el pedido quedaria con un total que no
 * coincide con sus renglones, o con stock descontado que nadie compro.
 *
 * REGLA TRANSVERSAL: solo se puede tocar un pedido CONFIRMADO. Uno ENTREGADO
 * ya salio de la bodega y uno CANCELADO ya devolvio su stock; modificarlos
 * seria cambiar el pasado.
 */
@Service
public class DetallePedidoService {

    private final DetallePedidoRepository detalleRepository;
    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final MovimientoStockRepository movimientoRepository;

    public DetallePedidoService(DetallePedidoRepository detalleRepository,
                                PedidoRepository pedidoRepository,
                                ProductoRepository productoRepository,
                                MovimientoStockRepository movimientoRepository) {
        this.detalleRepository = detalleRepository;
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
    }

    // ==================================================================
    //  LEER
    // ==================================================================

    public List<DetallePedido> obtenerPorPedido(Integer idPedido) {
        return detalleRepository.buscarPorPedido(idPedido);
    }

    public Optional<DetallePedido> obtenerPorId(Integer id) {
        return detalleRepository.buscarPorIdConDetalle(id);
    }

    // ==================================================================
    //  CREAR: agregar un vino a un pedido existente
    // ==================================================================

    @Transactional
    public DetallePedido agregar(Integer idPedido, Integer idProducto, Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }
        Pedido pedido = buscarPedidoModificable(idPedido);
        Producto producto = bloquear(idProducto);

        if (producto.getStockActual() < cantidad) {
            throw new IllegalArgumentException("Stock insuficiente de " + producto.getNombre()
                    + " (disponible: " + producto.getStockActual() + ", pedido: " + cantidad + ")");
        }

        // Si el vino ya esta en el pedido, se suma a ese renglon en vez de
        // crear uno repetido: un pedido con el mismo vino dos veces es confuso
        // y ademas complica el recalculo.
        for (DetallePedido existente : pedido.getDetalles()) {
            if (existente.getProducto().getIdProducto().equals(idProducto)) {
                return cambiarCantidad(existente.getIdDetalle(),
                        existente.getCantidad() + cantidad);
            }
        }

        DetallePedido detalle = new DetallePedido();
        detalle.setPedido(pedido);
        detalle.setProducto(producto);
        detalle.setCantidad(cantidad);
        // El precio se congela AHORA, igual que en el checkout.
        detalle.setPrecioUnitario(producto.getPrecio());
        DetallePedido guardado = detalleRepository.save(detalle);

        aplicarAlStock(producto, -cantidad, TipoMovimiento.SALIDA,
                "Agregado al pedido #" + idPedido, pedido);
        pedido.getDetalles().add(guardado);
        recalcularTotal(pedido);
        return guardado;
    }

    // ==================================================================
    //  MODIFICAR: cambiar la cantidad de un renglon
    // ==================================================================

    @Transactional
    public DetallePedido cambiarCantidad(Integer idDetalle, Integer nuevaCantidad) {
        if (nuevaCantidad == null || nuevaCantidad <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser mayor a cero. Para sacar el vino del pedido, "
                    + "usar la operacion de borrado.");
        }
        DetallePedido detalle = detalleRepository.findById(idDetalle)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el renglon con id " + idDetalle));
        Pedido pedido = buscarPedidoModificable(detalle.getPedido().getIdPedido());
        Producto producto = bloquear(detalle.getProducto().getIdProducto());

        int diferencia = nuevaCantidad - detalle.getCantidad();
        if (diferencia == 0) return detalle;

        // Si la diferencia es positiva hay que sacar mas botellas del stock;
        // si es negativa, hay que devolverlas.
        if (diferencia > 0 && producto.getStockActual() < diferencia) {
            throw new IllegalArgumentException("Stock insuficiente de " + producto.getNombre()
                    + " para subir a " + nuevaCantidad + " (disponible: "
                    + producto.getStockActual() + ", faltan: " + diferencia + ")");
        }

        detalle.setCantidad(nuevaCantidad);
        detalleRepository.save(detalle);

        aplicarAlStock(producto, -diferencia,
                diferencia > 0 ? TipoMovimiento.SALIDA : TipoMovimiento.AJUSTE,
                "Cambio de cantidad en el pedido #" + pedido.getIdPedido()
                        + " (de " + (nuevaCantidad - diferencia) + " a " + nuevaCantidad + ")",
                pedido);
        recalcularTotal(pedido);
        return detalle;
    }

    // ==================================================================
    //  BORRAR: sacar un vino del pedido
    // ==================================================================

    /**
     * Aca si se borra la fila de verdad, y es la unica excepcion del sistema.
     * El motivo: un renglon no es un hecho historico, es parte de la
     * composicion del pedido. Lo que SI queda registrado es el movimiento de
     * AJUSTE que devuelve las botellas al stock.
     */
    @Transactional
    public void quitar(Integer idDetalle) {
        DetallePedido detalle = detalleRepository.findById(idDetalle)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el renglon con id " + idDetalle));
        Pedido pedido = buscarPedidoModificable(detalle.getPedido().getIdPedido());

        // Un pedido sin renglones no tiene sentido: si es el ultimo, hay que
        // cancelar el pedido entero en vez de vaciarlo.
        if (detalleRepository.countByPedidoIdPedido(pedido.getIdPedido()) <= 1) {
            throw new IllegalArgumentException(
                    "No se puede dejar el pedido #" + pedido.getIdPedido() + " sin renglones. "
                    + "Si el cliente ya no quiere nada, corresponde cancelar el pedido.");
        }

        Producto producto = bloquear(detalle.getProducto().getIdProducto());
        int cantidad = detalle.getCantidad();

        pedido.getDetalles().removeIf(d -> d.getIdDetalle().equals(idDetalle));
        detalleRepository.delete(detalle);

        aplicarAlStock(producto, cantidad, TipoMovimiento.AJUSTE,
                "Renglon quitado del pedido #" + pedido.getIdPedido(), pedido);
        recalcularTotal(pedido);
    }

    // ==================================================================
    //  AYUDANTES
    // ==================================================================

    /** Solo se puede tocar un pedido CONFIRMADO y activo. */
    private Pedido buscarPedidoModificable(Integer idPedido) {
        Pedido pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el pedido con id " + idPedido));
        if (Boolean.FALSE.equals(pedido.getActivo())) {
            throw new IllegalArgumentException(
                    "El pedido #" + idPedido + " esta dado de baja.");
        }
        if (pedido.getEstado() != EstadoPedido.CONFIRMADO) {
            throw new IllegalArgumentException(
                    "Solo se puede modificar un pedido CONFIRMADO. El #" + idPedido
                    + " esta " + pedido.getEstado() + ".");
        }
        return pedido;
    }

    private Producto bloquear(Integer idProducto) {
        return productoRepository.buscarParaActualizar(idProducto)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el producto con id " + idProducto));
    }

    /** Suma la cantidad al stock (puede ser negativa) y deja el movimiento. */
    private void aplicarAlStock(Producto producto, int cantidad, TipoMovimiento tipo,
                                String motivo, Pedido pedido) {
        if (cantidad == 0) return;
        producto.setStockActual(producto.getStockActual() + cantidad);
        productoRepository.save(producto);
        movimientoRepository.save(new MovimientoStock(
                producto, pedido.getUsuario(), tipo, cantidad, motivo, pedido));
    }

    /**
     * Vuelve a sumar todos los renglones. Se usa el precio CONGELADO de cada
     * uno, no el precio actual del catalogo: si el vino cambio de precio
     * despues de la compra, el pedido no se altera.
     */
    private void recalcularTotal(Pedido pedido) {
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedido d : detalleRepository.buscarPorPedido(pedido.getIdPedido())) {
            total = total.add(d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad())));
        }
        pedido.setTotal(total);
        pedidoRepository.save(pedido);
    }
}
