package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.*;
import com.albaenlosandes.inventario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Ingreso con Google.
 *
 * COMO FUNCIONA, EN CRIOLLO:
 * el boton de Google del navegador no nos manda un email, nos manda un
 * "ID token": un papel firmado por Google que dice quien es la persona.
 * Nuestro trabajo es comprobar que ese papel lo firmo Google de verdad y
 * que fue emitido PARA NOSOTROS. Recien ahi se le cree.
 *
 * POR QUE HAY QUE VERIFICARLO SI YA VIENE DE GOOGLE:
 * el token llega por el navegador, y cualquiera puede llamar a nuestra
 * API directamente con un token inventado, o con uno legitimo pero
 * emitido para OTRA aplicacion. Sin verificar, alcanzaria con mandar
 * {"email":"admin@albaenlosandes.com"} para entrar como administrador.
 *
 * COMO SE VERIFICA ACA:
 * se le pregunta al propio Google, con su endpoint 'tokeninfo'. Google
 * valida la firma y nos devuelve el contenido ya comprobado.
 *
 *   VENTAJA: no agrega ninguna dependencia nueva al proyecto.
 *   DESVENTAJA: una llamada HTTP a Google por cada ingreso. Para
 *   produccion de verdad conviene verificar la firma localmente contra
 *   las claves publicas de Google (la libreria google-api-client lo
 *   hace), que no necesita salir a la red. Se eligio tokeninfo porque
 *   es mucho mas facil de leer y de explicar, y el volumen de esta
 *   tienda no lo justifica.
 *
 * LO QUE ESTO *NO* ARREGLA:
 * el proyecto sigue sin sesiones ni tokens propios (ver la nota en
 * UsuarioService). Google Sign-In asegura el MOMENTO DE INGRESAR; de
 * ahi en adelante la API sigue confiando en lo que diga el navegador.
 * Es una mejora real, pero parcial, y conviene decirlo asi.
 */
@Service
public class ServicioGoogle {

    private static final Logger log = LoggerFactory.getLogger(ServicioGoogle.class);
    private static final String TOKENINFO = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final UsuarioRepository repository;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${alba.google.client-id}") private String clientId;

    public ServicioGoogle(UsuarioRepository repository) {
        this.repository = repository;
    }

    /**
     * Verifica el token y devuelve el usuario que corresponde, creandolo
     * si es la primera vez que entra.
     */
    @Transactional
    public Usuario ingresar(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Falta el token de Google");
        }
        JsonNode datos = comprobarConGoogle(idToken);

        // ---- 1. Que el token sea PARA NOSOTROS ----
        // 'aud' es para quien se emitio. Si no es nuestro client id, es un
        // token de otra aplicacion y no vale aca aunque Google lo firmara.
        String aud = texto(datos, "aud");
        if (!clientId.equals(aud)) {
            log.warn("Token de Google emitido para otra aplicacion: aud={}", aud);
            throw new IllegalArgumentException("Ese ingreso de Google no es para este sitio.");
        }

