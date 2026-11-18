package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.model.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * ==================================================================
 *  LAS PAGINAS DE CONTENIDO FIJO Y EL INGRESO
 * ==================================================================
 *
 * Es un @Controller, no un @RestController: devuelve el NOMBRE de una
 * plantilla y Thymeleaf la renderiza. Los @RestController de /api/**
 * siguen intactos devolviendo JSON para la aplicacion de escritorio.
 *
 * ------------------------------------------------------------------
 *  POR QUE LAS RUTAS NO EMPIEZAN CON /api
 * ------------------------------------------------------------------
 * El laboratorio de la presentacion monta su controlador de fragmentos
 * en @RequestMapping("/api/productos"). Aca no se puede: ese prefijo ya
 * esta ocupado por los siete @RestController que consume la aplicacion
 * de escritorio en Swing, y una misma direccion no puede devolver JSON y
 * HTML a la vez.
 *
 * La separacion queda asi:
 *     /api/**        JSON, para la aplicacion de escritorio
 *     todo lo demas  HTML, para el navegador
 *
 * Es una desviacion deliberada de la presentacion, forzada por el
 * segundo consumidor. A cambio, las direcciones quedan como las espera
 * la semantica REST que la misma presentacion pide: /vinos/3 es el
 * recurso, no /vino.html?id=3.
 */
@Controller
public class PaginasController {

    // ============================================================
    //  INICIO
    // ============================================================

    /**
     * La portada. El texto es fijo y viaja en el HTML; los vinos
     * destacados los pide la propia pagina con hx-get una vez dibujada
     * (ver el contenedor #vinosDestacados en la plantilla).
     */
    @GetMapping("/")
    public String inicio() {
        return "publico/inicio";
    }

    // ============================================================
    //  CONTENIDO FIJO
    // ============================================================

    @GetMapping("/finca")
    public String finca() {
        return "publico/finca";
    }

    @GetMapping("/nosotros")
    public String nosotros() {
        return "publico/nosotros";
    }

    // ============================================================
    //  LEGALES
    // ============================================================
    //  Van bajo /legal/ y no sueltas en la raiz porque son tres
    //  documentos del mismo tipo, y asi la regla de seguridad que las
    //  deja abiertas se escribe una sola vez: /legal/**.
    //
    //  Las anclas internas (#derechos, #arrepentimiento, ...) siguen
    //  funcionando igual: son ids dentro del documento, no rutas.

    @GetMapping("/legal/privacidad")
    public String privacidad() {
        return "legal/privacidad";
    }

    @GetMapping("/legal/terminos")
    public String terminos() {
        return "legal/terminos";
    }

    @GetMapping("/legal/arrepentimiento")
    public String arrepentimiento() {
        return "legal/arrepentimiento";
    }

    // ============================================================
    //  CONTACTO
    // ============================================================

    /**
     * El formulario de consulta.
     *
     * Si quien mira ya ingreso, el nombre y el email vienen completados
     * desde la sesion: no tiene por que volver a escribir lo que el
     * sistema ya sabe. Antes esto lo hacia contacto.js:25 leyendo
     * localStorage.
     */
    @GetMapping("/contacto")
    public String contacto(@ModelAttribute("usuario") Usuario usuario, Model modelo) {
        ConsultaContacto consulta = new ConsultaContacto();
        if (usuario != null) {
            consulta.setNombre((usuario.getNombre() + " " + usuario.getApellido()).trim());
            consulta.setEmail(usuario.getEmail());
        }
        modelo.addAttribute("consulta", consulta);
        return "publico/contacto";
    }

    /**
     * El envio de la consulta.
     *
     * SIGUE SIN MANDAR CORREOS, y la pagina lo dice igual que antes. Lo
     * que cambia es donde se valida: antes las tres reglas (nombre de
     * tres letras, email con forma de email, mensaje de diez caracteres)
     * corrian en contacto.js y se salteaban apagando el JavaScript.
     * Ahora las comprueba el servidor con Bean Validation, y ademas el
     * formulario trae validacion nativa de HTML5 para avisar antes de
     * mandar. Son las dos capas que pide la primera presentacion.
     *
     * POR QUE NO SE MANDA EL CORREO IGUAL, SI YA EXISTE ServicioCorreo:
     * porque entonces habria dos circuitos de consulta distintos (este y
     * el de cancelaciones) y uno de los dos iba a quedar sin atender. El
     * dia que se decida atenderlo, el servicio ya esta.
     */
    @PostMapping("/contacto")
    public String enviarConsulta(@Valid @ModelAttribute("consulta") ConsultaContacto consulta,
                                 BindingResult errores,
                                 Model modelo) {
        if (errores.hasErrors()) {
            return "publico/contacto";
        }
        modelo.addAttribute("enviada", true);
        return "publico/contacto";
    }

    /**
     * Los datos de una consulta de contacto.
     *
     * Las reglas son las mismas que aplicaba contacto.js:46, escritas
     * una sola vez y del lado del servidor.
     */
    public static class ConsultaContacto {
        @NotBlank(message = "Ingresá tu nombre completo")
        @Size(min = 3, message = "Ingresá tu nombre completo")
        private String nombre = "";

        @NotBlank(message = "Email inválido")
        @Email(message = "Email inválido")
        private String email = "";

        private String asunto = "Consulta general";

        @NotBlank(message = "Contanos un poco más (mínimo 10 caracteres)")
        @Size(min = 10, message = "Contanos un poco más (mínimo 10 caracteres)")
        private String mensaje = "";

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getAsunto() { return asunto; }
        public void setAsunto(String asunto) { this.asunto = asunto; }
        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    }

}
