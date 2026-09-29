package com.albaenlosandes.inventario.controller;

import com.albaenlosandes.inventario.model.DetallePedido;
import com.albaenlosandes.inventario.service.DetallePedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API de los renglones de un pedido.
 *
 * Permite modificar la composicion de un pedido ya confirmado: agregarle un
 * vino, cambiarle la cantidad a uno, o sacarle uno. Cada operacion ajusta el
 * stock, deja el movimiento correspondiente y recalcula el total del pedido.
 *
 *   GET    /api/detalles/pedido/{idPedido}   los renglones de un pedido
 *   GET    /api/detalles/{id}                un renglon
 *   POST   /api/detalles                     agregar un vino al pedido
 *   PUT    /api/detalles/{id}?cantidad=3     cambiar la cantidad
 *   DELETE /api/detalles/{id}                sacar el vino del pedido
 */
@RestController
@RequestMapping("/api/detalles")
@CrossOrigin(origins = "*")
public class DetallePedidoController {

    private final DetallePedidoService service;

    public DetallePedidoController(DetallePedidoService service) {
        this.service = service;
    }

    /** GET /api/detalles/pedido/3 -> los renglones de ese pedido */
    @GetMapping("/pedido/{idPedido}")
    public List<DetallePedido> obtenerPorPedido(@PathVariable Integer idPedido) {
        return service.obtenerPorPedido(idPedido);
    }

    /** GET /api/detalles/7 -> un renglon puntual */
    @GetMapping("/{id}")
    public DetallePedido obtenerPorId(@PathVariable Integer id) {
        return service.obtenerPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el renglon con id " + id));
    }

    /**
     * POST /api/detalles?idPedido=3&idProducto=5&cantidad=2
     * Agrega un vino a un pedido CONFIRMADO. Si el vino ya estaba, suma a
     * ese renglon en vez de crear uno repetido.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DetallePedido agregar(@RequestParam Integer idPedido,
                                 @RequestParam Integer idProducto,
                                 @RequestParam Integer cantidad) {
        return service.agregar(idPedido, idProducto, cantidad);
    }

    /** PUT /api/detalles/7?cantidad=5 -> cambia la cantidad y ajusta el stock */
    @PutMapping("/{id}")
    public DetallePedido cambiarCantidad(@PathVariable Integer id,
                                         @RequestParam Integer cantidad) {
        return service.cambiarCantidad(id, cantidad);
    }

    /** DELETE /api/detalles/7 -> saca el vino del pedido y devuelve su stock */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> quitar(@PathVariable Integer id) {
        service.quitar(id);
        return ResponseEntity.noContent().build();
    }
}
