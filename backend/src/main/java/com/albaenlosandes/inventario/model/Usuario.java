package com.albaenlosandes.inventario.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * Entidad JPA: mapea la tabla 'usuarios'.
 * Clase <-> Tabla | Atributo <-> Columna (ORM)
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    /** Validacion de entrada (requisito 8 del TP): se controla antes de llegar a MySQL. */
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    @Column(nullable = false)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    @Column(nullable = false)
    private String apellido;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido: se espera algo como nombre@dominio.com.")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * WRITE_ONLY: se puede recibir por JSON (alta de usuario)
     * pero NUNCA se devuelve en las respuestas de la API.
     */
    /*
     * Sin @NotBlank a proposito: en el ALTA la exige UsuarioService.registrar(),
     * pero en la EDICION tiene que poder venir vacia para conservar la actual.
     * La misma clase se usa para las dos operaciones, asi que la regla no
     * puede vivir en la anotacion.
     */
    /*
     * nullable = true desde que existe el ingreso con Google: una cuenta
     * de Google NO TIENE contrasena. Guardar una cadena vacia o un
     * "google" de relleno seria peor, porque el dia que se compare la
     * contrasena esa cuenta entraria con cualquier cosa.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Como ingresa esta cuenta. Sin este dato no se puede distinguir
     * "no tiene contrasena porque usa Google" de "quedo sin contrasena
     * por un error".
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProveedorIngreso proveedor = ProveedorIngreso.LOCAL;

    /**
     * El identificador que Google le da a la persona (el claim 'sub').
     *
     * POR QUE NO ALCANZA CON EL EMAIL: el email se puede cambiar, y con
     * el tiempo dos personas distintas pueden terminar con el mismo. El
     * 'sub' no cambia nunca y es unico; es lo que la documentacion de
     * Google indica usar como clave de la cuenta.
     *
     * WRITE_ONLY: no tiene por que salir en las respuestas de la API.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "google_sub", unique = true)
    private String googleSub;

    @Size(max = 30, message = "El telefono no puede superar los 30 caracteres")
    private String telefono;

    /**
     * PREFERENCIAS DE VINO: los varietales y las lineas que el cliente
     * marco como favoritos, separados por coma ("Malbec,Chardonnay").
     *
     * Las elige el propio cliente desde "Mi cuenta" y sirven para
     * recomendarle vinos del catalogo. No son obligatorias: en null
     * significa que todavia no eligio ninguna.
     *
     * POR QUE TEXTO SEPARADO POR COMA Y NO UNA TABLA HIJA: se leen y se
     * guardan siempre completas, y nunca se consultan por separado. El
     * razonamiento largo esta en db/agregar_preferencias_vino.sql.
     */
    @Size(max = 255, message = "Son demasiados varietales favoritos")
    @Column(name = "varietales_favoritos")
    private String varietalesFavoritos;

    @Size(max = 255, message = "Son demasiadas lineas favoritas")
    @Column(name = "lineas_favoritas")
    private String lineasFavoritas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol = Rol.CLIENTE;

    @Column(name = "fecha_alta", insertable = false, updatable = false)
    private LocalDateTime fechaAlta;

    @Column(nullable = false)
    private Boolean activo = true;

    public Usuario() { }

    // ----- getters y setters -----
    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public ProveedorIngreso getProveedor() { return proveedor; }
    public void setProveedor(ProveedorIngreso proveedor) { this.proveedor = proveedor; }
    public String getGoogleSub() { return googleSub; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getVarietalesFavoritos() { return varietalesFavoritos; }
    public void setVarietalesFavoritos(String varietalesFavoritos) { this.varietalesFavoritos = varietalesFavoritos; }
    public String getLineasFavoritas() { return lineasFavoritas; }
    public void setLineasFavoritas(String lineasFavoritas) { this.lineasFavoritas = lineasFavoritas; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public LocalDateTime getFechaAlta() { return fechaAlta; }
    public void setFechaAlta(LocalDateTime fechaAlta) { this.fechaAlta = fechaAlta; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
