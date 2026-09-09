package com.albaenlosandes.inventario.repository;

import com.albaenlosandes.inventario.model.EstadoSolicitud;
import com.albaenlosandes.inventario.model.SolicitudCancelacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SolicitudCancelacionRepository extends JpaRepository<SolicitudCancelacion, Integer> {

    /**
     * Cada solicitud arrastra su usuario, su pedido y los renglones del pedido.
     * Sin JOIN FETCH, la ficha del reclamo disparaba una consulta por cada
     * relacion de cada fila (el problema "N+1").
     */
    @Query("""
           SELECT DISTINCT s FROM SolicitudCancelacion s
           LEFT JOIN FETCH s.usuario
           LEFT JOIN FETCH s.resueltoPor
           LEFT JOIN FETCH s.pedido p
           LEFT JOIN FETCH p.detalles d
           LEFT JOIN FETCH d.producto
           ORDER BY s.fechaSolicitud DESC
           """)
    List<SolicitudCancelacion> buscarTodasConDetalle();

    /** Historial de un pedido, de la mas nueva a la mas vieja. */
    List<SolicitudCancelacion> findByPedidoIdPedidoOrderByFechaSolicitudDesc(Integer idPedido);

    /** "Mis solicitudes": el seguimiento que ve el cliente. */
    List<SolicitudCancelacion> findByUsuarioIdUsuarioOrderByFechaSolicitudDesc(Integer idUsuario);

    /** Bandeja del administrador: las que esperan respuesta, la mas vieja primero. */
    List<SolicitudCancelacion> findByEstadoInOrderByFechaSolicitudAsc(List<EstadoSolicitud> estados);

    /**
     * ¿Este pedido ya tiene una solicitud abierta? Evita que el cliente mande
     * la misma cancelacion cinco veces. Se necesita @Query porque hay que
     * preguntar por DOS estados a la vez sobre una relacion anidada.
     */
    @Query("""
           SELECT COUNT(s) > 0 FROM SolicitudCancelacion s
           WHERE s.pedido.idPedido = :idPedido
             AND s.estado IN (com.albaenlosandes.inventario.model.EstadoSolicitud.PENDIENTE,
                              com.albaenlosandes.inventario.model.EstadoSolicitud.EN_REVISION)
           """)
    boolean tieneSolicitudAbierta(Integer idPedido);
}
