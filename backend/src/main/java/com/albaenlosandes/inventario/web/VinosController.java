package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.model.Producto;
import com.albaenlosandes.inventario.service.FichasVino;
import com.albaenlosandes.inventario.service.ProductoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * ==================================================================
 *  EL CATALOGO: LAS LINEAS Y LA FICHA DE CADA VINO
 * ==================================================================
 *
 * QUE REEMPLAZA:
 *   js/vinos.js  (101 lineas)  -> GET /vinos
 *   js/vino.js   (259 lineas)  -> GET /vinos/{id}
 *   js/inicio.js (48 lineas)   -> GET /vinos/destacados
 *   js/modules/vino.js (100)   -> el fragmento de la tarjeta
 *
 * LO QUE CAMBIA PARA QUIEN MIRA. Antes, vino.html pesaba 1.186 bytes y
 * no tenia ni el titulo: el <h1>, el <title> de la pestana y las cinco
 * secciones las escribia JavaScript despues de pedir el catalogo
 * ENTERO por fetch y buscar el vino por id en memoria. Ahora el
 * servidor manda la pagina hecha, con su h1 y su title.
 *
 * LAS DIRECCIONES TAMBIEN CAMBIAN, y para mejor:
 *     antes   /vino.html?id=3
 *     ahora   /vinos/3
 * Es lo que pide la semantica REST que ensena la presentacion: la
 * direccion nombra al recurso. La vieja sigue andando (redirige), que
 * para eso esta CompatibilidadController.
 */
@Controller
public class VinosController {

    /**
     * Que cuenta cada linea. Es texto editorial, no sale de la base:
     * estaba en vinos.js:20 y se muda tal cual.
     *
     * POR QUE NO VA EN LA BASE: describe una linea comercial, no un
     * producto. No hay tabla de lineas (la columna 'linea' es un enum),
     * y crear una con cuatro filas de texto fijo para que nadie las
     * edite nunca seria una tabla de adorno.
     */
    public record LineaConVinos(String nombre, String texto, List<VinoVista> vinos) { }

    private static final List<String[]> LINEAS = List.of(
        new String[]{"Finca",
            "La puerta de entrada a la finca. Vinos jóvenes, frescos y frutados, " +
            "pensados para la mesa de todos los días."},
        new String[]{"Estate Reserve",
            "Un paso más en estructura y complejidad. Uvas seleccionadas de los " +
            "mejores cuadros del viñedo, con paso por madera."},
        new String[]{"Gran Reserva",
            "Los vinos de guarda de la bodega. Gran cuerpo e identidad, con crianza " +
            "en barrica de roble francés y final prolongado."},
        new String[]{"Ediciones Especiales",
            "Blends de partida limitada, donde se busca la expresión más alta de la " +
            "finca. Se sugiere decantar."}
    );

    /** Cuantos vinos muestra la portada. El resto esta en la tienda. */
    private static final int DESTACADOS_EN_LA_PORTADA = 8;

    private final ProductoService productos;
    private final FichasVino fichas;

    public VinosController(ProductoService productos, FichasVino fichas) {
        this.productos = productos;
        this.fichas = fichas;
    }

    // ============================================================
    //  NUESTROS VINOS — el catalogo agrupado por linea
    // ============================================================

    /**
     * Es la cara informativa del catalogo: no se compra desde aca, se
     * compra en /tienda. Las dos leen los mismos productos, asi que no
     * pueden quedar desincronizadas.
     *
     * Una linea sin vinos no se muestra vacia: se saltea, igual que
     * hacia vinos.js:54.
     */
    @GetMapping("/vinos")
    public String porLinea(Model modelo) {
        List<VinoVista> activos = activos();

        List<LineaConVinos> lineas = LINEAS.stream()
                .map(l -> new LineaConVinos(l[0], l[1],
                        activos.stream().filter(v -> v.getLinea().equals(l[0])).toList()))
                .filter(l -> !l.vinos().isEmpty())
                .toList();

        modelo.addAttribute("lineas", lineas);
        return "publico/vinos";
    }

    // ============================================================
    //  LOS DESTACADOS DE LA PORTADA — fragmento para HTMX
    // ============================================================

    /**
     * Devuelve SOLO la grilla de tarjetas, sin encabezado ni pie: es un
     * fragmento, no una pagina. La portada lo pide con hx-get apenas se
     * dibuja y lo mete adentro de #vinosDestacados.
     *
     * Se puede probar con curl y se ve lo que manda:
     *     curl localhost:8080/vinos/destacados
     *
     * POR QUE UNA RUTA PROPIA Y NO EL HEADER HX-Request SOBRE /vinos:
     * porque asi el fragmento es un recurso con su direccion, se prueba
     * suelto y no depende de que el pedido venga de HTMX.
     */
    @GetMapping("/vinos/destacados")
    public String destacados(Model modelo) {
        modelo.addAttribute("vinos", activos().stream().limit(DESTACADOS_EN_LA_PORTADA).toList());
        modelo.addAttribute("comprable", false);
        return "fragmentos/vino :: grilla";
    }

    // ============================================================
    //  LA FICHA DE UN VINO
    // ============================================================

    /**
     * Mezcla las dos fuentes que ya mezclaba vino.js:
     *   - de la base: precio, stock, cosecha, si esta a la venta
     *   - de las fichas: cata, vinificacion, crianza, vinedo, analitica
     *
     * Si el vino no tiene ficha cargada, la pagina se dibuja igual con
     * lo que si sabe la base y saltea las secciones que faltan.
     *
     * Un id que no existe da 404 de verdad, con la pagina de error del
     * sitio. Antes daba 200 con un cartel dibujado por JavaScript, que
     * para un buscador significa "esta pagina existe".
     */
    @GetMapping("/vinos/{id}")
    public String ficha(@PathVariable Integer id, Model modelo) throws NoResourceFoundException {
        Producto producto = productos.obtenerPorId(id)
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .orElseThrow(() -> new NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/vinos/" + id));

        VinoVista vino = VinoVista.de(producto);

        modelo.addAttribute("vino", vino);
        modelo.addAttribute("ficha", fichas.de(vino.getNombre()));   // null si no tiene
        modelo.addAttribute("vinedo", fichas.vinedo());
        modelo.addAttribute("certificacion", fichas.certificacion());

        // Las otras etiquetas de la misma linea, para seguir mirando.
        modelo.addAttribute("hermanos", activos().stream()
                .filter(v -> v.getLinea().equals(vino.getLinea()) && !v.getId().equals(vino.getId()))
                .toList());

        return "publico/vino";
    }

    // ============================================================

    /** Los vinos a la venta, ya traducidos a lo que miran las pantallas. */
    private List<VinoVista> activos() {
        return productos.obtenerTodos().stream().map(VinoVista::de).toList();
    }
}
