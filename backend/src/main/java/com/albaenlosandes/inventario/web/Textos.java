package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.model.EstadoPedido;
import com.albaenlosandes.inventario.model.EstadoSolicitud;
import com.albaenlosandes.inventario.model.MedioPago;
import com.albaenlosandes.inventario.model.MotivoCancelacion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * ==================================================================
 *  COMO SE ESCRIBE CADA VALOR DE LA BASE
 * ==================================================================
 *
 * DE DONDE SALE ESTA CLASE. En la base, los estados y los motivos son
 * enums en MAYUSCULAS_CON_GUION: PRODUCTO_EQUIVOCADO, EN_REVISION,
 * TRANSFERENCIA. Eso esta bien para una columna y mal para una
 * pantalla. La traduccion existia, repartida en tres archivos del
 * navegador:
 *
 *     cuenta.js:37   MOTIVOS
 *     cuenta.js:45   ESTADOS_SOLICITUD
 *     cuenta.js:914  capitalizar()
 *     api.js:102     LINEA_A_TEXTO   (ya migrado, vive en VinoVista)
 *
 * POR QUE JUNTARLAS EN UN SOLO LUGAR. Porque estaban repetidas: el
 * panel de administracion tiene su propia copia de los mismos textos
 * en admin.js. Dos tablas separadas para los mismos seis motivos es
 * la forma mas simple de que, el dia que se agregue un motivo, una
 * pantalla lo muestre bien y la otra muestre "PRODUCTO_EQUIVOCADO".
 *
 * POR QUE NO SON ARCHIVOS .properties DE MENSAJES. Thymeleaf tiene
 * #{clave} y un messages.properties por idioma, que es lo correcto
 * cuando hay mas de un idioma. El sitio es solo en castellano, y un
 * archivo de claves sueltas alejaria el texto del enum al que
 * pertenece. Si algun dia hay ingles, esto se convierte en eso.
 *
 * SI FALTA UNA CLAVE se devuelve el valor crudo del enum en vez de
 * null: una pantalla que dice PRODUCTO_EQUIVOCADO es fea, una que
 * dice "null" o se cae es peor.
 */
public final class Textos {

    private Textos() { }

    /** cuenta.js:37 (MOTIVOS), tal cual. */
    private static final Map<MotivoCancelacion, String> MOTIVOS = Map.of(
            MotivoCancelacion.ARREPENTIMIENTO,     "Me arrepentí de la compra",
            MotivoCancelacion.ERROR_AL_COMPRAR,    "Me equivoqué de vino o de cantidad",
            MotivoCancelacion.PROBLEMA_PAGO,       "Tuve un problema con el pago",
            MotivoCancelacion.DEMORA_ENTREGA,      "La entrega se está demorando",
            MotivoCancelacion.PRODUCTO_EQUIVOCADO, "Me llegó algo distinto a lo que pedí",
            MotivoCancelacion.OTRO,                "Otro motivo");

    /** cuenta.js:45 (ESTADOS_SOLICITUD), tal cual. */
    private static final Map<EstadoSolicitud, String> ESTADOS_SOLICITUD = Map.of(
            EstadoSolicitud.PENDIENTE,   "Pendiente",
            EstadoSolicitud.EN_REVISION, "En revisión",
            EstadoSolicitud.APROBADA,    "Aprobada",
            EstadoSolicitud.RECHAZADA,   "Rechazada");

    public static String de(MotivoCancelacion motivo) {
        return motivo == null ? "" : MOTIVOS.getOrDefault(motivo, motivo.name());
    }

    public static String de(EstadoSolicitud estado) {
        return estado == null ? "" : ESTADOS_SOLICITUD.getOrDefault(estado, estado.name());
    }

    /**
     * Los estados del pedido y los medios de pago se muestran
     * capitalizados, que es lo que hacia capitalizar() en
     * cuenta.js:914: CONFIRMADO -> "Confirmado".
     *
     * No llevan tabla propia porque la palabra ya es la que se
     * muestra; lo unico que cambia son las mayusculas.
     */
    public static String de(EstadoPedido estado) {
        return capitalizar(estado == null ? null : estado.name());
    }

    /**
     * El medio de pago.
     *
     * MERCADO_PAGO ES LA EXCEPCION y por eso la tabla de una sola fila:
     * capitalizar() lo dejaria en "Mercado_pago", y un guion bajo en
     * pantalla delata que se esta mostrando el valor crudo de la base en
     * lugar del nombre de algo. Los otros tres son una sola palabra y no
     * necesitan traduccion.
     */
    public static String de(MedioPago medio) {
        if (medio == MedioPago.MERCADO_PAGO) {
            return "Mercado Pago";
        }
        return capitalizar(medio == null ? null : medio.name());
    }

    private static String capitalizar(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * ==============================================================
     *  LAS FECHAS
     * ==============================================================
     *
     * Dos formatos, los mismos que tenia util.js:
     *
     *     larga   12 de noviembre de 2026     (fecha)
     *     corta   12/11/2026                  (fechaCorta)
     *
     * POR QUE NO SE USA #temporals DE THYMELEAF: existe y formatea
     * bien, pero habria que repetir el patron en cada plantilla que
     * muestre una fecha, y basta con que uno quede distinto para que
     * la misma fecha se escriba de dos maneras en dos pantallas. Con
     * esto, el formato se decide una vez.
     *
     * LOCALE EXPLICITO ("es", "AR"): sin el, el nombre del mes sale en
     * el idioma del sistema operativo del servidor. En la maquina de
     * desarrollo seria castellano y en un servidor de verdad podria
     * ser ingles, y nadie se daria cuenta hasta verlo publicado.
     */
    private static final Locale ARGENTINA = Locale.of("es", "AR");

    private static final DateTimeFormatter LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ARGENTINA);

    private static final DateTimeFormatter CORTA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", ARGENTINA);

    public static String fecha(LocalDateTime cuando) {
        return cuando == null ? "" : cuando.format(LARGA);
    }

    public static String fechaCorta(LocalDateTime cuando) {
        return cuando == null ? "" : cuando.format(CORTA);
    }
}
