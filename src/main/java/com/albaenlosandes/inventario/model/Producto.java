package com.albaenlosandes.inventario.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Entidad JPA: mapea la tabla 'productos' (los vinos del catalogo).
 */
@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Integer idProducto;

    /**
     * Validacion de entrada (requisito 8 del TP): estas anotaciones se controlan
     * ANTES de tocar la base. Si algo no cumple, la peticion se rechaza con 400
     * y un mensaje claro, en vez de estallar contra la restriccion de MySQL.
     */
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    @Column(nullable = false)
    private String nombre;

    @NotNull(message = "La linea es obligatoria (FINCA, ESTATE_RESERVE, GRAN_RESERVA o EDICION_ESPECIAL)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Linea linea;

    @NotBlank(message = "El varietal es obligatorio")
    @Size(max = 80, message = "El varietal no puede superar los 80 caracteres")
    @Column(nullable = false)
    private String varietal;

    @Min(value = 1900, message = "La anada no puede ser anterior a 1900")
    @Max(value = 2100, message = "La anada no puede ser posterior a 2100")
    private Integer anada;

    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @PositiveOrZero(message = "El precio no puede ser negativo")
    @Column(nullable = false)
    private BigDecimal precio;

    @NotNull(message = "El stock actual es obligatorio")
    @PositiveOrZero(message = "El stock actual no puede ser negativo")
    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual;

    @NotNull(message = "El stock minimo es obligatorio")
    @PositiveOrZero(message = "El stock minimo no puede ser negativo")
    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo;

    @Size(max = 300, message = "La URL de la imagen no puede superar los 300 caracteres")
    @Column(name = "imagen_url")
    private String imagenUrl;

    @Column(nullable = false)
    private Boolean activo = true;

    public Producto() { }

    public Producto(String nombre, Linea linea, String varietal, BigDecimal precio) {
        this.nombre = nombre;
        this.linea = linea;
        this.varietal = varietal;
        this.precio = precio;
    }

    /** Regla de negocio simple expuesta en el JSON: ¿hay que reponer? */
    @Transient
    public boolean isBajoStockMinimo() {
        return stockActual != null && stockMinimo != null && stockActual < stockMinimo;
    }

    // ----- getters y setters -----
    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Linea getLinea() { return linea; }
    public void setLinea(Linea linea) { this.linea = linea; }
    public String getVarietal() { return varietal; }
    public void setVarietal(String varietal) { this.varietal = varietal; }
    public Integer getAnada() { return anada; }
    public void setAnada(Integer anada) { this.anada = anada; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public Integer getStockActual() { return stockActual; }
    public void setStockActual(Integer stockActual) { this.stockActual = stockActual; }
    public Integer getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(Integer stockMinimo) { this.stockMinimo = stockMinimo; }
    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
