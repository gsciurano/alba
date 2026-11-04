package com.albaenlosandes.inventario.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA: mapea la tabla 'pedidos'.
 * Relaciones:
 *  - ManyToOne con Usuario (muchos pedidos pertenecen a un usuario)
 *  - OneToMany con DetallePedido (un pedido tiene muchos renglones)
 */
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Integer idPedido;

    /** Validacion de entrada (requisito 8 del TP). */
    @NotNull(message = "El pedido debe indicar el usuario que compra")
    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    /**
     * La fecha la pone MySQL (DEFAULT CURRENT_TIMESTAMP), por eso es
     * insertable=false: Hibernate no la escribe.
     *
     * @Generated le dice que, justamente porque la genera la base, la
     * VUELVA A LEER despues del INSERT. Sin esto el objeto recien
     * guardado queda con fecha=null y el comprobante que se manda por
     * correo sale sin fecha de compra. Releerlo con una consulta aparte
     * no alcanza: Spring deja abierta la sesion de JPA durante todo el
     * pedido HTTP (open-session-in-view), asi que devuelve el mismo
     * objeto en memoria en vez de ir a la base.
     */
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado = EstadoPedido.CONFIRMADO;

    @NotNull(message = "El medio de pago es obligatorio (TARJETA o TRANSFERENCIA)")
    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false)
    private MedioPago medioPago;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    /**
     * CONFIRMACION DEL PAGO. Es un eje distinto del 'estado': un pedido
     * puede estar CONFIRMADO y la transferencia todavia sin acreditar.
     *
     * La tarjeta nace en true (se cobra en el acto). La
     * transferencia nace en false y la tilda un administrador cuando ve
     * la plata en la cuenta; de ahi sale "esperando comprobante", que no
     * se guarda en ningun lado: se deduce de medioPago + pagoConfirmado.
     */
    @Column(name = "pago_confirmado", nullable = false)
    private Boolean pagoConfirmado = false;

    @Column(name = "pago_confirmado_en")
    private LocalDateTime pagoConfirmadoEn;

    /**
     * Quien dio el pago por acreditado. Tildar un pago es decir "entro
     * plata": sin saber quien lo tildo no hay a quien preguntarle cuando
     * el banco no muestra el deposito.
     */
    @ManyToOne
    @JoinColumn(name = "pago_confirmado_por")
    private Usuario pagoConfirmadoPor;

    /**
     * cascade = ALL: al guardar el pedido se guardan sus renglones.
     * mappedBy = "pedido": la FK vive en la tabla detalle_pedido.
     */
    /**
     * Baja logica, igual que en productos y usuarios. Un pedido inactivo
     * deja de aparecer en los listados pero su fila y su historial quedan:
     * borrar una venta de verdad haria desaparecer plata que entro.
     */
    @Column(nullable = false)
    private Boolean activo = true;

    @NotEmpty(message = "El pedido debe tener al menos un producto")
    @Valid   // @Valid propaga la validacion a cada renglon de la lista
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
    private List<DetallePedido> detalles = new ArrayList<>();

    public Pedido() { }

    // ----- getters y setters -----
    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public MedioPago getMedioPago() { return medioPago; }
    public void setMedioPago(MedioPago medioPago) { this.medioPago = medioPago; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public Boolean getPagoConfirmado() { return pagoConfirmado; }
    public void setPagoConfirmado(Boolean pagoConfirmado) { this.pagoConfirmado = pagoConfirmado; }
    public LocalDateTime getPagoConfirmadoEn() { return pagoConfirmadoEn; }
    public void setPagoConfirmadoEn(LocalDateTime pagoConfirmadoEn) { this.pagoConfirmadoEn = pagoConfirmadoEn; }
    public Usuario getPagoConfirmadoPor() { return pagoConfirmadoPor; }
    public void setPagoConfirmadoPor(Usuario pagoConfirmadoPor) { this.pagoConfirmadoPor = pagoConfirmadoPor; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public List<DetallePedido> getDetalles() { return detalles; }
    public void setDetalles(List<DetallePedido> detalles) { this.detalles = detalles; }
}
