/**
 * js/modules/store.js
 * ============================================================
 * CAPA DE PERSISTENCIA — ahora contra la API REST de la Etapa 1.
 *
 * QUE CAMBIO Y POR QUE:
 * antes este archivo guardaba todo en localStorage del navegador. Ahora los
 * productos y los pedidos viven en MySQL y se piden al back-end de Spring Boot
 * a traves de api.js. Se cumplio lo que prometia el comentario original: la
 * persistencia se reemplazo cambiando este archivo, y las pantallas siguen
 * pidiendo los datos con los mismos nombres de siempre.
 *
 * EL PROBLEMA DE LA ESPERA, Y COMO SE RESUELVE:
 * leer de localStorage es instantaneo; pedir datos por HTTP tarda. Si cada
 * lectura tuviera que esperar la red, habria que poner un "await" en las
 * ~40 lineas de las pantallas que dibujan tarjetas, filtros y tablas.
 *
 * En vez de eso se usa una CACHE EN MEMORIA:
 *
 *   - cargar()  pide los datos al servidor UNA vez, al abrir la pagina.
 *   - las LECTURAS (getAll, getById, getActivos) leen de esa copia y siguen
 *     siendo instantaneas, asi que las pantallas no cambiaron.
 *   - las ESCRITURAS (create, update, remove, cambiarEstado) si van al
 *     servidor, y cuando el servidor contesta refrescan la copia. Son las
 *     unicas que hay que esperar con "await".
 *
 * QUE SIGUE SIENDO LOCAL, Y POR QUE:
 *   - el CARRITO: es borrador del cliente, no una venta. Un carrito a medio
 *     armar no tiene por que ocupar lugar en la base. Recien al confirmar se
 *     convierte en un pedido y ahi si viaja al servidor.
 *   - la SESION del admin: el back-end todavia no tiene endpoint de login
 *     (las contrasenas estan guardadas sin hashear, con una nota en
 *     UsuarioService de que BCrypt viene despues). Hasta que exista, el
 *     ingreso al panel se valida en el navegador.
 * ============================================================
 */

import { api, ErrorApi } from "./api.js";

export { ErrorApi };
export { ESTADOS_REALES } from "./api.js";

// ============================================================
//  LA CACHE
// ============================================================

let _productos  = [];
let _pedidos    = [];
let _cargado    = false;

/** true cuando ya se trajeron los datos del servidor al menos una vez. */
export function estaCargado() { return _cargado; }

/**
 * Trae productos y pedidos del servidor. Se llama una vez al abrir la pagina.
 * Los dos pedidos salen en paralelo (Promise.all) para no esperar uno y
 * despues el otro.
 *
 * Si el back-end esta apagado, api.js lanza un ErrorApi con un mensaje claro
 * y quien llamo a cargar() decide que mostrar en pantalla.
 */
export async function cargar() {
  const [prods, peds] = await Promise.all([
    api.productos.listar(),   // /productos/auditoria: incluye los dados de baja
    api.pedidos.listar()
  ]);
  _productos = prods;
  _pedidos   = peds;
  _cargado   = true;
}

/** Vuelve a pedir solo los productos (despues de una escritura). */
export async function recargarProductos() {
  _productos = await api.productos.listar();
}

/** Vuelve a pedir solo los pedidos. */
export async function recargarPedidos() {
  _pedidos = await api.pedidos.listar();
}

// ============================================================
//  PRODUCTOS
// ============================================================

export const productos = {
  /** Todos, incluidos los dados de baja. Para el panel de gestion. */
  getAll() { return _productos; },

  /** Solo los que se pueden comprar. Para la tienda. */
  getActivos() { return _productos.filter(p => p.activo); },

  getById(id) { return _productos.find(p => p.id === Number(id)) || null; },

  /** Alta. El id lo asigna MySQL (AUTO_INCREMENT), no el navegador. */
  async create(datos) {
    const creado = await api.productos.crear(datos);
    await recargarProductos();
    return creado;
  },

  /**
   * Modificacion.
   *
   * OJO CON UN DETALLE DEL BACK-END: el controlador valida el cuerpo con
   * @Valid, y nombre, linea, varietal, precio, stockActual y stockMinimo
   * son obligatorios. Entonces un PUT con solo { precio: 16000 } se rechaza
   * con un 400, aunque el servicio por dentro sepa fusionar campos.
   *
   * Por eso aca se toma el producto que ya esta en la cache, se le aplican
   * los cambios encima y se manda COMPLETO. Asi la edicion rapida de una
   * celda de la tabla sigue funcionando con un solo campo.
   */
  async update(id, cambios) {
    const actual = this.getById(id);
    const completo = actual ? { ...actual, ...cambios } : cambios;
    const guardado = await api.productos.actualizar(id, completo);
    await recargarProductos();
    return guardado;
  },

  /** Baja LOGICA: el back-end pone activo = false y conserva el historial. */
  async remove(id) {
    await api.productos.eliminar(id);
    await recargarProductos();
    return true;
  },

  /** Reactivar: el mismo PUT de update, con activo = true. */
  async reactivar(id) {
    const p = this.getById(id);
    if (!p) return false;
    await this.update(id, { activo: true });
    return true;
  }
};

