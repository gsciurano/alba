package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.*;
import com.albaenlosandes.inventario.repository.PedidoRepository;
import com.albaenlosandes.inventario.repository.SolicitudCancelacionRepository;
import com.albaenlosandes.inventario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ==========================================================================
 *  LAS REGLAS DEL CIRCUITO DE CANCELACION.
 * ==========================================================================
 *
 * Que resuelve esta clase:
 *
 *  1. Que solo el DUENIO del pedido pueda pedir cancelarlo.
 *  2. Que no se pueda pedir sobre un pedido ya entregado o ya cancelado.
 *  3. Que no se acumulen solicitudes repetidas del mismo pedido.
 *  4. La CANCELACION DIRECTA: si el pedido es reciente y el motivo es simple,
 *     se aprueba sola y el cliente no espera a nadie.
 *  5. Que aprobar una solicitud cancele el pedido de verdad, con todo lo que
 *     eso implica (devolver stock y dejar el movimiento de AJUSTE).
 *
 * Reutiliza PedidoService en vez de repetir su logica: la devolucion de stock
 * ya estaba resuelta ahi y no hay que escribirla dos veces.
 */
@Service
public class SolicitudCancelacionService {

    private final SolicitudCancelacionRepository solicitudRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PedidoService pedidoService;
    private final PoliticaCancelacion politica;

    public SolicitudCancelacionService(SolicitudCancelacionRepository solicitudRepository,
                                       PedidoRepository pedidoRepository,
                                       UsuarioRepository usuarioRepository,
                                       PedidoService pedidoService,
                                       PoliticaCancelacion politica) {
        this.solicitudRepository = solicitudRepository;
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.pedidoService = pedidoService;
        this.politica = politica;
    }

    // ==================================================================
    //  CONSULTAS
    // ==================================================================

    public List<SolicitudCancelacion> obtenerTodas() {
        return solicitudRepository.buscarTodasConDetalle();
    }

    public Optional<SolicitudCancelacion> obtenerPorId(Integer id) {
        return solicitudRepository.findById(id);
    }

    /** El seguimiento del cliente: "mis solicitudes". */
    public List<SolicitudCancelacion> obtenerPorUsuario(Integer idUsuario) {
        return solicitudRepository.findByUsuarioIdUsuarioOrderByFechaSolicitudDesc(idUsuario);
    }

    public List<SolicitudCancelacion> obtenerPorPedido(Integer idPedido) {
        return solicitudRepository.findByPedidoIdPedidoOrderByFechaSolicitudDesc(idPedido);
    }

