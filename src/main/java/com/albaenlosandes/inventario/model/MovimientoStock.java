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

    /**
     * Baja logica. Un movimiento anulado sigue en la tabla: el historial de
     * auditoria no se borra nunca. Solo deja de contar en los listados.
     */
    @Column(nullable = false)
    private Boolean activo = true;

    /**
     * EL CONTRA-ASIENTO. Cuando se anula un movimiento se crea otro con la
     * cantidad opuesta, y ese nuevo movimiento apunta aca al que corrige.
     *
     * Es la forma correcta de "borrar" un registro de auditoria, y es la
     * misma que usa la contabilidad: no se tacha el asiento equivocado,
     * se agrega el asiento contrario. Asi queda constancia de las dos cosas:
     * que paso y que se corrigio.
     */
    @ManyToOne
    @JoinColumn(name = "id_movimiento_anulado")
    private MovimientoStock movimientoAnulado;

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
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public MovimientoStock getMovimientoAnulado() { return movimientoAnulado; }
    public void setMovimientoAnulado(MovimientoStock m) { this.movimientoAnulado = m; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
