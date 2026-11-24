package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.DetallePedido;
import com.albaenlosandes.inventario.model.Pedido;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ==================================================================
 *  EL COBRO POR MERCADO PAGO
 * ==================================================================
 *
 * Es el unico medio de pago de la tienda que cobra de verdad. La
 * tarjeta del checkout es simulada y la transferencia la acredita un
 * administrador a mano; esto habla con un servicio de afuera.
 *
 * COMO FUNCIONA, EN TRES PASOS:
 *
 *   1) Se le manda a Mercado Pago una PREFERENCIA: que se compra, a que
 *      precio y cuanto es el total. Mercado Pago la guarda y devuelve
 *      una direccion ("init_point").
 *   2) Se manda a la persona a esa direccion. Ahi elige con que pagar y
 *      pone sus datos. Ni la tarjeta ni el DNI pasan por este servidor:
 *      por eso este camino y no un formulario propio.
 *   3) Se le pregunta a Mercado Pago si el pago de ese pedido salio
 *      aprobado, y recien entonces el pedido se da por pagado.
 *
 * EL PRECIO LO FIJA EL SERVIDOR, igual que en el resto de la tienda: a
 * la preferencia va el total que calculo CalculadoraDeCuentas y que
 * quedo guardado en el pedido. El navegador no lo toca en ningun punto
 * del camino.
 *
 * SIN CREDENCIAL, NO EXISTE. Si no esta configurado el access token,
 * isDisponible() devuelve false, la ficha no aparece en el checkout y
 * el controlador rechaza el medio. La alternativa -ofrecerlo y fallar
 * al apretar "Pagar"- deja a la persona con un pedido a medias.
 *
 * LA CREDENCIAL NO ESTA EN EL REPOSITORIO: viaja por la variable de
 * entorno MP_ACCESS_TOKEN, que arrancar.sh saca del llavero de macOS.
 * Es la misma decision que con las claves del correo.
 *
 * MIENTRAS ESTO SEA UN TRABAJO DE FACULTAD van las credenciales de
 * PRUEBA, que empiezan con TEST-. Con ellas el circuito es el real pero
 * no se mueve plata, y se paga con los usuarios y las tarjetas de
 * prueba que publica Mercado Pago.
 */
@Service
public class ServicioMercadoPago {

    private static final Logger log = LoggerFactory.getLogger(ServicioMercadoPago.class);

    /** Pesos argentinos. La tienda cobra solo en pesos. */
    private static final String MONEDA = "ARS";

    /**
     * Lo que ve la persona en el resumen de su tarjeta. Mercado Pago lo
     * corta a 13 caracteres, asi que ya va corto: "ALBAENLOSAND" no se
     * entiende y "ALBA ANDES" si.
     */
    private static final String EN_EL_RESUMEN = "ALBA ANDES";

    private final String accessToken;
    private final String urlPublica;
    private final boolean produccion;

    public ServicioMercadoPago(
            @Value("${alba.mercadopago.access-token:}") String accessToken,
            @Value("${alba.mercadopago.url-publica:}") String urlPublica,
            @Value("${alba.mercadopago.produccion:false}") boolean produccion) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.urlPublica = urlPublica == null ? "" : urlPublica.trim().replaceAll("/+$", "");
        this.produccion = produccion;

