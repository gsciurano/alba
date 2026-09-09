package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.MotivoCancelacion;
import com.albaenlosandes.inventario.model.PreguntaFrecuente;
import com.albaenlosandes.inventario.repository.PreguntaFrecuenteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** ABM de las preguntas frecuentes + la consulta que usa el formulario del cliente. */
@Service
public class PreguntaFrecuenteService {

    private final PreguntaFrecuenteRepository repository;

    public PreguntaFrecuenteService(PreguntaFrecuenteRepository repository) {
        this.repository = repository;
    }

    /** Todas las activas, para la pagina de ayuda completa. */
    public List<PreguntaFrecuente> obtenerActivas() {
        return repository.findByActivaTrueOrderByOrdenAsc();
    }

    public List<PreguntaFrecuente> obtenerTodas() {
        return repository.findAll();
    }

    public Optional<PreguntaFrecuente> obtenerPorId(Integer id) {
        return repository.findById(id);
    }

    /**
     * Las que corresponden al problema que eligio el cliente.
     * Primero las del motivo, despues las generales.
     */
    public List<PreguntaFrecuente> obtenerParaMotivo(MotivoCancelacion motivo) {
        if (motivo == null) return obtenerActivas();
        return repository.paraElMotivo(motivo);
    }

    public PreguntaFrecuente guardar(PreguntaFrecuente p) {
        return repository.save(p);
    }

    /** Baja logica: se conserva por si hay que reactivarla. */
    public void eliminar(Integer id) {
        PreguntaFrecuente p = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la pregunta con id " + id));
        p.setActiva(false);
        repository.save(p);
    }
}
