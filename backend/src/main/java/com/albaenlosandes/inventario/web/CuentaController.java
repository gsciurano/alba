package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.config.UsuarioAutenticado;
import com.albaenlosandes.inventario.model.*;
import com.albaenlosandes.inventario.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.List;

/**
 * ==================================================================
 *  MI CUENTA
 * ==================================================================
 *
 * QUE REEMPLAZA: la mitad de js/cuenta.js (1.301 lineas), que era el
 * segundo archivo mas grande del frontend. Concretamente:
 *
 *     cuenta.js:656 SECCIONES           -> las cinco rutas de abajo
 *     cuenta.js:675 pantallaCliente     -> GET /cuenta/{seccion}
 *     cuenta.js:738 mostrarSeccion      -> el fragmento 'panel'
 *     cuenta.js:785 seccionDatos        -> GET y POST /cuenta/datos
 *     cuenta.js:860 seccionPedidos      -> GET /cuenta/pedidos
 *     cuenta.js:926 seccionSolicitudes  -> GET /cuenta/solicitudes
 *     cuenta.js:983 seccionPreferencias -> GET y POST /cuenta/preferencias
 *     cuenta.js:1129 seccionSeguridad   -> GET y POST /cuenta/seguridad
 *     cuenta.js:1201 abrirCancelacion   -> CancelacionController
 *
 * ============================================================
 *  EL #HASH SE CONVIERTE EN DIRECCIONES DE VERDAD
 * ============================================================
 * La version vieja cambiaba de seccion con el ancla de la direccion:
 * cuenta.html#pedidos, escuchando 'hashchange'. Funcionaba, pero el
 * servidor nunca se enteraba de que seccion se estaba mirando -el
 * navegador no manda lo que sigue al numeral- asi que cada seccion
 * tenia que dibujarse del lado del cliente por definicion.
 *
 * Ahora cada seccion es una direccion:
 *     /cuenta/datos   /cuenta/pedidos   /cuenta/solicitudes
 *     /cuenta/preferencias   /cuenta/seguridad
 *
 * Y las cinco funcionan de las dos maneras: si el pedido viene de
 * HTMX se devuelve solo el panel de la derecha y la pagina no se
 * recarga; si viene del navegador a secas, la pagina entera. Es el
 * mismo recurso, servido en dos tamanios.
 *
 * UN DETALLE QUE ARREGLA SOLO: el pie del sitio ya enlazaba estas
 * cinco direcciones. Hasta ahora las atendia un andamio en
 * PaginasController que las mandaba a todas a /cuenta, porque no
 * existian. Ahora existen y ese andamio se fue.
 */
@Controller
@RequestMapping("/cuenta")
public class CuentaController {

    /**
     * El menu de la izquierda. Es el array SECCIONES de cuenta.js:656,
     * con la diferencia de que cada entrada ahora es una direccion y
     * no el nombre de una funcion que dibuja.
     */
    public record Seccion(String id, String texto, String titulo, String bajada) { }

    private static final List<Seccion> SECCIONES = List.of(
        new Seccion("datos", "Datos principales", "Datos principales",
                    "Tus datos de contacto para los pedidos."),
        new Seccion("pedidos", "Mis pedidos", "Mis pedidos",
                    "Todo lo que compraste, de lo más nuevo a lo más viejo."),
        new Seccion("solicitudes", "Mis solicitudes", "Mis solicitudes de cancelación",
                    "Los pedidos que pediste cancelar y qué se te respondió."),
        new Seccion("preferencias", "Preferencias de vino", "Preferencias de vino",
                    "Marcá lo que te gusta y te mostramos los vinos del catálogo que van con eso."),
        new Seccion("seguridad", "Seguridad", "Seguridad",
                    "Cambiá la contraseña con la que ingresás.")
    );

    /** Cuantos vinos sugeridos se muestran. Era el .slice(0, 6) de cuenta.js:1085. */
    private static final int SUGERIDOS = 6;

