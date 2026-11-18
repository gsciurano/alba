package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.controller.RecuperoVencidoException;
import com.albaenlosandes.inventario.service.ServicioRecupero;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ==================================================================
 *  RECUPERAR LA CONTRASENA — LOS CUATRO PASOS
 * ==================================================================
 *
 * Era el modal de cuenta.js:293, con los cuatro pasos dibujados uno
 * encima del otro en la misma ventana. Ahora cada paso es una pantalla
 * con su direccion:
 *
 *     1  /cuenta/recupero            de que cuenta estamos hablando
 *     2  /cuenta/recupero/telefono   los numeros tapados del telefono
 *     3  /cuenta/recupero/codigo     el codigo que llego al mail
 *     4  /cuenta/recupero/clave      la contrasena nueva
 *
 * ------------------------------------------------------------------
 *  QUIEN SE ACUERDA EN QUE PASO VA: EL TOKEN, NO LA SESION
 * ------------------------------------------------------------------
 * ServicioRecupero entrega un token en el primer paso y es el que
 * guarda el estado: a que cuenta pertenece, cuantos intentos quedan,
 * si ya se confirmo el telefono y si ya se confirmo el codigo. Los
 * pasos siguientes lo devuelven en un campo oculto.
 *
 * NO SE GUARDA NADA EN LA SESION a proposito: el estado de este
 * circuito ya vive en un solo lugar, del lado del servidor, con su
 * vencimiento y su cuenta de intentos. Copiarlo a la sesion seria
 * tener dos relojes que pueden decir horas distintas.
 *
 * EL EMAIL SI VIAJA entre pasos, pero solo para dos cosas de pantalla:
 * volver a mostrarlo si hay que empezar de nuevo, y dejarlo escrito en
 * el formulario de ingreso al terminar. No da permiso de nada.
 *
 * ------------------------------------------------------------------
 *  EL TOKEN MUERTO (vencido, gastado o sin intentos)
 * ------------------------------------------------------------------
 * ServicioRecupero tira RecuperoVencidoException. Cuando eso pasa no
 * sirve de nada dejar a la persona escribiendo en un formulario que ya
 * no va a aceptar nada: se la manda al paso 1 con el aviso a la vista.
 * Es lo mismo que decidia fallaRecupero() en cuenta.js:315 mirando el
 * codigo 410.
 *
 * Cualquier otro error es "proba de nuevo" y se queda en el paso donde
 * esta, con el mensaje del servicio.
 */
@Controller
@RequestMapping("/cuenta/recupero")
public class RecuperoController {

    private final ServicioRecupero recupero;

    public RecuperoController(ServicioRecupero recupero) {
        this.recupero = recupero;
    }

    // ============================================================
    //  PASO 1 — DE QUE CUENTA ESTAMOS HABLANDO
    // ============================================================

    /**
     * El email llega como parametro cuando se entra desde el enlace de
     * "¿Olvidaste tu contraseña?", que lo lleva ya escrito para no
     * hacerlo escribir dos veces lo mismo (cuenta.js:235).
     */
    @GetMapping
    public String pasoEmail(@RequestParam(required = false) String email, Model modelo) {
        modelo.addAttribute("paso", 1);
        modelo.addAttribute("email", email == null ? "" : email.trim());
        return "cuenta/recupero";
    }

    /**
     * Busca la cuenta y pasa a los numeros del telefono.
     *
     * LO QUE VUELVE DEL SERVICIO es el token, el telefono con numeros
     * tapados y CUANTOS tapo. Ese numero no es un detalle: con el la
     * pantalla dibuja la cantidad justa de casilleros.
     */
    @PostMapping
    public String buscarCuenta(@RequestParam String email, Model modelo) {
        String limpio = email == null ? "" : email.trim();
        try {
            ServicioRecupero.Inicio inicio = recupero.iniciar(limpio);
            modelo.addAttribute("paso", 2);
            modelo.addAttribute("email", limpio);
            modelo.addAttribute("token", inicio.token());
            modelo.addAttribute("telefonoParcial", inicio.telefonoParcial());
            modelo.addAttribute("faltan", inicio.faltan());
        } catch (IllegalArgumentException e) {
            /*
             * Aca caen "No hay ninguna cuenta con el email ...", la
             * cuenta que entra con Google y la que no tiene telefono
             * cargado. El mensaje es el del servicio.
             */
            modelo.addAttribute("paso", 1);
            modelo.addAttribute("email", limpio);
            modelo.addAttribute("error", e.getMessage());
        }
        return "cuenta/recupero";
    }

    // ============================================================
    //  PASO 2 — LOS NUMEROS TAPADOS DEL TELEFONO
    // ============================================================

    /**
     * Si los numeros estan bien, el servicio MANDA EL CODIGO POR MAIL
     * antes de contestar. Por eso este paso tarda mas que los otros:
     * esta esperando al servidor de correo.
     */
    @PostMapping("/telefono")
    public String confirmarTelefono(@RequestParam String token,
                                    @RequestParam String email,
                                    @RequestParam(required = false) String digitos,
                                    @RequestParam int faltan,
                                    @RequestParam(required = false) String telefonoParcial,
                                    RedirectAttributes flash,
                                    Model modelo) {
        String escritos = soloNumeros(digitos);

        if (escritos.length() < faltan) {
            return volverAlTelefono(modelo, token, email, faltan, telefonoParcial,
                                    "Completá todos los casilleros.");
        }

        try {
            ServicioRecupero.Enviado enviado = recupero.confirmarTelefono(token, escritos);
            modelo.addAttribute("paso", 3);
            modelo.addAttribute("token", token);
            modelo.addAttribute("email", email);
            modelo.addAttribute("emailParcial", enviado.emailParcial());
            return "cuenta/recupero";
        } catch (RecuperoVencidoException e) {
            return alPrimerPaso(flash, email, e.getMessage());
        } catch (IllegalArgumentException e) {
            return volverAlTelefono(modelo, token, email, faltan, telefonoParcial, e.getMessage());
        }
    }