// ============================================================
//  PEDIDOS
// ============================================================

export const pedidos = {
  getAll() { return _pedidos; },

  getById(id) { return _pedidos.find(p => p.id === Number(id)) || null; },

  /**
   * Confirma la compra.
   *
   * LO IMPORTANTE: el navegador NO calcula el total ni descuenta el stock.
   * Manda que vino y cuanto, y PedidoService hace el resto del lado del
   * servidor: bloquea cada producto, verifica el stock, congela el precio,
   * calcula el total y registra los movimientos de SALIDA. Esa logica no se
   * duplica aca, porque si se duplicara se podria falsear desde el navegador.
   */
  async create({ comprador, medioPago, items }) {
    const idUsuario = await resolverUsuario(comprador);
    const pedido = await api.pedidos.crear(idUsuario, medioPago, items);
    // El stock cambio en el servidor: hay que refrescar las dos colecciones.
    await Promise.all([recargarProductos(), recargarPedidos()]);
    return pedido;
  },

  /**
   * Cambia el estado. Si pasa a CANCELADO el back-end devuelve el stock
   * automaticamente, asi que tambien se refrescan los productos.
   */
  async cambiarEstado(id, nuevoEstado) {
    const pedido = await api.pedidos.cambiarEstado(id, nuevoEstado);
    await Promise.all([recargarProductos(), recargarPedidos()]);
    return pedido;
  }
};

/**
 * El checkout pide nombre, email y telefono, pero la tabla pedidos no guarda
 * datos del comprador: guarda un id_usuario. Entonces hay que convertir esos
 * datos en un usuario de la base.
 *
 * Si el email ya existe se reutiliza ese cliente (el email es UNIQUE en MySQL,
 * asi que insertarlo de nuevo daria error). Si no existe, se da de alta como
 * CLIENTE.
 *
 * LIMITACION CONOCIDA: UsuarioService.registrar() exige una contrasena, pero
 * una tienda real no se la pide a alguien que solo esta comprando. Como
 * solucion transitoria se genera un valor aleatorio, que no sirve para
 * iniciar sesion. Lo correcto seria que el back-end acepte una compra sin
 * cuenta, o que haya un registro de verdad antes de comprar.
 */
async function resolverUsuario(comprador) {
  const existente = await api.usuarios.porEmail(comprador.email);
  if (existente) return existente.idUsuario;

  // "Juan Carlos Perez" -> nombre "Juan Carlos", apellido "Perez".
  const partes   = comprador.nombre.trim().split(/\s+/);
  const apellido = partes.length > 1 ? partes.pop() : "-";
  const nombre   = partes.join(" ");

  const creado = await api.usuarios.crear({
    nombre,
    apellido,
    email:    comprador.email,
    telefono: comprador.telefono,
    rol:      "CLIENTE",
    // Valor aleatorio, no una contrasena elegida: ver la nota de arriba.
    passwordHash: "sin-cuenta-" + crypto.randomUUID()
  });
  return creado.idUsuario;
}

// ============================================================
//  AUDITORIA — ahora con los datos reales del servidor
// ============================================================

/**
 * Antes esto era una lista inventada en el navegador. Ahora muestra la tabla
 * movimientos_stock, que es la trazabilidad que el back-end ya venia
 * llevando: cada entrada, salida o ajuste de stock con su usuario, su motivo
 * y su fecha. Es informacion verificable en MySQL, no un registro decorativo.
 *
 * QUE NO APARECE: el back-end registra movimientos de STOCK, no acciones de
 * pantalla. "Inicio sesion" o "cambio un precio" no quedan asentados porque
 * no existe una tabla de auditoria general. Para tenerlas habria que agregarla.
 */
export const auditoria = {
  async getAll() {
    const movimientos = await api.movimientos.listar();
    return movimientos.map(m => {
      const u = m.usuario || {};
      const signo = m.cantidad > 0 ? "+" : "";
      return {
        fecha:  m.fecha,
        autor:  [u.nombre, u.apellido].filter(Boolean).join(" ") || "sistema",
        accion: m.tipo + " de stock (" + signo + m.cantidad + ")" +
                (m.motivo ? " — " + m.motivo : ""),
        objeto: m.producto?.nombre || "-",
        anulado: m.activo === false
      };
    });
  }
};

// ============================================================
//  SESION — ahora contra el servidor
// ============================================================

const CLAVE_SESION = "alba_sesion";

