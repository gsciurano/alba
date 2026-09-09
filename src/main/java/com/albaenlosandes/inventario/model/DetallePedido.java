package com.albaenlosandes.inventario.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Entidad JPA: mapea la tabla 'detalle_pedido' (renglones del pedido).
 * precio_unitario es una "foto" del precio al momento de la compra:
 * si el vino cambia de precio despues, el pedido historico no se altera.
 */
@Entity
@Table(name = "detalle_pedido")
public class DetallePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Integer idDetalle;

    /**
     * @JsonIgnore evita el bucle infinito al serializar:
     * Pedido -> detalles -> pedido -> detalles -> ...
     */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    /**
     * Validacion de entrada (requisito 8 del TP).
     * OJO: 'pedido' y 'precioUnitario' NO se validan porque no vienen en el JSON;
     * los completa PedidoService durante el checkout.
     */
    @NotNull(message = "Cada renglon debe indicar el producto")
    @ManyToOne
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @NotNull(message = "Cada renglon debe indicar la cantidad")
    @Positive(message = "La cantidad debe ser mayor a cero")
    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    public DetallePedido() { }

    // ----- getters y setters -----
    public Integer getIdDetalle() { return idDetalle; }
    public void setIdDetalle(Integer idDetalle) { this.idDetalle = idDetalle; }
    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
}
