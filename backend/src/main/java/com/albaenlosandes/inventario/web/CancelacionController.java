package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.config.UsuarioAutenticado;
import com.albaenlosandes.inventario.model.MotivoCancelacion;
import com.albaenlosandes.inventario.model.Pedido;
import com.albaenlosandes.inventario.service.PedidoService;
import com.albaenlosandes.inventario.service.PoliticaCancelacion;
import com.albaenlosandes.inventario.service.PreguntaFrecuenteService;
import com.albaenlosandes.inventario.service.SolicitudCancelacionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ==================================================================
 *  PEDIR LA CANCELACION DE UN PEDIDO
 * ==================================================================
 *
 * Era el modal de cuenta.js:1201. Ahora es una PANTALLA PROPIA con su
 * direccion, /cuenta/cancelacion/81, y no una ventana encima de "Mis
 * pedidos".
 *
 * POR QUE DEJO DE SER UN MODAL: lo que se decide ahi no es un detalle.
 * Se leen las preguntas frecuentes del motivo, se lee que va a pasar
 * con ese pedido y despues se confirma. Un modal no se puede compartir,
 * no se puede volver a el, y si la pagina se recarga se pierde. Una
 * direccion propia si.
 *
 * LAS TRES COSAS QUE MUESTRA, y de donde salen:
 *     las preguntas del motivo   PreguntaFrecuenteService.obtenerParaMotivo
 *     que va a pasar             SolicitudCancelacionService.evaluar
 *     el pedido                  PedidoService
 *
 * AL CAMBIAR EL MOTIVO cambian las dos primeras, y nada mas. Por eso el
 * <select> pide con hx-get solo ese pedazo (fragmento 'ayuda'), igual
 * que hacia refrescar() en cuenta.js:1228. Sin HTMX el <select> va con
 * su propio boton y recarga la pantalla entera: la pantalla funciona
 * igual, solo que con un viaje mas.
 */
@Controller
@RequestMapping("/cuenta/cancelacion")
public class CancelacionController {

    private final PedidoService pedidos;
    private final SolicitudCancelacionService solicitudes;
    private final PreguntaFrecuenteService preguntas;

    /** Cuantas preguntas se muestran. Era el .slice(0, 4) de cuenta.js:1241. */
    private static final int PREGUNTAS = 4;

    public CancelacionController(PedidoService pedidos,
                                 SolicitudCancelacionService solicitudes,
                                 PreguntaFrecuenteService preguntas) {
        this.pedidos = pedidos;
        this.solicitudes = solicitudes;
        this.preguntas = preguntas;
    }

    /**
     * Los motivos para el <select>, con el texto con el que se muestran.
     *
     * El orden es el del enum y no el de un mapa suelto: Textos.de()
     * tiene la traduccion, pero un Map no garantiza orden y la lista
     * cambiaria de posicion entre visitas.
     */
    @ModelAttribute("motivos")
    public Map<MotivoCancelacion, String> motivos() {
        Map<MotivoCancelacion, String> lista = new LinkedHashMap<>();
        Arrays.stream(MotivoCancelacion.values()).forEach(m -> lista.put(m, Textos.de(m)));
        return lista;
    }

    // ============================================================
    //  LA PANTALLA
    // ============================================================

    @GetMapping("/{idPedido}")
    public String formulario(@PathVariable Integer idPedido,
                             @RequestParam(required = false) MotivoCancelacion motivo,
                             @AuthenticationPrincipal UsuarioAutenticado autenticado,
                             Model modelo) {

        Integer idUsuario = autenticado.getIdUsuario();

        /*
         * EL PEDIDO SE BUSCA ENTRE LOS DEL USUARIO DE LA SESION, no por
         * id suelto. Si no, escribir /cuenta/cancelacion/82 mostraria el
         * pedido de otra persona: la direccion la escribe quien quiera.
         */
        Pedido pedido = pedidos.obtenerPorUsuario(idUsuario).stream()
                .filter(p -> p.getIdPedido().equals(idPedido))
                .findFirst()
                .orElse(null);

        if (pedido == null) {
            return "redirect:/cuenta/pedidos";
        }

        MotivoCancelacion elegido = motivo == null ? MotivoCancelacion.ARREPENTIMIENTO : motivo;

        modelo.addAttribute("pedido", pedido);
        modelo.addAttribute("elegido", elegido);
        llenarAyuda(idPedido, idUsuario, elegido, modelo);
        return "cuenta/cancelacion";
    }

