/**
 * js/modules/api.js
 * ============================================================
 * CLIENTE HTTP contra la API REST de la Etapa 1 (Spring Boot).
 *
 * Este es el unico archivo del frontend que sabe que existe un
 * servidor en localhost:8080. Cumple dos trabajos:
 *
 *   1) HABLAR HTTP: fetch con GET / POST / PUT / DELETE y un manejo
 *      de errores unico, que entiende el formato de error que ya
 *      devuelve el GlobalExceptionHandler del back-end.
 *
 *   2) TRADUCIR entre los dos modelos. El back-end y el front-end
 *      nombran las cosas distinto porque se escribieron por separado:
 *
 *        back-end          front-end
 *        ---------------   -----------
 *        idProducto        id
 *        anada             cosecha
 *        descripcion       notasCata
 *        stockActual       stock
 *        linea: FINCA      linea: "Finca"
 *        imagenUrl         imagen (archivo local)
 *
 *      En vez de renombrar campos en las pantallas (que obligaria a
 *      tocar tienda.js y admin.js enteros), la traduccion vive aca
 *      concentrada en cuatro funciones: productoDesdeApi,
 *      productoHaciaApi, pedidoDesdeApi y pedidoHaciaApi.
 *      Las pantallas siguen viendo el modelo que ya conocian.
 * ============================================================
 */

/** Raiz de la API. Si el back-end se mueve de puerto, se cambia aca. */
export const BASE = "http://localhost:8080/api";

// ============================================================
//  1) TRANSPORTE HTTP
// ============================================================

/** Error con el mensaje que mando el back-end, para poder mostrarlo tal cual. */
export class ErrorApi extends Error {
  constructor(mensaje, estado, campos) {
    super(mensaje);
    this.name = "ErrorApi";
    this.estado = estado;   // codigo HTTP (400, 404, 409, 500...)
    this.campos = campos;   // errores por campo, si el back-end los manda
  }
}

/**
 * Hace una llamada y devuelve el JSON. Centraliza el manejo de errores:
 * cualquier respuesta que no sea 2xx se convierte en un ErrorApi con el
 * texto que escribio el back-end, que ya viene en castellano y explicado.
 */
async function pedir(ruta, opciones = {}) {
  let respuesta;
  try {
    respuesta = await fetch(BASE + ruta, {
      headers: { "Content-Type": "application/json" },
      ...opciones
    });
  } catch (e) {
    // fetch solo falla asi cuando no hay servidor del otro lado.
    throw new ErrorApi(
      "No se puede contactar al servidor. Verifica que Spring Boot este " +
      "corriendo en localhost:8080.", 0, null);
  }

  // 204 No Content (lo devuelve DELETE): no hay cuerpo que parsear.
  if (respuesta.status === 204) return null;

  const texto = await respuesta.text();
  let cuerpo = null;
  try { cuerpo = texto ? JSON.parse(texto) : null; } catch { /* no era JSON */ }

  if (!respuesta.ok) {
    // Formato de error del GlobalExceptionHandler: { estado, error, campos, ruta }
    const detalle = cuerpo?.campos
      ? Object.values(cuerpo.campos).join(" · ")
      : null;
    throw new ErrorApi(
      detalle || cuerpo?.error || ("Error " + respuesta.status),
      respuesta.status,
      cuerpo?.campos || null);
  }
  return cuerpo;
}

const get    = (ruta)        => pedir(ruta);
const post   = (ruta, datos) => pedir(ruta, { method: "POST",   body: JSON.stringify(datos) });
const put    = (ruta, datos) => pedir(ruta, { method: "PUT",    body: JSON.stringify(datos) });
const borrar = (ruta)        => pedir(ruta, { method: "DELETE" });

// ============================================================
//  2) TRADUCCION DE MODELOS
// ============================================================

/**
 * La columna 'linea' en MySQL es un enum en MAYUSCULAS_CON_GUION.
 * El front-end lo muestra como texto legible y lo usa en los filtros.
 */
