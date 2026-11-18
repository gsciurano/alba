package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.config.UsuarioAutenticado;
import com.albaenlosandes.inventario.model.Rol;
import com.albaenlosandes.inventario.model.Usuario;
import com.albaenlosandes.inventario.service.ServicioGoogle;
import com.albaenlosandes.inventario.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ==================================================================
 *  ENTRAR AL SITIO: ALTA DE CUENTA Y BOTON DE GOOGLE
 * ==================================================================
 *
 * El ingreso con email y contrasena no esta aca: lo atiende Spring
 * Security solo (SecurityConfig.formLogin, que escucha en
 * POST /cuenta/ingresar). Aca estan las otras dos puertas de la misma
 * pantalla, que Security no puede atender porque no son un usuario y
 * una contrasena:
 *
 *     /cuenta/registro   el alta, que antes vivia en cuenta.js:572
 *     /cuenta/google     el boton de Google, que vivia en cuenta.js:123
 *
 * ------------------------------------------------------------------
 *  POR QUE LAS DOS TIENEN QUE ABRIR LA SESION A MANO
 * ------------------------------------------------------------------
 * Quien se acaba de dar de alta, o quien entro con Google, YA probo
 * quien es: pedirle que ahora escriba de nuevo el email y la
 * contrasena en el formulario de al lado seria pedirle dos veces lo
 * mismo. Pero la sesion la crea el filtro de Security cuando procesa
 * SU formulario, y estos dos pedidos no pasan por ahi.
 *
 * De ahi 'abrirLaSesion': arma la autenticacion a mano y la guarda en
 * la sesion con el mismo repositorio que usa Security, para que el
 * resto del sitio (y @AuthenticationPrincipal) no note la diferencia.
 *
 * LAS VALIDACIONES NO SE REPITEN ACA. UsuarioService.registrar() ya
 * revisa la forma del email, que no este repetido y los requisitos de
 * la contrasena, con los mismos mensajes que mostraba el JavaScript.
 * Escribir esas reglas otra vez en este archivo seria tener dos
 * versiones de la misma regla, que es como empiezan a separarse.
 */
@Controller
@RequestMapping("/cuenta")
public class IngresoController {

    private final UsuarioService usuarios;
    private final ServicioGoogle google;

    /*
     * El mismo repositorio que usa Security por defecto para leer y
     * escribir la sesion. Si se usara otra cosa, la sesion que abre
     * este controlador no seria la que despues lee el filtro.
     */
    private final SecurityContextRepository sesiones = new HttpSessionSecurityContextRepository();

    public IngresoController(UsuarioService usuarios, ServicioGoogle google) {
        this.usuarios = usuarios;
        this.google = google;
    }

    // ============================================================
    //  LA SOLAPA "CREAR CUENTA"
    // ============================================================

    /**
     * La misma pantalla del ingreso, con la segunda solapa abierta.
     *
     * LAS DOS SOLAPAS SON DOS DIRECCIONES y no un botón que esconde un
     * panel, como hacia pantallaIngreso() en cuenta.js:79. Asi el alta
     * se puede enlazar, volver atras funciona, y cuando el formulario
     * vuelve con un error la pagina sigue mostrando la solapa correcta.
     */
    @GetMapping("/registro")
    public String registro(Model modelo) {
        modelo.addAttribute("solapa", "registro");
        return "cuenta/ingresar";
    }

    /**
     * Da de alta la cuenta y deja a la persona adentro.
     *
     * A DONDE VA DESPUES: al inicio, igual que cuenta.js:622. Acaba de
     * crear la cuenta, no viene a revisar un pedido.
     */
    @PostMapping("/registro")
    public String crearCuenta(@RequestParam String nombre,
                              @RequestParam String apellido,
                              @RequestParam String email,
                              @RequestParam(required = false) String telefono,
                              @RequestParam String password,
                              HttpServletRequest pedido,
                              HttpServletResponse respuesta,
                              Model modelo) {

        Usuario nuevo = new Usuario();
        nuevo.setNombre(limpio(nombre));
        nuevo.setApellido(limpio(apellido));
        nuevo.setEmail(limpio(email));
        nuevo.setTelefono(limpio(telefono));
        nuevo.setPasswordHash(password);
        nuevo.setRol(Rol.CLIENTE);

        try {
            Usuario creado = usuarios.registrar(nuevo);
            abrirLaSesion(creado, pedido, respuesta);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            /*
             * Vuelve la pantalla con el error y con lo que la persona
             * habia escrito, menos la contrasena: que el navegador
             * guarde una contrasena rechazada en el HTML no suma nada y
             * queda en la cache de la pagina.
             */
            modelo.addAttribute("solapa", "registro");
            modelo.addAttribute("errorRegistro", e.getMessage());
            modelo.addAttribute("alta", nuevo);
            return "cuenta/ingresar";
        }
    }

    // ============================================================
    //  EL BOTON DE GOOGLE
    // ============================================================

    /**
     * Recibe el token que firmo Google y abre la sesion.
     *
     * COMO LLEGA HASTA ACA: la libreria de Google dibuja su boton en la
     * pantalla de ingreso, y cuando la persona elige su cuenta le
     * entrega a la pagina un 'credential' (un JWT firmado). Un
     * JavaScript de tres lineas lo pone en un formulario oculto y lo
     * manda por POST, con el token CSRF del sitio.
     *
     * POR QUE NO SE CONFIA EN ESE TOKEN SIN MIRARLO: lo manda el
     * navegador, asi que podria venir de cualquier parte.
     * ServicioGoogle.ingresar() comprueba la firma, que el token sea
     * para nuestro client id, que lo haya emitido Google y que el
     * correo este verificado, antes de devolver el usuario.
     */
    @PostMapping("/google")
    public String conGoogle(@RequestParam String credential,
                            HttpServletRequest pedido,
                            HttpServletResponse respuesta,
                            Model modelo) {
        try {
            Usuario usuario = google.ingresar(credential);
            abrirLaSesion(usuario, pedido, respuesta);
            return usuario.getRol() == Rol.ADMIN ? "redirect:/admin" : "redirect:/";
        } catch (IllegalArgumentException e) {
            modelo.addAttribute("solapa", "ingreso");
            modelo.addAttribute("errorGoogle", e.getMessage());
            return "cuenta/ingresar";
        }
    }

    // ============================================================
    //  ABRIR LA SESION A MANO
    // ============================================================

    /**
     * Deja a 'usuario' ingresado para el resto de sus pedidos.
     *
     * LA SESION VIEJA SE TIRA ANTES (invalidate) y se arranca una
     * nueva. Si no, alguien podria dejar una sesion abierta en una
     * maquina ajena, esperar a que otra persona se de de alta en esa
     * misma pestana y quedarse con una cookie que ahora apunta a la
     * cuenta nueva. Es lo mismo que hace Security con su formulario.
     */
    private void abrirLaSesion(Usuario usuario, HttpServletRequest pedido, HttpServletResponse respuesta) {
        if (pedido.getSession(false) != null) {
            pedido.getSession(false).invalidate();
        }
        pedido.getSession(true);

        UsuarioAutenticado principal = new UsuarioAutenticado(usuario);
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));

        SecurityContextHolder.setContext(contexto);
        sesiones.saveContext(contexto, pedido, respuesta);
    }

    private static String limpio(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