    private final UsuarioService usuarios;
    private final PedidoService pedidos;
    private final SolicitudCancelacionService solicitudes;
    private final ProductoService productos;

    public CuentaController(UsuarioService usuarios,
                            PedidoService pedidos,
                            SolicitudCancelacionService solicitudes,
                            ProductoService productos) {
        this.usuarios = usuarios;
        this.pedidos = pedidos;
        this.solicitudes = solicitudes;
        this.productos = productos;
    }

    // ============================================================
    //  LA PUERTA
    // ============================================================

    /**
     * /cuenta manda a la primera seccion si ya ingreso, y muestra el
     * formulario de ingreso si no.
     *
     * POR QUE REDIRIGE EN VEZ DE DIBUJAR LA PRIMERA SECCION ACA: para
     * que la direccion diga cual se esta mirando. Si /cuenta mostrara
     * "Datos principales" sin cambiar la barra, esa pantalla no se
     * podria compartir ni volver a ella, que es el problema que tenia
     * el #hash.
     */
    @GetMapping
    public String cuenta(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return autenticado == null ? "cuenta/ingresar" : "redirect:/cuenta/datos";
    }

    // ============================================================
    //  LAS CINCO SECCIONES
    // ============================================================

    /**
     * Una seccion del perfil.
     *
     * UNA SOLA RUTA PARA LAS CINCO, con el nombre en la direccion.
     * Cinco metodos casi iguales serian cinco lugares donde acordarse
     * de volver a leer el usuario.
     *
     * EL USUARIO SE VUELVE A LEER DE LA BASE en cada vista, y no se
     * usa el que trae la sesion. Es la misma decision que explicaba
     * cuenta.js:685: el de la sesion es una copia del momento en que
     * ingreso, y aca se muestran y se editan sus datos. Si cambio algo
     * -el propio cliente en otra pestana, o un administrador desde el
     * panel- lo que vale es lo que dice la base.
     */
    @GetMapping("/{seccion}")
    public String seccion(@PathVariable String seccion,
                          @AuthenticationPrincipal UsuarioAutenticado autenticado,
                          @RequestHeader(value = "HX-Request", required = false) String htmx,
                          HttpServletResponse respuesta,
                          Model modelo) {

        Seccion elegida = SECCIONES.stream()
                .filter(s -> s.id().equals(seccion))
                .findFirst()
                /*
                 * Una direccion inventada a mano no deja la pagina en
                 * blanco: cae en la primera seccion, igual que hacia
                 * mostrarSeccion() en cuenta.js:744 con un #hash viejo.
                 */
                .orElse(SECCIONES.get(0));

        Usuario usuario = alDia(autenticado);
        llenarElModelo(elegida, usuario, modelo);

        if (!esHtmx(htmx)) {
            return "cuenta/perfil";
        }
        /*
         * HX-Push-Url para que la barra de direcciones acompane al
         * panel. Sin esto, cambiar de seccion dejaria la direccion en
         * la que se entro y recargar mostraria otra cosa.
         */
        respuesta.setHeader("HX-Push-Url", "/cuenta/" + elegida.id());
        return "cuenta/panel :: cuerpo";
    }