        if (isDisponible()) {
            /*
             * El access token es GLOBAL en el SDK (un campo estatico), no
             * un parametro de cada llamada. Se pone una sola vez, al
             * arrancar, y no se vuelve a tocar: cambiarlo en caliente
             * afectaria a cualquier cobro que este en curso.
             */
            MercadoPagoConfig.setAccessToken(this.accessToken);
            log.info("Mercado Pago configurado ({}). Vuelta al sitio: {}",
                    esDePrueba() ? "modo PRUEBA, no se cobra plata de verdad"
                                 : "modo PRODUCCION, las compras COBRAN PLATA DE VERDAD",
                    urlPublicaConfigurada() ? this.urlPublica : "sin URL publica, se verifica a mano");
        } else {
            log.info("Mercado Pago apagado: falta MP_ACCESS_TOKEN. "
                    + "La tienda no va a ofrecer ese medio de pago.");
        }
    }

    /** Si no hay credencial, la tienda no ofrece este medio de pago. */
    public boolean isDisponible() {
        return !accessToken.isEmpty();
    }

    /**
     * Si se esta cobrando de mentira. Se usa para avisarlo en el
     * checkout: que una compra no mueva plata de verdad es justo lo que
     * no se puede dejar a la imaginacion de quien mira.
     *
     * POR QUE ES UNA DECISION CONFIGURADA Y NO SE DEDUCE DEL TOKEN.
     * Antes esto miraba si el token empezaba con "TEST-", que era como
     * se distinguian las credenciales de prueba. Mercado Pago cambio
     * eso: hoy las credenciales de prueba de una aplicacion tambien
     * empiezan con "APP_USR-" y lo unico que las separa es de que
     * solapa del panel se copiaron, Prueba o Produccion. Mirando el
     * texto del token, el aviso de "modo de prueba" quedaba al revez:
     * no aparecia justo cuando era verdad.
     *
     * Asi que lo decide quien configura el servidor, con
     * MP_MODO=produccion, y por defecto se asume prueba: equivocarse
     * para este lado muestra un aviso de mas, y para el otro lado deja
     * a alguien creyendo que compro algo que nadie le va a mandar.
     */
    public boolean esDePrueba() {
        return !produccion;
    }

    /**
     * Si hay una direccion publica para volver al sitio.
     *
     * POR QUE PUEDE NO HABERLA: Mercado Pago no acepta localhost en las
     * direcciones de vuelta, y mientras el sitio corra solo en esta
     * maquina no hay ninguna direccion que darle. Sin esto, el circuito
     * igual funciona: la persona paga y despues vuelve sola, y el pedido
     * se verifica con el boton "Ya pagué" de la pantalla de
     * confirmacion, que es lo que llama a buscarPagoAprobado().
     */
    public boolean urlPublicaConfigurada() {
        return !urlPublica.isEmpty();
    }

    /** Lo que hace falta para mandar a alguien a pagar. */
    public record Cobro(String idPreferencia, String aDondePagar) { }

    /** Un pago que Mercado Pago dio por aprobado. */
    public record PagoAprobado(String id, BigDecimal monto, String medio) { }

    /**
     * Crea la preferencia de un pedido ya guardado y devuelve a donde hay
     * que mandar a la persona.
     *
     * POR QUE EL PEDIDO SE GUARDA ANTES. La preferencia necesita el total
     * definitivo y una referencia para reconocer el pago despues, y las
     * dos cosas son el pedido. Ademas es el mismo orden que ya usa la
     * transferencia: el pedido existe y queda esperando la plata.
     *
     * EL external_reference ES EL NUMERO DE PEDIDO. Es lo que permite,
     * mas tarde, preguntar "¿hay un pago aprobado del pedido 112?" sin
     * guardar nada en la sesion ni confiar en lo que vuelva por la URL.
     */
    public Cobro crearCobro(Pedido pedido) throws MPException, MPApiException {
        if (!isDisponible()) {
            throw new IllegalStateException("Mercado Pago no esta configurado en este servidor.");
        }

        List<PreferenceItemRequest> items = new ArrayList<>();
        for (DetallePedido renglon : pedido.getDetalles()) {
            items.add(PreferenceItemRequest.builder()
                    .id(String.valueOf(renglon.getProducto().getIdProducto()))
                    .title(renglon.getProducto().getNombre())
                    .quantity(renglon.getCantidad())
                    .unitPrice(precioUnitario(renglon))
                    .currencyId(MONEDA)
                    .build());
        }

        /*
         * EL ENVIO Y LOS IMPUESTOS VAN COMO UN RENGLON MAS, y no en los
         * campos de shipments: lo que no puede pasar es que la suma de
         * los renglones no de el total del pedido. Si Mercado Pago cobra
         * un peso distinto al que dice el comprobante, el que queda mal
         * es el sitio.
         */
        BigDecimal sumaDeVinos = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal resto = pedido.getTotal().subtract(sumaDeVinos);
        if (resto.compareTo(BigDecimal.ZERO) != 0) {
            items.add(PreferenceItemRequest.builder()
                    .id("cargos")
                    .title(resto.signum() > 0 ? "Envío e impuestos" : "Descuento")
                    .quantity(1)
                    .unitPrice(resto)
                    .currencyId(MONEDA)
                    .build());
        }

        PreferenceRequest.PreferenceRequestBuilder preferencia = PreferenceRequest.builder()
                .items(items)
                .externalReference(String.valueOf(pedido.getIdPedido()))
                .statementDescriptor(EN_EL_RESUMEN)
                .payer(PreferencePayerRequest.builder()
                        .name(pedido.getUsuario().getNombre())
                        .surname(pedido.getUsuario().getApellido())
                        .email(pedido.getUsuario().getEmail())
                        .build());

        /*
         * Las direcciones de vuelta SOLO si hay una direccion publica.
         * Mandar localhost hace que Mercado Pago rechace la preferencia
         * entera, asi que es mejor no mandar ninguna: la persona paga y
         * vuelve sola al sitio.
         *
         * auto_return="approved" hace que vuelva sin apretar nada cuando
         * el pago sale bien. Sin back_urls no se puede pedir.
         */
        if (urlPublicaConfigurada()) {
            String vuelta = urlPublica + "/tienda/mercadopago/volver";
            preferencia.backUrls(PreferenceBackUrlsRequest.builder()
                            .success(vuelta)
                            .pending(vuelta)
                            .failure(vuelta)
                            .build())
                    .autoReturn("approved");
        }

        Preference creada = new PreferenceClient().create(preferencia.build());

        /*
         * init_point es la direccion de pago. En las versiones viejas del
         * SDK habia que usar sandbox_init_point con credenciales de
         * prueba; ahora init_point sirve para las dos, pero si alguna vez
         * viniera vacio el otro es un reemplazo valido y no hay por que
         * fallar por eso.
         */
        String aDondePagar = creada.getInitPoint() != null && !creada.getInitPoint().isBlank()
                ? creada.getInitPoint()
                : creada.getSandboxInitPoint();

        log.info("Mercado Pago: preferencia {} para el pedido #{} por $ {}",
                creada.getId(), pedido.getIdPedido(), pedido.getTotal());
        return new Cobro(creada.getId(), aDondePagar);
    }

    /**
     * Pregunta a Mercado Pago si hay un pago APROBADO de este pedido.
     *
     * POR QUE SE PREGUNTA Y NO SE CREE LO QUE VUELVE POR LA URL. Al
     * volver de pagar, Mercado Pago agrega a la direccion un
     * status=approved y un payment_id. Eso es texto en una barra de
     * direcciones: cualquiera puede escribirlo a mano y darse por
     * pagado. Lo unico que vale es lo que contesta Mercado Pago cuando se
     * le pregunta con la credencial del vendedor.
     *
     * TAMBIEN SE VERIFICA EL MONTO: un pago aprobado por menos plata que
     * el total no alcanza. Es raro, pero la consecuencia de no mirarlo es
     * entregar vino cobrando de menos.
     */
    public Optional<PagoAprobado> buscarPagoAprobado(Pedido pedido) throws MPException, MPApiException {
        if (!isDisponible()) {
            return Optional.empty();
        }

        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("external_reference", String.valueOf(pedido.getIdPedido()));

        List<Payment> pagos = new PaymentClient()
                .search(MPSearchRequest.builder().limit(20).offset(0).filters(filtros).build())
                .getResults();

        if (pagos == null) {
            return Optional.empty();
        }

        for (Payment pago : pagos) {
            if (!"approved".equals(pago.getStatus())) {
                continue;
            }
            BigDecimal cobrado = pago.getTransactionAmount() == null
                    ? BigDecimal.ZERO : pago.getTransactionAmount();
            if (cobrado.compareTo(pedido.getTotal()) < 0) {
                log.warn("Mercado Pago: el pago {} del pedido #{} es por $ {} y el pedido es de $ {}. No se da por pagado.",
                        pago.getId(), pedido.getIdPedido(), cobrado, pedido.getTotal());
                continue;
            }
            return Optional.of(new PagoAprobado(
                    String.valueOf(pago.getId()), cobrado, pago.getPaymentMethodId()));
        }
        return Optional.empty();
    }

    /**
     * El precio de una botella.
     *
     * SALE DEL RENGLON GUARDADO y no de la ficha del vino: si el vino
     * cambia de precio entre la compra y el pago, se cobra el precio al
     * que se vendio, que es el que dice el comprobante.
     *
     * El de la ficha queda solo como ultimo recurso, para un pedido
     * viejo guardado sin precio unitario. Igual no cambia el total que se
     * cobra: lo que no cierre con el total del pedido se corrige en el
     * renglon de cargos.
     */
    private BigDecimal precioUnitario(DetallePedido renglon) {
        if (renglon.getPrecioUnitario() != null) {
            return renglon.getPrecioUnitario();
        }
        BigDecimal deLaFicha = renglon.getProducto().getPrecio();
        return deLaFicha != null ? deLaFicha : BigDecimal.ZERO;
    }
}
