package com.albaenlosandes.inventario.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Entidad JPA: mapea la tabla 'solicitudes_cancelacion'.
 *
 * POR QUE EXISTE ESTA TABLA:
 * antes, cancelar un pedido era un acto instantaneo del administrador y no
 * quedaba registro de quien lo pidio ni por que. Ahora el pedido de
 * cancelacion es un dato mas del sistema: tiene autor, motivo, estado,
 * fechas y respuesta. Eso permite hacerle seguimiento.
 *
 * Es el mismo criterio que 'movimientos_stock': si algo importante pasa,
 * tiene que quedar escrito.
 */
@Entity
@Table(name = "solicitudes_cancelacion")
public class SolicitudCancelacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Integer idSolicitud;

    /** El pedido que se quiere cancelar. */
    @NotNull(message = "La solicitud debe indicar el pedido")
    @ManyToOne
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    /** Quien la pide. El servicio controla que sea el duenio del pedido. */
    @NotNull(message = "La solicitud debe indicar el usuario que la pide")
    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @NotNull(message = "El motivo es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoCancelacion motivo;

    @Size(max = 500, message = "El comentario no puede superar los 500 caracteres")
    @Column(length = 500)
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(name = "fecha_solicitud", insertable = false, updatable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    /** Lo que se le contesta al cliente. Lo escribe el admin o lo genera el sistema. */
    @Column(length = 1000)
    private String respuesta;

    /** Null cuando la resolvio el sistema solo (cancelacion automatica). */
    @ManyToOne
    @JoinColumn(name = "id_resuelto_por")
    private Usuario resueltoPor;

    /** true = la aprobo la regla automatica; false = la resolvio una persona. */
    @Column(name = "resuelta_automaticamente", nullable = false)
    private Boolean resueltaAutomaticamente = false;

    public SolicitudCancelacion() { }

    /** ¿Sigue abierta? Sirve para no dejar pedir dos veces lo mismo. */
    @Transient
    public boolean estaAbierta() {
        return estado == EstadoSolicitud.PENDIENTE || estado == EstadoSolicitud.EN_REVISION;
    }

    // ----- getters y setters -----
    public Integer getIdSolicitud() { return idSolicitud; }
    public void setIdSolicitud(Integer idSolicitud) { this.idSolicitud = idSolicitud; }
    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public MotivoCancelacion getMotivo() { return motivo; }
    public void setMotivo(MotivoCancelacion motivo) { this.motivo = motivo; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public EstadoSolicitud getEstado() { return estado; }
    public void setEstado(EstadoSolicitud estado) { this.estado = estado; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime f) { this.fechaSolicitud = f; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(LocalDateTime f) { this.fechaResolucion = f; }
    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
    public Usuario getResueltoPor() { return resueltoPor; }
    public void setResueltoPor(Usuario u) { this.resueltoPor = u; }
    public Boolean getResueltaAutomaticamente() { return resueltaAutomaticamente; }
    public void setResueltaAutomaticamente(Boolean b) { this.resueltaAutomaticamente = b; }
}