    private void llenarElModelo(Seccion seccion, Usuario usuario, Model modelo) {
        modelo.addAttribute("secciones", SECCIONES);
        modelo.addAttribute("seccion", seccion);
        modelo.addAttribute("perfil", usuario);

        switch (seccion.id()) {
            case "pedidos" -> {
                /*
                 * Del mas nuevo al mas viejo: lo ultimo que compro es
                 * lo que viene a mirar (cuenta.js:874).
                 */
                modelo.addAttribute("misPedidos", pedidos.obtenerPorUsuario(usuario.getIdUsuario())
                        .stream()
                        .sorted(Comparator.comparing(Pedido::getIdPedido).reversed())
                        .toList());
                /*
                 * Las solicitudes viajan con los pedidos porque cada
                 * tarjeta muestra si ya se pidio cancelar ese pedido
                 * (cuenta.js:877). Se traen de una vez y no una por
                 * pedido.
                 *
                 * Y van COMO MAPA pedido -> solicitud: la plantilla
                 * necesita, por cada tarjeta, la solicitud de ESE
                 * pedido. Con una lista habria que recorrerla desde el
                 * HTML, que es escribir una busqueda en el peor lugar
                 * posible. El mapa se arma una vez aca.
                 */
                List<SolicitudCancelacion> mias = solicitudesDe(usuario);
                modelo.addAttribute("misSolicitudes", mias);
                modelo.addAttribute("solicitudPorPedido", mias.stream()
                        .filter(s -> s.getPedido() != null)
                        .collect(Collectors.toMap(
                                s -> s.getPedido().getIdPedido(),
                                s -> s,
                                /*
                                 * Si un pedido tuviera dos solicitudes
                                 * (no deberia, pero la base no lo
                                 * impide), vale la mas nueva: la lista
                                 * ya viene de la mas nueva a la mas
                                 * vieja, asi que se queda la primera.
                                 */
                                (primera, segunda) -> primera)));
            }
            case "solicitudes" -> modelo.addAttribute("misSolicitudes", solicitudesDe(usuario));
            case "preferencias" -> prepararPreferencias(usuario, modelo);
            default -> { }
        }
    }

