package com.albaenlosandes.inventario.controller;

import com.albaenlosandes.inventario.model.EstadoPedido;
import com.albaenlosandes.inventario.model.Pedido;
import com.albaenlosandes.inventario.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = "*")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    /** GET /api/pedidos -> todos los pedidos (panel de gestion) */
    @GetMapping
    public List<Pedido> obtenerTodos() {
        return service.obtenerTodos();
    }

    /** GET /api/pedidos/1 -> el ticket/comprobante de un pedido */
    @GetMapping("/{id}")
    public ResponseEntity<Pedido> obtenerPorId(@PathVariable Integer id) {
        return service.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido con id " + id));
    }

    /** GET /api/pedidos/usuario/2 -> historial de un cliente ("Mi cuenta") */
    @GetMapping("/usuario/{idUsuario}")
    public List<Pedido> obtenerPorUsuario(@PathVariable Integer idUsuario) {
        return service.obtenerPorUsuario(idUsuario);
    }

    /**
     * POST /api/pedidos -> CHECKOUT (tras el pago simulado).
     * Body de ejemplo:
     * {
     *   "usuario":   { "idUsuario": 2 },
     *   "medioPago": "TARJETA",
     *   "detalles": [
     *     { "producto": { "idProducto": 2 }, "cantidad": 2 },
     *     { "producto": { "idProducto": 5 }, "cantidad": 1 }
     *   ]
     * }
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido crear(@Valid @RequestBody Pedido pedido) {
        return service.crearPedido(pedido);
    }

    /** PUT /api/pedidos/1/estado?valor=ENTREGADO (o CANCELADO: devuelve stock) */
    @PutMapping("/{id}/estado")
    public Pedido cambiarEstado(@PathVariable Integer id,
                                @RequestParam EstadoPedido valor) {
        return service.cambiarEstado(id, valor);
    }
}
