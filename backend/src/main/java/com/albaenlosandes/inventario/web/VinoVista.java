package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.model.Linea;
import com.albaenlosandes.inventario.model.Producto;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * ==================================================================
 *  UN VINO, COMO LO NECESITA UNA PANTALLA
 * ==================================================================
 *
 * DE DONDE SALE ESTA CLASE. Hasta ahora esta traduccion la hacia el
 * navegador: js/modules/api.js:131 (productoDesdeApi) convertia lo que
 * devolvia la API en el objeto que esperaban las pantallas, porque el
 * back-end y el front-end nombran las cosas distinto desde que se
 * escribieron por separado:
 *
 *     base de datos     pantalla
 *     ---------------   -----------
 *     idProducto        id
 *     anada             cosecha
 *     descripcion       notasCata
 *     stockActual       stock
 *     linea: FINCA      linea: "Finca"
 *     imagenUrl         imagen (archivo local)
 *
 * Esa traduccion no desaparece: se muda al servidor, que es donde
 * siempre tuvo que estar. La entidad Producto sigue siendo la de la
 * base y las plantillas reciben esto.
 *
 * POR QUE NO SE LE PASA LA ENTIDAD Producto DIRECTO A LA PLANTILLA:
 * porque entonces la plantilla tendria que saber que 'anada' se muestra
 * como "Cosecha", que FINCA se escribe "Finca" y como se arma el nombre
 * del archivo de la foto. Eso es logica, y la cuarta regla de la
 * presentacion es clara: la plantilla formatea y presenta, la logica
 * vive en el servidor.
 */
public class VinoVista {

    /**
     * La columna 'linea' en MySQL es un enum en MAYUSCULAS_CON_GUION.
     * Esta es la misma tabla que tenia api.js:102 (LINEA_A_TEXTO).
     */
    private static final Map<Linea, String> TEXTO_DE_LINEA = Map.of(
            Linea.FINCA,            "Finca",
            Linea.ESTATE_RESERVE,   "Estate Reserve",
            Linea.GRAN_RESERVA,     "Gran Reserva",
            Linea.EDICION_ESPECIAL, "Ediciones Especiales"
    );

    private final Integer id;
    private final String nombre;
    private final String linea;
    private final String varietal;
    private final Integer cosecha;
    private final BigDecimal precio;
    private final int stock;
    private final int stockMinimo;
    private final String notasCata;
    private final String imagen;

    private VinoVista(Producto p) {
        this.id          = p.getIdProducto();
        this.nombre      = p.getNombre();
        this.linea       = TEXTO_DE_LINEA.getOrDefault(p.getLinea(), String.valueOf(p.getLinea()));
        this.varietal    = p.getVarietal();
        this.cosecha     = p.getAnada();
        this.precio      = p.getPrecio();
        this.stock       = p.getStockActual() == null ? 0 : p.getStockActual();
        this.stockMinimo = p.getStockMinimo() == null ? 0 : p.getStockMinimo();
        this.notasCata   = p.getDescripcion() == null ? "" : p.getDescripcion();
        this.imagen      = rutaDeLaFoto(p.getNombre(), p.getAnada());
    }

    public static VinoVista de(Producto p) {
        return new VinoVista(p);
    }

    /**
     * La foto local de un vino, a partir de su nombre y su anada.
     *
     * Es la misma regla que aplicaba api.js:120 (imagenLocal): se saca
     * los acentos, se pasa a minusculas y se reemplaza todo lo que no
     * sea letra o numero por un guion, y queda
     * "gran-reserva-malbec-2021.jpg".
     *
     * LA COLUMNA imagenUrl NO SE USA PARA ESTO: apunta al sitio original
     * de la bodega, que es una direccion externa. Las fotos estan
     * descargadas en el proyecto, y si alguna faltara el <img> cae al
     * dibujo de respaldo (ver el fragmento de la tarjeta).
     */
    static String rutaDeLaFoto(String nombre, Integer anada) {
        if (nombre == null || nombre.isBlank()) {
            return SIN_FOTO;
        }
        String slug = Normalizer.normalize(nombre.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")      // saca los acentos
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        String ruta = "/assets/img/vinos/" + slug + (anada != null ? "-" + anada : "") + ".jpg";

        /*
         * SE COMPRUEBA QUE LA FOTO EXISTA, ACA Y NO EN EL NAVEGADOR.
         *
         * Antes, cada <img> del sitio llevaba enganchado un evento
         * 'error': el navegador pedia la foto, el servidor contestaba
         * 404, y recien ahi el JavaScript dibujaba una botella de
         * respaldo. Funcionaba, pero significaba un pedido fallido por
         * cada vino sin foto y un parpadeo mientras tanto.
         *
         * El servidor ya sabe que archivos tiene. Preguntarselo cuesta
         * una lectura de disco que el sistema operativo cachea, y a
         * cambio el HTML sale con la ruta correcta de entrada.
         */
        return new ClassPathResource(CARPETA_ESTATICOS + ruta).exists() ? ruta : SIN_FOTO;
    }

    /*
     * ESTA CONSTANTE TIENE QUE SEGUIR A application.properties.
     *
     * La propiedad spring.web.resources.static-locations dice de donde
     * sirve Spring los archivos estaticos. Aca se repite esa misma
     * carpeta porque la comprobacion de arriba lee del classpath a mano,
     * sin pasar por el servidor web. Si alguien mueve la carpeta y se
     * olvida de esta linea, el metodo no falla: simplemente contesta que
     * ninguna foto existe y los diez vinos salen con la botella de
     * respaldo. Es el tipo de error que no se nota en los registros.
     */
    private static final String CARPETA_ESTATICOS = "frontend/static";

    /** El dibujo de respaldo para un vino que todavia no tiene fotografia. */
    private static final String SIN_FOTO = "/assets/img/vinos/_sin-foto.svg";

    // ----- lo que miran las plantillas -----

    public Integer getId()          { return id; }
    public String getNombre()       { return nombre; }
    public String getLinea()        { return linea; }
    public String getVarietal()     { return varietal; }
    public Integer getCosecha()     { return cosecha; }
    public BigDecimal getPrecio()   { return precio; }
    public int getStock()           { return stock; }
    public int getStockMinimo()     { return stockMinimo; }
    public String getNotasCata()    { return notasCata; }
    public String getImagen()       { return imagen; }

    /**
     * ------------------------------------------------------------
     *  LO QUE SE PUEDE DECIR DEL STOCK, Y LO QUE NO
     * ------------------------------------------------------------
     * El numero exacto de botellas es informacion de la bodega, no del
     * visitante: saber que quedan 549 no le cambia nada a quien compra y
     * expone el inventario. Lo unico que de verdad le sirve es si se
     * agoto o si queda poco.
     *
     * Por eso las pantallas publicas preguntan por estos dos metodos y
     * nunca muestran getStock(). Es la misma decision que ya estaba
     * comentada en vino.js:117 y en modules/vino.js:39, ahora del lado
     * del servidor: el numero ni siquiera viaja al navegador.
     */
    public boolean isAgotado() {
        return stock <= 0;
    }

    public boolean isUltimas() {
        return !isAgotado() && stock <= stockMinimo;
    }
}
