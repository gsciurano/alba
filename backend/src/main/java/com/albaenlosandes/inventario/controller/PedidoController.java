package com.albaenlosandes.inventario.controller;

import com.albaenlosandes.inventario.model.EstadoPedido;
import com.albaenlosandes.inventario.model.Pedido;
import com.albaenlosandes.inventario.service.PedidoService;
import com.albaenlosandes.inventario.service.ServicioCorreo;
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
    private final ServicioCorreo correo;

    public PedidoController(PedidoService service, ServicioCorreo correo) {
        this.service = service;
        this.correo = correo;
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
        Pedido guardado = service.crearPedido(pedido);
        /*
         * El comprobante se manda ACA y no adentro de crearPedido a
         * proposito: crearPedido es @Transactional, asi que cuando
         * devuelve, la compra YA ESTA COMPROMETIDA en la base. Si el
         * correo saliera desde adentro y la transaccion se revirtiera
         * (por ejemplo por falta de stock), el cliente recibiria el
         * comprobante de un pedido que no existe.
         *
         * El metodo es @Async y se traga sus propios errores: la compra
         * no se cae porque el servidor de correo no conteste.
         */
        correo.enviarComprobante(guardado);
        return guardado;
    }

    /**
     * DELETE /api/pedidos/3 -> baja logica del pedido.
     * Si estaba CONFIRMADO, primero lo cancela (lo que devuelve el stock)
     * y despues lo marca inactivo. La fila y su historial no se borran.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** PUT /api/pedidos/1/estado?valor=ENTREGADO (o CANCELADO: devuelve stock) */
    @PutMapping("/{id}/estado")
    public Pedido cambiarEstado(@PathVariable Integer id,
                                @RequestParam EstadoPedido valor) {
        return service.cambiarEstado(id, valor);
    }

    /**
     * PUT /api/pedidos/1/pago?confirmado=true&admin=1
     *
     * Marca que la transferencia de un pedido se acredito. Solo aplica a
     * pedidos pagados con TRANSFERENCIA; el resto se cobra en el acto.
     *
     * 'admin' es quien lo tilda y queda guardado con la fecha. Va como
     * parametro porque el proyecto todavia no tiene sesiones en el
     * servidor (ver la nota en UsuarioService): cuando las tenga, este
     * dato sale de la sesion y deja de viajar en la URL.
     */
    /**
     * POST /api/pedidos/1/comprobante -> vuelve a mandar el comprobante.
     *
     * POR QUE EXISTE: el envio es @Async y se traga sus errores para no
     * tirar abajo una compra. Si el correo falla (el servidor no
     * contestaba, el cliente escribio mal su direccion y despues la
     * corrigio), hasta ahora no habia forma de reintentarlo sin inventar
     * un pedido nuevo.
     *
     * Devuelve 202 (Accepted) y no 200: el envio sale en otro hilo, asi
     * que cuando esto contesta todavia no se sabe si llego. El resultado
     * queda en el log.
     */
    @PostMapping("/{id}/comprobante")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarComprobante(@PathVariable Integer id) {
        Pedido pedido = service.obtenerPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido con id " + id));
        correo.enviarComprobante(pedido);
    }

    @PutMapping("/{id}/pago")
    public Pedido confirmarPago(@PathVariable Integer id,
                                @RequestParam boolean confirmado,
                                @RequestParam(required = false) Integer admin) {
        return service.confirmarPago(id, confirmado, admin);
    }
}
