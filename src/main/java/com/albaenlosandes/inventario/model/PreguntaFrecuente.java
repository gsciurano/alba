package com.albaenlosandes.inventario.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entidad JPA: mapea la tabla 'preguntas_frecuentes'.
 *
 * POR QUE ESTAN EN LA BASE Y NO ESCRITAS DENTRO DEL CODIGO:
 *  1. La bodega puede corregir un texto sin que nadie recompile ni despliegue.
 *  2. La ventana de escritorio y la pagina web leen las MISMAS preguntas
 *     desde el mismo lugar. Si estuvieran escritas en cada interfaz,
 *     tarde o temprano dirian cosas distintas.
 *
 * 'motivo' en null significa pregunta general: se muestra siempre.
 * Con un valor, aparece solo cuando el cliente elige ese motivo.
 */
@Entity
@Table(name = "preguntas_frecuentes")
public class PreguntaFrecuente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pregunta")
    private Integer idPregunta;

    @NotBlank(message = "La pregunta es obligatoria")
    @Size(max = 200, message = "La pregunta no puede superar los 200 caracteres")
    @Column(nullable = false)
    private String pregunta;

    @NotBlank(message = "La respuesta es obligatoria")
    @Size(max = 1000, message = "La respuesta no puede superar los 1000 caracteres")
    @Column(nullable = false, length = 1000)
    private String respuesta;

    /** null = general. Con valor = solo para ese motivo. */
    @Enumerated(EnumType.STRING)
    private MotivoCancelacion motivo;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(nullable = false)
    private Boolean activa = true;

    public PreguntaFrecuente() { }

    public Integer getIdPregunta() { return idPregunta; }
    public void setIdPregunta(Integer idPregunta) { this.idPregunta = idPregunta; }
    public String getPregunta() { return pregunta; }
    public void setPregunta(String pregunta) { this.pregunta = pregunta; }
    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
    public MotivoCancelacion getMotivo() { return motivo; }
    public void setMotivo(MotivoCancelacion motivo) { this.motivo = motivo; }
    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
    public Boolean getActiva() { return activa; }
    public void setActiva(Boolean activa) { this.activa = activa; }
}