/**
 * QUE CAMBIO: antes el ingreso se validaba contra una lista escrita en el
 * propio JavaScript, asi que el rol de cada persona lo decidia el
 * navegador. Ahora lo decide POST /api/usuarios/login y el navegador solo
 * recuerda la respuesta.
 *
 * HASTA DONDE LLEGA ESTO: el servidor confirma quien es, pero no entrega
 * ninguna credencial de sesion. Lo que queda guardado es un dato, no una
 * llave: sirve para que la pagina sepa a quien saludar y que mostrar, no
 * para autorizar nada. Los endpoints de administracion siguen abiertos a
 * cualquiera que llame a la API directamente. Cerrarlos es trabajo de
 * Spring Security, fuera del alcance de esta entrega.
 *
 * La contrasena no se guarda en ningun lado: viaja una vez y se descarta.
 */
export const sesion = {
  async login(email, password) {
    const u = await api.usuarios.login(email, password);
    const datos = {
      idUsuario: u.idUsuario,
      nombre:    u.nombre,
      apellido:  u.apellido,
      email:     u.email,
      telefono:  u.telefono || "",
      rol:       u.rol                       // ADMIN o CLIENTE
    };
    localStorage.setItem(CLAVE_SESION, JSON.stringify(datos));
    return datos;
  },

  /** Alta de cliente nuevo. Si sale bien, lo deja ya ingresado. */
  async registrar({ nombre, apellido, email, telefono, password }) {
    await api.usuarios.crear({
      nombre, apellido, email, telefono,
      rol: "CLIENTE",
      passwordHash: password
    });
    return this.login(email, password);
  },

  actual() {
    try { return JSON.parse(localStorage.getItem(CLAVE_SESION)) || null; }
    catch { return null; }
  },

  esAdmin()   { return this.actual()?.rol === "ADMIN"; },
  nombreCompleto() {
    const u = this.actual();
    return u ? [u.nombre, u.apellido].filter(Boolean).join(" ") : "";
  },

  logout() { localStorage.removeItem(CLAVE_SESION); }
};

// ============================================================
//  CARRITO — borrador local del cliente
// ============================================================

const CLAVE_CARRITO = "alba_carrito";

const leerCarrito = () => {
  try { return JSON.parse(localStorage.getItem(CLAVE_CARRITO)) || []; }
  catch { return []; }
};

export const carrito = {
  getItems() { return leerCarrito(); },
  _guardar(items) { localStorage.setItem(CLAVE_CARRITO, JSON.stringify(items)); },

  /**
   * Agrega al carrito mirando el stock de la cache.
   *
   * Este control es por comodidad, para avisar al instante. El control que
   * de verdad cuenta es el del servidor al confirmar: entre que alguien
   * mira el catalogo y paga, otro pudo comprar la ultima botella.
   */
  agregar(idProducto, cantidad = 1) {
    const prod = productos.getById(idProducto);
    if (!prod || !prod.activo) return { ok: false, mensaje: "Producto no disponible" };
    const items = this.getItems();
    const existente = items.find(i => i.id === prod.id);
    const yaEnCarrito = existente ? existente.cantidad : 0;
    if (yaEnCarrito + cantidad > prod.stock) {
      return { ok: false, mensaje: "Solo quedan " + prod.stock + " unidades de " + prod.nombre };
    }
    if (existente) existente.cantidad += cantidad;
    else items.push({ id: prod.id, nombre: prod.nombre, precio: prod.precio,
                      imagen: prod.imagen, cantidad });
    this._guardar(items);
    return { ok: true };
  },

  cambiarCantidad(idProducto, cantidad) {
    const prod = productos.getById(idProducto);
    if (!prod) return { ok: false, mensaje: "Producto inexistente" };
    if (cantidad > prod.stock) return { ok: false, mensaje: "Solo quedan " + prod.stock + " unidades" };
    const items = this.getItems();
    const item = items.find(i => i.id === Number(idProducto));
    if (!item) return { ok: false, mensaje: "No esta en el carrito" };
    if (cantidad <= 0) return this.quitar(idProducto);
    item.cantidad = cantidad;
    this._guardar(items);
    return { ok: true };
  },

  quitar(idProducto) {
    this._guardar(this.getItems().filter(i => i.id !== Number(idProducto)));
    return { ok: true };
  },

  vaciar() { this._guardar([]); },

  cantidadTotal() { return this.getItems().reduce((s, i) => s + i.cantidad, 0); }
};

// ============================================================
//  SINCRONIZACION ENTRE PESTANAS
// ============================================================

/**
 * Con una sola base compartida, la forma de ver los cambios de otra pestania
 * es volver a preguntarle al servidor. Esto avisa cuando cambia el carrito o
 * la sesion en otra pestania; para productos y pedidos, quien escucha vuelve
 * a llamar a cargar().
 */
export function alCambiarEnOtraPestana(callback) {
  window.addEventListener("storage", (e) => {
    if (e.key === CLAVE_CARRITO || e.key === CLAVE_SESION) callback(e.key);
  });
}
