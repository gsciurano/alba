package com.albaenlosandes.inventario.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/**
 * ==================================================================
 *  LAS DIRECCIONES VIEJAS SIGUEN LLEVANDO A ALGUN LADO
 * ==================================================================
 *
 * Hasta esta migracion cada pagina era un archivo: index.html,
 * finca.html, terminos.html. Ahora son rutas: /, /finca,
 * /legal/terminos.
 *
 * POR QUE NO ALCANZA CON CAMBIAR LOS ENLACES DEL SITIO. Las direcciones
 * viejas no viven solo adentro del proyecto:
 *   - estan escritas dentro de los textos legales, que son documentos
 *     que alguien pudo haber guardado o impreso;
 *   - pudieron compartirse por mensaje o quedar en un marcador;
 *   - cuenta.html#pedidos aparece citada en los terminos y condiciones
 *     como el lugar donde se tramita una cancelacion, que es una
 *     obligacion legal, no una comodidad.
 *
 * Un 301 cuesta tres lineas y evita que todo eso termine en un "no se
 * encuentra la pagina". Se devuelve 301 (permanente) y no 302 porque la
 * mudanza es definitiva: asi los buscadores reemplazan la direccion
 * vieja por la nueva en vez de seguir ofreciendo las dos.
 *
 * Este archivo se queda. No es andamiaje: es la cicatriz prolija de la
 * mudanza, y conviene que siga ahi mientras exista la posibilidad de que
 * alguien llegue por un enlace viejo.
 */
@Controller
public class CompatibilidadController {

    @GetMapping("/index.html")
    public RedirectView inicio() {
        return permanente("/");
    }

    @GetMapping("/finca.html")
    public RedirectView finca() {
        return permanente("/finca");
    }

    @GetMapping("/nosotros.html")
    public RedirectView nosotros() {
        return permanente("/nosotros");
    }

    @GetMapping("/contacto.html")
    public RedirectView contacto() {
        return permanente("/contacto");
    }

    @GetMapping("/privacidad.html")
    public RedirectView privacidad() {
        return permanente("/legal/privacidad");
    }

    @GetMapping("/terminos.html")
    public RedirectView terminos() {
        return permanente("/legal/terminos");
    }

    @GetMapping("/arrepentimiento.html")
    public RedirectView arrepentimiento() {
        return permanente("/legal/arrepentimiento");
    }

    /**
     * La tienda vieja.
     *
     * ES EL REDIRECCIONAMIENTO MAS NECESARIO DE TODOS, porque la
     * direccion vieja no esta solo en marcadores ajenos: la enlazan
     * dos pantallas del propio sitio que todavia no se migraron. El
     * menu de arriba la escribe sitio.js:53 y el boton "Ver el
     * catalogo" de la cuenta esta en cuenta.js:867. Sin esto, entrar
     * a la tienda desde la pagina de la cuenta daria 404.
     *
     * EL ANCLA SE PIERDE, Y NO SE PUEDE EVITAR: sitio.js:128 mandaba a
     * tienda.html#carrito. El navegador NO manda la parte que sigue al
     * numeral al servidor, asi que este metodo nunca la ve y no puede
     * redirigir a /tienda/carrito. Quien venga de ese boton cae en el
     * catalogo con el carrito a un clic, en el icono de arriba. Se
     * arregla solo cuando se migre la pantalla que lo escribe.
     */
    @GetMapping("/tienda.html")
    public RedirectView tienda() {
        return permanente("/tienda");
    }

    @GetMapping("/vinos.html")
    public RedirectView vinos() {
        return permanente("/vinos");
    }

    /**
     * La ficha de un vino cambio de forma, no solo de nombre:
     *     antes   /vino.html?id=3
     *     ahora   /vinos/3
     *
     * El id viajaba como parametro de consulta y ahora es parte de la
     * direccion, que es como corresponde cuando identifica al recurso.
     * Si alguien llega sin id (por ejemplo desde un enlace cortado), se
     * lo manda al listado en vez de darle un error.
     */
    @GetMapping("/vino.html")
    public RedirectView vino(@RequestParam(required = false) Integer id) {
        return permanente(id == null ? "/vinos" : "/vinos/" + id);
    }

    /**
     * La cuenta tambien cambio de forma, no solo de nombre: antes una
     * sola pagina elegia la seccion por el numeral
     * (cuenta.html#pedidos) y ahora cada seccion tiene su direccion
     * (/cuenta/pedidos). Como el navegador no manda el numeral al
     * servidor, aca solo se puede mandar a la puerta: /cuenta decide si
     * corresponde el ingreso o el perfil.
     */
    @GetMapping("/cuenta.html")
    public RedirectView cuenta() {
        return permanente("/cuenta");
    }

    /**
     * Un 301 y no el 302 que devuelve 'redirect:' por defecto.
     *
     * La diferencia no es cosmetica: 302 significa "por ahora esta aca"
     * y un buscador sigue ofreciendo la direccion vieja; 301 significa
     * "se mudo para siempre" y la reemplaza por la nueva. La mudanza es
     * definitiva, asi que corresponde 301.
     */
    private RedirectView permanente(String destino) {
        RedirectView redireccion = new RedirectView(destino);
        redireccion.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
        return redireccion;
    }

    /*
     * Falta todavia admin.html: el panel sigue sirviendose como archivo
     * estatico porque su etapa de migracion no llego. Suma su
     * redireccion aca el dia que /admin exista, y recien entonces se
     * borra el archivo de static/.
     */
}
