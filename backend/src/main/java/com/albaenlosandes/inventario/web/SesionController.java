package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.config.UsuarioAutenticado;
import com.albaenlosandes.inventario.model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ==================================================================
 *  QUIEN ESTA INGRESADO, PARA EL PANEL VIEJO
 * ==================================================================
 *
 * EL PROBLEMA QUE RESUELVE. El panel de administracion
 * (static/admin.html + js/panel/) es la unica pantalla que todavia no
 * se migro: se dibuja sola en el navegador y recordaba quien habia
 * entrado en el localStorage, porque antes el ingreso tambien era cosa
 * del navegador. Desde que ingresar es cosa del servidor (Spring
 * Security, sesion y cookie), ese localStorage quedo siempre vacio, y
 * admin.js hacia esto:
 *
 *     if (!sesion.esAdmin()) location.replace("cuenta.html");
 *
 * Resultado: un administrador ingresado de verdad entraba al panel, el
 * panel no lo reconocia y lo devolvia a la pantalla de ingreso, que al
 * verlo ingresado lo mandaba a su perfil. El panel era inalcanzable, y
 * para quien mira parecia que estaba roto.
 *
 * LA REGLA QUE NO SE ROMPE. Quien esta ingresado lo decide el servidor,
 * nunca el navegador. Este endpoint no es una puerta nueva: solo cuenta
 * lo que la sesion ya sabe. Si no hay nadie ingresado devuelve 401 y
 * ningun dato, y el rol que informa es el de la base, no uno que el
 * navegador pueda editar: tocar el localStorage no habilita nada,
 * porque cada pedido del panel a /admin/** lo sigue revisando
 * SecurityConfig contra la sesion.
 *
 * POR QUE VIVE EN web/ Y NO EN controller/. Las direcciones de
 * controller/ son la API que consume la aplicacion de escritorio, que
 * no tiene sesion ni cookie. Esta depende de la sesion del navegador,
 * asi que es parte del sitio web aunque devuelva JSON.
 *
 * SE BORRA CUANDO EL PANEL PASE A THYMELEAF: ahi los datos del
 * administrador van a llegar dibujados en la pagina, como en el resto
 * del sitio, y preguntarlos aparte deja de tener sentido.
 */
@Controller
public class SesionController {

    /**
     * Devuelve los datos de quien esta ingresado, o 401 si no hay nadie.
     *
     * VIAJA TAMBIEN EL TOKEN CSRF, y no es un descuido. El boton "Salir"
     * del panel tiene que cerrar la sesion del servidor, y cerrarla es
     * un POST a /cuenta/salir que Spring rechaza sin token. Las paginas
     * de Thymeleaf lo reciben escrito en el formulario; el panel, que es
     * un archivo estatico, no tiene como. Entregarlo aca no lo expone:
     * solo se lo puede leer con la cookie de la sesion, que es
     * exactamente lo que el token protege, y es el mismo criterio con el
     * que Spring ofrece CookieCsrfTokenRepository.
     */
    @GetMapping("/cuenta/quien-soy")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quienSoy(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            CsrfToken token) {

        if (autenticado == null) {
            return ResponseEntity.status(401).build();
        }

        Usuario usuario = autenticado.getUsuario();
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idUsuario", usuario.getIdUsuario());
        datos.put("nombre", usuario.getNombre());
        datos.put("apellido", usuario.getApellido());
        datos.put("email", usuario.getEmail());
        datos.put("rol", usuario.getRol().name());
        if (token != null) {
            datos.put("csrfHeader", token.getHeaderName());
            datos.put("csrfToken", token.getToken());
        }
        return ResponseEntity.ok(datos);
    }
}
