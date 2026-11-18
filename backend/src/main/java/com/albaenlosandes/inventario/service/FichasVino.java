package com.albaenlosandes.inventario.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * ==================================================================
 *  LAS FICHAS DE CATA
 * ==================================================================
 *
 * QUE SON. El texto editorial de cada vino: las notas de cata, como se
 * vinifica, cuanto tiempo pasa en barrica, con que acompanarlo y los
 * numeros del laboratorio. Son diez fichas, una por etiqueta, sacadas
 * de las fichas publicas de albaenlosandes.com.
 *
 * POR QUE SIGUEN SIN ESTAR EN LA BASE. Es la misma razon que ya estaba
 * escrita en data/fichas-vinos.js: la tabla 'productos' guarda lo que
 * cambia y lo que el panel tiene que poder editar (precio, stock,
 * anada, estado). La ficha de cata no cambia entre dos consultas al
 * stock y nadie la edita desde el panel. Meterla en MySQL obligaria a
 * agregar una decena de columnas de texto que ningun formulario llena.
 *
 * QUE SI CAMBIO. Antes era un modulo JavaScript que bajaba al navegador
 * (14 KB) y que solo podia leer el navegador. Ahora es un JSON que lee
 * el servidor, porque es el servidor el que arma la pagina. El texto es
 * exactamente el mismo: se convirtio con un script, sin tocar una coma.
 *
 * SE LEE UNA SOLA VEZ, al arrancar. Es contenido fijo: volver a leer el
 * archivo en cada visita seria gastar disco para obtener siempre lo
 * mismo.
 */
@Service
public class FichasVino {

    /** Vista, nariz y boca, como las publica la bodega. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cata(String vista, String nariz, String boca) { }

    /** Los numeros del laboratorio. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Analitica(String alcohol, String acidez, String ph, String azucar) { }

    /**
     * Una ficha.
     *
     * 'servicio' solo lo tienen dos de las diez (es la recomendacion de
     * decantar o de servir a cierta temperatura, y la bodega no la da
     * para todas). Las plantillas lo preguntan antes de dibujarlo, asi
     * que donde falta no queda un titulo vacio.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ficha(
            String presentacion,
            String composicion,
            Cata cata,
            String vinificacion,
            String crianza,
            String maridaje,
            String servicio,
            Analitica analitica,
            String origen) { }

    /**
     * El vinedo es el mismo para las diez etiquetas, asi que se describe
     * una sola vez. Si se repitiera en cada ficha, corregir un dato
     * obligaria a corregirlo diez veces.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vinedo(
            String region,
            String antiguedad,
            String altura,
            String orientacion,
            String conduccion,
            String suelo,
            String cosecha) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Archivo(Vinedo vinedo, String certificacion, Map<String, Ficha> fichas) { }

    private Archivo datos;

    @PostConstruct
    void cargar() throws IOException {
        try (InputStream entrada = new ClassPathResource("datos/fichas-vinos.json").getInputStream()) {
            this.datos = new ObjectMapper().readValue(entrada, Archivo.class);
        }
    }

    /**
     * La ficha de un vino, buscada por su NOMBRE exacto, que es el mismo
     * de la columna 'nombre' de la tabla productos.
     *
     * Devuelve null si ese vino todavia no tiene ficha: pasa con
     * cualquier vino nuevo cargado desde el panel. La pagina de detalle
     * lo contempla y muestra lo que si sabe de la base, en vez de
     * romperse o dejar titulos vacios.
     */
    public Ficha de(String nombreDelVino) {
        return datos.fichas().get(nombreDelVino);
    }

    public Vinedo vinedo() {
        return datos.vinedo();
    }

    /** "Vino vegano certificado por IRAM": vale para todas las etiquetas. */
    public String certificacion() {
        return datos.certificacion();
    }
}