    private List<SolicitudCancelacion> solicitudesDe(Usuario usuario) {
        return solicitudes.obtenerPorUsuario(usuario.getIdUsuario()).stream()
                .sorted(Comparator.comparing(SolicitudCancelacion::getFechaSolicitud,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    // ============================================================
    //  DATOS PRINCIPALES
    // ============================================================

    /**
     * Guarda nombre, apellido, email y telefono.
     *
     * SE MANDA EL PERFIL COMPLETO Y NO SOLO LO QUE CAMBIO, igual que
     * antes: actualizarPerfil() toca unicamente los campos que recibe,
     * asi que guardar los datos no borra las preferencias de vino.
     *
     * LAS VALIDACIONES SON LAS DEL SERVICIO. UsuarioService ya revisa
     * la forma del email y que no este repetido; repetir esas reglas
     * aca seria tener dos versiones de la misma regla, que es como
     * empiezan a separarse.
     */
    @PostMapping("/datos")
    public String guardarDatos(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                               @RequestParam String nombre,
                               @RequestParam String apellido,
                               @RequestParam String email,
                               @RequestParam(required = false) String telefono,
                               @RequestHeader(value = "HX-Request", required = false) String htmx,
                               Model modelo) {
        Usuario usuario = alDia(autenticado);

        Usuario datos = new Usuario();
        datos.setNombre(nombre == null ? "" : nombre.trim());
        datos.setApellido(apellido == null ? "" : apellido.trim());
        datos.setEmail(email == null ? "" : email.trim());
        datos.setTelefono(telefono == null ? "" : telefono.trim());

        String aviso = null;
        String error = null;
        try {
            usuario = usuarios.actualizarPerfil(usuario.getIdUsuario(), datos);
            aviso = "Tus datos quedaron guardados.";
        } catch (IllegalArgumentException e) {
            // Aca cae, por ejemplo, "Ya existe un usuario con el email ..."
            error = e.getMessage();
            /*
             * Se devuelve lo que la persona escribio y no lo que hay
             * en la base: si el email estaba repetido, el formulario
             * tiene que seguir mostrando el que intento poner para
             * poder corregirlo, no el viejo.
             */
            usuario = datos;
            usuario.setIdUsuario(autenticado.getUsuario().getIdUsuario());
        }

        modelo.addAttribute("aviso", aviso);
        modelo.addAttribute("error", error);
        llenarElModelo(SECCIONES.get(0), usuario, modelo);
        return esHtmx(htmx) ? "cuenta/panel :: cuerpo" : "cuenta/perfil";
    }

    // ============================================================
    //  PREFERENCIAS DE VINO
    // ============================================================

    /**
     * LAS OPCIONES SALEN DEL CATALOGO REAL, no de una lista escrita a
     * mano. Si la bodega suma un varietal aparece solo; si deja de
     * trabajarlo, deja de ofrecerse. Era la decision de cuenta.js:968
     * y se conserva.
     *
     * LOS BLENDS SE JUNTAN EN UNA SOLA OPCION "Blend": en la base cada
     * uno lleva su mezcla completa en el nombre del varietal ("Blend
     * (Malbec, Cabernet Franc...)"), y como opciones para tildar
     * serian dos renglones larguisimos que dicen casi lo mismo.
     */
    private static final String BLEND = "Blend";

    private static String etiquetaVarietal(String v) {
        String texto = v == null ? "" : v;
        return texto.startsWith(BLEND) ? BLEND : texto;
    }

    /**
     * Un vino con su puntaje de coincidencia. Existe solo para poder
     * ordenar por puntaje y despues por nombre, que es lo que hacia el
     * .map().filter().sort() de cuenta.js:1078.
     *
     * Dos puntos = coincide en varietal Y en linea. Uno = en una sola.
     * Cero no entra en la lista.
     */
    private record VinoPuntuado(VinoVista vino, int puntos) { }

    private void prepararPreferencias(Usuario usuario, Model modelo) {
        List<VinoVista> catalogo = productos.obtenerTodos().stream().map(VinoVista::de).toList();

        modelo.addAttribute("varietales", catalogo.stream()
                .map(v -> etiquetaVarietal(v.getVarietal()))
                .filter(v -> !v.isBlank()).distinct().sorted().toList());
        modelo.addAttribute("lineas", catalogo.stream()
                .map(VinoVista::getLinea).filter(l -> l != null && !l.isBlank())
                .distinct().toList());

        List<String> favVarietales = aLista(usuario.getVarietalesFavoritos());
        List<String> favLineas = aLista(usuario.getLineasFavoritas());
        modelo.addAttribute("favVarietales", favVarietales);
        modelo.addAttribute("favLineas", favLineas);

        /*
         * LOS VINOS QUE COINCIDEN CON LO GUARDADO. Es la razon de que
         * esta seccion exista: sin esto, el formulario seria un par de
         * listas de tildes que no hacen nada (cuenta.js:1061).
         *
         * Primero los que coinciden en las dos cosas, que son los mas
         * parecidos a lo que la persona dijo que le gusta.
         */
        modelo.addAttribute("sugeridos", catalogo.stream()
                .map(v -> new VinoPuntuado(v,
                        (favVarietales.contains(etiquetaVarietal(v.getVarietal())) ? 1 : 0)
                      + (favLineas.contains(v.getLinea()) ? 1 : 0)))
                .filter(vp -> vp.puntos() > 0)
                .sorted(Comparator.comparingInt(VinoPuntuado::puntos).reversed()
                        .thenComparing(vp -> vp.vino().getNombre()))
                .map(VinoPuntuado::vino)
                .limit(SUGERIDOS)
                .toList());
        modelo.addAttribute("eligioAlgo", !favVarietales.isEmpty() || !favLineas.isEmpty());
    }

    @PostMapping("/preferencias")
    public String guardarPreferencias(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                      @RequestParam(required = false) List<String> varietal,
                                      @RequestParam(required = false) List<String> linea,
                                      @RequestHeader(value = "HX-Request", required = false) String htmx,
                                      Model modelo) {
        Usuario usuario = alDia(autenticado);

        /*
         * Las preferencias se guardan como texto separado por coma,
         * que es como estan en la columna de MySQL. Esta es la unica
         * conversion; antes la hacian aLista() y tildados() en
         * cuenta.js:1111.
         *
         * NO TILDAR NADA TIENE QUE PODER GUARDARSE: si los parametros
         * no vienen, la lista es null y se escribe vacio. Si se
         * ignorara el caso, nadie podria desmarcar lo que eligio.
         */
        Usuario datos = new Usuario();
        datos.setNombre(usuario.getNombre());
        datos.setApellido(usuario.getApellido());
        datos.setEmail(usuario.getEmail());
        datos.setVarietalesFavoritos(varietal == null ? "" : String.join(",", varietal));
        datos.setLineasFavoritas(linea == null ? "" : String.join(",", linea));

        String aviso = null, error = null;
        try {
            usuario = usuarios.actualizarPerfil(usuario.getIdUsuario(), datos);
            aviso = "Listo, vamos a tener en cuenta lo que te gusta.";
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        }

        modelo.addAttribute("aviso", aviso);
        modelo.addAttribute("error", error);
        llenarElModelo(SECCIONES.get(3), usuario, modelo);
        return esHtmx(htmx) ? "cuenta/panel :: cuerpo" : "cuenta/perfil";
    }

    // ============================================================
    //  SEGURIDAD
    // ============================================================

    /**
     * Cambia la contrasena.
     *
     * LO QUE SE COMPRUEBA ACA Y LO QUE COMPRUEBA EL SERVICIO: que las
     * dos nuevas coincidan y que la nueva sea distinta de la actual
     * son cosas del formulario, no del dominio, y el servicio no las
     * conoce. Que la actual sea la correcta y que la nueva cumpla los
     * requisitos los decide UsuarioService, que es donde viven.
     *
     * NINGUNA DE LAS TRES CONTRASENAS VUELVE EN LA RESPUESTA. Se
     * devuelve el formulario vacio, haya salido bien o mal: rellenar
     * un campo de contrasena con lo que se tecleo significa mandarlo
     * de vuelta por la red y dejarlo en el HTML.
     */
    @PostMapping("/seguridad")
    public String cambiarPassword(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                  @RequestParam String actual,
                                  @RequestParam String nueva,
                                  @RequestParam String repetir,
                                  @RequestHeader(value = "HX-Request", required = false) String htmx,
                                  Model modelo) {
        Usuario usuario = alDia(autenticado);
        String aviso = null, error = null;

        if (actual == null || actual.isEmpty()) {
            error = "Escribí tu contraseña actual.";
        } else if (nueva == null || nueva.isEmpty()) {
            error = "Escribí la contraseña nueva.";
        } else if (!nueva.equals(repetir)) {
            error = "Las dos contraseñas nuevas no coinciden.";
        } else if (nueva.equals(actual)) {
            error = "La contraseña nueva tiene que ser distinta de la actual.";
        } else {
            try {
                usuarios.cambiarPassword(usuario.getIdUsuario(), actual, nueva);
                aviso = "Tu contraseña quedó cambiada.";
            } catch (IllegalArgumentException e) {
                // "La contrasena actual no es correcta." y los requisitos.
                error = e.getMessage();
            }
        }

        modelo.addAttribute("aviso", aviso);
        modelo.addAttribute("error", error);
        llenarElModelo(SECCIONES.get(4), usuario, modelo);
        return esHtmx(htmx) ? "cuenta/panel :: cuerpo" : "cuenta/perfil";
    }

    // ============================================================

    /** El usuario leido de la base, no la copia de la sesion. */
    private Usuario alDia(UsuarioAutenticado autenticado) {
        return usuarios.obtenerPorId(autenticado.getUsuario().getIdUsuario())
                .orElse(autenticado.getUsuario());
    }

    /** "Malbec, Blend" -> ["Malbec", "Blend"]. Era aLista() de cuenta.js:1111. */
    private static List<String> aLista(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(texto.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static boolean esHtmx(String cabecera) {
        return cabecera != null;
    }
}