        // ---- 2. Que lo haya emitido Google ----
        String iss = texto(datos, "iss");
        if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) {
            throw new IllegalArgumentException("El token no fue emitido por Google.");
        }

        // ---- 3. Que el email este verificado ----
        // Sin esto, alguien podria crearse una cuenta de Google con el
        // email de otra persona sin haberlo confirmado y quedarse con su
        // cuenta de la tienda.
        if (!"true".equalsIgnoreCase(texto(datos, "email_verified"))) {
            throw new IllegalArgumentException(
                    "Google no confirmo ese correo. Verificalo en tu cuenta de Google y volve a intentar.");
        }

        String sub   = texto(datos, "sub");
        String email = texto(datos, "email");
        if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google no devolvio los datos minimos de la cuenta.");
        }

        return buscarOCrear(sub, email.trim().toLowerCase(),
                            texto(datos, "given_name"), texto(datos, "family_name"));
    }

    /**
     * Encuentra la cuenta o la crea.
     *
     * EL ORDEN IMPORTA:
     *
     *  1. Por 'sub'. Es la clave estable: si la persona cambio su email
     *     en Google, igual la reconocemos y le actualizamos el email.
     *
     *  2. Por email. Es alguien que ya tenia cuenta con contrasena y
     *     ahora entra con Google. SE VINCULAN las dos, no se crea una
     *     cuenta nueva: si se creara, la persona perderia sus pedidos y
     *     ademas chocaria contra el UNIQUE del email.
     *     Se le deja la contrasena que tenia, asi puede seguir entrando
     *     por cualquiera de los dos caminos.
     *
     *  3. Si no esta, se crea como CLIENTE. Nunca como ADMIN: el rol no
     *     lo decide quien entra.
     */
    private Usuario buscarOCrear(String sub, String email, String nombre, String apellido) {
        Optional<Usuario> porSub = repository.findByGoogleSub(sub);
        if (porSub.isPresent()) {
            Usuario u = exigirActivo(porSub.get());
            if (!email.equals(u.getEmail())) {
                log.info("La cuenta de Google {} cambio de email: {} -> {}", sub, u.getEmail(), email);
                u.setEmail(email);
                return repository.save(u);
            }
            return u;
        }

        Optional<Usuario> porEmail = repository.findByEmail(email);
        if (porEmail.isPresent()) {
            Usuario u = exigirActivo(porEmail.get());
            u.setGoogleSub(sub);
            // El proveedor NO se pisa a GOOGLE: la cuenta conserva su
            // contrasena y puede entrar por los dos caminos. Marcarla como
            // GOOGLE la dejaria sin poder usar su contrasena de siempre.
            log.info("Se vinculo la cuenta existente {} con Google", email);
            return repository.save(u);
        }

        Usuario nuevo = new Usuario();
        nuevo.setEmail(email);
        // Google no siempre manda el nombre: mejor un relleno visible que
        // reventar el NOT NULL de la columna.
        nuevo.setNombre(vacioNo(nombre, "Cliente"));
        nuevo.setApellido(vacioNo(apellido, "de Google"));
        nuevo.setProveedor(ProveedorIngreso.GOOGLE);
        nuevo.setGoogleSub(sub);
        nuevo.setPasswordHash(null);     // no tiene: entra por Google
        nuevo.setRol(Rol.CLIENTE);
        nuevo.setActivo(true);
        log.info("Alta de cuenta nueva por Google: {}", email);
        return repository.save(nuevo);
    }

    /**
     * Una cuenta dada de baja no entra, ni con Google.
     * Si no se controlara, el ingreso con Google seria una puerta de
     * atras para los usuarios que un administrador desactivo.
     */
    private Usuario exigirActivo(Usuario u) {
        if (Boolean.FALSE.equals(u.getActivo())) {
            throw new IllegalArgumentException(
                    "Esa cuenta esta dada de baja. Escribinos para reactivarla.");
        }
        return u;
    }

    private JsonNode comprobarConGoogle(String idToken) {
        try {
            HttpRequest pedido = HttpRequest.newBuilder()
                    .uri(URI.create(TOKENINFO + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> r = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                // 400 = token vencido, mal formado o inventado. Es el caso
                // normal de un ingreso que no sirve, no una falla nuestra.
                log.warn("Google rechazo el token (HTTP {})", r.statusCode());
                throw new IllegalArgumentException(
                        "El ingreso con Google no es valido o ya vencio. Proba de nuevo.");
            }
            return json.readTree(r.body());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // Aca cae quedarse sin internet. Es distinto de un token malo:
            // no es culpa de quien entra, asi que el mensaje lo aclara.
            log.error("No se pudo consultar a Google: {}", e.getMessage());
            throw new IllegalStateException(
                    "No se pudo verificar el ingreso con Google. Revisa tu conexion y proba de nuevo.");
        }
    }

    private String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }

    private String vacioNo(String valor, String porDefecto) {
        return valor == null || valor.isBlank() ? porDefecto : valor.trim();
    }
}
