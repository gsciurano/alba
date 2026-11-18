package com.albaenlosandes.inventario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Manda los correos por la API HTTP de Brevo en vez de por SMTP.
 *
 * POR QUE EXISTE: muchas redes (universidades, oficinas, algunos
 * proveedores de internet hogareno y casi todos los routers con filtro
 * de seguridad) bloquean los puertos de SMTP para que una maquina
 * infectada no pueda mandar spam. El bloqueo no corta la conexion: la
 * deja abierta y nunca contesta, asi que el sintoma es un
 * "SocketTimeoutException: Read timed out" despues de cinco segundos,
 * con la configuracion perfectamente bien puesta.
 *
 * Comprobado en esta maquina el 2026-11-04: los cuatro puertos de
 * Brevo (25, 465, 587 y 2525) se quedan sin respuesta, y el 443
 * contesta al instante. La API usa 443, el mismo puerto que cualquier
 * pagina web, que ninguna red bloquea porque bloquearlo seria dejar sin
 * internet a todo el mundo.
 *
 * NO REEMPLAZA AL SMTP, CONVIVE CON EL. Si BREVO_API_KEY no esta
 * definida, esta clase se declara inactiva y ServicioCorreo sigue
 * mandando por SMTP como siempre. Asi el proyecto anda igual en una red
 * que no filtra nada y con el servidor de prueba local, sin tener que
 * dar de alta una cuenta para desarrollar.
 *
 * OJO, SON DOS CLAVES DISTINTAS. La de SMTP (MAIL_PASSWORD) no sirve
 * aca. La de la API se saca del mismo lugar, Settings -> SMTP & API,
 * pero de la pestania "API keys", y empieza con "xkeysib-".
 */
@Component
public class TransporteApiBrevo {

    private static final Logger log = LoggerFactory.getLogger(TransporteApiBrevo.class);

    private static final URI ENVIO = URI.create("https://api.brevo.com/v3/smtp/email");

    @Value("${alba.correo.api-key:}") private String apiKey;

    private final ObjectMapper json = new ObjectMapper();

    /*
     * El timeout es mas largo que los cinco segundos del SMTP porque
     * aca no hay ida y vuelta de saludo, autenticacion y datos: es un
     * solo pedido. Diez segundos alcanzan de sobra y no dejan un hilo
     * colgado si Brevo no contesta.
     */
    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** true cuando hay clave de API cargada y conviene usar este camino. */
    public boolean activo() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Manda un mensaje y lanza si Brevo no lo acepta.
     *
     * Lanza a proposito en vez de devolver un booleano: quien llama ya
     * decide que hacer con el error segun el tipo de mensaje (el
     * comprobante lo registra y sigue, el codigo de recupero tira el
     * intento abajo), y una excepcion lleva el motivo que contesto
     * Brevo, que es justo lo que hace falta leer en el log.
     */
    public void enviar(String remitente, String nombreRemitente, String responderA,
                       String destino, String copiaOculta,
                       String asunto, String texto, String html) throws Exception {

        ObjectNode cuerpo = json.createObjectNode();

        ObjectNode de = cuerpo.putObject("sender");
        de.put("email", remitente);
        de.put("name", nombreRemitente);

        ArrayNode para = cuerpo.putArray("to");
        para.addObject().put("email", destino);

        if (copiaOculta != null && !copiaOculta.isBlank()) {
            cuerpo.putArray("bcc").addObject().put("email", copiaOculta.trim());
        }
        if (responderA != null && !responderA.isBlank()) {
            cuerpo.putObject("replyTo").put("email", responderA);
        }

        cuerpo.put("subject", asunto);
        cuerpo.put("htmlContent", html);
        // Las dos versiones viajan en el mismo pedido, igual que en el
        // multiparte del SMTP: el cliente de correo elige cual muestra.
        cuerpo.put("textContent", texto);

        HttpRequest pedido = HttpRequest.newBuilder(ENVIO)
                .timeout(Duration.ofSeconds(15))
                .header("api-key", apiKey)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(cuerpo), java.nio.charset.StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> respuesta =
                cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

        /*
         * Brevo contesta 201 cuando encolo el mensaje. Cualquier otra
         * cosa es un rechazo, y el cuerpo dice por que en texto claro
         * ("sender not valid", "api key not found", el limite diario
         * agotado). Ese cuerpo va dentro de la excepcion: sin el, el
         * log diria nada mas "fallo" y habria que adivinar.
         */
        if (respuesta.statusCode() != 201) {
            throw new IllegalStateException(
                    "Brevo rechazo el envio (HTTP " + respuesta.statusCode() + "): "
                    + respuesta.body());
        }
        log.debug("Brevo acepto el mensaje para {}", destino);
    }
}