const LINEA_A_TEXTO = {
  FINCA:            "Finca",
  ESTATE_RESERVE:   "Estate Reserve",
  GRAN_RESERVA:     "Gran Reserva",
  EDICION_ESPECIAL: "Ediciones Especiales"
};
const TEXTO_A_LINEA = Object.fromEntries(
  Object.entries(LINEA_A_TEXTO).map(([k, v]) => [v, k]));

/**
 * El back-end guarda 'imagenUrl' apuntando a albaenlosandes.com. Las fotos
 * tambien estan descargadas en assets/img/vinos/ con el nombre
 * "nombre-del-vino-anada.jpg", asi que se arma esa ruta local.
 *
 * Si el archivo no existe (por ejemplo para un vino nuevo cargado desde el
 * admin), el <img> de la tarjeta ya tiene un onerror que cae al placeholder
 * dibujado en SVG. Por eso aca no hace falta comprobar que exista.
 */
function imagenLocal(nombre, anada) {
  const slug = String(nombre || "")
    .toLowerCase()
    .normalize("NFD").replace(/[̀-ͯ]/g, "")  // saca acentos
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
  if (!slug) return "";
  return "assets/img/vinos/" + slug + (anada ? "-" + anada : "") + ".jpg";
}

/** Producto: como lo manda Spring Boot -> como lo esperan las pantallas. */
export function productoDesdeApi(p) {
  return {
    id:         p.idProducto,
    nombre:     p.nombre,
    linea:      LINEA_A_TEXTO[p.linea] || p.linea,
    varietal:   p.varietal,
    cosecha:    p.anada,
    bodega:     "Alba en los Andes",   // el back-end no modela la bodega:
                                       // todo el catalogo es de la misma.
    precio:     Number(p.precio),
    stock:      p.stockActual,
    stockMinimo: p.stockMinimo,
    notasCata:  p.descripcion || "",
    imagen:     imagenLocal(p.nombre, p.anada),
    imagenUrlOrigen: p.imagenUrl || "",
    activo:     p.activo !== false
  };
}

/** Producto: como lo arma el formulario del admin -> como lo pide la API. */
export function productoHaciaApi(p) {
  const salida = {};
  if (p.nombre      !== undefined) salida.nombre      = p.nombre;
  if (p.linea       !== undefined) salida.linea       = TEXTO_A_LINEA[p.linea] || p.linea;
  if (p.varietal    !== undefined) salida.varietal    = p.varietal;
  if (p.cosecha     !== undefined) salida.anada       = Number(p.cosecha);
  if (p.notasCata   !== undefined) salida.descripcion = p.notasCata;
  if (p.precio      !== undefined) salida.precio      = Number(p.precio);
  if (p.stock       !== undefined) salida.stockActual = Number(p.stock);
  if (p.stockMinimo !== undefined) salida.stockMinimo = Number(p.stockMinimo);
  if (p.activo      !== undefined) salida.activo      = p.activo;
  // 'imagen' NO se manda: la columna imagen_url admite 300 caracteres y una
  // imagen subida en base64 ocupa miles. Ver el README para el detalle.
  if (p.imagenUrlOrigen) salida.imagenUrl = p.imagenUrlOrigen;
  return salida;
}

/**
 * Los estados no coinciden: MySQL solo admite CONFIRMADO, ENTREGADO y
 * CANCELADO, mientras que el panel mostraba cinco. Se traduce a los tres
 * reales y el admin ahora ofrece solo esos (ver admin.js).
 */
const ESTADO_A_TEXTO = {
  CONFIRMADO: "confirmado",
  ENTREGADO:  "entregado",
  CANCELADO:  "cancelado"
};
const TEXTO_A_ESTADO = Object.fromEntries(
  Object.entries(ESTADO_A_TEXTO).map(([k, v]) => [v, k]));

