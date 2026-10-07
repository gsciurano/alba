/**
 * js/admin.js
 * ============================================================
 * PANEL DE GESTION — ocho secciones sobre la API de la Etapa 1.
 *
 *   Tablero      numeros del dia y los dos avisos que importan
 *   Inventario   catalogo completo, alta/edicion/baja de vinos
 *   Ingreso      suma botellas y deja el movimiento de ENTRADA
 *   Movimientos  auditoria de stock, de solo lectura
 *   Pedidos      ventas de la tienda y cambio de estado
 *   Clientes     usuarios, altas y bajas logicas
 *   Solicitudes  bandeja de cancelaciones, para responderlas
 *   FAQ          preguntas que ve el cliente al querer cancelar
 *
 * COMO ESTA ARMADO: cada seccion es una funcion que recibe el contenedor
 * y lo llena. 'ir(vista)' limpia y llama a la que corresponde. No hay
 * ruteo ni estado global compartido entre secciones: cada una se dibuja
 * entera con los datos del momento, que es lo mas simple de seguir y
 * evita que una pantalla quede desactualizada respecto de otra.
 *
 * LOS DATOS: productos y pedidos salen de store.js (que los cachea porque
 * tambien los usa la tienda). Movimientos, usuarios, solicitudes y
 * preguntas se piden directo a api.js: son exclusivos del panel y se
 * miran de a una seccion por vez, asi que cachearlos no aportaria.
 * ============================================================
 */

import {
  productos, pedidos, sesion, cargar, recargarProductos, recargarPedidos, ESTADOS_REALES
} from "./modules/store.js";
import { api } from "./modules/api.js";
import { LINEAS, VARIETALES } from "../data/productos.js";
import { pesos, fecha, fechaCorta, hora, placeholderVino, $, $$, crear, toast } from "./modules/util.js";

// ============================================================
//  ESTADO DE LA PANTALLA
// ============================================================

/** Usuarios con rol ADMIN: son los que pueden figurar como responsables. */
let operadores = [];
/** El que esta elegido arriba a la derecha. Firma los movimientos de stock. */
let idOperador = null;
/** Seccion visible, para poder volver a dibujarla despues de un cambio. */
let vistaActual = "tablero";

/**
 * Como se llama cada motivo de cancelacion en pantalla. En la base son
 * valores de un enum en mayusculas; aca se muestran como los leeria una
 * persona. 'null' significa que la pregunta sirve para cualquier motivo.
 */
const MOTIVOS = {
  ARREPENTIMIENTO:     "Me arrepentí de la compra",
  ERROR_AL_COMPRAR:    "Me equivoqué al comprar",
  PROBLEMA_PAGO:       "Problema con el pago",
  DEMORA_ENTREGA:      "La entrega se demora",
  PRODUCTO_EQUIVOCADO: "Me llegó algo distinto",
  OTRO:                "Otro"
};
const ESTADOS_SOLICITUD = {
  PENDIENTE:   "Pendiente",
  EN_REVISION: "En revisión",
  APROBADA:    "Aprobada",
  RECHAZADA:   "Rechazada"
};

// ============================================================
//  ARRANQUE
// ============================================================

/**
 * GUARDA DE ACCESO.
 *
 * El ingreso ya no se hace aca: se hace en cuenta.html contra
 * POST /api/usuarios/login, y el servidor es el que dice el rol. Esta
 * pagina solo comprueba que quien llego sea un ADMIN y, si no, lo manda
 * a ingresar.
 *
 * ES UNA GUARDA DE PANTALLA, NO DE SEGURIDAD: evita que alguien entre por
 * error o curioseando, pero los endpoints del back-end siguen abiertos a
 * quien llame a la API directamente. Cerrarlos es trabajo de Spring
 * Security, fuera del alcance de esta entrega.
 */
document.addEventListener("DOMContentLoaded", () => {
  if (!sesion.esAdmin()) {
    location.replace("cuenta.html");
    return;
  }
  entrarAlPanel();
});

async function entrarAlPanel() {
  $("#appAdmin").classList.remove("oculto");
  $("#btnSalir").addEventListener("click", () => { sesion.logout(); location.href = "index.html"; });
  $$(".nav-item").forEach(b => b.addEventListener("click", () => ir(b.dataset.vista)));

  const cont = $("#vistaContenedor");
  cont.innerHTML = '<div class="vacio"><p>Cargando datos del servidor...</p></div>';

  try {
    await cargar();                 // productos y pedidos
    await cargarOperadores();       // quienes pueden firmar movimientos
  } catch (e) {
    cont.innerHTML = "";
    cont.append(pantallaSinServidor(e.message));
    return;
  }
  ir("tablero");
}

/**
 * Llena el selector de operador con los usuarios ADMIN de la base.
 *
 * No es decorativo: el back-end exige un idUsuario para registrar un
 * ingreso de mercaderia y para resolver una solicitud, y lo guarda. Lo que
 * se elija aca es lo que despues figura como responsable en la auditoria.
 */
async function cargarOperadores() {
  const todos = await api.usuarios.listar();
  operadores = todos.filter(u => u.rol === "ADMIN");
  if (!operadores.length) operadores = todos.slice(0, 1);   // red de seguridad
  idOperador = operadores[0]?.idUsuario ?? null;

  const sel = $("#selectorOperador");
  sel.innerHTML = "";
  operadores.forEach(u => sel.append(
    crear("option", { value: u.idUsuario }, nombreDe(u))));
  sel.addEventListener("change", e => { idOperador = Number(e.target.value); });
}

const nombreDe = (u) => u ? [u.nombre, u.apellido].filter(Boolean).join(" ") : "—";
const operadorActual = () => operadores.find(u => u.idUsuario === idOperador) || operadores[0];

function pantallaSinServidor(mensaje) {
  return crear("div", { class: "vacio" },
    crear("div", { class: "vacio__icono", html: svg("enchufe") }),
    crear("h3", {}, "No se puede conectar con el servidor"),
    crear("p", {}, mensaje),
    crear("button", { class: "b-primario", style: "margin-top:18px",
      onclick: () => location.reload() }, "Reintentar"));
}

// ============================================================
//  NAVEGACION
// ============================================================

const VISTAS = {
  tablero:     vistaTablero,
  inventario:  vistaInventario,
  ingreso:     vistaIngreso,
  movimientos: vistaMovimientos,
  pedidos:     vistaPedidos,
  clientes:    vistaClientes,
  solicitudes: vistaSolicitudes,
  faq:         vistaFaq
};

async function ir(vista, datos) {
  vistaActual = vista;
  $$(".nav-item").forEach(b => b.classList.toggle("activo", b.dataset.vista === vista));
  const cont = $("#vistaContenedor");
  cont.innerHTML = "";
  try {
    await (VISTAS[vista] || vistaTablero)(cont, datos);
  } catch (e) {
    cont.innerHTML = "";
    cont.append(pantallaSinServidor(e.message));
  }
}

/** Vuelve a dibujar la seccion visible (despues de guardar algo). */
const redibujar = () => ir(vistaActual);

