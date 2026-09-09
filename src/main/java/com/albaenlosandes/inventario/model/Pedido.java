package com.albaenlosandes.inventario.model;

import jakarta.persistence.*;
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

    @Column(insertable = false, updatable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado = EstadoPedido.CONFIRMADO;

    @NotNull(message = "El medio de pago es obligatorio (TARJETA, TRANSFERENCIA o EFECTIVO)")
    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false)
    private MedioPago medioPago;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    /**
     * cascade = ALL: al guardar el pedido se guardan sus renglones.
     * mappedBy = "pedido": la FK vive en la tabla detalle_pedido.
     */
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
    public List<DetallePedido> getDetalles() { return detalles; }
    public void setDetalles(List<DetallePedido> detalles) { this.detalles = detalles; }
}
