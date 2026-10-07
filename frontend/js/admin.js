/**
 * js/admin.js
 * ============================================================
 * Panel de ADMINISTRACION (admin.html).
 *
 * Login con rol + guarda, dashboard, CRUD de productos con edicion inline,
 * gestion de pedidos, categorias y trazabilidad de stock.
 *
 * Todo contra store.js, que ahora escribe en MySQL a traves de la API de la
 * Etapa 1. Cada cambio hecho aca queda en la base: no es una maqueta.
 *
 * El login sigue siendo del navegador porque el back-end todavia no tiene
 * endpoint de autenticacion (ver la nota en store.js).
 * ============================================================
 */

import {
  productos, pedidos, auditoria, sesion, cargar, ESTADOS_REALES
} from "./modules/store.js";
import { VARIETALES, LINEAS, BODEGAS } from "../data/productos.js";
import { pesos, fecha, placeholderVino, $, $$, crear, toast } from "./modules/util.js";

/**
 * Los estados posibles son los tres del enum de MySQL.
 *
 * Antes la lista tenia cinco ("preparacion" y "enviado" incluidos), pero la
 * columna estado solo acepta CONFIRMADO, ENTREGADO y CANCELADO: elegir
 * cualquiera de los otros dos daba error del servidor. Se muestran los
 * reales. Agregar los intermedios es un cambio de base de datos, no de
 * pantalla.
 */
const ESTADOS_PEDIDO = ESTADOS_REALES;

// ------------------------------------------------------------
//  ARRANQUE Y GUARDA DE ACCESO
// ------------------------------------------------------------
document.addEventListener("DOMContentLoaded", () => {
  $("#formLogin").addEventListener("submit", intentarLogin);
  if (sesion.actual()) entrarAlPanel();
});

function intentarLogin(e) {
  e.preventDefault();
  const u = sesion.login($("#usuario").value.trim(), $("#password").value);
  if (!u) { $("#loginError").textContent = "Usuario o contrasena incorrectos"; return; }
  entrarAlPanel();
}

async function entrarAlPanel() {
  const u = sesion.actual();
  $("#pantallaLogin").classList.add("oculto");
  $("#appAdmin").classList.remove("oculto");
  $("#usuarioActual").textContent = u.nombre + " (" + u.rol + ")";
  $("#btnSalir").addEventListener("click", () => { sesion.logout(); location.reload(); });
  $$(".admin-nav__item").forEach(b =>
    b.addEventListener("click", () => cambiarVista(b.dataset.vista, b)));

  // Se traen los datos del servidor antes de dibujar la primera vista.
  const cont = $("#vistaContenedor");
  cont.innerHTML = '<div class="estado"><p>Cargando datos del servidor...</p></div>';
  try {
    await cargar();
  } catch (e) {
    cont.innerHTML = `
      <div class="estado">
        <div class="estado__icono">🔌</div>
        <h3>No se puede conectar con el servidor</h3>
        <p>${e.message}</p>
        <button class="btn btn--primario" style="margin-top:16px"
                onclick="location.reload()">Reintentar</button>
      </div>`;
    return;
  }
  cambiarVista("dashboard", $('.admin-nav__item[data-vista="dashboard"]'));
}

/**
 * La vista de trazabilidad pide sus datos al servidor, por eso se usa await:
 * las demas dibujan desde la copia que ya esta en memoria.
 */
async function cambiarVista(vista, boton) {
  $$(".admin-nav__item").forEach(b => b.classList.remove("activo"));
  if (boton) boton.classList.add("activo");
  const cont = $("#vistaContenedor");
  cont.innerHTML = "";
  const render = { dashboard: vistaDashboard, productos: vistaProductos,
                   pedidos: vistaPedidos, categorias: vistaCategorias,
                   auditoria: vistaAuditoria }[vista] || vistaDashboard;
  try {
    await render(cont);
  } catch (e) {
    cont.innerHTML = "";
    cont.append(estadoVacio("No se pudieron traer los datos: " + e.message));
  }
}

/**
 * Envuelve una escritura contra el servidor: si sale bien avisa y redibuja,
 * y si el back-end la rechaza muestra SU mensaje, que ya viene explicado
 * (sin stock, falta un campo obligatorio, email repetido).
 */