/**
 * Envuelve una escritura contra el servidor. Si el back-end la rechaza
 * muestra SU mensaje, que ya viene explicado y en castellano.
 */
async function guardar(accion, mensajeOk, refrescar = true) {
  try {
    await accion();
    if (mensajeOk) toast(mensajeOk);
    if (refrescar) await redibujar();
    return true;
  } catch (e) {
    toast(e.message, "error");
    return false;
  }
}

// ============================================================
//  PIEZAS REUTILIZABLES
// ============================================================

function encabezado(titulo, bajada, ...acciones) {
  return crear("header", { class: "encabezado" },
    crear("div", {},
      crear("h1", { class: "encabezado__titulo" }, titulo),
      bajada ? crear("p", { class: "encabezado__bajada" }, bajada) : ""),
    acciones.length ? crear("div", { style: "display:flex;gap:12px;flex-wrap:wrap" }, ...acciones) : "");
}

const tarjeta = (...hijos) => crear("section", { class: "tarjeta-panel" }, ...hijos);

function estadoVacio(titulo, texto, icono = "lupa") {
  return crear("div", { class: "vacio" },
    crear("div", { class: "vacio__icono", html: svg(icono) }),
    crear("h3", {}, titulo),
    texto ? crear("p", {}, texto) : "");
}

const marcaEstado = (texto, variante) =>
  crear("span", { class: "marca-estado marca-estado--" + variante }, texto);

/**
 * Tabla con cabeceras que ordenan al hacer clic.
 *
 * columnas: [{ clave, rotulo, orden, clase, render(fila) }]
 *   - 'orden' en true hace la cabecera clickeable
 *   - 'render' devuelve el contenido de la celda; si no esta, se usa el
 *     valor crudo de fila[clave]
 *
 * El orden se guarda en 'estado' ({clave, dir}), que vive en la vista que
 * llama. Asi cada seccion recuerda su propio criterio mientras esta
 * abierta, sin necesidad de un estado global.
 */
function tabla(columnas, filas, estado, alReordenar, mensajeVacio = "No hay nada para mostrar.") {
  const thead = crear("thead", {});
  const trCab = crear("tr", {});
  columnas.forEach(col => {
    let contenido = col.rotulo;
    if (col.orden) {
      const activa = estado?.clave === col.clave;
      contenido = crear("button", {
        class: "th-orden",
        "data-dir": activa ? estado.dir : null,
        onclick: () => {
          const dir = activa && estado.dir === "asc" ? "desc" : "asc";
          alReordenar({ clave: col.clave, dir });
        }
      }, col.rotulo);
    }
    trCab.append(crear("th", { class: col.clase || null }, contenido));
  });
  thead.append(trCab);

  const tbody = crear("tbody", {});
  if (!filas.length) {
    tbody.append(crear("tr", {},
      crear("td", { class: "celda-vacia", colspan: String(columnas.length) }, mensajeVacio)));
  } else {
    filas.forEach(f => {
      const tr = crear("tr", {});
      columnas.forEach(col =>
        tr.append(crear("td", { class: col.clase || null },
          col.render ? col.render(f) : String(f[col.clave] ?? ""))));
      tbody.append(tr);
    });
  }
  return crear("div", { class: "tabla-caja" }, crear("table", { class: "tabla" }, thead, tbody));
}

/** Ordena una copia de la lista segun {clave, dir}, con 'valor' por columna. */
function ordenar(lista, estado, valorDe) {
  if (!estado?.clave) return lista;
  const signo = estado.dir === "desc" ? -1 : 1;
  return [...lista].sort((a, b) => {
    const va = valorDe(a, estado.clave), vb = valorDe(b, estado.clave);
    if (va == null) return 1;
    if (vb == null) return -1;
    if (typeof va === "number" && typeof vb === "number") return (va - vb) * signo;
    return String(va).localeCompare(String(vb), "es", { numeric: true }) * signo;
  });
}

/** Miniatura de la botella, con el placeholder dibujado si no hay foto. */
function miniatura(p) {
  const img = crear("img", { src: p.imagen || "", alt: "", loading: "lazy" });
  img.addEventListener("error", () => { img.src = placeholderVino(p.nombre); });
  return crear("span", { class: "miniatura" }, img);
}

