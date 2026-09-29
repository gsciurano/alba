package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.*;
import com.albaenlosandes.inventario.repository.MovimientoStockRepository;
import com.albaenlosandes.inventario.repository.ProductoRepository;
import com.albaenlosandes.inventario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * ==========================================================================
 *  LOS MOVIMIENTOS DE STOCK: el historial del inventario.
 * ==========================================================================
 *
 * Esta clase tiene las cuatro operaciones, pero ninguna se comporta como en
 * una tabla comun, y el motivo es que un movimiento NO es un dato editable:
 * es un HECHO que ya ocurrio. Si se pudiera reescribir el historial, el
 * historial no serviria para auditar nada.
 *
 * Por eso:
 *
 *   CREAR     -> es un AJUSTE DE INVENTARIO (conteo fisico). No se "carga
 *                un movimiento": se corrige el stock, y el movimiento es la
 *                constancia de esa correccion.
 *
 *   LEER      -> normal.
 *
 *   MODIFICAR -> solo el MOTIVO. La cantidad, el tipo y el producto no se
 *                tocan: eso seria reescribir lo que paso. Corregir la
 *                redaccion de por que paso, en cambio, es legitimo.
 *
 *   BORRAR    -> es una ANULACION CON CONTRA-ASIENTO. No se borra la fila:
 *                se marca como anulada y se crea un movimiento con la
 *                cantidad opuesta, enlazado al original. Es exactamente lo
 *                que hace la contabilidad con un asiento equivocado.
 */
@Service
public class MovimientoStockService {

