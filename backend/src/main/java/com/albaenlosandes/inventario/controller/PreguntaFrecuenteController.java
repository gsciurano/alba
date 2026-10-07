package com.albaenlosandes.inventario.controller;

import com.albaenlosandes.inventario.model.MotivoCancelacion;
import com.albaenlosandes.inventario.model.PreguntaFrecuente;
import com.albaenlosandes.inventario.service.PreguntaFrecuenteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API de las preguntas frecuentes.
 *
 * La usa el formulario de la pagina web: cuando el cliente elige el motivo
 * de su problema, el frontend pide las preguntas de ESE motivo y se las
 * muestra. La idea es que muchas veces resuelva ahi mismo y no tenga que
 * mandar ninguna solicitud.
 *
 *   GET /api/faq                          -> todas las activas
 *   GET /api/faq/motivo/DEMORA_ENTREGA    -> las de ese problema + las generales
 *   POST/PUT/DELETE                       -> ABM para el administrador
 */
@RestController
@RequestMapping("/api/faq")
@CrossOrigin(origins = "*")
public class PreguntaFrecuenteController {

    private final PreguntaFrecuenteService service;

    public PreguntaFrecuenteController(PreguntaFrecuenteService service) {
        this.service = service;
    }

    @GetMapping
    public List<PreguntaFrecuente> obtenerActivas() {
        return service.obtenerActivas();
    }

    /** Incluye las inactivas: solo para el panel de gestion. */
    @GetMapping("/todas")
    public List<PreguntaFrecuente> obtenerTodas() {
        return service.obtenerTodas();
    }

    /** Las que le sirven al cliente segun el problema que eligio. */
    @GetMapping("/motivo/{motivo}")
    public List<PreguntaFrecuente> obtenerPorMotivo(@PathVariable MotivoCancelacion motivo) {
        return service.obtenerParaMotivo(motivo);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreguntaFrecuente> obtenerPorId(@PathVariable Integer id) {
        return service.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la pregunta con id " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PreguntaFrecuente crear(@Valid @RequestBody PreguntaFrecuente p) {
        return service.guardar(p);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PreguntaFrecuente> actualizar(@PathVariable Integer id,
                                                        @Valid @RequestBody PreguntaFrecuente p) {
        if (service.obtenerPorId(id).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe la pregunta con id " + id);
        }
        p.setIdPregunta(id);
        return ResponseEntity.ok(service.guardar(p));
    }

    /** Baja logica: deja de mostrarse pero se conserva. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
