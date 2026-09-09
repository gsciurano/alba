package com.albaenlosandes.inventario.repository;

import com.albaenlosandes.inventario.model.MotivoCancelacion;
import com.albaenlosandes.inventario.model.PreguntaFrecuente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PreguntaFrecuenteRepository extends JpaRepository<PreguntaFrecuente, Integer> {

    List<PreguntaFrecuente> findByActivaTrueOrderByOrdenAsc();

    /**
     * Las que se le muestran al cliente cuando elige un motivo:
     * las especificas de ese motivo MAS las generales (motivo nulo).
     *
     * Necesita @Query porque hay que mezclar dos condiciones con OR sobre
     * el mismo campo, incluyendo el caso "es nulo". Un nombre de metodo
     * derivado no puede expresar eso de forma legible.
     */
    @Query("""
           SELECT p FROM PreguntaFrecuente p
           WHERE p.activa = true
             AND (p.motivo = :motivo OR p.motivo IS NULL)
           ORDER BY CASE WHEN p.motivo IS NULL THEN 1 ELSE 0 END, p.orden
           """)
    List<PreguntaFrecuente> paraElMotivo(@Param("motivo") MotivoCancelacion motivo);
}
