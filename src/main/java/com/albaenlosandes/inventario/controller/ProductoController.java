package com.albaenlosandes.inventario.controller;

import com.albaenlosandes.inventario.model.Linea;
import com.albaenlosandes.inventario.model.Producto;
import com.albaenlosandes.inventario.service.ProductoService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * API REST del catalogo/inventario de vinos.
 * El Controller SOLO recibe peticiones HTTP y delega en el Service:
 * no contiene reglas de negocio ni SQL (separacion de responsabilidades).
 *
 * @CrossOrigin permite que el frontend (HTML/JS) consuma la API.
 */
@RestController
@RequestMapping("/api/productos")
@CrossOrigin(origins = "*")
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    /** GET /api/productos  -> catalogo completo (vinos activos) */
    @GetMapping
    public List<Producto> obtenerTodos() {
        return service.obtenerTodos();
    }

    /** GET /api/productos/5 -> un vino puntual */
    @GetMapping("/{id}")
    public ResponseEntity<Producto> obtenerPorId(@PathVariable Integer id) {
        return service.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el producto con id " + id));
    }

    /** GET /api/productos/linea/GRAN_RESERVA -> filtrar por linea */
    @GetMapping("/linea/{linea}")
    public List<Producto> obtenerPorLinea(@PathVariable Linea linea) {
        return service.obtenerPorLinea(linea);
    }

    /** GET /api/productos/buscar?nombre=malbec -> buscador */
    @GetMapping("/buscar")
    public List<Producto> buscar(@RequestParam String nombre) {
        return service.buscarPorNombre(nombre);
    }

    /**
     * GET /api/productos/precio?desde=20000 -> vinos de ese precio para arriba.
     * @RequestParam toma el valor que viene despues del '?' en la URL.
     * required=false + defaultValue: si no mandan nada, no falla.
     */
    @GetMapping("/precio")
    public List<Producto> buscarDesdePrecio(
            @RequestParam(required = false, defaultValue = "0") BigDecimal desde) {
        return service.buscarDesdePrecio(desde);
    }

    /** GET /api/productos/alertas -> vinos bajo stock minimo (panel admin) */
    @GetMapping("/alertas")
    public List<Producto> alertasStockMinimo() {
        return service.alertasStockMinimo();
    }

    /**
     * POST /api/productos -> alta de un vino nuevo.
     * @Valid dispara las validaciones de la entidad ANTES de entrar al metodo.
     * 201 CREATED es el codigo correcto cuando se crea un recurso nuevo (200 seria "ok" a secas).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Producto guardar(@Valid @RequestBody Producto producto) {
        return service.guardar(producto);
    }

    /** PUT /api/productos/5 -> modificar un vino existente */
    @PutMapping("/{id}")
    public ResponseEntity<Producto> actualizar(@PathVariable Integer id,
                                               @Valid @RequestBody Producto producto) {
        if (service.obtenerPorId(id).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe el producto con id " + id);
        }
        // El servicio mezcla los datos nuevos con los que ya estaban,
        // para no borrar en silencio los campos que el JSON no trajo.
        return ResponseEntity.ok(service.actualizar(id, producto));
    }

    /** DELETE /api/productos/5 -> baja logica (activo = false) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/productos/5/ingreso?cantidad=24&idUsuario=1&motivo=Reposicion
     * Ingreso de mercaderia: sube stock + registra movimiento ENTRADA.
     */
    @PostMapping("/{id}/ingreso")
    public Producto ingresarStock(@PathVariable Integer id,
                                  @RequestParam Integer cantidad,
                                  @RequestParam Integer idUsuario,
                                  @RequestParam(required = false) String motivo) {
        return service.ingresarStock(id, cantidad, motivo, idUsuario);
    }
}
