package com.albaenlosandes.inventario.web;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * ==================================================================
 *  LOS FILTROS DEL CATALOGO
 * ==================================================================
 *
 * DE DONDE SALE ESTA CLASE. Reemplaza al objeto 'filtros' de
 * js/tienda.js:28 y a la funcion productosFiltrados() de
 * js/tienda.js:107. Antes el navegador se traia los diez vinos al
 * arrancar y filtraba sobre esa copia en memoria; ahora los seis
 * campos llegan como parametros del pedido HTTP y el filtrado ocurre
 * aca.
 *
 * POR QUE EN JAVA Y NO EN SQL. Diez vinos. Una consulta por cada
 * combinacion de filtros (y el WHERE dinamico que eso necesita) seria
 * mas codigo y mas lento que recorrer una lista de diez elementos que
 * Hibernate ya trajo. Si el catalogo creciera a cientos, esto pasa a
 * ser una Specification de Spring Data y el resto del codigo no se
 * entera: ese es el motivo de que el filtrado viva en una clase sola y
 * no desparramado en el controlador.
 *
 * ES UN RECORD CON VALORES POR OMISION. Spring arma uno con lo que
 * venga en la URL; lo que no venga queda en null o vacio, y un filtro
 * vacio no filtra. Asi /tienda sin parametros devuelve el catalogo
 * entero, igual que antes.
 */
public record FiltrosDelCatalogo(String texto,
                                 String varietal,
                                 String linea,
                                 String cosecha,
                                 String precioMax,
                                 String orden) {

    /** Los cuatro ordenes del desplegable, con los mismos valores que el HTML. */
    private static final String POR_NOMBRE      = "nombre";
    private static final String POR_PRECIO_ASC  = "precio-asc";
    private static final String POR_PRECIO_DESC = "precio-desc";
    private static final String POR_COSECHA     = "cosecha";

    /** Sin ningun filtro puesto: lo que ve quien entra a /tienda. */
    public static FiltrosDelCatalogo vacios() {
        return new FiltrosDelCatalogo("", "", "", "", "", POR_NOMBRE);
    }

    /** true si no hay nada puesto. Decide si se muestra "Limpiar filtros". */
    public boolean isSinUsar() {
        return vacio(texto) && vacio(varietal) && vacio(linea)
                && vacio(cosecha) && vacio(precioMax)
                && (vacio(orden) || POR_NOMBRE.equals(orden));
    }

    /**
     * Aplica los seis filtros y el orden.
     *
     * UNA DIFERENCIA A PROPOSITO CON EL CODIGO VIEJO. La busqueda de
     * js/tienda.js:109 miraba nombre + varietal + linea + BODEGA. La
     * bodega era la constante "Alba en los Andes" que ponia
     * api.js:138 para todos los vinos, porque el back-end no modela
     * bodegas. O sea que buscar "alba", "andes" o "los" devolvia el
     * catalogo completo y parecia que el buscador no funcionaba. Aca se
     * busca en nombre, varietal y linea, que son los tres datos que
     * distinguen un vino de otro.
     *
     * SE BUSCA SIN ACENTOS: "cabernet" encuentra a "Cabernet" y
     * "anada" encontraria "añada". Antes solo se bajaba a minusculas,
     * asi que quien escribia sin acentos no encontraba nada.
     */
    public List<VinoVista> aplicarA(List<VinoVista> vinos) {
        return vinos.stream()
                .filter(this::coincideElTexto)
                .filter(v -> vacio(varietal) || varietal.equals(v.getVarietal()))
                .filter(v -> vacio(linea)    || linea.equals(v.getLinea()))
                .filter(v -> vacio(cosecha)  || cosecha.equals(String.valueOf(v.getCosecha())))
                .filter(this::entraEnElPrecio)
                .sorted(comparador())
                .toList();
    }

    private boolean coincideElTexto(VinoVista v) {
        if (vacio(texto)) {
            return true;
        }
        String donde = sinAcentos(v.getNombre() + " " + v.getVarietal() + " " + v.getLinea());
        return donde.contains(sinAcentos(texto));
    }

    private boolean entraEnElPrecio(VinoVista v) {
        if (vacio(precioMax)) {
            return true;
        }
        try {
            return v.getPrecio().compareTo(new BigDecimal(precioMax.trim())) <= 0;
        } catch (NumberFormatException e) {
            // Un precioMax que no es un numero (alguien editando la URL a
            // mano) se ignora. Rechazar el pedido con un 400 por un
            // parametro decorativo seria dejar la tienda sin catalogo.
            return true;
        }
    }

    private Comparator<VinoVista> comparador() {
        return switch (orden == null ? POR_NOMBRE : orden) {
            case POR_PRECIO_ASC  -> Comparator.comparing(VinoVista::getPrecio);
            case POR_PRECIO_DESC -> Comparator.comparing(VinoVista::getPrecio).reversed();
            // Cosecha de la mas nueva a la mas vieja, como en el codigo viejo.
            case POR_COSECHA     -> Comparator.comparing(VinoVista::getCosecha,
                                        Comparator.nullsLast(Comparator.reverseOrder()));
            default              -> Comparator.comparing(VinoVista::getNombre,
                                        String.CASE_INSENSITIVE_ORDER);
        };
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    /** Minusculas y sin acentos, para comparar como escribe la gente. */
    private static String sinAcentos(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
