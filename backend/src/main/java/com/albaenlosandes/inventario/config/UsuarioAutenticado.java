package com.albaenlosandes.inventario.config;

import com.albaenlosandes.inventario.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * ==================================================================
 *  QUIEN ESTA INGRESADO, VISTO POR SPRING SECURITY
 * ==================================================================
 *
 * Spring Security no sabe nada de nuestra entidad Usuario: trabaja con
 * su propia interfaz UserDetails. Esta clase es el puente entre las dos.
 *
 * POR QUE GUARDA EL Usuario ENTERO Y NO SOLO EL EMAIL: las plantillas
 * necesitan el nombre para saludar, el rol para decidir si existe el
 * acceso al panel, y el id para pedir los pedidos de esa persona. Si
 * guardaramos solo el email, cada pagina tendria que volver a buscar el
 * usuario en la base para mostrar el encabezado.
 *
 * El id de usuario sale de aca y NO de un parametro de la direccion.
 * Eso es lo que termina con los '?idUsuario=1' que viajaban a la vista
 * en las operaciones sensibles: ahora el servidor sabe de quien se trata
 * sin que el navegador se lo tenga que decir.
 */
public class UsuarioAutenticado implements UserDetails {

    private final Usuario usuario;

    public UsuarioAutenticado(Usuario usuario) {
        this.usuario = usuario;
    }

    /** La entidad completa, para las plantillas y los controladores. */
    public Usuario getUsuario() {
        return usuario;
    }

    public Integer getIdUsuario() {
        return usuario.getIdUsuario();
    }

    /**
     * Spring Security espera los roles con el prefijo "ROLE_". Nuestro
     * enum Rol tiene ADMIN y CLIENTE, asi que quedan ROLE_ADMIN y
     * ROLE_CLIENTE, y en la configuracion se escriben con hasRole("ADMIN"),
     * que agrega el prefijo solo.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
    }

    /**
     * La contrasena no se devuelve nunca, ni siquiera a Spring Security:
     * la comparacion la hace ProveedorAutenticacion llamando al servicio
     * que ya existia. Devolver null evita que quede dando vueltas en
     * memoria dentro del objeto de sesion.
     */
    @Override
    public String getPassword() {
        return null;
    }

    /** El "nombre de usuario" para Spring Security es el email. */
    @Override
    public String getUsername() {
        return usuario.getEmail();
    }

    /**
     * Una cuenta dada de baja (activo = false) no puede ingresar. Es la
     * misma regla que ya aplicaba UsuarioService.autenticar(); se repite
     * aca para que tambien valga si la cuenta se da de baja mientras la
     * persona tiene la sesion abierta.
     */
    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(usuario.getActivo());
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
