package com.albaenlosandes.inventario.config;

import com.albaenlosandes.inventario.model.Usuario;
import com.albaenlosandes.inventario.service.UsuarioService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ==================================================================
 *  COMO SE COMPRUEBA UNA CONTRASENA
 * ==================================================================
 *
 * Spring Security pregunta "estos datos son correctos?" y esta clase
 * contesta. En vez de escribir la comprobacion de nuevo, delega en
 * UsuarioService.autenticar(), que es el metodo que ya usaba
 * POST /api/usuarios/login desde la primera entrega.
 *
 * POR QUE DELEGAR Y NO USAR EL CAMINO HABITUAL (UserDetailsService +
 * PasswordEncoder): el camino habitual asume que en la base hay un hash.
 * Aca no lo hay todavia (ver abajo), y para encajar en ese molde habria
 * que registrar un codificador "que no codifica nada", que es
 * exactamente el tipo de pieza que despues alguien deja puesta sin
 * darse cuenta. Delegando, la regla sigue viviendo en un solo lugar y
 * el dia que se pase a BCrypt se cambia alla adentro y esto no se toca.
 *
 * ADVERTENCIA QUE NO HAY QUE PERDER DE VISTA: las contrasenas siguen
 * guardadas EN TEXTO PLANO en la columna password_hash, como estaban.
 * Esta clase no empeora nada —compara exactamente igual que antes— pero
 * tampoco lo arregla: sumar sesiones no convierte en seguro lo que se
 * guarda mal. Pasarlas a BCrypt implica una migracion que invalida las
 * contrasenas existentes, incluidas las de prueba, y quedo fuera del
 * alcance acordado para esta migracion.
 *
 * POR QUE EL MISMO ERROR PARA TODOS LOS FALLOS: autenticar() ya devuelve
 * vacio tanto si el email no existe, como si la contrasena esta mal,
 * como si la cuenta esta dada de baja. Distinguirlos le confirmaria a
 * cualquiera que pregunte que direcciones estan registradas.
 */
@Component
public class ProveedorAutenticacion implements AuthenticationProvider {

    private final UsuarioService usuarios;

    public ProveedorAutenticacion(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public Authentication authenticate(Authentication intento) throws AuthenticationException {
        String email = intento.getName();
        String password = intento.getCredentials() == null ? null : intento.getCredentials().toString();

        Optional<Usuario> encontrado = usuarios.autenticar(email, password);
        if (encontrado.isEmpty()) {
            throw new BadCredentialsException("Email o contraseña incorrectos.");
        }

        UsuarioAutenticado principal = new UsuarioAutenticado(encontrado.get());
        // El segundo parametro es la contrasena: va en null a proposito,
        // para que no quede guardada dentro del objeto de sesion.
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> tipo) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
    }
}
