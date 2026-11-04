package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.*;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Manda el comprobante de compra al cliente.
 *
 * ACLARACION IMPORTANTE: esto es un COMPROBANTE, no una factura fiscal.
 * Una factura con CAE requiere los web services de ARCA (ex AFIP), punto
 * de venta electronico y certificados. Este mail sirve para que la
 * persona tenga por escrito que compro, que compro y cuanto pago.
 *
 * TRES DECISIONES QUE VALE LA PENA EXPLICAR:
 *
 * 1. SE MANDA DESPUES DE QUE LA COMPRA ESTA GUARDADA, nunca adentro de
 *    la transaccion. Si se mandara adentro y la transaccion se revirtiera
 *    (por ejemplo, por falta de stock), el cliente recibiria el
 *    comprobante de un pedido que no existe.
 *
 * 2. SI EL CORREO FALLA, LA COMPRA NO FALLA. El pedido ya esta hecho y
 *    las botellas ya se descontaron: tirar abajo una venta porque el
 *    servidor de correo no contesto seria mucho peor que no avisar. Por
 *    eso cada excepcion se atrapa y se registra.
 *
 * 3. VA EN @Async: el checkout no tiene por que esperar al servidor de
 *    correo. Sin esto, cada compra sumaria el ida y vuelta del SMTP al
 *    tiempo que la persona ve la rueda girando.
 */
@Service
public class ServicioCorreo {

    private static final Logger log = LoggerFactory.getLogger(ServicioCorreo.class);

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private final JavaMailSender correo;

    @Value("${alba.correo.remitente}")   private String remitente;
    @Value("${alba.correo.nombre}")      private String nombreRemitente;
    @Value("${alba.correo.responder-a}") private String responderA;
    @Value("${alba.correo.activo}")      private boolean activo;
    @Value("${alba.correo.copia:}")     private String copiaBodega;

    @Value("${alba.banco.alias}")    private String bancoAlias;
    @Value("${alba.banco.cvu}")      private String bancoCvu;
    @Value("${alba.banco.titular}")  private String bancoTitular;

    @Value("${spring.mail.host}") private String host;
    @Value("${spring.mail.port}") private int puerto;
    @Value("${spring.mail.username:}") private String usuarioSmtp;
    @Value("${spring.mail.password:}") private String claveSmtp;
    @Value("${spring.mail.properties.mail.smtp.auth:false}") private boolean smtpAuth;
    @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") private boolean smtpTls;

    public ServicioCorreo(JavaMailSender correo) {
        this.correo = correo;
    }