    private final MovimientoStockRepository repository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public MovimientoStockService(MovimientoStockRepository repository,
                                  ProductoRepository productoRepository,
                                  UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ==================================================================
    //  LEER
    // ==================================================================

    /** Trae los movimientos activos con su producto y su usuario en UNA sola consulta. */
    public List<MovimientoStock> obtenerTodos() {
        return repository.buscarTodosConDetalle();
    }

    /** Incluye los anulados. Es la vista completa de auditoria. */
    public List<MovimientoStock> obtenerTodosIncluyendoAnulados() {
        return repository.buscarTodosIncluyendoAnulados();
    }

    public Optional<MovimientoStock> obtenerPorId(Integer id) {
        return repository.findById(id);
    }

    /** Trazabilidad completa de un vino */
    public List<MovimientoStock> obtenerPorProducto(Integer idProducto) {
        return repository.buscarPorProductoConDetalle(idProducto);
    }

    // ==================================================================
    //  CREAR: el ajuste de inventario
    // ==================================================================

    /**
     * AJUSTE MANUAL DE INVENTARIO. Es la operacion del conteo fisico: se
     * cuentan las botellas del deposito, no coinciden con el sistema, y hay
     * que corregir la diferencia dejando constancia de por que.
     *
     * La cantidad puede ser positiva (aparecieron botellas) o negativa
     * (faltan botellas), pero nunca cero: un ajuste de cero no ajusta nada.
     *
     * @Transactional porque toca dos tablas: sube o baja el stock del
     * producto Y guarda el movimiento. Las dos cosas o ninguna.
     */
    @Transactional
    public MovimientoStock registrarAjuste(Integer idProducto, Integer cantidad,
                                           String motivo, Integer idUsuario) {
        if (cantidad == null || cantidad == 0) {
            throw new IllegalArgumentException(
                    "La cantidad del ajuste no puede ser cero: tiene que decir cuantas "
                    + "botellas sobran (positivo) o faltan (negativo).");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException(
                    "Un ajuste manual necesita un motivo: es lo unico que explica por que "
                    + "el stock del sistema no coincidia con el del deposito.");
        }

        // Se bloquea el producto, igual que en una venta: si alguien esta
        // comprando este vino al mismo tiempo, el ajuste espera su turno.
        Producto producto = productoRepository.buscarParaActualizar(idProducto)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el producto con id " + idProducto));
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario con id " + idUsuario));

        int nuevoStock = producto.getStockActual() + cantidad;
        if (nuevoStock < 0) {
            throw new IllegalArgumentException(
                    "El ajuste dejaria el stock de " + producto.getNombre() + " en "
                    + nuevoStock + ". El stock no puede quedar negativo "
                    + "(actual: " + producto.getStockActual() + ").");
        }

        producto.setStockActual(nuevoStock);
        productoRepository.save(producto);

        return repository.save(new MovimientoStock(
                producto, usuario, TipoMovimiento.AJUSTE, cantidad, motivo.trim(), null));
    }

    // ==================================================================
    //  MODIFICAR: solo el motivo
    // ==================================================================

    /**
     * Corrige la REDACCION del motivo. No toca la cantidad, ni el tipo, ni
     * el producto, ni la fecha.
     *
     * Ejemplo de uso legitimo: alguien cargo un ingreso con el motivo
     * "reposicion" y despues se sabe que era "devolucion de un cliente".
     * El hecho es el mismo; lo que cambia es la explicacion.
     *
     * Si se permitiera cambiar la cantidad, el stock del producto y la suma
     * de sus movimientos dejarian de coincidir, y toda la trazabilidad se
     * volveria mentira.
     */
    @Transactional
    public MovimientoStock actualizarMotivo(Integer idMovimiento, String nuevoMotivo) {
        if (nuevoMotivo == null || nuevoMotivo.isBlank()) {
            throw new IllegalArgumentException("El motivo no puede quedar vacio.");
        }
        MovimientoStock m = repository.findById(idMovimiento)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el movimiento con id " + idMovimiento));

        if (Boolean.FALSE.equals(m.getActivo())) {
            throw new IllegalArgumentException(
                    "El movimiento #" + idMovimiento + " esta anulado: no se puede modificar.");
        }
        m.setMotivo(nuevoMotivo.trim());
        return repository.save(m);
    }

    // ==================================================================
    //  BORRAR: anulacion con contra-asiento
    // ==================================================================

    /**
     * ANULA un movimiento. No borra nada:
     *
     *   1. marca el movimiento original como inactivo;
     *   2. crea un movimiento NUEVO con la cantidad opuesta, enlazado al
     *      original por el campo movimientoAnulado;
     *   3. corrige el stock del producto con esa cantidad opuesta.
     *
     * Despues de esto el historial muestra las dos filas: la equivocada y
     * la que la corrige. Eso es lo que permite auditar: se ve el error Y
     * se ve la correccion.
     *
     * @param idUsuario quien anula. Queda registrado en el contra-asiento.
     */
    @Transactional
    public MovimientoStock anular(Integer idMovimiento, String motivo, Integer idUsuario) {
        MovimientoStock original = repository.findById(idMovimiento)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el movimiento con id " + idMovimiento));

        if (Boolean.FALSE.equals(original.getActivo())) {
            throw new IllegalArgumentException(
                    "El movimiento #" + idMovimiento + " ya estaba anulado.");
        }

        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario con id " + idUsuario));

        // Se bloquea el producto antes de tocar su stock.
        Producto producto = productoRepository.buscarParaActualizar(
                        original.getProducto().getIdProducto())
                .orElseThrow(() -> new IllegalArgumentException("No existe el producto"));

        int opuesta = -original.getCantidad();
        int nuevoStock = producto.getStockActual() + opuesta;
        if (nuevoStock < 0) {
            throw new IllegalArgumentException(
                    "No se puede anular el movimiento #" + idMovimiento + ": el stock de "
                    + producto.getNombre() + " quedaria en " + nuevoStock + ". "
                    + "Probablemente esas botellas ya se vendieron.");
        }

        producto.setStockActual(nuevoStock);
        productoRepository.save(producto);

        original.setActivo(false);
        repository.save(original);

        String texto = (motivo == null || motivo.isBlank())
                ? "Anulacion del movimiento #" + idMovimiento
                : "Anulacion del movimiento #" + idMovimiento + ": " + motivo.trim();

        MovimientoStock contraAsiento = new MovimientoStock(
                producto, usuario, TipoMovimiento.AJUSTE, opuesta, texto,
                original.getPedidoRelacionado());
        contraAsiento.setMovimientoAnulado(original);
        return repository.save(contraAsiento);
    }
}
