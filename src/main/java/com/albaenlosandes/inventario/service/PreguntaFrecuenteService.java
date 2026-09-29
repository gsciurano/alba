package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.MotivoCancelacion;
import com.albaenlosandes.inventario.model.PreguntaFrecuente;
import com.albaenlosandes.inventario.repository.PreguntaFrecuenteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Capa de negocio de las preguntas frecuentes.
 *
 * PARA QUE SIRVE ESTA ENTIDAD:
 * cuando un cliente quiere cancelar un pedido, el formulario le muestra las
 * preguntas relacionadas con el problema que eligio. Muchas veces lee la
 * respuesta ("los envios tardan 5 a 7 dias habiles") y ya no necesita mandar
 * el reclamo. Es lo que hace que el tramite no sea tedioso.
 *
 * POR QUE ESTAN EN LA BASE Y NO ESCRITAS EN EL CODIGO:
 *   1. la bodega corrige un texto sin que nadie recompile ni despliegue;
 *   2. la ventana de escritorio y la futura pagina web leen las MISMAS
 *      preguntas. Si estuvieran escritas en cada interfaz, tarde o temprano
 *      dirian cosas distintas.
 */
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

    /**
     * Incluye tambien las dadas de baja. Solo la usa el panel de gestion,
     * para poder reactivar una pregunta que se habia quitado.
     */
    public List<PreguntaFrecuente> obtenerTodas() {
        return repository.findAll();
    }

    /** Devuelve un Optional: obliga a quien llama a contemplar que no exista. */
    public Optional<PreguntaFrecuente> obtenerPorId(Integer id) {
        return repository.findById(id);
    }

    /**
     * LA CONSULTA QUE USA EL FORMULARIO DEL CLIENTE.
     *
     * Devuelve las preguntas de ese motivo MAS las generales (las que tienen
     * el motivo en null y se muestran siempre), con las especificas primero.
     *
     * Si no se indica motivo, se devuelven todas las activas: es el caso de
     * la pagina de ayuda completa, donde todavia no eligio un problema.
     */
    public List<PreguntaFrecuente> obtenerParaMotivo(MotivoCancelacion motivo) {
        if (motivo == null) return obtenerActivas();
        return repository.paraElMotivo(motivo);
    }

    /**
     * Sirve para el alta y para la modificacion: si el objeto trae id,
     * Hibernate actualiza; si no lo trae, inserta. Es el comportamiento
     * estandar de save() en JPA.
     */
    public PreguntaFrecuente guardar(PreguntaFrecuente p) {
        return repository.save(p);
    }

    /**
     * BAJA LOGICA, igual que en productos y usuarios: la pregunta deja de
     * mostrarse al cliente pero la fila queda, por si hay que reactivarla o
     * por si se quiere saber que decia antes.
     */
    public void eliminar(Integer id) {
        PreguntaFrecuente p = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la pregunta con id " + id));
        p.setActiva(false);
        repository.save(p);
    }
}