    /**
     * Al arrancar, deja dicho en el log si los correos van a llegar de
     * verdad o no.
     *
     * POR QUE HACE FALTA: el envio es @Async y se traga sus errores para
     * no tirar abajo una compra. Eso esta bien, pero tiene un costo: si
     * el correo esta mal configurado, las compras siguen saliendo y
     * nadie se entera de que los comprobantes no llegan a nadie. Este
     * aviso aparece UNA vez, al arrancar, donde se mira.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void avisarComoQuedoElCorreo() {
        if (!activo) {
            log.warn("CORREO APAGADO (alba.correo.activo=false): no se envia ningun comprobante.");
            return;
        }
        boolean esLocal = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        log.info("Correo: servidor {}:{} | remitente {} <{}> | copia a {}",
                 host, puerto, nombreRemitente, remitente,
                 (copiaBodega == null || copiaBodega.isBlank()) ? "(nadie)" : copiaBodega);

        if (esLocal) {
            log.warn("El correo apunta a {}:{}, un servidor LOCAL. Sirve para probar, "
                   + "pero los comprobantes NO llegan a ninguna casilla real. "
                   + "Para que lleguen hay que definir MAIL_HOST, MAIL_USER y MAIL_PASSWORD.",
                     host, puerto);
            return;
        }
        if (usuarioSmtp == null || usuarioSmtp.isBlank()) {
            log.warn("MAIL_USER esta vacio con un servidor externo ({}): casi seguro "
                   + "el proveedor va a rechazar el envio por falta de autenticacion.", host);
        }
        if (claveSmtp == null || claveSmtp.isBlank()) {
            log.error("FALTA MAIL_PASSWORD. El servidor {} va a rechazar la conexion y "
                    + "NINGUN comprobante va a llegar. Exportar la clave SMTP antes de "
                    + "arrancar: export MAIL_PASSWORD='...'", host);
        }
        /*
         * Estos dos valen false por defecto porque el servidor de prueba
         * local no los usa. Contra un proveedor externo son obligatorios,
         * y el sintoma de que falten es feo: la conexion se corta sin
         * decir por que, o el proveedor contesta "authentication required"
         * aunque MAIL_USER y MAIL_PASSWORD esten bien puestos.
         */
        if (!smtpAuth || !smtpTls) {
            log.error("Con un servidor externo ({}) hacen falta MAIL_AUTH=true y "
                    + "MAIL_TLS=true. Ahora estan auth={} tls={}: el envio va a fallar "
                    + "aunque el usuario y la clave sean correctos.", host, smtpAuth, smtpTls);
        }
        /*
         * El aviso que mas cuesta descubrir solo: mandar como un dominio
         * que no es nuestro. SPF y DKIM fallan, DMARC lo marca como
         * falsificado y el mensaje termina en spam o rechazado. El
         * remitente tiene que ser una direccion verificada en el
         * proveedor.
         */
        String dominio = remitente.contains("@")
                ? remitente.substring(remitente.indexOf('@') + 1) : "";
        log.info("Si los comprobantes caen en spam, revisar que '{}' sea un remitente "
               + "verificado en el proveedor y que el dominio '{}' tenga SPF y DKIM.",
                 remitente, dominio);
    }

    /**
     * Envia el comprobante del pedido recien confirmado.
     *
     * No devuelve nada ni lanza nada hacia afuera a proposito: quien la
     * llama ya cerro la venta y no puede hacer nada con el error.
     */
    @Async
    public void enviarComprobante(Pedido pedido) {
        if (!activo) {
            log.info("Correo desactivado (alba.correo.activo=false): no se envia el pedido #{}",
                     pedido.getIdPedido());
            return;
        }
        String destino = pedido.getUsuario() != null ? pedido.getUsuario().getEmail() : null;
        if (destino == null || destino.isBlank()) {
            log.warn("El pedido #{} no tiene email: no hay a donde mandar el comprobante",
                     pedido.getIdPedido());
            return;
        }
        try {
            MimeMessage mensaje = correo.createMimeMessage();
            // true = multiparte; UTF-8 para que los acentos y la enie lleguen bien
            MimeMessageHelper ayuda = new MimeMessageHelper(mensaje, true, "UTF-8");
            // Direccion + nombre visible: en la bandeja de entrada se lee
            // "Alba en los Andes" y no la parte de antes del arroba.
            ayuda.setFrom(remitente, nombreRemitente);
            ayuda.setReplyTo(responderA, nombreRemitente);
            ayuda.setTo(destino);
            /*
             * COPIA A LA BODEGA.
             *
             * Va como COPIA OCULTA (Bcc) y no como Cc por dos razones:
             * el cliente no tiene por que ver la casilla interna, y con
             * Cc cualquier "responder a todos" manda la respuesta del
             * cliente a los dos lados.
             *
             * Si alba.correo.copia esta vacio no se agrega nada, asi que
             * quien no quiera la copia no configura nada.
             */
            if (copiaBodega != null && !copiaBodega.isBlank()) {
                ayuda.setBcc(copiaBodega.trim());
            }
            ayuda.setSubject("Tu compra en Alba en los Andes — pedido #" + pedido.getIdPedido());
            // Dos versiones del mismo mensaje: el cliente de correo elige.
            // La de texto plano no es un descarte, es para quien tiene el
            // HTML desactivado y para que no lo tomen por spam.
            ayuda.setText(cuerpoTexto(pedido), cuerpoHtml(pedido));
            correo.send(mensaje);
            log.info("Comprobante del pedido #{} enviado a {}{}", pedido.getIdPedido(), destino,
                     (copiaBodega != null && !copiaBodega.isBlank())
                             ? " (con copia a " + copiaBodega.trim() + ")" : "");
        } catch (Exception e) {
            // Se registra y se sigue: la venta ya esta hecha.
            log.error("No se pudo enviar el comprobante del pedido #{} a {}: {}",
                      pedido.getIdPedido(), destino, e.getMessage());
        }
    }

    // ==================================================================
    //  ARMADO DEL MENSAJE
    // ==================================================================

    private boolean esperaTransferencia(Pedido p) {
        return p.getMedioPago() == MedioPago.TRANSFERENCIA
            && !Boolean.TRUE.equals(p.getPagoConfirmado());
    }

    /**
     * El HTML se escribe a mano, sin motor de plantillas.
     *
     * POR QUE CON ESTILOS ADENTRO DE CADA ETIQUETA Y CON UNA TABLA:
     * los clientes de correo (Gmail, Outlook) descartan buena parte del
     * CSS y muchos ignoran las hojas de estilo. Lo que se banca en todos
     * es una tabla con los estilos puestos en el atributo style. Es feo
     * de escribir y es lo unico que se ve igual en todos lados.
     */
    private String cuerpoHtml(Pedido p) {
        StringBuilder renglones = new StringBuilder();
        for (DetallePedido d : p.getDetalles()) {
            BigDecimal subtotal = d.getPrecioUnitario()
                    .multiply(BigDecimal.valueOf(d.getCantidad()));
            renglones.append("""
                <tr>
                  <td style="padding:10px 0;border-bottom:1px solid #e6e9eb">%s</td>
                  <td style="padding:10px 0;border-bottom:1px solid #e6e9eb;text-align:center">%d</td>
                  <td style="padding:10px 0;border-bottom:1px solid #e6e9eb;text-align:right">%s</td>
                  <td style="padding:10px 0;border-bottom:1px solid #e6e9eb;text-align:right"><strong>%s</strong></td>
                </tr>
                """.formatted(
                    nombreDe(d), d.getCantidad(),
                    pesos(d.getPrecioUnitario()), pesos(subtotal)));
        }

        String avisoPago = esperaTransferencia(p) ? """
            <div style="background:#fbf0db;border-radius:8px;padding:16px 18px;margin:24px 0">
              <p style="margin:0 0 10px;font-weight:700;color:#8a5e0a">Falta que hagas la transferencia</p>
              <p style="margin:0 0 12px;color:#6b4d0c;line-height:1.6">
                Tu pedido queda reservado. Preparamos el envío cuando veamos el dinero acreditado.
              </p>
              <table role="presentation" cellpadding="0" cellspacing="0" style="font-size:14px;color:#6b4d0c">
                <tr><td style="padding:3px 14px 3px 0">Alias</td><td style="font-family:monospace"><strong>%s</strong></td></tr>
                <tr><td style="padding:3px 14px 3px 0">CVU</td><td style="font-family:monospace"><strong>%s</strong></td></tr>
                <tr><td style="padding:3px 14px 3px 0">Titular</td><td><strong>%s</strong></td></tr>
              </table>
              <p style="margin:12px 0 0;color:#6b4d0c;line-height:1.6">
                Después enviános el comprobante a <a href="mailto:%s" style="color:#8a5e0a">%s</a>
                indicando el pedido <strong>#%d</strong>.
              </p>
            </div>
            """.formatted(bancoAlias, bancoCvu, bancoTitular,
                          responderA, responderA, p.getIdPedido())
            : """
            <p style="margin:24px 0;padding:14px 18px;background:#e1f1e8;border-radius:8px;color:#1e7a54">
              <strong>Pago registrado.</strong> Ya estamos preparando tu pedido.
            </p>
            """;

        return """
            <!doctype html>
            <html lang="es"><body style="margin:0;padding:0;background:#f4f6f7">
              <table role="presentation" cellpadding="0" cellspacing="0" width="100%%"
                     style="background:#f4f6f7;padding:28px 12px">
                <tr><td align="center">
                  <table role="presentation" cellpadding="0" cellspacing="0" width="600"
                         style="max-width:600px;background:#ffffff;border-radius:12px;overflow:hidden;
                                font-family:Helvetica,Arial,sans-serif;color:#1f2d33">

                    <tr><td style="background:#0b2c38;padding:28px 32px;text-align:center">
                      <p style="margin:0;color:#ffffff;font-size:19px;letter-spacing:2.6px;
                                text-transform:uppercase">Alba en los Andes</p>
                      <p style="margin:6px 0 0;color:#8fadb7;font-size:12px;letter-spacing:1.6px;
                                text-transform:uppercase">Valle de Uco &middot; Mendoza</p>
                    </td></tr>

                    <tr><td style="padding:32px">
                      <h1 style="margin:0 0 6px;font-size:23px">Gracias por tu compra</h1>
                      <p style="margin:0 0 4px;color:#5a6670;font-size:15px">
                        Pedido <strong style="color:#1f2d33">#%d</strong> &middot; %s
                      </p>
                      <p style="margin:0;color:#5a6670;font-size:15px">Medio de pago: %s</p>

                      %s

                      <table role="presentation" cellpadding="0" cellspacing="0" width="100%%"
                             style="font-size:15px;border-collapse:collapse">
                        <tr style="color:#5a6670;font-size:12.5px;text-transform:uppercase;letter-spacing:1px">
                          <td style="padding-bottom:8px;border-bottom:2px solid #0b2c38">Vino</td>
                          <td style="padding-bottom:8px;border-bottom:2px solid #0b2c38;text-align:center">Cant.</td>
                          <td style="padding-bottom:8px;border-bottom:2px solid #0b2c38;text-align:right">Precio</td>
                          <td style="padding-bottom:8px;border-bottom:2px solid #0b2c38;text-align:right">Subtotal</td>
                        </tr>
                        %s
                        <tr>
                          <td colspan="3" style="padding:16px 0 0;text-align:right;font-size:16px">Total</td>
                          <td style="padding:16px 0 0;text-align:right;font-size:20px"><strong>%s</strong></td>
                        </tr>
                      </table>

                      <p style="margin:28px 0 0;color:#5a6670;font-size:14px;line-height:1.7">
                        Guardá este correo: el número de pedido es con lo que podemos encontrar tu
                        compra. Si algo no coincide, respondé a este mensaje.
                      </p>
                    </td></tr>

                    <tr><td style="background:#0b2c38;padding:20px 32px;text-align:center">
                      <p style="margin:0;color:#8fadb7;font-size:12px;line-height:1.7">
                        Beber con moderación. Prohibida su venta a menores de 18 años &mdash; Ley 24.788<br>
                        Este es un comprobante de compra, no una factura fiscal.
                      </p>
                    </td></tr>

                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(
                p.getIdPedido(),
                p.getFecha() != null ? p.getFecha().format(FORMATO_FECHA) : "",
                capitalizar(p.getMedioPago()),
                avisoPago,
                renglones.toString(),
                pesos(p.getTotal()));
    }

    /** La misma informacion en texto, para quien no ve HTML. */
    private String cuerpoTexto(Pedido p) {
        StringBuilder t = new StringBuilder();
        t.append("ALBA EN LOS ANDES\n\n");
        t.append("Gracias por tu compra.\n\n");
        t.append("Pedido #").append(p.getIdPedido()).append("\n");
        if (p.getFecha() != null) t.append("Fecha: ").append(p.getFecha().format(FORMATO_FECHA)).append("\n");
        t.append("Medio de pago: ").append(capitalizar(p.getMedioPago())).append("\n\n");

        if (esperaTransferencia(p)) {
            t.append("FALTA QUE HAGAS LA TRANSFERENCIA\n");
            t.append("  Alias:   ").append(bancoAlias).append("\n");
            t.append("  CVU:     ").append(bancoCvu).append("\n");
            t.append("  Titular: ").append(bancoTitular).append("\n");
            t.append("Después enviá el comprobante a ").append(responderA)
             .append(" indicando el pedido #").append(p.getIdPedido()).append(".\n");
            t.append("Tu pedido queda reservado hasta que veamos el dinero acreditado.\n\n");
        } else {
            t.append("Pago registrado. Ya estamos preparando tu pedido.\n\n");
        }

        t.append("DETALLE\n");
        for (DetallePedido d : p.getDetalles()) {
            BigDecimal subtotal = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
            t.append("  ").append(d.getCantidad()).append(" x ").append(nombreDe(d))
             .append("  ").append(pesos(d.getPrecioUnitario()))
             .append("  =  ").append(pesos(subtotal)).append("\n");
        }
        t.append("\nTOTAL: ").append(pesos(p.getTotal())).append("\n\n");
        t.append("Beber con moderación. Prohibida su venta a menores de 18 años — Ley 24.788\n");
        t.append("Este es un comprobante de compra, no una factura fiscal.\n");
        return t.toString();
    }

    // ----- ayudantes -----

    /**
     * Un vino dado de baja despues de la compra deja el producto en null
     * desde el punto de vista del listado; el comprobante no puede quedar
     * con un hueco, asi que se dice que paso.
     */
    private String nombreDe(DetallePedido d) {
        return d.getProducto() != null && d.getProducto().getNombre() != null
                ? d.getProducto().getNombre()
                : "(vino dado de baja del catálogo)";
    }

    /** "$ 100.250", con punto de miles, como se muestra en la tienda. */
    private String pesos(BigDecimal monto) {
        if (monto == null) return "$ 0";
        return "$ " + String.format("%,d", monto.longValue()).replace(',', '.');
    }

    private String capitalizar(MedioPago medio) {
        if (medio == null) return "";
        String n = medio.name().toLowerCase();
        return n.substring(0, 1).toUpperCase() + n.substring(1);
    }
}