/** Iconos en SVG, escritos a mano para no depender de ninguna libreria. */
function svg(nombre) {
  const d = {
    mas:       '<path d="M12 5v14M5 12h14"/>',
    refrescar: '<path d="M21 12a9 9 0 11-3-6.7"/><path d="M21 3v6h-6"/>',
    candado:   '<rect x="4" y="10" width="16" height="10" rx="2"/><path d="M8 10V7a4 4 0 018 0v3"/>',
    lupa:      '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4.3-4.3"/>',
    lapiz:     '<path d="M4 20h4L19 9a2.1 2.1 0 00-3-3L5 17v3z"/>',
    tacho:     '<path d="M4 7h16M10 11v6M14 11v6"/><path d="M6 7l1 13h10l1-13"/><path d="M9 7V4h6v3"/>',
    enchufe:   '<path d="M9 2v6M15 2v6"/><path d="M6 8h12v3a6 6 0 01-12 0V8z"/><path d="M12 17v5"/>'
  }[nombre] || "";
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
          stroke-linecap="round" stroke-linejoin="round">${d}</svg>`;
}
const conIcono = (nombre, texto, clase) =>
  crear("button", { class: clase, html: svg(nombre) + "<span>" + texto + "</span>" });

// ============================================================
//  1) TABLERO
// ============================================================

async function vistaTablero(cont) {
  const vinos = productos.getAll().filter(p => p.activo);
  const listaPedidos = pedidos.getAll();
  const movimientos = await api.movimientos.listar();
  const solicitudes = await api.solicitudes.listar();

  const botellas   = vinos.reduce((s, p) => s + p.stock, 0);
  // Valorizacion = cuanto vale el inventario a precio de lista.
  const valorizado = vinos.reduce((s, p) => s + p.stock * p.precio, 0);
  const aReponer   = vinos.filter(p => p.stock <= p.stockMinimo);
  const abiertas   = solicitudes.filter(s => s.estado === "PENDIENTE" || s.estado === "EN_REVISION");

  const hoy = new Date();
  const delMes = listaPedidos.filter(p => {
    const f = new Date(p.fecha);
    return f.getMonth() === hoy.getMonth() && f.getFullYear() === hoy.getFullYear();
  });
  const nombreMes = hoy.toLocaleDateString("es-AR", { month: "long", year: "numeric" });

  const btnActualizar = conIcono("refrescar", "Actualizar", "b-suave");
  btnActualizar.addEventListener("click", async () => {
    await Promise.all([recargarProductos(), recargarPedidos()]);
    toast("Datos actualizados");
    redibujar();
  });

  cont.append(encabezado("Tablero",
    "Actualizado " + hora(new Date().toISOString()), btnActualizar));

  cont.append(crear("div", { class: "indicadores" },
    indicador("Vinos en catálogo", vinos.length),
    indicador("Botellas en stock", botellas.toLocaleString("es-AR")),
    indicador("Valorización", pesos(valorizado), "stock × precio de lista", null, true),
    indicador("A reponer", aReponer.length, null,
              { texto: "Ver inventario", vista: "inventario" }, false, aReponer.length > 0),
    indicador("Pedidos del mes", delMes.length, nombreMes),
    indicador("Solicitudes sin responder", abiertas.length, null,
              { texto: "Ir a la bandeja", vista: "solicitudes" }, false, abiertas.length > 0)
  ));

  // ---- los dos paneles de abajo ----
  const panelAlertas = tarjeta(
    crear("div", { class: "panel-titulo" },
      crear("h2", {}, "Alertas de stock mínimo"),
      crear("a", { href: "#", onclick: (e) => { e.preventDefault(); ir("ingreso"); } },
            "Ingresar mercadería →")),
    tabla(
      [{ clave: "nombre", rotulo: "Vino" },
       { clave: "stock",  rotulo: "Stock",  clase: "num" },
       { clave: "stockMinimo", rotulo: "Mínimo", clase: "num" }],
      aReponer, null, null,
      "Ningún vino está bajo su stock mínimo."));

  const panelMovs = tarjeta(
    crear("div", { class: "panel-titulo" },
      crear("h2", {}, "Últimos movimientos"),
      crear("a", { href: "#", onclick: (e) => { e.preventDefault(); ir("movimientos"); } },
            "Ver todos →")),
    tabla(
      [{ clave: "fecha", rotulo: "Fecha", render: m => fecha(m.fecha) },
       { clave: "vino",  rotulo: "Vino",  render: m => m.producto?.nombre || "—" },
       { clave: "tipo",  rotulo: "Tipo",  render: m => etiquetaTipo(m.tipo) },
       { clave: "cantidad", rotulo: "Cant.", clase: "num",
         render: m => crear("span", { class: "tipo-mov tipo-mov--" + m.tipo.toLowerCase() },
                            (m.cantidad > 0 ? "+" : "") + m.cantidad) }],
      movimientos.slice(0, 8), null, null,
      "Todavía no hay movimientos."));

  cont.append(crear("div", { class: "doble-columna" }, panelAlertas, panelMovs));
}

function indicador(rotulo, valor, pie, enlace, chico, alerta) {
  const caja = crear("div", { class: "indicador" + (alerta ? " indicador--alerta" : "") },
    crear("div", { class: "indicador__rotulo" }, rotulo),
    crear("div", { class: "indicador__valor" + (chico ? " indicador__valor--chico" : "") }, String(valor)));
  if (pie) caja.append(crear("div", { class: "indicador__pie" }, pie));
  if (enlace) caja.append(crear("div", { class: "indicador__pie" },
    crear("a", { href: "#", onclick: (e) => { e.preventDefault(); ir(enlace.vista); } }, enlace.texto)));
  return caja;
}

const etiquetaTipo = (tipo) => crear("span",
  { class: "tipo-mov tipo-mov--" + tipo.toLowerCase() },
  tipo.charAt(0) + tipo.slice(1).toLowerCase());

// ============================================================
//  2) INVENTARIO
// ============================================================

let ordenInventario = { clave: "nombre", dir: "asc" };

function vistaInventario(cont) {
  const todos   = productos.getAll();
  const activos = todos.filter(p => p.activo);
  const bajos   = activos.filter(p => p.stock <= p.stockMinimo);

  const btnNuevo = conIcono("mas", "Nuevo vino", "b-primario");
  btnNuevo.addEventListener("click", () => abrirFormVino());

  cont.append(encabezado("Inventario",
    `${activos.length} vinos activos · ${bajos.length} bajo mínimo.`, btnNuevo));

  const lista = ordenar(todos, ordenInventario, (p, c) => p[c]);

  cont.append(crear("section", { class: "tarjeta-panel tarjeta-panel--pegada" },
    tabla([
      { clave: "nombre", rotulo: "Vino", orden: true,
        render: p => crear("span", { class: "celda-vino" }, miniatura(p),
          crear("span", {},
            crear("span", { class: "fuerte" }, p.nombre),
            p.activo ? "" : crear("span", { class: "tenue", style: "display:block;font-size:13px" },
                                  "dado de baja"))) },
      { clave: "linea",    rotulo: "Línea",    orden: true },
      { clave: "varietal", rotulo: "Varietal", orden: true },
      { clave: "cosecha",  rotulo: "Añada",    orden: true },
      { clave: "precio",   rotulo: "Precio",   orden: true, render: p => pesos(p.precio) },
      { clave: "stock",    rotulo: "Stock",    orden: true, clase: "num",
        render: p => crear("span", { class: "fuerte" }, String(p.stock)) },
      { clave: "acciones", rotulo: "", clase: "angosta", render: p => menuVino(p) }
    ], lista, ordenInventario,
       (nuevo) => { ordenInventario = nuevo; redibujar(); },
       "Todavía no hay vinos cargados.")));
}

/** Menu de los tres puntos de cada fila del inventario. */
function menuVino(p) {
  const caja = crear("span", { class: "acciones" });
  const boton = crear("button", { class: "acciones__boton", "aria-label": "Acciones de " + p.nombre }, "⋮");

  boton.addEventListener("click", (e) => {
    e.stopPropagation();
    const yaAbierto = caja.querySelector(".acciones__menu");
    cerrarMenus();
    if (yaAbierto) return;

    const menu = crear("div", { class: "acciones__menu" },
      crear("button", { onclick: () => { cerrarMenus(); abrirFormVino(p.id); } }, "Editar vino"),
      crear("button", { onclick: () => { cerrarMenus(); ir("ingreso", { idVino: p.id }); } },
            "Ingresar stock"),
      crear("button", { onclick: () => { cerrarMenus(); ir("movimientos", { idVino: p.id }); } },
            "Ver movimientos"),
      p.activo
        ? crear("button", { class: "peligro", onclick: () => { cerrarMenus(); darDeBaja(p); } },
                "Dar de baja")
        : crear("button", { onclick: () => { cerrarMenus();
            guardar(() => productos.reactivar(p.id), "Vino reactivado"); } }, "Reactivar")
    );
    caja.append(menu);
  });

  caja.append(boton);
  return caja;
}

function cerrarMenus() { $$(".acciones__menu").forEach(m => m.remove()); }
document.addEventListener("click", cerrarMenus);

function darDeBaja(p) {
  if (!confirm(`Dar de baja "${p.nombre}"?\n\nEs baja logica: deja de verse en la tienda ` +
               `pero se conserva su historial de ventas y movimientos.`)) return;
  guardar(() => productos.remove(p.id), "Vino dado de baja");
}

/**
 * Alta y edicion de un vino.
 *
 * La foto va por URL y no como archivo subido: la columna imagen_url
 * admite 300 caracteres y una imagen en base64 ocupa miles, asi que el
 * servidor la rechazaria. Las fotos del catalogo original ya estan
 * descargadas y api.js las encuentra por el nombre del vino.
 */
function abrirFormVino(id = null) {
  const p = id ? productos.getById(id) : {
    nombre: "", linea: LINEAS[0], varietal: VARIETALES[0],
    cosecha: new Date().getFullYear(), precio: 0, stock: 0, stockMinimo: 5,
    notasCata: "", imagenUrlOrigen: "", activo: true
  };

  const form = crear("form", { class: "form-panel", novalidate: "true" },
    crear("h2", { style: "font-size:22px;margin-bottom:20px;color:var(--vino)" },
          id ? "Editar vino" : "Nuevo vino"),
    campo("nombre", "Nombre", "text", p.nombre),
    crear("div", { class: "form-fila-2" },
      campoSelect("linea", "Línea", LINEAS, p.linea),
      campoSelect("varietal", "Varietal", VARIETALES, p.varietal)),
    crear("div", { class: "form-fila-2" },
      campo("cosecha", "Añada", "number", p.cosecha),
      campo("precio", "Precio", "number", p.precio)),
    crear("div", { class: "form-fila-2" },
      campo("stock", "Stock actual", "number", p.stock),
      campo("stockMinimo", "Stock mínimo", "number", p.stockMinimo)),
    crear("div", { class: "campo campo--ancho", style: "margin-bottom:16px" },
      crear("label", { for: "notasCata" }, "Notas de cata"),
      crear("textarea", { id: "notasCata", rows: "4" }, p.notasCata)),
    campo("imagenUrlOrigen", "URL de la foto", "url", p.imagenUrlOrigen),
    crear("div", { class: "form-acciones" },
      crear("button", { type: "submit", class: "b-primario" },
            id ? "Guardar cambios" : "Crear vino"),
      crear("button", { type: "button", class: "b-suave", onclick: cerrarModal }, "Cancelar"))
  );

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const datos = {
      nombre:      $("#nombre", form).value.trim(),
      linea:       $("#linea", form).value,
      varietal:    $("#varietal", form).value,
      cosecha:     Number($("#cosecha", form).value),
      precio:      Number($("#precio", form).value),
      stock:       Number($("#stock", form).value),
      stockMinimo: Number($("#stockMinimo", form).value),
      notasCata:   $("#notasCata", form).value.trim(),
      imagenUrlOrigen: $("#imagenUrlOrigen", form).value.trim(),
      activo:      p.activo !== false
    };
    if (datos.nombre.length < 3) { toast("El nombre es obligatorio", "error"); return; }

    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Guardando...";

    const ok = await guardar(
      () => id ? productos.update(id, datos) : productos.create(datos),
      id ? "Vino actualizado" : "Vino creado", false);

    if (ok) { cerrarModal(); redibujar(); }
    else { boton.disabled = false; boton.textContent = id ? "Guardar cambios" : "Crear vino"; }
  });

  abrirModal(form);
}

// ============================================================
//  3) INGRESO DE MERCADERIA
// ============================================================

/**
 * Suma botellas al stock de un vino.
 *
 * POR QUE NO SE EDITA EL STOCK A MANO: un PUT del campo stock cambiaria
 * el numero sin dejar rastro. Este endpoint sube el stock Y registra el
 * movimiento de ENTRADA con su responsable y su motivo, en una sola
 * operacion del servidor. Esa es la diferencia entre un inventario que se
 * puede auditar y una planilla.
 */
function vistaIngreso(cont, datos) {
  const vinos = productos.getAll().filter(p => p.activo)
                         .sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));

  cont.append(encabezado("Ingreso de mercadería",
    "Suma botellas al stock de un vino y deja registrado el movimiento de ENTRADA."));

  const selVino = crear("select", { id: "vino" },
    crear("option", { value: "" }, "Elegí un vino"),
    ...vinos.map(v => crear("option", { value: v.id }, v.nombre)));

  const valorActual = crear("div", { class: "previo__valor" }, "—");
  const valorNuevo  = crear("div", { class: "previo__valor previo__valor--nuevo" }, "—");
  const inpCantidad = crear("input", { id: "cantidad", type: "number", min: "1", placeholder: "24" });

  /** Muestra en cuanto quedaria el stock antes de confirmar. */
  function recalcular() {
    const v = productos.getById(Number(selVino.value));
    const n = Number(inpCantidad.value);
    valorActual.textContent = v ? String(v.stock) : "—";
    valorNuevo.textContent  = (v && n > 0) ? String(v.stock + n) : "—";
  }
  selVino.addEventListener("change", recalcular);
  inpCantidad.addEventListener("input", recalcular);

  const selResponsable = crear("select", { id: "responsable" },
    ...operadores.map(u => crear("option",
      { value: u.idUsuario, selected: u.idUsuario === idOperador ? "true" : null }, nombreDe(u))));

  const form = crear("form", { class: "form-panel", novalidate: "true" },
    crear("div", { class: "campo campo--ancho" },
      crear("label", { for: "vino" }, "Vino"), selVino),

    crear("div", { class: "previo" },
      crear("div", {}, crear("div", { class: "previo__rotulo" }, "Stock actual"), valorActual),
      crear("div", { class: "previo__flecha" }, "→"),
      crear("div", {}, crear("div", { class: "previo__rotulo" }, "Quedaría en"), valorNuevo)),

    crear("div", { class: "form-fila-2" },
      crear("div", { class: "campo campo--ancho" },
        crear("label", { for: "cantidad" }, "Cantidad de botellas"), inpCantidad),
      crear("div", { class: "campo campo--ancho" },
        crear("label", { for: "responsable" }, "Responsable"), selResponsable)),

    crear("div", { class: "campo campo--ancho", style: "margin-top:4px" },
      crear("label", { for: "motivo" }, "Motivo"),
      crear("input", { id: "motivo", type: "text", placeholder: "Reposición desde bodega" })),

    crear("div", { class: "form-acciones" },
      crear("button", { type: "submit", class: "b-primario" }, "Registrar ingreso"),
      crear("button", { type: "button", class: "b-suave",
                        onclick: () => ir("inventario") }, "Volver al inventario"))
  );

  // Si se llego desde el menu de un vino, viene ya elegido.
  if (datos?.idVino) { selVino.value = String(datos.idVino); recalcular(); }

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const idVino   = Number(selVino.value);
    const cantidad = Number(inpCantidad.value);
    if (!idVino)       { toast("Elegí un vino", "error"); return; }
    if (!(cantidad > 0)) { toast("La cantidad tiene que ser mayor que cero", "error"); return; }

    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Registrando...";

    try {
      await api.productos.ingreso(idVino, cantidad, Number(selResponsable.value),
                                  $("#motivo", form).value.trim());
      await recargarProductos();
      toast(`Ingresaron ${cantidad} botellas`);
      ir("inventario");
    } catch (err) {
      toast(err.message, "error");
      boton.disabled = false; boton.textContent = "Registrar ingreso";
    }
  });

  cont.append(crear("section", { class: "tarjeta-panel", style: "max-width:920px" }, form));
}

// ============================================================
//  4) MOVIMIENTOS DE STOCK
// ============================================================

async function vistaMovimientos(cont, datos) {
  const movimientos = await api.movimientos.listar();
  const vinos = productos.getAll().sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));

  let filtro = datos?.idVino ? String(datos.idVino) : "";

  const sello = crear("span", { class: "sello-lectura", html: svg("candado") + "<span>Solo lectura</span>" });
  cont.append(encabezado("Movimientos de stock",
    "Registro de auditoría de solo lectura. Cada entrada, salida y ajuste queda acá para siempre.",
    sello));

  const caja = crear("section", { class: "tarjeta-panel" });
  cont.append(caja);

  function pintar() {
    caja.innerHTML = "";
    const lista = filtro
      ? movimientos.filter(m => String(m.producto?.idProducto) === filtro)
      : movimientos;

    const sel = crear("select", { id: "fVino" },
      crear("option", { value: "" }, "Todos los vinos"),
      ...vinos.map(v => crear("option",
        { value: v.id, selected: String(v.id) === filtro ? "true" : null }, v.nombre)));
    sel.addEventListener("change", e => { filtro = e.target.value; pintar(); });

    caja.append(crear("div", { class: "filtros" },
      crear("div", { class: "campo" }, crear("label", { for: "fVino" }, "Filtrar por vino"), sel),
      crear("span", { class: "filtros__cuenta" },
        lista.length + (lista.length === 1 ? " movimiento" : " movimientos"))));

    caja.append(tabla([
      { clave: "fecha",  rotulo: "Fecha", render: m => fecha(m.fecha) },
      { clave: "vino",   rotulo: "Vino",
        render: m => crear("span", { class: "fuerte" }, m.producto?.nombre || "—") },
      { clave: "tipo",   rotulo: "Tipo", render: m => etiquetaTipo(m.tipo) },
      { clave: "cantidad", rotulo: "Cantidad", clase: "num",
        render: m => crear("span", { class: "tipo-mov tipo-mov--" + m.tipo.toLowerCase() },
                           (m.cantidad > 0 ? "+" : "") + m.cantidad) },
      { clave: "motivo", rotulo: "Motivo", render: m => m.motivo || "—" },
      { clave: "quien",  rotulo: "Responsable", render: m => nombreDe(m.usuario) }
    ], lista, null, null, "No hay movimientos para ese vino."));
  }
  pintar();
}

// ============================================================
//  5) PEDIDOS
// ============================================================

let ordenPedidos = { clave: "id", dir: "desc" };

function vistaPedidos(cont) {
  const todos = pedidos.getAll();
  let filtro = "";

  cont.append(encabezado("Pedidos",
    "Todos los pedidos de la tienda. Cambiá el estado desde el detalle."));

  const caja = crear("section", { class: "tarjeta-panel" });
  cont.append(caja);

  function pintar() {
    caja.innerHTML = "";
    const lista = ordenar(
      filtro ? todos.filter(p => p.estado === filtro) : todos,
      ordenPedidos,
      (p, c) => c === "cliente" ? p.comprador.nombre
              : c === "fecha"   ? new Date(p.fecha).getTime()
              : p[c]);

    const sel = crear("select", { id: "fEstado" },
      crear("option", { value: "" }, "Todos"),
      ...ESTADOS_REALES.map(e => crear("option",
        { value: e, selected: e === filtro ? "true" : null }, capitalizar(e))));
    sel.addEventListener("change", e => { filtro = e.target.value; pintar(); });

    caja.append(crear("div", { class: "filtros" },
      crear("div", { class: "campo" }, crear("label", { for: "fEstado" }, "Estado"), sel),
      crear("span", { class: "filtros__cuenta" },
        lista.length + (lista.length === 1 ? " pedido" : " pedidos"))));

    caja.append(tabla([
      { clave: "id", rotulo: "N.º", orden: true,
        render: p => crear("span", { class: "fuerte" }, "#" + p.id) },
      { clave: "fecha",   rotulo: "Fecha",   orden: true, render: p => fecha(p.fecha) },
      { clave: "cliente", rotulo: "Cliente", orden: true, render: p => p.comprador.nombre || "—" },
      { clave: "medioPago", rotulo: "Pago", render: p => capitalizar(p.medioPago || "") },
      { clave: "total",  rotulo: "Total", orden: true, clase: "num",
        render: p => crear("span", { class: "fuerte" }, pesos(p.total)) },
      { clave: "estado", rotulo: "Estado", orden: true,
        render: p => marcaEstado(capitalizar(p.estado), p.estado) },
      { clave: "ver", rotulo: "", clase: "angosta",
        render: p => crear("button", { class: "b-chico",
          onclick: () => abrirDetallePedido(p) }, "Ver detalle") }
    ], lista, ordenPedidos,
       (nuevo) => { ordenPedidos = nuevo; pintar(); },
       "No hay pedidos con ese estado."));
  }
  pintar();
}

function abrirDetallePedido(p) {
  const filas = p.items.map(i => crear("tr", {},
    crear("td", {}, i.nombre),
    crear("td", { class: "num" }, String(i.cantidad)),
    crear("td", { class: "num" }, pesos(i.precio)),
    crear("td", { class: "num fuerte" }, pesos(i.precio * i.cantidad))));

  const selEstado = crear("select", { id: "nuevoEstado" },
    ...ESTADOS_REALES.map(e => crear("option",
      { value: e, selected: e === p.estado ? "true" : null }, capitalizar(e))));

  const panel = crear("div", { class: "form-panel" },
    crear("h2", { style: "font-size:22px;color:var(--vino)" }, "Pedido #" + p.id),
    crear("p", { class: "encabezado__bajada", style: "margin-bottom:20px" },
          fecha(p.fecha) + " · " + capitalizar(p.medioPago || "")),

    dato("Cliente", p.comprador.nombre),
    dato("Email", p.comprador.email || "—"),
    dato("Teléfono", p.comprador.telefono || "—"),

    crear("div", { class: "tabla-caja", style: "margin:18px 0" },
      crear("table", { class: "tabla" },
        crear("thead", {}, crear("tr", {},
          crear("th", {}, "Vino"), crear("th", { class: "num" }, "Cant."),
          crear("th", { class: "num" }, "Precio"), crear("th", { class: "num" }, "Subtotal"))),
        crear("tbody", {}, ...filas,
          crear("tr", {}, crear("td", { colspan: "3", class: "num fuerte" }, "Total"),
                          crear("td", { class: "num fuerte" }, pesos(p.total)))))),

    crear("div", { class: "campo campo--ancho" },
      crear("label", { for: "nuevoEstado" }, "Estado del pedido"), selEstado),
    crear("p", { class: "encabezado__bajada", style: "margin-top:10px;font-size:14px" },
      "Pasarlo a Cancelado devuelve las botellas al stock y queda registrado como un ajuste."),

    crear("div", { class: "form-acciones" },
      crear("button", { class: "b-primario", onclick: async () => {
        if (selEstado.value === p.estado) { cerrarModal(); return; }
        const ok = await guardar(() => pedidos.cambiarEstado(p.id, selEstado.value),
                                 "Estado actualizado", false);
        if (ok) { cerrarModal(); redibujar(); }
      } }, "Guardar estado"),
      crear("button", { class: "b-suave", onclick: cerrarModal }, "Cerrar"))
  );
  abrirModal(panel);
}

const dato = (rotulo, valor) => crear("div", { class: "detalle-dato" },
  crear("div", { class: "detalle-dato__rotulo" }, rotulo),
  crear("div", { class: "detalle-dato__valor" }, valor));

const capitalizar = (s) => s ? s.charAt(0).toUpperCase() + s.slice(1).toLowerCase() : "";

// ============================================================
//  6) CLIENTES Y USUARIOS
// ============================================================

let ordenClientes = { clave: "nombre", dir: "asc" };

async function vistaClientes(cont) {
  const usuarios = await api.usuarios.listar();
  let filtro = "";

  const btnNuevo = conIcono("mas", "Nuevo usuario", "b-primario");
  btnNuevo.addEventListener("click", () => abrirFormUsuario());

  cont.append(encabezado("Clientes y usuarios",
    "Altas, modificaciones y bajas lógicas. Las contraseñas nunca se muestran.", btnNuevo));

  const caja = crear("section", { class: "tarjeta-panel" });
  cont.append(caja);

  function pintar() {
    caja.innerHTML = "";
    const lista = ordenar(
      filtro ? usuarios.filter(u => u.rol === filtro) : usuarios,
      ordenClientes,
      (u, c) => c === "nombre" ? nombreDe(u)
              : c === "alta"   ? new Date(u.fechaAlta).getTime()
              : u[c]);

    const sel = crear("select", { id: "fRol" },
      crear("option", { value: "" }, "Todos"),
      crear("option", { value: "ADMIN",   selected: filtro === "ADMIN"   ? "true" : null }, "Admin"),
      crear("option", { value: "CLIENTE", selected: filtro === "CLIENTE" ? "true" : null }, "Cliente"));
    sel.addEventListener("change", e => { filtro = e.target.value; pintar(); });

    caja.append(crear("div", { class: "filtros" },
      crear("div", { class: "campo" }, crear("label", { for: "fRol" }, "Rol"), sel),
      crear("span", { class: "filtros__cuenta" },
        lista.length + (lista.length === 1 ? " usuario" : " usuarios"))));

    caja.append(tabla([
      { clave: "nombre", rotulo: "Nombre", orden: true,
        render: u => crear("span", {},
          crear("span", { class: "fuerte" }, nombreDe(u)),
          crear("span", { class: "tenue", style: "display:block;font-size:13.5px" }, "#" + u.idUsuario)) },
      { clave: "email",    rotulo: "Email", orden: true },
      { clave: "telefono", rotulo: "Teléfono", render: u => u.telefono || "—" },
      { clave: "rol", rotulo: "Rol", orden: true,
        render: u => marcaEstado(capitalizar(u.rol), u.rol.toLowerCase()) },
      { clave: "alta", rotulo: "Alta", orden: true, render: u => fechaCorta(u.fechaAlta) },
      { clave: "acciones", rotulo: "", clase: "angosta", render: u => {
        const editar = conIcono("lapiz", "Editar", "b-chico");
        editar.addEventListener("click", () => abrirFormUsuario(u));
        const baja = conIcono("tacho", "Baja", "b-chico b-chico--peligro");
        baja.addEventListener("click", () => bajaUsuario(u));
        return crear("span", { style: "display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end" },
                     editar, baja);
      } }
    ], lista, ordenClientes,
       (nuevo) => { ordenClientes = nuevo; pintar(); },
       "No hay usuarios con ese rol."));
  }
  pintar();
}

function bajaUsuario(u) {
  if (!confirm(`Dar de baja a ${nombreDe(u)}?\n\n` +
               `Es baja logica: sus pedidos y movimientos se conservan.`)) return;
  // El back-end rechaza dejar la bodega sin ningun ADMIN: si pasa, se
  // muestra su mensaje tal cual, que explica el motivo.
  guardar(() => api.usuarios.eliminar(u.idUsuario), "Usuario dado de baja");
}

/**
 * Alta y edicion de usuario.
 *
 * SOBRE LA CONTRASENA: en el alta es obligatoria (lo exige
 * UsuarioService.registrar) y en la edicion no se pide, porque el
 * servidor conserva la que ya estaba si no se manda. El campo nunca
 * muestra la actual: la API no la devuelve, y no deberia.
 */
function abrirFormUsuario(u = null) {
  const esNuevo = !u;
  const form = crear("form", { class: "form-panel", novalidate: "true" },
    crear("h2", { style: "font-size:22px;margin-bottom:20px;color:var(--vino)" },
          esNuevo ? "Nuevo usuario" : "Editar usuario"),
    crear("div", { class: "form-fila-2" },
      campo("nombre", "Nombre", "text", u?.nombre || ""),
      campo("apellido", "Apellido", "text", u?.apellido || "")),
    campo("email", "Email", "email", u?.email || ""),
    crear("div", { class: "form-fila-2" },
      campo("telefono", "Teléfono", "text", u?.telefono || ""),
      campoSelect("rol", "Rol", ["CLIENTE", "ADMIN"], u?.rol || "CLIENTE",
                  v => capitalizar(v))),
    esNuevo
      ? campo("passwordHash", "Contraseña", "password", "")
      : crear("p", { class: "encabezado__bajada", style: "font-size:14px" },
              "La contraseña no se modifica desde acá: se conserva la que ya tenía."),
    crear("div", { class: "form-acciones" },
      crear("button", { type: "submit", class: "b-primario" },
            esNuevo ? "Crear usuario" : "Guardar cambios"),
      crear("button", { type: "button", class: "b-suave", onclick: cerrarModal }, "Cancelar"))
  );

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const datos = {
      nombre:   $("#nombre", form).value.trim(),
      apellido: $("#apellido", form).value.trim(),
      email:    $("#email", form).value.trim(),
      telefono: $("#telefono", form).value.trim(),
      rol:      $("#rol", form).value
    };
    if (esNuevo) datos.passwordHash = $("#passwordHash", form).value;

    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Guardando...";

    const ok = await guardar(
      () => esNuevo ? api.usuarios.crear(datos) : api.usuarios.actualizar(u.idUsuario, datos),
      esNuevo ? "Usuario creado" : "Usuario actualizado", false);

    if (ok) { cerrarModal(); redibujar(); }
    else { boton.disabled = false; boton.textContent = esNuevo ? "Crear usuario" : "Guardar cambios"; }
  });

  abrirModal(form);
}

// ============================================================
//  7) SOLICITUDES DE CANCELACION
// ============================================================

async function vistaSolicitudes(cont) {
  const todas = await api.solicitudes.listar();
  const abiertas = todas.filter(s => s.estado === "PENDIENTE" || s.estado === "EN_REVISION");

  let verTodas = false;
  let elegida = null;

  cont.append(encabezado("Solicitudes de cancelación",
    `${abiertas.length} sin responder de ${todas.length} en total.`));

  const grilla = crear("div", { class: "bandeja-grilla" });
  const colBandeja = crear("section", { class: "tarjeta-panel" });
  const colDetalle = crear("section", { class: "tarjeta-panel" });
  grilla.append(colBandeja, colDetalle);
  cont.append(grilla);

  function pintarBandeja() {
    colBandeja.innerHTML = "";
    const lista = verTodas ? todas : abiertas;

    const sel = crear("select", { id: "fVer", style: "min-width:150px" },
      crear("option", { value: "abiertas", selected: verTodas ? null : "true" }, "Abiertas"),
      crear("option", { value: "todas",    selected: verTodas ? "true" : null }, "Todas"));
    sel.addEventListener("change", e => {
      verTodas = e.target.value === "todas"; elegida = null; pintarBandeja(); pintarDetalle();
    });

    colBandeja.append(crear("div", { class: "panel-titulo" },
      crear("h2", {}, "Bandeja"),
      crear("label", { style: "display:flex;align-items:center;gap:10px;font-size:15px" },
            "Ver", sel)));

    if (!lista.length) {
      colBandeja.append(estadoVacio(
        verTodas ? "No hay solicitudes" : "No hay solicitudes abiertas",
        verTodas ? "Nadie pidió cancelar todavía." : "Todo respondido."));
      return;
    }

    const caja = crear("div", { class: "bandeja__lista" });
    lista.forEach(s => {
      const item = crear("button", {
        class: "bandeja__item" + (elegida?.idSolicitud === s.idSolicitud ? " activo" : ""),
        onclick: () => { elegida = s; pintarBandeja(); pintarDetalle(); }
      },
        crear("div", { class: "bandeja__fila" },
          crear("span", { class: "bandeja__pedido" }, "Pedido #" + s.pedido?.idPedido),
          marcaEstado(ESTADOS_SOLICITUD[s.estado] || s.estado, claseSolicitud(s.estado))),
        crear("div", { class: "bandeja__quien" }, nombreDe(s.usuario)),
        crear("div", { class: "bandeja__fecha" },
              (MOTIVOS[s.motivo] || s.motivo) + " · " + fechaCorta(s.fechaSolicitud)));
      caja.append(item);
    });
    colBandeja.append(caja);
  }

  function pintarDetalle() {
    colDetalle.innerHTML = "";
    if (!elegida) {
      colDetalle.append(crear("p", {
        style: "text-align:center;color:var(--texto-tenue);padding:70px 20px;font-size:16px" },
        "Elegí una solicitud de la bandeja para ver el reclamo."));
      return;
    }
    const s = elegida;
    const abierta = s.estado === "PENDIENTE" || s.estado === "EN_REVISION";

    colDetalle.append(crear("div", { class: "panel-titulo" },
      crear("h2", {}, "Pedido #" + s.pedido?.idPedido),
      marcaEstado(ESTADOS_SOLICITUD[s.estado] || s.estado, claseSolicitud(s.estado))));

    colDetalle.append(
      dato("Cliente", nombreDe(s.usuario) + (s.usuario?.email ? " · " + s.usuario.email : "")),
      dato("Motivo", MOTIVOS[s.motivo] || s.motivo),
      dato("Fecha del reclamo", fecha(s.fechaSolicitud)),
      dato("Total del pedido", pesos(s.pedido?.total ?? 0)));

    if (s.comentario) {
      colDetalle.append(crear("div", { class: "detalle-dato" },
        crear("div", { class: "detalle-dato__rotulo" }, "Lo que escribió el cliente"),
        crear("div", { class: "cita" }, s.comentario)));
    }

    if (!abierta) {
      colDetalle.append(crear("div", { class: "detalle-dato" },
        crear("div", { class: "detalle-dato__rotulo" }, "Respuesta de la bodega"),
        crear("div", { class: "cita" }, s.respuesta || "(sin texto)")));
      colDetalle.append(crear("p", { class: "encabezado__bajada", style: "font-size:14px" },
        "Resuelta el " + fecha(s.fechaResolucion) +
        (s.resueltaAutomaticamente ? " de forma automática (dentro de las 24 horas)."
                                   : " por " + nombreDe(s.resueltoPor) + ".")));
      return;
    }

    // ---- responder ----
    const texto = crear("textarea", { id: "respuesta", rows: "4",
      placeholder: "Lo que va a leer el cliente..." });

    colDetalle.append(
      crear("div", { class: "campo campo--ancho", style: "margin-top:6px" },
        crear("label", { for: "respuesta" }, "Respuesta para el cliente"), texto),
      crear("p", { class: "encabezado__bajada", style: "font-size:14px;margin-top:10px" },
        "Aprobar cancela el pedido y devuelve las botellas al stock. Rechazar lo deja como está."),
      crear("div", { class: "form-acciones" },
        crear("button", { class: "b-primario", onclick: () => resolver(true) }, "Aprobar cancelación"),
        crear("button", { class: "b-suave", onclick: () => resolver(false) }, "Rechazar")));

    async function resolver(aprobar) {
      const respuesta = texto.value.trim();
      if (respuesta.length < 5) { toast("Escribí una respuesta para el cliente", "error"); return; }
      try {
        await api.solicitudes.resolver(s.idSolicitud, aprobar, idOperador, respuesta);
        // Aprobar cancela el pedido y devuelve stock: hay que refrescar.
        await Promise.all([recargarProductos(), recargarPedidos()]);
        toast(aprobar ? "Cancelación aprobada" : "Solicitud rechazada");
        redibujar();
      } catch (e) { toast(e.message, "error"); }
    }
  }

  pintarBandeja();
  pintarDetalle();
}

const claseSolicitud = (estado) => ({
  PENDIENTE: "pendiente", EN_REVISION: "revision",
  APROBADA: "aprobada", RECHAZADA: "rechazada"
}[estado] || "pendiente");

// ============================================================
//  8) PREGUNTAS FRECUENTES
// ============================================================

let ordenFaq = { clave: "motivo", dir: "asc" };

async function vistaFaq(cont) {
  const preguntas = await api.faq.listar();
  let filtro = "";

  const btnNueva = conIcono("mas", "Nueva pregunta", "b-primario");
  btnNueva.addEventListener("click", () => abrirFormPregunta());

  cont.append(encabezado("Preguntas frecuentes",
    "Se muestran al cliente cuando elige un motivo de cancelación. " +
    "Las generales aparecen con todos los motivos.", btnNueva));

  const caja = crear("section", { class: "tarjeta-panel" });
  cont.append(caja);

  function pintar() {
    caja.innerHTML = "";
    const lista = ordenar(
      filtro ? preguntas.filter(p => (p.motivo || "") === (filtro === "GENERAL" ? "" : filtro))
             : preguntas,
      ordenFaq,
      (p, c) => c === "motivo" ? (p.motivo ? MOTIVOS[p.motivo] : "General") : p[c]);

    const sel = crear("select", { id: "fMotivo" },
      crear("option", { value: "" }, "Todos"),
      crear("option", { value: "GENERAL", selected: filtro === "GENERAL" ? "true" : null }, "General"),
      ...Object.entries(MOTIVOS).map(([k, v]) =>
        crear("option", { value: k, selected: filtro === k ? "true" : null }, v)));
    sel.addEventListener("change", e => { filtro = e.target.value; pintar(); });

    caja.append(crear("div", { class: "filtros" },
      crear("div", { class: "campo" }, crear("label", { for: "fMotivo" }, "Motivo"), sel),
      crear("span", { class: "filtros__cuenta" },
        lista.length + (lista.length === 1 ? " pregunta" : " preguntas"))));

    caja.append(tabla([
      { clave: "motivo", rotulo: "Motivo", orden: true,
        render: p => marcaEstado(p.motivo ? MOTIVOS[p.motivo] : "General",
                                 p.motivo ? "cliente" : "general") },
      { clave: "orden",    rotulo: "Orden", orden: true, clase: "num" },
      { clave: "pregunta", rotulo: "Pregunta", orden: true,
        render: p => crear("span", {},
          crear("span", { class: "fuerte" }, p.pregunta),
          // Una pregunta quitada no se borra de la base: deja de mostrarse
          // al cliente. Se marca para poder distinguirla y reactivarla.
          p.activa === false
            ? crear("span", { class: "tenue", style: "display:block;font-size:13px" },
                    "quitada — no la ve el cliente")
            : "") },
      { clave: "respuesta", rotulo: "Respuesta",
        render: p => crear("span", { class: "recorte tenue" }, p.respuesta) },
      { clave: "acciones", rotulo: "", clase: "angosta", render: p => {
        const editar = conIcono("lapiz", "Editar", "b-chico");
        editar.addEventListener("click", () => abrirFormPregunta(p));

        let segundo;
        if (p.activa === false) {
          segundo = conIcono("mas", "Reponer", "b-chico");
          segundo.addEventListener("click", () =>
            guardar(() => api.faq.actualizar(p.idPregunta, { ...p, activa: true }),
                    "Pregunta repuesta"));
        } else {
          segundo = conIcono("tacho", "Quitar", "b-chico b-chico--peligro");
          segundo.addEventListener("click", () => {
            if (!confirm(`Quitar la pregunta "${p.pregunta}"?\n\n` +
                         `Deja de mostrarse al cliente, pero se puede reponer.`)) return;
            guardar(() => api.faq.eliminar(p.idPregunta), "Pregunta quitada");
          });
        }
        return crear("span", { style: "display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end" },
                     editar, segundo);
      } }
    ], lista, ordenFaq,
       (nuevo) => { ordenFaq = nuevo; pintar(); },
       "No hay preguntas para ese motivo."));
  }
  pintar();
}

function abrirFormPregunta(p = null) {
  const esNueva = !p;
  const selMotivo = crear("select", { id: "motivo" },
    crear("option", { value: "", selected: !p?.motivo ? "true" : null },
          "General (todos los motivos)"),
    ...Object.entries(MOTIVOS).map(([k, v]) =>
      crear("option", { value: k, selected: p?.motivo === k ? "true" : null }, v)));

  const form = crear("form", { class: "form-panel", novalidate: "true" },
    crear("h2", { style: "font-size:22px;margin-bottom:20px;color:var(--vino)" },
          esNueva ? "Nueva pregunta" : "Editar pregunta"),
    crear("div", { class: "form-fila-2" },
      crear("div", { class: "campo campo--ancho" },
        crear("label", { for: "motivo" }, "Motivo"), selMotivo),
      campo("orden", "Orden", "number", p?.orden ?? 1)),
    campo("pregunta", "Pregunta", "text", p?.pregunta || ""),
    crear("div", { class: "campo campo--ancho", style: "margin-bottom:16px" },
      crear("label", { for: "respuesta" }, "Respuesta"),
      crear("textarea", { id: "respuesta", rows: "6" }, p?.respuesta || "")),
    crear("div", { class: "form-acciones" },
      crear("button", { type: "submit", class: "b-primario" },
            esNueva ? "Crear pregunta" : "Guardar cambios"),
      crear("button", { type: "button", class: "b-suave", onclick: cerrarModal }, "Cancelar"))
  );

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const datos = {
      // Cadena vacia significa "sin motivo", o sea una pregunta general.
      motivo:    $("#motivo", form).value || null,
      orden:     Number($("#orden", form).value) || 0,
      pregunta:  $("#pregunta", form).value.trim(),
      respuesta: $("#respuesta", form).value.trim(),
      activa:    true
    };
    if (datos.pregunta.length < 5)  { toast("Escribí la pregunta", "error"); return; }
    if (datos.respuesta.length < 5) { toast("Escribí la respuesta", "error"); return; }

    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Guardando...";

    const ok = await guardar(
      () => esNueva ? api.faq.crear(datos) : api.faq.actualizar(p.idPregunta, datos),
      esNueva ? "Pregunta creada" : "Pregunta actualizada", false);

    if (ok) { cerrarModal(); redibujar(); }
    else { boton.disabled = false; boton.textContent = esNueva ? "Crear pregunta" : "Guardar cambios"; }
  });

  abrirModal(form);
}

// ============================================================
//  CAMPOS DE FORMULARIO Y MODAL
// ============================================================

function campo(id, rotulo, tipo, valor) {
  return crear("div", { class: "campo campo--ancho", style: "margin-bottom:16px" },
    crear("label", { for: id }, rotulo),
    crear("input", { type: tipo, id, value: String(valor ?? "") }));
}

function campoSelect(id, rotulo, opciones, elegida, etiqueta = (v) => v) {
  const s = crear("select", { id });
  opciones.forEach(o => s.append(
    crear("option", { value: o, selected: o === elegida ? "true" : null }, etiqueta(o))));
  return crear("div", { class: "campo campo--ancho", style: "margin-bottom:16px" },
    crear("label", { for: id }, rotulo), s);
}

function abrirModal(contenido) {
  cerrarModal();
  const cerrar = crear("button", { class: "modal__cerrar", "aria-label": "Cerrar",
                                   onclick: cerrarModal }, "×");
  const modal = crear("div", { class: "modal", role: "dialog", "aria-modal": "true" },
                      cerrar, contenido);
  const fondo = crear("div", { class: "modal-fondo", id: "modalFondo",
    onclick: (e) => { if (e.target.id === "modalFondo") cerrarModal(); } }, modal);
  $("#raizModal").append(fondo);
  document.addEventListener("keydown", cerrarConEsc);
}

function cerrarModal() {
  const f = $("#modalFondo");
  if (f) f.remove();
  document.removeEventListener("keydown", cerrarConEsc);
}
function cerrarConEsc(e) { if (e.key === "Escape") cerrarModal(); }