async function guardar(accion, mensajeOk, vista) {
  try {
    await accion();
    if (mensajeOk) toast(mensajeOk);
    if (vista) await recargarVista(vista);
    return true;
  } catch (e) {
    toast(e.message, "error");
    if (vista) await recargarVista(vista);   // deja la pantalla con lo real
    return false;
  }
}

/**
 * Quien hizo el cambio ya no se manda desde el navegador: antes se usaba
 * para armar el log local. Ahora el back-end es el que registra el usuario
 * en movimientos_stock, que es donde esa informacion vale.
 */

// ------------------------------------------------------------
//  DASHBOARD
// ------------------------------------------------------------
function vistaDashboard(cont) {
  const todos = productos.getAll();
  const activos = todos.filter(p => p.activo);
  const bajoStock = activos.filter(p => p.stock <= p.stockMinimo);
  const listaPedidos = pedidos.getAll();
  const ingresos = listaPedidos.filter(p => p.estado !== "cancelado").reduce((s, p) => s + p.total, 0);

  cont.append(crear("h2", {}, "Dashboard"));
  const metricas = crear("div", { class: "metricas" },
    metrica(activos.length, "Productos activos"),
    metrica(todos.reduce((s, p) => s + p.stock, 0), "Botellas en stock"),
    metrica(bajoStock.length, "Bajo stock minimo", bajoStock.length > 0),
    metrica(listaPedidos.length, "Pedidos"),
    metrica(pesos(ingresos), "Ingresos simulados")
  );
  cont.append(metricas);

  if (bajoStock.length) {
    cont.append(crear("h3", { style: "margin:10px 0 14px;font-family:var(--fuente-titulo)" }, "Vinos que hay que reponer"));
    const env = crear("div", { class: "tabla-env" });
    const tabla = crear("table", {},
      crear("thead", {}, fila("th", ["Vino", "Stock actual", "Minimo"])));
    const tbody = crear("tbody", {});
    bajoStock.forEach(p => tbody.append(fila("td", [p.nombre, p.stock, p.stockMinimo])));
    tabla.append(tbody); env.append(tabla); cont.append(env);
  }
}

function metrica(valor, etiqueta, alerta = false) {
  return crear("div", { class: "metrica" + (alerta ? " metrica--alerta" : "") },
    crear("div", { class: "metrica__valor" }, String(valor)),
    crear("div", { class: "metrica__etiqueta" }, etiqueta));
}

// ------------------------------------------------------------
//  PRODUCTOS — CRUD con edicion inline
// ------------------------------------------------------------
function vistaProductos(cont) {
  cont.append(crear("h2", {}, "Productos"));
  cont.append(crear("div", { class: "barra-acciones" },
    crear("p", { style: "color:var(--texto-tenue);font-size:14px" },
      "Editá stock y precio directo en la tabla. El cambio se refleja al instante en la tienda."),
    crear("button", { class: "btn btn--primario", onclick: () => abrirFormProducto() }, "+ Nuevo producto")
  ));

  const env = crear("div", { class: "tabla-env" });
  const tabla = crear("table", {},
    crear("thead", {}, fila("th", ["", "Nombre", "Linea", "Varietal", "Cosecha", "Precio ($)", "Stock", "Estado", "Acciones"])));
  const tbody = crear("tbody", {});

  productos.getAll().forEach(p => {
    const precioInput = crear("input", { class: "celda-edit", type: "number", value: String(p.precio), min: "0",
      onchange: (e) => guardarInline(p.id, "precio", Number(e.target.value)) });
    const stockInput = crear("input", { class: "celda-edit", type: "number", value: String(p.stock), min: "0",
      onchange: (e) => guardarInline(p.id, "stock", Number(e.target.value)) });

    const img = crear("img", { class: "mini-foto", src: p.imagen, alt: p.nombre });
    img.addEventListener("error", () => { img.src = placeholderVino(p.nombre); });

    const tr = crear("tr", {},
      crear("td", {}, img),
      crear("td", {}, p.nombre),
      crear("td", {}, p.linea),
      crear("td", {}, p.varietal),
      crear("td", {}, String(p.cosecha)),
      crear("td", { class: "num" }, precioInput),
      crear("td", { class: "num" }, stockInput),
      crear("td", {}, pill(p.activo ? "activo" : "inactivo")),
      crear("td", {},
        crear("button", { class: "btn btn--fantasma btn--chico", onclick: () => abrirFormProducto(p.id) }, "Editar"),
        " ",
        p.activo
          ? crear("button", { class: "btn btn--peligro btn--chico", onclick: () => darBaja(p.id) }, "Baja")
          : crear("button", { class: "btn btn--fantasma btn--chico",
              onclick: () => guardar(() => productos.reactivar(p.id),
                                     "Producto reactivado", "productos") }, "Reactivar")
      )
    );
    tbody.append(tr);
  });
  tabla.append(tbody); env.append(tabla); cont.append(env);
}

