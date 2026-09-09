package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * ==========================================================================
 *  LA UNICA CLASE DEL ESCRITORIO QUE SABE QUE EXISTE UNA RED.
 * ==========================================================================
 *
 * Es el equivalente, del lado del cliente, a lo que el Repository es del lado
 * del servidor: aisla el "hablar con algo de afuera" en un solo lugar.
 * Ningun panel de la ventana escribe una URL ni sabe lo que es HTTP: todos
 * le piden las cosas a esta clase.
 *
 * MUY IMPORTANTE PARA LA DEFENSA:
 * aca NO hay ni una regla de negocio. No se valida stock, no se calculan
 * totales, no se decide nada. Todo eso vive en el servidor. El escritorio
 * solo pide y muestra. Por eso la ventana y la futura pagina web se comportan
 * exactamente igual: comparten el mismo cerebro.
 */
public class ApiCliente {

    /**
     * La direccion del servidor. Es lo UNICO que hay que cambiar para que la
     * ventana apunte a otra computadora: por eso la aplicacion no es "monolitica"
     * (requisito del TP). Se puede pisar al arrancar: java ... http://otra-pc:8080
     */
    private final String baseUrl;

    /** Cliente HTTP que trae Java de fabrica desde la version 11. No hay que instalar nada. */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Jackson: traduce entre texto JSON y objetos Java. Es el mismo que usa Spring del otro lado. */
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()   // habilita fechas (LocalDateTime)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ApiCliente(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String getBaseUrl() { return baseUrl; }

    // ======================================================================
    //  MOTOR: los 4 verbos HTTP. Todo lo de abajo se apoya en estos.
    // ======================================================================