export const ESTADOS_REALES = Object.values(ESTADO_A_TEXTO);
export const aEstadoApi = (texto) => TEXTO_A_ESTADO[texto] || texto;

/** Pedido: como lo manda Spring Boot -> como lo esperan las pantallas. */
export function pedidoDesdeApi(p) {
  const detalles = p.detalles || [];
  const items = detalles.map(d => ({
    id:       d.producto?.idProducto,
    nombre:   d.producto?.nombre || "(producto dado de baja)",
    cantidad: d.cantidad,
    precio:   Number(d.precioUnitario)   // precio congelado al comprar
  }));
  const u = p.usuario || {};
  return {
    id:     p.idPedido,
    fecha:  p.fecha,
    estado: ESTADO_A_TEXTO[p.estado] || p.estado,
    medioPago: p.medioPago,
    comprador: {
      nombre:   [u.nombre, u.apellido].filter(Boolean).join(" "),
      email:    u.email || "",
      telefono: u.telefono || ""
    },
    idUsuario: u.idUsuario,
    items,
    // El back-end guarda un unico 'total' (suma de cantidad x precio
    // congelado). No guarda envio ni impuestos desglosados, asi que el
    // subtotal es ese total y los otros dos quedan en cero.
    subtotal:  Number(p.total),
    envio:     0,
    impuestos: 0,
    total:     Number(p.total),
    activo:    p.activo !== false
  };
}

// ============================================================
//  3) LAS OPERACIONES, UNA POR ENDPOINT
// ============================================================