function guardarInline(id, campo, valor) {
  if (isNaN(valor) || valor < 0) { toast("Valor invalido", "error"); recargarVista("productos"); return; }
  guardar(() => productos.update(id, { [campo]: valor }), campo + " actualizado", "productos");
}

async function darBaja(id) {
  const p = productos.getById(id);
  if (!confirm("Dar de baja \"" + p.nombre + "\"? Es baja logica: deja de verse en la tienda pero no se borra.")) return;
  await guardar(() => productos.remove(id), "Producto dado de baja", "productos");
}

/**
 * Formulario de alta/edicion en un modal.
 *
 * SOBRE LA IMAGEN: antes se subia un archivo y se guardaba convertido a
 * base64. Eso funcionaba cuando los datos vivian en el navegador, pero no
 * contra la base: la columna imagen_url admite 300 caracteres y un base64
 * de una foto ocupa decenas de miles. El servidor lo rechazaria.
 *
 * Asi que el campo pasa a ser la URL de la foto, que es lo que la columna
 * modela. Las fotos del catalogo original ya estan descargadas en
 * assets/img/vinos/ y api.js las encuentra por el nombre del vino, por eso
 * los diez vinos de Alba se ven sin tener que cargar nada.
 */
function abrirFormProducto(id = null) {
  const p = id ? productos.getById(id) : {
    nombre: "", linea: LINEAS[0], varietal: VARIETALES[0], cosecha: new Date().getFullYear(),
    bodega: BODEGAS[0], precio: 0, stock: 0, stockMinimo: 5, notasCata: "",
    imagen: "", imagenUrlOrigen: "", activo: true
  };

  const preview = crear("img", { class: "preview-foto",
    src: p.imagen || p.imagenUrlOrigen || placeholderVino(p.nombre || "Vino"),
    alt: "Vista previa" });
  preview.addEventListener("error", () => { preview.src = placeholderVino(p.nombre || "Vino"); });

  const inputUrl = crear("input", { type: "url", id: "imagenUrl", maxlength: "300",
    value: p.imagenUrlOrigen || "", placeholder: "https://...",
    oninput: (e) => { preview.src = e.target.value || placeholderVino(p.nombre || "Vino"); } });

  const form = crear("form", { class: "carrito-panel", novalidate: "true" },
    crear("h2", {}, id ? "Editar producto" : "Nuevo producto"),
    campoTexto("nombre", "Nombre", p.nombre),
    crear("div", { class: "form-fila" },
      campoSelect("linea", "Linea", LINEAS, p.linea),
      campoSelect("varietal", "Varietal", VARIETALES, p.varietal)),
    crear("div", { class: "form-fila" },
      campoTexto("cosecha", "Cosecha", p.cosecha, "number"),
      campoSelect("bodega", "Bodega", BODEGAS, p.bodega)),
    crear("div", { class: "form-fila" },
      campoTexto("precio", "Precio", p.precio, "number"),
      campoTexto("stock", "Stock", p.stock, "number")),
    campoTexto("stockMinimo", "Stock minimo", p.stockMinimo, "number"),
    crear("div", { class: "form-grupo" },
      crear("label", { for: "notasCata" }, "Notas de cata"),
      crear("textarea", { id: "notasCata", rows: "4" }, p.notasCata)),
    crear("div", { class: "form-grupo" },
      crear("label", { for: "imagenUrl" }, "URL de la foto"), inputUrl,
      crear("span", { style: "font-size:13px;color:var(--texto-tenue)" },
        "Si se deja vacio se muestra una botella dibujada."),
      preview),
    crear("span", { class: "form-error", id: "errProd", "aria-live": "polite" }),
    crear("button", { type: "submit", class: "btn btn--primario btn--bloque", style: "margin-top:10px" },
      id ? "Guardar cambios" : "Crear producto")
  );

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const datos = {
      nombre: $("#nombre", form).value.trim(),
      linea: $("#linea", form).value,
      varietal: $("#varietal", form).value,
      cosecha: Number($("#cosecha", form).value),
      bodega: $("#bodega", form).value,
      precio: Number($("#precio", form).value),
      stock: Number($("#stock", form).value),
      stockMinimo: Number($("#stockMinimo", form).value),
      notasCata: $("#notasCata", form).value.trim(),
      imagenUrlOrigen: $("#imagenUrl", form).value.trim(),
      activo: p.activo !== false
    };
    // Validacion rapida en pantalla. El back-end la repite con Bean
    // Validation, que es la que no se puede saltear.
    if (datos.nombre.length < 3) return errProd("El nombre es obligatorio");
    if (datos.precio < 0 || isNaN(datos.precio)) return errProd("Precio invalido");
    if (datos.stock < 0 || isNaN(datos.stock)) return errProd("Stock invalido");

    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true;
    boton.textContent = "Guardando...";

    const ok = await guardar(
      () => id ? productos.update(id, datos) : productos.create(datos),
      id ? "Producto actualizado" : "Producto creado",
      null);                       // se redibuja despues de cerrar el modal

    if (ok) { cerrarModal(); await recargarVista("productos"); }
    else { boton.disabled = false; boton.textContent = id ? "Guardar cambios" : "Crear producto"; }
  });

  function errProd(msg) { $("#errProd", form).textContent = msg; toast(msg, "error"); }
  montarModal(form);
}

