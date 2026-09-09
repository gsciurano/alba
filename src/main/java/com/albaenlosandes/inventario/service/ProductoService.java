package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.*;
import com.albaenlosandes.inventario.repository.MovimientoStockRepository;
import com.albaenlosandes.inventario.repository.ProductoRepository;
import com.albaenlosandes.inventario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Capa de negocio de productos.
 * El Repository habla con la base; el Service aplica las REGLAS.
 * Spring inyecta las dependencias por constructor (Dependency Injection).
 */
@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final MovimientoStockRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;

    public ProductoService(ProductoRepository productoRepository,
                           MovimientoStockRepository movimientoRepository,
                           UsuarioRepository usuarioRepository) {
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<Producto> obtenerTodos() {
        return productoRepository.findByActivoTrue();
    }

    public Optional<Producto> obtenerPorId(Integer id) {
        return productoRepository.findById(id);
    }

    public List<Producto> obtenerPorLinea(Linea linea) {
        return productoRepository.findByLineaAndActivoTrue(linea);
    }

    public List<Producto> buscarPorNombre(String texto) {
        return productoRepository.findByNombreContainingIgnoreCaseAndActivoTrue(texto);
    }

    /**
     * Vinos de cierto precio para arriba. La REGLA vive aca, no en el repositorio:
     * si no mandan un precio, se asume cero (o sea, trae todo el catalogo).
     */
    public List<Producto> buscarDesdePrecio(BigDecimal precioMinimo) {
        BigDecimal desde = (precioMinimo == null) ? BigDecimal.ZERO : precioMinimo;
        if (desde.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio minimo no puede ser negativo");
        }
        return productoRepository.findByPrecioGreaterThanEqualAndActivoTrueOrderByPrecioDesc(desde);
    }

    /** Alerta del panel de gestion: vinos que hay que reponer */
    public List<Producto> alertasStockMinimo() {
        return productoRepository.alertasStockMinimo();
    }

    public Producto guardar(Producto producto) {
        return productoRepository.save(producto);
    }

    /**
     * ==================================================================
     *  MODIFICACION QUE NO PISA LO QUE NO SE MANDO.
     * ==================================================================
     *
     * QUE PASABA ANTES: el controlador recibia un Producto armado desde el
     * JSON y lo guardaba tal cual. Si el JSON no traia la descripcion, la
     * anada o la imagen, esos campos llegaban en null y se guardaban en null:
     * el vino perdia datos en silencio, sin ningun aviso.
     *
     * COMO FUNCIONA AHORA: se carga el vino que YA esta en la base y se
     * copian encima solo los campos que vinieron. Los que no vinieron
     * conservan su valor actual.
     *
     * PARA BORRAR UN CAMPO a proposito hay que mandarlo vacio (""), no
     * omitirlo: "no lo mande" y "quiero borrarlo" son cosas distintas.
     */
    @Transactional
    public Producto actualizar(Integer id, Producto datos) {
        Producto actual = productoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el producto con id " + id));

        if (datos.getNombre() != null)       actual.setNombre(datos.getNombre());
        if (datos.getLinea() != null)        actual.setLinea(datos.getLinea());
        if (datos.getVarietal() != null)     actual.setVarietal(datos.getVarietal());
        if (datos.getAnada() != null)        actual.setAnada(datos.getAnada());
        if (datos.getDescripcion() != null)  actual.setDescripcion(vacioANull(datos.getDescripcion()));
        if (datos.getPrecio() != null)       actual.setPrecio(datos.getPrecio());
        if (datos.getStockActual() != null)  actual.setStockActual(datos.getStockActual());
        if (datos.getStockMinimo() != null)  actual.setStockMinimo(datos.getStockMinimo());
        if (datos.getImagenUrl() != null)    actual.setImagenUrl(vacioANull(datos.getImagenUrl()));
        if (datos.getActivo() != null)       actual.setActivo(datos.getActivo());

        return productoRepository.save(actual);
    }

    /** Una cadena vacia significa "borrame este dato". */
    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * BAJA LOGICA (no DELETE fisico): el producto deja de mostrarse
     * pero su historial de ventas y movimientos se conserva.
     * Un DELETE real romperia las claves foraneas de pedidos historicos.
     */
    public void eliminar(Integer id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el producto con id " + id));
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    /**
     * Ingreso de mercaderia: sube el stock Y registra el movimiento
     * de ENTRADA (inventario perpetuo: nada cambia sin dejar rastro).
     */
    @Transactional
    public Producto ingresarStock(Integer idProducto, Integer cantidad, String motivo, Integer idUsuario) {
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a ingresar debe ser mayor a cero");
        }
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new IllegalArgumentException("No existe el producto con id " + idProducto));
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + idUsuario));

        producto.setStockActual(producto.getStockActual() + cantidad);
        productoRepository.save(producto);

        String detalle = (motivo == null || motivo.isBlank()) ? "Ingreso de mercaderia" : motivo;
        movimientoRepository.save(new MovimientoStock(
                producto, usuario, TipoMovimiento.ENTRADA, cantidad, detalle, null));

        return producto;
    }
}