export const api = {

  productos: {
    /**
     * Catalogo COMPLETO, incluidos los vinos dados de baja, que es lo que
     * necesita el panel de gestion para poder reactivarlos.
     *
     * POR QUE HAY UN RESPALDO: /productos/auditoria se agrego junto con esta
     * integracion. Si el back-end que esta corriendo todavia no lo tiene
     * (porque no se reinicio despues de compilar), devuelve 400 y entonces
     * se usa /productos, que trae solo los activos. La tienda funciona
     * igual; lo unico que no se puede en ese caso es reactivar un vino.
     */
    async listar() {
      try {
        const lista = await get("/productos/auditoria");
        return lista.map(productoDesdeApi);
      } catch (e) {
        if (e.estado !== 400 && e.estado !== 404) throw e;
        console.warn("El back-end no tiene /api/productos/auditoria todavia: " +
                     "se listan solo los vinos activos. Reinicia Spring Boot.");
        const lista = await get("/productos");
        return lista.map(productoDesdeApi);
      }
    },
    activos:   ()            => get("/productos").then(l => l.map(productoDesdeApi)),
    porId:     (id)          => get("/productos/" + id).then(productoDesdeApi),
    crear:     (datos)       => post("/productos", productoHaciaApi(datos)).then(productoDesdeApi),
    actualizar:(id, datos)   => put("/productos/" + id, productoHaciaApi(datos)).then(productoDesdeApi),
    /** Baja logica: el back-end pone activo = false, no borra la fila. */
    eliminar:  (id)          => borrar("/productos/" + id),
    alertas:   ()            => get("/productos/alertas").then(l => l.map(productoDesdeApi)),
    // No hay un endpoint /reactivar: se reactiva con el PUT de arriba
    // mandando activo = true. Lo hace store.productos.reactivar().

    /**
     * Ingreso de mercaderia. Sube el stock Y deja registrado el movimiento
     * de ENTRADA con su responsable, en una sola operacion del servidor.
     * No se hace con un PUT de stock: eso cambiaria el numero sin dejar
     * rastro de quien ni por que.
     */
    ingreso: (id, cantidad, idUsuario, motivo) =>
      post("/productos/" + id + "/ingreso?cantidad=" + cantidad +
           "&idUsuario=" + idUsuario +
           (motivo ? "&motivo=" + encodeURIComponent(motivo) : ""), {})
        .then(productoDesdeApi)
  },

  pedidos: {
    listar: ()      => get("/pedidos").then(l => l.map(pedidoDesdeApi)),
    porId:  (id)    => get("/pedidos/" + id).then(pedidoDesdeApi),
    /**
     * Crea el pedido. El cuerpo es el que documenta PedidoController:
     *   { usuario:{idUsuario}, medioPago, detalles:[{producto:{idProducto}, cantidad}] }
     * El total NO se manda: lo calcula PedidoService congelando el precio
     * de cada producto, que es justamente la logica que no hay que duplicar
     * en el navegador.
     */
    crear: (idUsuario, medioPago, items) =>
      post("/pedidos", {
        usuario:  { idUsuario },
        medioPago,
        detalles: items.map(i => ({
          producto: { idProducto: i.id },
          cantidad: i.cantidad
        }))
      }).then(pedidoDesdeApi),
    /** PUT /api/pedidos/{id}/estado?valor=ENTREGADO */
    cambiarEstado: (id, estadoTexto) =>
      put("/pedidos/" + id + "/estado?valor=" + aEstadoApi(estadoTexto), {})
        .then(pedidoDesdeApi),
    eliminar: (id) => borrar("/pedidos/" + id)
  },

  usuarios: {
    listar: () => get("/usuarios"),
    /** Incluye los dados de baja. Para el panel de gestion. */
    listarTodos: () => get("/usuarios/auditoria"),
    crear:  (datos) => post("/usuarios", datos),
    /**
     * Modificacion. UsuarioService conserva la contrasena si no se manda,
     * asi que el formulario del panel nunca necesita pedirla para editar.
     */
    actualizar: (id, datos) => put("/usuarios/" + id, datos),
    /** Baja logica. El back-end rechaza dar de baja al ultimo ADMIN. */
    eliminar: (id) => borrar("/usuarios/" + id),
    /**
     * El back-end no tiene un endpoint "buscar por email", pero el email es
     * unico en la tabla, asi que se busca en la lista. Con un catalogo de
     * clientes chico como este alcanza; con miles habria que agregar
     * GET /api/usuarios?email=... al back-end.
     */
    async porEmail(email) {
      const todos = await get("/usuarios");
      const buscado = String(email).trim().toLowerCase();
      return todos.find(u => String(u.email).toLowerCase() === buscado) || null;
    }
  },

  /**
   * Movimientos de stock: es la trazabilidad real que ya lleva el back-end
   * (quien movio que producto, cuanto y por que). El panel de admin lo usa
   * como registro de auditoria, porque es la fuente de verdad del servidor.
   */
  movimientos: {
    listar: () => get("/movimientos")
  },

  /**
   * Preguntas frecuentes del circuito de cancelaciones. Son las que ve el
   * cliente cuando elige un motivo: estan para resolverle el problema sin
   * que tenga que esperar una respuesta.
   */
  faq: {
    /** /faq devuelve solo las activas; /faq/todas incluye las quitadas. */
    listar:     () => get("/faq/todas"),
    activas:    () => get("/faq"),
    crear:      (datos)     => post("/faq", datos),
    actualizar: (id, datos) => put("/faq/" + id, datos),
    eliminar:   (id)        => borrar("/faq/" + id)
  },

  solicitudes: {
    listar: () => get("/solicitudes"),
    /**
     * PUT /api/solicitudes/5/resolver?aprobar=true&idAdmin=1
     * Si se aprueba, el back-end cancela el pedido y devuelve el stock:
     * por eso quien llama tiene que refrescar productos y pedidos despues.
     */
    resolver: (id, aprobar, idAdmin, respuesta) =>
      put("/solicitudes/" + id + "/resolver?aprobar=" + aprobar + "&idAdmin=" + idAdmin,
          { respuesta })
  },

  /** Ping para avisar en pantalla si el back-end esta caido. */
  async disponible() {
    try { await get("/productos"); return true; } catch { return false; }
  }
};