// ------------------------------------------------------------
//  PEDIDOS
// ------------------------------------------------------------
function vistaPedidos(cont) {
  cont.append(crear("h2", {}, "Pedidos"));
  const lista = pedidos.getAll();
  if (!lista.length) { cont.append(estadoVacio("No hay pedidos todavia.")); return; }

  const env = crear("div", { class: "tabla-env" });
  const tabla = crear("table", {},
    crear("thead", {}, fila("th", ["N", "Fecha", "Cliente", "Items", "Total", "Estado", "Cambiar estado"])));
  const tbody = crear("tbody", {});
  lista.slice().reverse().forEach(p => {
    // Pasar a CANCELADO hace que el back-end devuelva el stock y registre el
    // movimiento de ajuste: por eso se refresca todo despues del cambio.
    const select = crear("select", { class: "celda-edit", style: "width:auto",
      onchange: (e) => guardar(() => pedidos.cambiarEstado(p.id, e.target.value),
                               "Estado actualizado", "pedidos") });
    ESTADOS_PEDIDO.forEach(es => select.append(crear("option", { value: es, selected: es === p.estado ? "true" : null }, es)));
    tbody.append(crear("tr", {},
      crear("td", {}, "#" + p.id),
      crear("td", {}, fecha(p.fecha)),
      crear("td", {}, p.comprador.nombre),
      crear("td", {}, String(p.items.reduce((s, i) => s + i.cantidad, 0))),
      crear("td", { class: "num" }, pesos(p.total)),
      crear("td", {}, pill(p.estado)),
      crear("td", {}, select)
    ));
  });
  tabla.append(tbody); env.append(tabla); cont.append(env);
}

// ------------------------------------------------------------
//  CATEGORIAS (varietales, lineas, bodegas)
// ------------------------------------------------------------
function vistaCategorias(cont) {
  cont.append(crear("h2", {}, "Categorias"));
  cont.append(crear("p", { style: "color:var(--texto-tenue);margin-bottom:24px" },
    "Varietales, lineas y bodegas que usa el catalogo, con cuantos vinos tiene cada uno."));
  const activos = productos.getActivos();
  const bloque = (titulo, valores, campo) => {
    const cont2 = crear("div", { style: "margin-bottom:28px" }, crear("h3", { style: "font-family:var(--fuente-titulo);margin-bottom:10px" }, titulo));
    const env = crear("div", { class: "tabla-env" });
    const tabla = crear("table", {}, crear("thead", {}, fila("th", [titulo.slice(0, -1), "Vinos"])));
    const tbody = crear("tbody", {});
    valores.forEach(v => tbody.append(fila("td", [v, activos.filter(p => p[campo] === v).length])));
    tabla.append(tbody); env.append(tabla); cont2.append(env); return cont2;
  };
  cont.append(bloque("Varietales", VARIETALES, "varietal"));
  cont.append(bloque("Lineas", LINEAS, "linea"));
  cont.append(bloque("Bodegas", BODEGAS, "bodega"));
}