    private HttpResponse<String> enviar(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // El servidor esta apagado, la direccion esta mal, o no hay red.
            throw new ApiException(0, "No se pudo contactar al servidor en " + baseUrl
                    + ".\nRevisa que la aplicacion Spring Boot este corriendo.", Map.of());
        }
    }

    /** Si el servidor contesto con un codigo de error, lo convierte en una ApiException legible. */
    private String verificar(HttpResponse<String> res) {
        int codigo = res.statusCode();
        if (codigo >= 200 && codigo < 300) return res.body();

        String mensaje = "El servidor respondio " + codigo;
        Map<String, String> campos = new LinkedHashMap<>();
        try {
            JsonNode n = json.readTree(res.body());
            if (n.hasNonNull("error")) mensaje = n.get("error").asText();
            if (n.has("campos")) {
                n.get("campos").fields().forEachRemaining(e -> campos.put(e.getKey(), e.getValue().asText()));
            }
        } catch (Exception ignorado) {
            if (codigo == 404) mensaje = "No se encontro lo que se pidio (404).";
        }
        throw new ApiException(codigo, mensaje, campos);
    }

    private String get(String ruta) {
        return verificar(enviar(HttpRequest.newBuilder(URI.create(baseUrl + ruta)).GET().build()));
    }

    private String enviarCuerpo(String metodo, String ruta, Object cuerpo) {
        try {
            String texto = json.writeValueAsString(cuerpo);
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                    .header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(texto, StandardCharsets.UTF_8))
                    .build();
            return verificar(enviar(req));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(0, "No se pudo preparar el envio: " + e.getMessage(), Map.of());
        }
    }

    private String sinCuerpo(String metodo, String ruta) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .method(metodo, HttpRequest.BodyPublishers.noBody()).build();
        return verificar(enviar(req));
    }

    private <T> T leer(String texto, TypeReference<T> tipo) {
        try {
            return json.readValue(texto, tipo);
        } catch (Exception e) {
            throw new ApiException(0, "El servidor devolvio algo que no se pudo interpretar: "
                    + e.getMessage(), Map.of());
        }
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ======================================================================
    //  PRODUCTOS (los vinos)
    // ======================================================================

    public List<Producto> listarProductos() {
        return leer(get("/api/productos"), new TypeReference<>() {});
    }

    public List<Producto> buscarProductos(String texto) {
        return leer(get("/api/productos/buscar?nombre=" + url(texto)), new TypeReference<>() {});
    }

    public List<Producto> productosPorLinea(Linea linea) {
        return leer(get("/api/productos/linea/" + linea.name()), new TypeReference<>() {});
    }

    public List<Producto> productosDesdePrecio(BigDecimal desde) {
        return leer(get("/api/productos/precio?desde=" + desde.toPlainString()), new TypeReference<>() {});
    }

    public List<Producto> alertasStock() {
        return leer(get("/api/productos/alertas"), new TypeReference<>() {});
    }

    public Producto crearProducto(Producto p) {
        return leer(enviarCuerpo("POST", "/api/productos", p), new TypeReference<>() {});
    }

    public Producto actualizarProducto(Integer id, Producto p) {
        return leer(enviarCuerpo("PUT", "/api/productos/" + id, p), new TypeReference<>() {});
    }

    /** Baja LOGICA: el servidor marca activo = false, no borra la fila. */
    public void eliminarProducto(Integer id) {
        sinCuerpo("DELETE", "/api/productos/" + id);
    }

    /** Ingreso de mercaderia: sube el stock Y deja el movimiento de ENTRADA. */
    public Producto ingresarStock(Integer idProducto, int cantidad, Integer idUsuario, String motivo) {
        String ruta = "/api/productos/" + idProducto + "/ingreso?cantidad=" + cantidad
                + "&idUsuario=" + idUsuario
                + (motivo == null || motivo.isBlank() ? "" : "&motivo=" + url(motivo));
        return leer(sinCuerpo("POST", ruta), new TypeReference<>() {});
    }

    // ======================================================================
    //  USUARIOS (clientes y administradores)
    // ======================================================================

    public List<Usuario> listarUsuarios() {
        return leer(get("/api/usuarios"), new TypeReference<>() {});
    }

    public Usuario crearUsuario(Usuario u) {
        return leer(enviarCuerpo("POST", "/api/usuarios", u), new TypeReference<>() {});
    }

    public Usuario actualizarUsuario(Integer id, Usuario u) {
        return leer(enviarCuerpo("PUT", "/api/usuarios/" + id, u), new TypeReference<>() {});
    }

    public void eliminarUsuario(Integer id) {
        sinCuerpo("DELETE", "/api/usuarios/" + id);
    }

    // ======================================================================
    //  PEDIDOS
    // ======================================================================

    public List<Pedido> listarPedidos() {
        return leer(get("/api/pedidos"), new TypeReference<>() {});
    }

    public List<Pedido> pedidosDeUsuario(Integer idUsuario) {
        return leer(get("/api/pedidos/usuario/" + idUsuario), new TypeReference<>() {});
    }

    /**
     * CHECKOUT. Se arma el JSON a mano con Maps porque el servidor solo necesita
     * los IDs: {"usuario":{"idUsuario":2},"medioPago":"TARJETA",
     *           "detalles":[{"producto":{"idProducto":5},"cantidad":2}]}
     * El precio y el total NO se mandan: los calcula y los congela el servidor.
     */
    public Pedido crearPedido(Integer idUsuario, MedioPago medioPago, Map<Integer, Integer> lineas) {
        List<Map<String, Object>> detalles = new ArrayList<>();
        lineas.forEach((idProducto, cantidad) -> detalles.add(Map.of(
                "producto", Map.of("idProducto", idProducto),
                "cantidad", cantidad)));

        Map<String, Object> cuerpo = Map.of(
                "usuario", Map.of("idUsuario", idUsuario),
                "medioPago", medioPago.name(),
                "detalles", detalles);

        return leer(enviarCuerpo("POST", "/api/pedidos", cuerpo), new TypeReference<>() {});
    }

    /** CANCELADO devuelve el stock automaticamente (regla del servidor). */
    public Pedido cambiarEstadoPedido(Integer idPedido, EstadoPedido nuevo) {
        return leer(sinCuerpo("PUT", "/api/pedidos/" + idPedido + "/estado?valor=" + nuevo.name()),
                new TypeReference<>() {});
    }

    // ======================================================================
    //  MOVIMIENTOS DE STOCK (la trazabilidad)
    // ======================================================================

    public List<MovimientoStock> listarMovimientos() {
        return leer(get("/api/movimientos"), new TypeReference<>() {});
    }

    public List<MovimientoStock> movimientosDeProducto(Integer idProducto) {
        return leer(get("/api/movimientos/producto/" + idProducto), new TypeReference<>() {});
    }

    // ======================================================================
    //  SOLICITUDES DE CANCELACION (el circuito de feedback)
    // ======================================================================

    public List<SolicitudCancelacion> listarSolicitudes() {
        return leer(get("/api/solicitudes"), new TypeReference<>() {});
    }

    /** El seguimiento del cliente: "mis solicitudes". */
    public List<SolicitudCancelacion> solicitudesDeUsuario(Integer idUsuario) {
        return leer(get("/api/solicitudes/usuario/" + idUsuario), new TypeReference<>() {});
    }

    /** Bandeja del administrador. */
    public List<SolicitudCancelacion> solicitudesPendientes() {
        return leer(get("/api/solicitudes/pendientes"), new TypeReference<>() {});
    }

    /** El administrador aprueba o rechaza. */
    public SolicitudCancelacion resolverSolicitud(Integer idSolicitud, boolean aprobar,
                                                  String respuesta, Integer idAdmin) {
        return leer(enviarCuerpo("PUT",
                "/api/solicitudes/" + idSolicitud + "/resolver?aprobar=" + aprobar + "&idAdmin=" + idAdmin,
                Map.of("respuesta", respuesta)), new TypeReference<>() {});
    }

    /** Prueba rapida de conexion al arrancar. */
    public boolean hayConexion() {
        try { get("/api/productos"); return true; } catch (ApiException e) { return false; }
    }
}