    // ============================================================
    //  PASO 3 — EL CODIGO QUE LLEGO AL MAIL
    // ============================================================

    @PostMapping("/codigo")
    public String confirmarCodigo(@RequestParam String token,
                                  @RequestParam String email,
                                  @RequestParam(required = false) String codigo,
                                  @RequestParam(required = false) String emailParcial,
                                  RedirectAttributes flash,
                                  Model modelo) {
        String escrito = soloNumeros(codigo);

        if (escrito.length() < LARGO_CODIGO) {
            return volverAlCodigo(modelo, token, email, emailParcial,
                                  "El código tiene " + LARGO_CODIGO + " números.");
        }

        try {
            recupero.confirmarCodigo(token, escrito);
            modelo.addAttribute("paso", 4);
            modelo.addAttribute("token", token);
            modelo.addAttribute("email", email);
            return "cuenta/recupero";
        } catch (RecuperoVencidoException e) {
            return alPrimerPaso(flash, email, e.getMessage());
        } catch (IllegalArgumentException e) {
            return volverAlCodigo(modelo, token, email, emailParcial, e.getMessage());
        }
    }

    // ============================================================
    //  PASO 4 — LA CONTRASENA NUEVA
    // ============================================================

    /**
     * Guarda la contrasena nueva y manda al ingreso con el email ya
     * puesto.
     *
     * NO SE INGRESA SOLO, aunque se podria: el servidor acaba de
     * aceptar la contrasena y alcanzaria con abrir la sesion. Se deja
     * el formulario con el email escrito para que la persona la teclee
     * una vez mas, que es el momento en que se da cuenta de si se la va
     * a acordar manana. Es la decision de cuenta.js:548, tal cual.
     */
    @PostMapping("/clave")
    public String guardarClave(@RequestParam String token,
                               @RequestParam String email,
                               @RequestParam String nueva,
                               @RequestParam(required = false) String repetir,
                               RedirectAttributes flash,
                               Model modelo) {
        if (!nueva.equals(repetir)) {
            return volverALaClave(modelo, token, email, "Las dos contraseñas no coinciden.");
        }

        try {
            recupero.cambiarPassword(token, nueva);
            flash.addFlashAttribute("avisoIngreso",
                    "Listo, ya podés entrar con tu contraseña nueva.");
            /*
             * El email va en la direccion y no como flash: la pantalla
             * de ingreso ya lo lee de ahi (param.email) desde que se
             * migro, y asi el enlace sigue sirviendo si se recarga.
             */
            return "redirect:/cuenta?email=" + java.net.URLEncoder.encode(
                    email, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RecuperoVencidoException e) {
            return alPrimerPaso(flash, email, e.getMessage());
        } catch (IllegalArgumentException e) {
            // Aca caen los requisitos de la contrasena, con su mensaje.
            return volverALaClave(modelo, token, email, e.getMessage());
        }
    }

    // ============================================================

    /** El largo del codigo que manda el servicio por mail. */
    private static final int LARGO_CODIGO = 6;

    /**
     * Volver al paso 1 con el aviso, cuando el token ya no sirve.
     *
     * Va por redirect y no dibujando el paso 1 aca: si se dibujara, la
     * direccion seguiria siendo la del paso que fallo y recargar
     * volveria a mandar ese formulario muerto.
     */
    private String alPrimerPaso(RedirectAttributes flash, String email, String aviso) {
        flash.addFlashAttribute("error", aviso);
        return "redirect:/cuenta/recupero?email=" + java.net.URLEncoder.encode(
                email == null ? "" : email, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String volverAlTelefono(Model modelo, String token, String email, int faltan,
                                    String telefonoParcial, String error) {
        modelo.addAttribute("paso", 2);
        modelo.addAttribute("token", token);
        modelo.addAttribute("email", email);
        modelo.addAttribute("faltan", faltan);
        modelo.addAttribute("telefonoParcial", telefonoParcial);
        modelo.addAttribute("error", error);
        return "cuenta/recupero";
    }

    private String volverAlCodigo(Model modelo, String token, String email,
                                  String emailParcial, String error) {
        modelo.addAttribute("paso", 3);
        modelo.addAttribute("token", token);
        modelo.addAttribute("email", email);
        modelo.addAttribute("emailParcial", emailParcial);
        modelo.addAttribute("error", error);
        return "cuenta/recupero";
    }

    private String volverALaClave(Model modelo, String token, String email, String error) {
        modelo.addAttribute("paso", 4);
        modelo.addAttribute("token", token);
        modelo.addAttribute("email", email);
        modelo.addAttribute("error", error);
        return "cuenta/recupero";
    }

    /**
     * Lo que se escribio en los casilleros llega como un campo por
     * casillero; aca se pegan y se tira lo que no sea un numero.
     *
     * Tambien cubre el caso de que alguien mande el formulario a mano
     * con letras adentro: el servicio espera numeros.
     */
    private static String soloNumeros(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }
}
