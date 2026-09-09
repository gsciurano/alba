package com.albaenlosandes.inventario.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad JPA: mapea la tabla 'movimientos_stock'.
 * Trazabilidad total (inventario perpetuo): TODA variacion de stock
 * queda registrada con quien, cuando, cuanto y por que.
 */
@Entity
@Table(name = "movimientos_stock")
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer idMovimiento;

    @ManyToOne
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimiento tipo;

    /** Positiva (ENTRADA) o negativa (SALIDA), segun el tipo */
    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false)
    private String motivo;

    /** Opcional: enlaza el movimiento con la venta que lo genero */
    @ManyToOne
    @JoinColumn(name = "id_pedido")
    private Pedido pedidoRelacionado;

    @Column(insertable = false, updatable = false)
    private LocalDateTime fecha;

    public MovimientoStock() { }

    public MovimientoStock(Producto producto, Usuario usuario, TipoMovimiento tipo,
                           Integer cantidad, String motivo, Pedido pedidoRelacionado) {
        this.producto = producto;
        this.usuario = usuario;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.motivo = motivo;
        this.pedidoRelacionado = pedidoRelacionado;
    }

    // ----- getters y setters -----
    public Integer getIdMovimiento() { return idMovimiento; }
    public void setIdMovimiento(Integer idMovimiento) { this.idMovimiento = idMovimiento; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public TipoMovimiento getTipo() { return tipo; }
    public void setTipo(TipoMovimiento tipo) { this.tipo = tipo; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public Pedido getPedidoRelacionado() { return pedidoRelacionado; }
    public void setPedidoRelacionado(Pedido pedidoRelacionado) { this.pedidoRelacionado = pedidoRelacionado; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