    /** Bandeja del administrador: lo que espera respuesta. */
    public List<SolicitudCancelacion> obtenerPendientes() {
        return solicitudRepository.findByEstadoInOrderByFechaSolicitudAsc(
                List.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.EN_REVISION));
    }

    // ==================================================================
    //  VISTA PREVIA (no guarda nada)
    // ==================================================================

    /**
     * Le dice al cliente que va a pasar ANTES de que confirme, para que no
     * tenga que probar a ver. Lo usa el formulario de la pagina web para
     * mostrar "esto se cancela al instante" o "esto pasa a revision".
     */
    public PoliticaCancelacion.Evaluacion evaluar(Integer idPedido, Integer idUsuario,
                                                  MotivoCancelacion motivo) {
        Pedido pedido = buscarPedidoDelUsuario(idPedido, idUsuario);
        return politica.evaluar(pedido, motivo);
    }

    // ==================================================================
    //  CREAR LA SOLICITUD
    // ==================================================================

    /**
     * El cliente pide cancelar. Segun el caso, esto termina de dos formas:
     *   - APROBADA en el acto (y el pedido queda cancelado), o
     *   - EN_REVISION esperando a un administrador.
     *
     * @Transactional: cancelar el pedido toca varias tablas (pedidos,
     * productos y movimientos_stock). Si algo falla, no puede quedar la
     * solicitud aprobada con el stock sin devolver.
     */
    @Transactional
    public SolicitudCancelacion crear(Integer idPedido, Integer idUsuario,
                                      MotivoCancelacion motivo, String comentario) {

        Pedido pedido = buscarPedidoDelUsuario(idPedido, idUsuario);

        // Regla 2: la politica dice si sobre este pedido tiene sentido pedir algo
        PoliticaCancelacion.Evaluacion evaluacion = politica.evaluar(pedido, motivo);
        if (!evaluacion.sePuedePedir()) {
            throw new IllegalArgumentException(evaluacion.explicacion());
        }

        // Regla 3: una sola solicitud abierta por pedido
        if (solicitudRepository.tieneSolicitudAbierta(idPedido)) {
            throw new IllegalArgumentException(
                    "El pedido #" + idPedido + " ya tiene una solicitud de cancelacion en curso. "
                    + "Podes seguir su estado desde tus solicitudes.");
        }

        Usuario usuario = pedido.getUsuario();

        SolicitudCancelacion s = new SolicitudCancelacion();
        s.setPedido(pedido);
        s.setUsuario(usuario);
        s.setMotivo(motivo);
        s.setComentario(comentario);

        // Regla 4: LA CANCELACION DIRECTA.
        // La decide la politica, no el cliente ni la interfaz: cancelar
        // mueve stock y plata, y esa decision vive en el servidor.
        if (evaluacion.cancelacionDirecta()) {

            // Regla 5: cancelar de verdad. PedidoService devuelve el stock
            // y registra el movimiento de AJUSTE. No lo repetimos aca.
            pedidoService.cambiarEstado(idPedido, EstadoPedido.CANCELADO);

            s.setEstado(EstadoSolicitud.APROBADA);
            s.setResueltaAutomaticamente(true);
            s.setFechaResolucion(LocalDateTime.now());
            s.setRespuesta("Aprobada automaticamente. " + evaluacion.explicacion()
                    + " El pedido quedo cancelado y las botellas volvieron al stock.");
        } else {
            s.setEstado(EstadoSolicitud.EN_REVISION);
            s.setRespuesta(null);
        }

        return solicitudRepository.save(s);
    }

    // ==================================================================
    //  RESOLVER (el administrador)
    // ==================================================================

    /**
     * Un administrador aprueba o rechaza una solicitud que quedo en revision.
     * Si la aprueba, recien ahi el pedido se cancela y vuelve el stock.
     */
    @Transactional
    public SolicitudCancelacion resolver(Integer idSolicitud, boolean aprobar,
                                         String respuesta, Integer idAdmin) {

        SolicitudCancelacion s = solicitudRepository.findById(idSolicitud)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la solicitud con id " + idSolicitud));

        if (!s.estaAbierta()) {
            throw new IllegalArgumentException(
                    "La solicitud #" + idSolicitud + " ya fue resuelta (" + s.getEstado() + ").");
        }

        Usuario admin = usuarioRepository.findById(idAdmin)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario con id " + idAdmin));
        if (admin.getRol() != Rol.ADMIN) {
            throw new IllegalArgumentException(
                    "Solo un ADMIN puede resolver solicitudes de cancelacion.");
        }
        if (respuesta == null || respuesta.isBlank()) {
            throw new IllegalArgumentException(
                    "Hay que escribir una respuesta para el cliente.");
        }

        if (aprobar) {
            // Puede haberse cancelado por otra via mientras tanto.
            if (s.getPedido().getEstado() != EstadoPedido.CANCELADO) {
                pedidoService.cambiarEstado(s.getPedido().getIdPedido(), EstadoPedido.CANCELADO);
            }
            s.setEstado(EstadoSolicitud.APROBADA);
        } else {
            s.setEstado(EstadoSolicitud.RECHAZADA);
        }

        s.setRespuesta(respuesta.trim());
        s.setResueltoPor(admin);
        s.setResueltaAutomaticamente(false);
        s.setFechaResolucion(LocalDateTime.now());
        return solicitudRepository.save(s);
    }

    // ==================================================================
    //  AYUDANTES
    // ==================================================================

    /** Regla 1: el pedido tiene que existir Y ser del usuario que reclama. */
    private Pedido buscarPedidoDelUsuario(Integer idPedido, Integer idUsuario) {
        Pedido pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el pedido con id " + idPedido));

        if (idUsuario != null && pedido.getUsuario() != null
                && !pedido.getUsuario().getIdUsuario().equals(idUsuario)) {
            throw new IllegalArgumentException(
                    "El pedido #" + idPedido + " no pertenece a ese usuario. "
                    + "Solo se puede pedir la cancelacion de los pedidos propios.");
        }
        return pedido;
    }
}