// ------------------------------------------------------------
//  TRAZABILIDAD DE STOCK
// ------------------------------------------------------------
/**
 * Muestra la tabla movimientos_stock del back-end: cada entrada, salida o
 * ajuste con su usuario, su motivo y su fecha. Es informacion real, que se
 * puede verificar con un SELECT en MySQL.
 *
 * Ya no hay boton de "restaurar datos de fabrica": antes solo reescribia el
 * localStorage del navegador, pero ahora los datos estan en la base y
 * borrarlos desde una pantalla seria destruir el historial de ventas. Para
 * volver al estado inicial estan los scripts SQL del back-end.
 */
async function vistaAuditoria(cont) {
  cont.append(crear("h2", {}, "Trazabilidad de stock"));
  cont.append(crear("div", { class: "barra-acciones" },
    crear("p", { style: "color:var(--texto-tenue);font-size:14px" },
      "Cada movimiento de inventario registrado por el servidor: quien, que vino, cuanto y por que.")
  ));

  const registros = await auditoria.getAll();
  if (!registros.length) { cont.append(estadoVacio("Sin movimientos registrados.")); return; }

  const env = crear("div", { class: "tabla-env" });
  const tabla = crear("table", {}, crear("thead", {}, fila("th", ["Fecha", "Usuario", "Movimiento", "Vino"])));
  const tbody = crear("tbody", {});
  registros.forEach(r => {
    const tr = fila("td", [fecha(r.fecha), r.autor, r.accion, r.objeto]);
    // Un movimiento anulado no se borra: queda tachado junto a su contrapartida.
    if (r.anulado) tr.style.textDecoration = "line-through";
    if (r.anulado) tr.style.opacity = "0.55";
    tbody.append(tr);
  });
  tabla.append(tbody); env.append(tabla); cont.append(env);
}

// ------------------------------------------------------------
//  AYUDANTES
// ------------------------------------------------------------
function fila(tag, celdas) {
  const tr = crear("tr", {});
  celdas.forEach(c => tr.append(crear(tag, tag === "td" && typeof c === "number" ? { class: "num" } : {},
    c?.nodeType ? c : String(c))));
  return tr;
}
function pill(estado) { return crear("span", { class: "pill pill--" + estado }, estado); }
function campoTexto(id, etiqueta, valor, tipo = "text") {
  return crear("div", { class: "form-grupo" },
    crear("label", { for: id }, etiqueta),
    crear("input", { type: tipo, id, value: String(valor ?? "") }));
}
function campoSelect(id, etiqueta, opciones, sel) {
  const s = crear("select", { id });
  opciones.forEach(o => s.append(crear("option", { value: o, selected: o === sel ? "true" : null }, o)));
  return crear("div", { class: "form-grupo" }, crear("label", { for: id }, etiqueta), s);
}
function estadoVacio(msg) {
  return crear("div", { class: "estado" }, crear("div", { class: "estado__icono" }, "📭"), crear("p", {}, msg));
}
function recargarVista(vista) {
  return cambiarVista(vista, $('.admin-nav__item[data-vista="' + vista + '"]'));
}

// ---- modal (igual que en la tienda) ----
function montarModal(contenido) {
  cerrarModal();
  const cerrar = crear("button", { class: "modal__cerrar", "aria-label": "Cerrar", onclick: cerrarModal }, "×");
  const modal = crear("div", { class: "modal", role: "dialog", "aria-modal": "true" }, cerrar, contenido);
  const fondo = crear("div", { class: "modal-fondo", id: "modalFondo",
    onclick: (e) => { if (e.target.id === "modalFondo") cerrarModal(); } }, modal);
  $("#raizModal").append(fondo);
  document.addEventListener("keydown", cerrarConEsc);
}
function cerrarModal() { const f = $("#modalFondo"); if (f) f.remove(); document.removeEventListener("keydown", cerrarConEsc); }
function cerrarConEsc(e) { if (e.key === "Escape") cerrarModal(); }
