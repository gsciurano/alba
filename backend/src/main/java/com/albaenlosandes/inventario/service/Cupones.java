package com.albaenlosandes.inventario.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ==================================================================
 *  LOS CODIGOS PROMOCIONALES
 * ==================================================================
 *
 * DE DONDE SALE ESTA CLASE. Los tres codigos vivian en el navegador,
 * en js/tienda.js:683, en una constante llamada CUPONES. Eso
 * significaba que cualquiera que abriera el archivo con el inspector
 * leia los descuentos vigentes, y -peor- que el descuento aplicado lo
 * calculaba y lo informaba el cliente. El pedido que salia para el
 * servidor no lo mencionaba, asi que tampoco quedaba registrado.
 *
 * Ahora el cupon se valida y se aplica de este lado. El navegador
 * manda un codigo tecleado; lo que ese codigo vale lo decide el
 * servidor.
 *
 * POR QUE SIGUE SIENDO UNA LISTA EN EL CODIGO Y NO UNA TABLA. Porque
 * una tabla de cupones de verdad necesita vigencia desde/hasta, tope
 * de usos, uso por cliente y alcance (que lineas, que vinos), mas el
 * ABM para cargarlos. Eso es una funcionalidad entera y no esta
 * pedida. Lo que si cambia respecto de antes es quien tiene la
 * autoridad: antes el navegador, ahora el servidor. Mover los codigos
 * a una tabla despues es un cambio chico; lo que habia que arreglar
 * era de que lado se decide.
 *
 * Los tres codigos y sus importes son los mismos que tenia tienda.js,
 * sin cambiarles una coma.
 */
@Service
public class Cupones {

    /**
     * Un cupon.
     *
     * @param codigo     como se teclea, siempre en mayusculas
     * @param etiqueta   lo que se le muestra al cliente cuando lo aplica
     * @param porcentaje fraccion del subtotal, o null si es monto fijo
     * @param monto      pesos fijos, o null si es porcentaje
     */
    public record Cupon(String codigo, String etiqueta,
                        BigDecimal porcentaje, BigDecimal monto) {

        /** Cuanto descuenta sobre un subtotal dado. */
        public BigDecimal descuentoSobre(BigDecimal subtotal) {
            if (porcentaje != null) {
                // HALF_UP y escala 0: el descuento se redondea al peso,
                // igual que hacia Math.round() en el navegador. Si se
                // dejara con centavos, el total de la pantalla y el de
                // la base podrian diferir en un centavo y nadie sabria
                // cual de los dos esta bien.
                return subtotal.multiply(porcentaje).setScale(0, java.math.RoundingMode.HALF_UP);
            }
            return monto;
        }
    }

    /**
     * LinkedHashMap y no Map.of(): el orden importa para poder
     * listarlos siempre igual, y Map.of() no garantiza ninguno.
     */
    private static final Map<String, Cupon> CATALOGO = new LinkedHashMap<>();
    static {
        agregar(new Cupon("ALBA10",     "10% en los vinos",    new BigDecimal("0.10"), null));
        agregar(new Cupon("VALLEUCO15", "15% en los vinos",    new BigDecimal("0.15"), null));
        agregar(new Cupon("BIENVENIDA", "$5.000 en los vinos", null, new BigDecimal("5000")));
    }
    private static void agregar(Cupon c) { CATALOGO.put(c.codigo(), c); }

    /**
     * Busca un codigo. Devuelve vacio si no existe.
     *
     * Se normaliza igual que en el navegador: se recortan los espacios
     * y se pasa a mayusculas, asi que " alba10 " entra.
     */
    public Optional<Cupon> buscar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CATALOGO.get(codigo.trim().toUpperCase()));
    }
}
