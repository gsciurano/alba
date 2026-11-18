package com.albaenlosandes.inventario.web;

/**
 * ==================================================================
 *  LA CUENTA A LA QUE SE TRANSFIERE
 * ==================================================================
 *
 * DE DONDE SALE. Es la constante DATOS_BANCARIOS de
 * js/modules/transferencia.js:29, mudada al servidor sin cambiarle un
 * digito.
 *
 * SON LOS DATOS REALES DE LA BODEGA. El CVU se verifico con sus dos
 * digitos verificadores antes de cargarlo: si alguna vez se cambia,
 * hay que volver a verificarlo, porque un digito mal escrito manda la
 * plata a otra cuenta que existe.
 *
 * ES UN CVU, NO UN CBU. El CVU es la cuenta de una billetera virtual
 * (el prefijo 000 y el alias terminado en ".mp" lo delatan), no la de
 * un banco. Para transferir da igual, pero el rotulo dice CVU porque
 * es lo que la persona va a buscar en su homebanking.
 *
 * POR QUE VIVE EN EL CODIGO Y NO EN LA BASE: no es un secreto. Es
 * exactamente el dato que se le entrega a quien tiene que pagarte;
 * esta pensado para ser publico. Guardarlo en una tabla agregaria una
 * entidad, un repositorio y una pantalla de edicion para un dato que
 * cambia una vez cada varios anios.
 *
 * POR QUE AHORA ES UN record Y NO UN OBJETO DE JAVASCRIPT: para que
 * la plantilla lo lea igual que cualquier otro dato del modelo. El
 * cambio no es de seguridad -esto se publica a proposito- sino de
 * lugar: el servidor dibuja la pantalla, asi que los datos de la
 * pantalla tienen que estar donde el servidor los vea.
 *
 * LOS CAMPOS VACIOS NO SE DIBUJAN. Para sumar el CUIT alcanza con
 * completarlo aca. Preferir una fila de menos antes que un dato
 * inventado, que es lo peor que puede pasar en una pantalla de pago.
 */
public record DatosBancarios(boolean ejemplo,
                             String titular,
                             String cvu,
                             String alias,
                             String cuit,
                             String contacto) {

    public static final DatosBancarios LOS_DE_LA_BODEGA = new DatosBancarios(
            false,
            "ALBA EN LOS ANDES SA",
            "0000003100019063106767",
            "tour.saxo.suma.mp",
            "",                              // falta: cuando lo tengamos, aparece solo
            /*
             * La misma casilla que imprime el comprobante por correo
             * (alba.correo.pagos en application.properties): si la
             * pantalla dice una direccion y el mail dice otra, el
             * cliente no sabe a cual mandar el comprobante.
             */
            "albaenlosandes@gmail.com");

    /** Para los th:if de la plantilla: una fila vacia no se dibuja. */
    public boolean isHayCuit() {
        return cuit != null && !cuit.isBlank();
    }
}