    /**
     * Las preguntas y el aviso del motivo elegido, nada mas.
     *
     * Lo pide el <select> al cambiar. Devuelve un fragmento y no la
     * pantalla entera porque el resto -el pedido, el comentario que la
     * persona ya escribio- no cambia, y volver a mandarlo borraria el
     * comentario a medio escribir.
     */
    @GetMapping("/{idPedido}/ayuda")
    public String ayuda(@PathVariable Integer idPedido,
                        @RequestParam MotivoCancelacion motivo,
                        @AuthenticationPrincipal UsuarioAutenticado autenticado,
                        Model modelo) {
        llenarAyuda(idPedido, autenticado.getIdUsuario(), motivo, modelo);
        return "cuenta/cancelacion :: ayuda";
    }

    private void llenarAyuda(Integer idPedido, Integer idUsuario,
                             MotivoCancelacion motivo, Model modelo) {
        /*
         * Las del motivo elegido mas las generales, hasta cuatro: era el
         * filtro de cuenta.js:1241. Las generales son las que no tienen
         * motivo anotado, y sirven para cualquiera.
         */
        modelo.addAttribute("preguntas", preguntas.obtenerParaMotivo(motivo).stream()
                .limit(PREGUNTAS)
                .toList());

        /*
         * QUE VA A PASAR CON ESTE PEDIDO, antes de confirmar. El cliente
         * no tiene que mandar la solicitud para enterarse de si se
         * cancela sola o pasa a revision.
         */
        PoliticaCancelacion.Evaluacion evaluacion = solicitudes.evaluar(idPedido, idUsuario, motivo);
        modelo.addAttribute("evaluacion", evaluacion);
        modelo.addAttribute("elegido", motivo);
        modelo.addAttribute("idPedido", idPedido);
    }

    // ============================================================
    //  CONFIRMAR
    // ============================================================

    /**
     * Registra la solicitud y manda a "Mis solicitudes".
     *
     * A DONDE VA DESPUES: a la seccion donde esta lo que acaba de pasar,
     * con el cartel de que quedo registrada. Dejarlo en "Mis pedidos"
     * seria mostrarle la lista de compras cuando lo que quiere saber es
     * en que quedo su pedido.
     *
     * EL AVISO VIAJA COMO FLASH ATTRIBUTE y no en la direccion: asi no
     * queda un ?aviso=... que se vuelva a mostrar cada vez que alguien
     * recarga o comparte el enlace.
     */
    @PostMapping("/{idPedido}")
    public String confirmar(@PathVariable Integer idPedido,
                           @RequestParam MotivoCancelacion motivo,
                           @RequestParam(required = false) String comentario,
                           @AuthenticationPrincipal UsuarioAutenticado autenticado,
                           RedirectAttributes flash,
                           Model modelo) {
        try {
            solicitudes.crear(idPedido, autenticado.getIdUsuario(), motivo,
                              comentario == null ? "" : comentario.trim());
            flash.addFlashAttribute("aviso", "Tu solicitud quedó registrada.");
            return "redirect:/cuenta/solicitudes";
        } catch (IllegalArgumentException e) {
            /*
             * Aca cae lo que decide la politica: un pedido que ya no se
             * puede cancelar, o uno que ya tiene una solicitud abierta.
             * Se vuelve a la misma pantalla con el error a la vista.
             */
            modelo.addAttribute("error", e.getMessage());
            return formulario(idPedido, motivo, autenticado, modelo);
        }
    }
}
