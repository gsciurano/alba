package com.albaenlosandes.inventario.controller;

import com.albaenlosandes.inventario.model.MotivoCancelacion;
import com.albaenlosandes.inventario.model.SolicitudCancelacion;
import com.albaenlosandes.inventario.service.PoliticaCancelacion;
import com.albaenlosandes.inventario.service.SolicitudCancelacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API del circuito de cancelacion pedida por el cliente.
 *
 *   GET  /api/solicitudes/evaluar    -> que pasaria si confirmo (no guarda nada)
 *   POST /api/solicitudes            -> pedir la cancelacion en firme
 *   GET  /api/solicitudes/usuario/2  -> el seguimiento del cliente
 *   GET  /api/solicitudes/pendientes -> la bandeja del administrador
 *   PUT  /api/solicitudes/5/resolver -> aprobar o rechazar
 */
@RestController
@RequestMapping("/api/solicitudes")
@CrossOrigin(origins = "*")
public class SolicitudCancelacionController {

    private final SolicitudCancelacionService service;

    public SolicitudCancelacionController(SolicitudCancelacionService service) {
        this.service = service;
    }

    /** GET /api/solicitudes -> todas (panel de gestion) */
    @GetMapping
    public List<SolicitudCancelacion> obtenerTodas() {
        return service.obtenerTodas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SolicitudCancelacion> obtenerPorId(@PathVariable Integer id) {
        return service.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud con id " + id));
    }

    /** GET /api/solicitudes/usuario/2 -> "mis solicitudes", el seguimiento */
    @GetMapping("/usuario/{idUsuario}")
    public List<SolicitudCancelacion> obtenerPorUsuario(@PathVariable Integer idUsuario) {
        return service.obtenerPorUsuario(idUsuario);
    }

    /** GET /api/solicitudes/pedido/3 -> historial de un pedido */
    @GetMapping("/pedido/{idPedido}")
    public List<SolicitudCancelacion> obtenerPorPedido(@PathVariable Integer idPedido) {
        return service.obtenerPorPedido(idPedido);
    }

    /** GET /api/solicitudes/pendientes -> lo que espera respuesta del admin */
    @GetMapping("/pendientes")
    public List<SolicitudCancelacion> obtenerPendientes() {
        return service.obtenerPendientes();
    }

    /**
     * GET /api/solicitudes/evaluar?idPedido=3&idUsuario=2&motivo=ARREPENTIMIENTO
     *
     * VISTA PREVIA: le dice al formulario que va a pasar si el cliente
     * confirma, sin guardar nada. Lo usa la pagina web para avisar
     * "esto se cancela al instante" o "esto pasa a revision" ANTES de
     * que el cliente apriete el boton.
     */
    @GetMapping("/evaluar")
    public PoliticaCancelacion.Evaluacion evaluar(@RequestParam Integer idPedido,
                                                  @RequestParam(required = false) Integer idUsuario,
                                                  @RequestParam MotivoCancelacion motivo) {
        return service.evaluar(idPedido, idUsuario, motivo);
    }

    /**
     * POST /api/solicitudes -> pedir la cancelacion en firme.
     * Segun el caso vuelve APROBADA (el pedido ya quedo cancelado)
     * o EN_REVISION (espera a un administrador).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudCancelacion crear(@RequestBody Map<String, Object> cuerpo) {
        return service.crear(
                entero(cuerpo.get("idPedido"), "idPedido"),
                cuerpo.get("idUsuario") == null ? null : entero(cuerpo.get("idUsuario"), "idUsuario"),
                motivo(cuerpo.get("motivo")),
                cuerpo.get("comentario") == null ? null : String.valueOf(cuerpo.get("comentario")));
    }

    /**
     * PUT /api/solicitudes/5/resolver?aprobar=true&idAdmin=1
     * Body: { "respuesta": "texto para el cliente" }
     */
    @PutMapping("/{id}/resolver")
    public SolicitudCancelacion resolver(@PathVariable Integer id,
                                         @RequestParam boolean aprobar,
                                         @RequestParam Integer idAdmin,
                                         @RequestBody Map<String, String> cuerpo) {
        return service.resolver(id, aprobar, cuerpo.get("respuesta"), idAdmin);
    }

    // ---- conversiones con mensajes claros ----

    private Integer entero(Object v, String campo) {
        if (v == null) throw new IllegalArgumentException("Falta el campo '" + campo + "'.");
        try {
            return v instanceof Number n ? n.intValue() : Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El campo '" + campo + "' tiene que ser un numero.");
        }
    }

    private MotivoCancelacion motivo(Object v) {
        if (v == null) throw new IllegalArgumentException(
                "Falta el campo 'motivo'. Valores validos: "
                + java.util.Arrays.toString(MotivoCancelacion.values()));
        try {
            return MotivoCancelacion.valueOf(String.valueOf(v).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El motivo '" + v + "' no existe. Valores validos: "
                    + java.util.Arrays.toString(MotivoCancelacion.values()));
        }
    }
}
