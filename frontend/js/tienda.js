/**
 * js/tienda.js
 * ============================================================
 * Logica de la TIENDA PUBLICA (index.html).
 *
 * Orquesta: catalogo con filtros/busqueda/orden, detalle de producto,
 * carrito persistente y checkout con validacion. Todo contra store.js,
 * nunca contra la base ni contra localStorage directo.
 *
 * El catalogo que se dibuja aca sale de MySQL a traves del back-end de la
 * Etapa 1. Lo unico que esta pantalla espera es el arranque: pide los datos
 * una vez y despues dibuja y filtra sobre esa copia.
 * ============================================================
 */

import { productos, carrito, pedidos, cargar, alCambiarEnOtraPestana }
  from "./modules/store.js";
import { VARIETALES, LINEAS } from "../data/productos.js";
import { pesos, placeholderVino, $, $$, crear, toast } from "./modules/util.js";

// Parametros de negocio del checkout.
const COSTO_ENVIO = 2500;
const TASA_IMPUESTO = 0.21;   // 21%

// Estado de los filtros actuales.
const filtros = { texto: "", varietal: "", linea: "", cosecha: "", precioMax: "", orden: "nombre" };

// ------------------------------------------------------------
//  ARRANQUE
// ------------------------------------------------------------
document.addEventListener("DOMContentLoaded", async () => {
  conectarEventos();

  // Mientras viaja el pedido HTTP se muestran los esqueletos de carga.
  const cont = $("#contenedorCatalogo");
  cont.innerHTML = '<div class="cargando">' + '<div class="esqueleto"></div>'.repeat(4) + '</div>';

  try {
    await cargar();              // una sola ida al servidor
  } catch (e) {
    mostrarServidorCaido(cont, e.message);
    return;                      // sin catalogo no hay nada que filtrar
  }

  poblarSelectoresDeFiltro();    // las cosechas salen de los datos reales
  renderCatalogo();
  actualizarBadgeCarrito();

  // Si en otra pestana se vacia el carrito, se actualiza el contador.
  alCambiarEnOtraPestana(() => actualizarBadgeCarrito());
});

/**
 * Pantalla de error cuando el back-end no responde. Se avisa explicitamente
 * en vez de mostrar un catalogo vacio, que haria pensar que no hay vinos.
 */
function mostrarServidorCaido(cont, mensaje) {
  cont.innerHTML = `
    <div class="estado">
      <div class="estado__icono">🔌</div>
      <h3>No se puede conectar con el servidor</h3>
      <p>${mensaje}</p>
      <p style="margin-top:10px;color:var(--texto-tenue);font-size:14px">
        El catalogo se lee de la API en <code>localhost:8080</code>.
      </p>
      <button class="btn btn--primario" style="margin-top:16px"
              onclick="location.reload()">Reintentar</button>
    </div>`;
  $("#resultadoFiltro").textContent = "";
}

// ------------------------------------------------------------
//  FILTROS
// ------------------------------------------------------------
function poblarSelectoresDeFiltro() {
  VARIETALES.forEach(v => $("#fVarietal").append(crear("option", { value: v }, v)));
  LINEAS.forEach(l => $("#fLinea").append(crear("option", { value: l }, l)));
  // Cosechas unicas, ordenadas.
  const cosechas = [...new Set(productos.getActivos().map(p => p.cosecha))].sort((a, b) => b - a);
  cosechas.forEach(c => $("#fCosecha").append(crear("option", { value: c }, String(c))));
}

function conectarEventos() {
  $("#fBuscar").addEventListener("input", e => { filtros.texto = e.target.value.toLowerCase(); renderCatalogo(); });
  $("#fVarietal").addEventListener("change", e => { filtros.varietal = e.target.value; renderCatalogo(); });
  $("#fLinea").addEventListener("change", e => { filtros.linea = e.target.value; renderCatalogo(); });
  $("#fCosecha").addEventListener("change", e => { filtros.cosecha = e.target.value; renderCatalogo(); });
  $("#fPrecio").addEventListener("change", e => { filtros.precioMax = e.target.value; renderCatalogo(); });
  $("#fOrden").addEventListener("change", e => { filtros.orden = e.target.value; renderCatalogo(); });
  $("#btnLimpiar").addEventListener("click", limpiarFiltros);
  $("#btnCarrito").addEventListener("click", abrirCarrito);
}

function limpiarFiltros() {
  Object.assign(filtros, { texto: "", varietal: "", linea: "", cosecha: "", precioMax: "", orden: "nombre" });
  $("#fBuscar").value = ""; $("#fVarietal").value = ""; $("#fLinea").value = "";
  $("#fCosecha").value = ""; $("#fPrecio").value = ""; $("#fOrden").value = "nombre";
  renderCatalogo();
}

/** Aplica todos los filtros y el orden sobre los productos activos. */
function productosFiltrados() {
  let lista = productos.getActivos();
  if (filtros.texto) {
    lista = lista.filter(p =>
      (p.nombre + " " + p.varietal + " " + p.linea + " " + p.bodega).toLowerCase().includes(filtros.texto));
  }
  if (filtros.varietal) lista = lista.filter(p => p.varietal === filtros.varietal);
  if (filtros.linea)    lista = lista.filter(p => p.linea === filtros.linea);
  if (filtros.cosecha)  lista = lista.filter(p => String(p.cosecha) === filtros.cosecha);
  if (filtros.precioMax) lista = lista.filter(p => p.precio <= Number(filtros.precioMax));

  const ordenes = {
    "nombre":      (a, b) => a.nombre.localeCompare(b.nombre),
    "precio-asc":  (a, b) => a.precio - b.precio,
    "precio-desc": (a, b) => b.precio - a.precio,
    "cosecha":     (a, b) => b.cosecha - a.cosecha
  };
  return lista.sort(ordenes[filtros.orden] || ordenes.nombre);
}

// ------------------------------------------------------------
//  RENDER DEL CATALOGO
// ------------------------------------------------------------
/**
 * Dibuja el catalogo desde la copia que ya esta en memoria, asi que es
 * instantaneo: no vuelve a preguntarle al servidor en cada tecla del buscador.
 *
 * Antes habia un setTimeout que simulaba una espera para mostrar los
 * esqueletos de carga. Ya no hace falta inventarla: la espera real es la del
 * arranque, y ahi si se muestran los esqueletos.
 */
function renderCatalogo() {
  const cont = $("#contenedorCatalogo");
  {
    const lista = productosFiltrados();
    $("#resultadoFiltro").textContent = lista.length + " vino" + (lista.length === 1 ? "" : "s") + " encontrado" + (lista.length === 1 ? "" : "s");

    if (lista.length === 0) {
      cont.innerHTML = `
        <div class="estado">
          <div class="estado__icono">🔍</div>
          <h3>No encontramos vinos con esos filtros</h3>
          <p>Proba cambiando la busqueda o los filtros.</p>
          <button class="btn btn--fantasma" id="vacioLimpiar" style="margin-top:16px">Limpiar filtros</button>
        </div>`;
      $("#vacioLimpiar").addEventListener("click", limpiarFiltros);
      return;
    }

    const grilla = crear("div", { class: "grilla" });
    lista.forEach(p => grilla.append(tarjetaProducto(p)));
    cont.innerHTML = "";
    cont.append(grilla);
  }
}

function tarjetaProducto(p) {
  const agotado = p.stock <= 0;
  const bajo = !agotado && p.stock <= p.stockMinimo;

  const img = crear("img", {
    src: p.imagen, alt: "Botella de " + p.nombre, loading: "lazy"
  });
  img.addEventListener("error", () => { img.src = placeholderVino(p.nombre); });

  let etiqueta = "";
  if (agotado) etiqueta = crear("span", { class: "etiqueta-stock etiqueta-stock--agotado" }, "Agotado");
  else if (bajo) etiqueta = crear("span", { class: "etiqueta-stock etiqueta-stock--bajo" }, "Ultimas botellas");

  const btn = crear("button", {
    class: "btn btn--primario btn--chico",
    disabled: agotado ? "true" : null,
    onclick: (e) => { e.stopPropagation(); agregarAlCarrito(p.id, 1); }
  }, agotado ? "Sin stock" : "Agregar");

  const tarjeta = crear("article", { class: "tarjeta", tabindex: "0",
    role: "button", "aria-label": "Ver detalle de " + p.nombre,
    onclick: () => abrirDetalle(p.id),
    onkeydown: (e) => { if (e.key === "Enter") abrirDetalle(p.id); }
  },
    crear("div", { class: "tarjeta__foto" }, img),
    crear("div", { class: "tarjeta__cuerpo" },
      crear("span", { class: "tarjeta__linea" }, p.linea),
      crear("h3", { class: "tarjeta__nombre" }, p.nombre),
      crear("span", { class: "tarjeta__meta" }, p.varietal + " · Cosecha " + p.cosecha),
      crear("div", { class: "tarjeta__pie" },
        crear("span", { class: "tarjeta__precio" }, pesos(p.precio)),
        etiqueta || ""
      ),
      btn
    )
  );
  return tarjeta;
}

// ------------------------------------------------------------
//  DETALLE DE PRODUCTO (modal)
// ------------------------------------------------------------
function abrirDetalle(id) {
  const p = productos.getById(id);
  if (!p) return;
  const agotado = p.stock <= 0;

  const img = crear("img", { src: p.imagen, alt: "Botella de " + p.nombre });
  img.addEventListener("error", () => { img.src = placeholderVino(p.nombre); });

  const inputCant = crear("input", { type: "number", value: "1", min: "1",
    max: String(p.stock), id: "detCantidad", "aria-label": "Cantidad" });

  const cuerpo = crear("div", { class: "detalle" },
    crear("div", { class: "detalle__foto" }, img),
    crear("div", { class: "detalle__info" },
      crear("p", { class: "detalle__linea" }, p.linea),
      crear("h2", { class: "detalle__nombre" }, p.nombre),
      crear("p", { class: "detalle__meta" }, p.varietal + " · Cosecha " + p.cosecha + " · " + p.bodega),
      crear("p", { class: "detalle__notas" }, p.notasCata),
      crear("p", { class: "detalle__precio" }, pesos(p.precio)),
      crear("p", { class: "detalle__meta" },
        agotado ? "Sin stock disponible" : "Stock disponible: " + p.stock + " botellas"),
      agotado ? "" : crear("div", { class: "selector-cantidad" },
        crear("button", { "aria-label": "Restar", onclick: () => ajustar(inputCant, -1, p.stock) }, "−"),
        inputCant,
        crear("button", { "aria-label": "Sumar", onclick: () => ajustar(inputCant, 1, p.stock) }, "+")
      ),
      crear("button", {
        class: "btn btn--primario btn--bloque",
        disabled: agotado ? "true" : null,
        onclick: () => { agregarAlCarrito(p.id, Number(inputCant.value)); cerrarModal(); }
      }, agotado ? "Sin stock" : "Agregar al carrito")
    )
  );
  montarModal(cuerpo);
}

function ajustar(input, delta, max) {
  let v = Number(input.value) + delta;
  v = Math.max(1, Math.min(max, v));
  input.value = v;
}

// ------------------------------------------------------------
//  CARRITO
// ------------------------------------------------------------
function agregarAlCarrito(id, cantidad) {
  const r = carrito.agregar(id, cantidad);
  if (r.ok) { toast("Agregado al carrito"); actualizarBadgeCarrito(); }
  else toast(r.mensaje, "error");
}

function actualizarBadgeCarrito() {
  const n = carrito.cantidadTotal();
  const badge = $("#carritoBadge");
  badge.textContent = n;
  badge.classList.toggle("oculto", n === 0);
}

function calcularTotales(items) {
  const subtotal = items.reduce((s, i) => s + i.precio * i.cantidad, 0);
  const envio = items.length ? COSTO_ENVIO : 0;
  const impuestos = Math.round(subtotal * TASA_IMPUESTO);
  return { subtotal, envio, impuestos, total: subtotal + envio + impuestos };
}

function abrirCarrito() {
  const items = carrito.getItems();
  const panel = crear("div", { class: "carrito-panel" });
  panel.append(crear("h2", {}, "Tu carrito"));

  if (items.length === 0) {
    panel.append(crear("div", { class: "estado" },
      crear("div", { class: "estado__icono" }, "🛒"),
      crear("p", {}, "Tu carrito esta vacio."),
      crear("button", { class: "btn btn--fantasma", style: "margin-top:14px", onclick: cerrarModal }, "Seguir comprando")
    ));
    montarModal(panel);
    return;
  }

  items.forEach(item => {
    const img = crear("img", { src: item.imagen, alt: item.nombre });
    img.addEventListener("error", () => { img.src = placeholderVino(item.nombre); });
    panel.append(crear("div", { class: "carrito-item" },
      crear("div", { class: "carrito-item__foto" }, img),
      crear("div", {},
        crear("div", { class: "carrito-item__nombre" }, item.nombre),
        crear("div", { class: "carrito-item__precio" }, pesos(item.precio) + " c/u"),
        crear("div", { class: "selector-cantidad", style: "margin-top:8px" },
          crear("button", { "aria-label": "Restar", onclick: () => cambiarCant(item.id, item.cantidad - 1) }, "−"),
          crear("input", { type: "number", value: String(item.cantidad), min: "1",
            "aria-label": "Cantidad de " + item.nombre,
            onchange: (e) => cambiarCant(item.id, Number(e.target.value)) }),
          crear("button", { "aria-label": "Sumar", onclick: () => cambiarCant(item.id, item.cantidad + 1) }, "+")
        )
      ),
      crear("div", { style: "text-align:right" },
        crear("div", { style: "font-weight:700;margin-bottom:8px" }, pesos(item.precio * item.cantidad)),
        crear("button", { class: "btn btn--peligro btn--chico", onclick: () => { carrito.quitar(item.id); abrirCarrito(); actualizarBadgeCarrito(); } }, "Quitar")
      )
    ));
  });

  const t = calcularTotales(items);
  panel.append(crear("div", { class: "carrito-resumen" },
    fila("Subtotal", pesos(t.subtotal)),
    fila("Envio", pesos(t.envio)),
    fila("Impuestos (21%)", pesos(t.impuestos)),
    crear("div", { class: "carrito-resumen__total" },
      crear("span", {}, "Total"), crear("span", {}, pesos(t.total)))
  ));
  panel.append(crear("button", { class: "btn btn--primario btn--bloque", style: "margin-top:20px",
    onclick: abrirCheckout }, "Finalizar compra"));

  montarModal(panel);
}

function cambiarCant(id, cantidad) {
  const r = carrito.cambiarCantidad(id, cantidad);
  if (!r.ok) toast(r.mensaje, "error");
  abrirCarrito();
  actualizarBadgeCarrito();
}

function fila(etiqueta, valor) {
  return crear("div", { class: "carrito-resumen__fila" },
    crear("span", {}, etiqueta), crear("span", {}, valor));
}

// ------------------------------------------------------------
//  CHECKOUT con validacion en cliente
// ------------------------------------------------------------
function abrirCheckout() {
  const items = carrito.getItems();
  if (items.length === 0) { toast("El carrito esta vacio", "error"); return; }
  const t = calcularTotales(items);

  const form = crear("form", { class: "carrito-panel", novalidate: "true", id: "formCheckout" },
    crear("h2", {}, "Finalizar compra"),
    campo("nombre", "Nombre y apellido", "text"),
    campo("email", "Email", "email"),
    campo("telefono", "Telefono", "tel"),
    campo("direccion", "Direccion de entrega", "text"),
    crear("div", { class: "form-grupo" },
      crear("label", { for: "medioPago" }, "Medio de pago"),
      crear("select", { id: "medioPago" },
        crear("option", { value: "TARJETA" }, "Tarjeta"),
        crear("option", { value: "TRANSFERENCIA" }, "Transferencia"),
        crear("option", { value: "EFECTIVO" }, "Efectivo"))
    ),
    crear("div", { class: "carrito-resumen" },
      crear("div", { class: "carrito-resumen__total" },
        crear("span", {}, "Total a pagar"), crear("span", {}, pesos(t.total)))),
    crear("button", { type: "submit", class: "btn btn--primario btn--bloque", style: "margin-top:18px" },
      "Confirmar pedido")
  );

  form.addEventListener("submit", (e) => { e.preventDefault(); procesarCheckout(form, items, t); });
  montarModal(form);
}

function campo(id, etiqueta, tipo) {
  return crear("div", { class: "form-grupo" },
    crear("label", { for: id }, etiqueta),
    crear("input", { type: tipo, id, name: id }),
    crear("span", { class: "form-error", id: "err-" + id, "aria-live": "polite" })
  );
}

/**
 * Valida en el cliente y despues confirma contra el servidor.
 *
 * La validacion de aca es para avisar rapido y no mandar un pedido que se
 * sabe mal. No reemplaza a la del back-end: Bean Validation y PedidoService
 * vuelven a controlar todo del otro lado, porque cualquiera puede saltear
 * el JavaScript del navegador.
 */
async function procesarCheckout(form, items, totales) {
  const datos = {
    nombre: form.nombre.value.trim(),
    email: form.email.value.trim(),
    telefono: form.telefono.value.trim(),
    direccion: form.direccion.value.trim(),
    medioPago: form.medioPago.value
  };
  let ok = true;
  const marcar = (id, cond, msg) => {
    const input = form[id], err = $("#err-" + id, form);
    if (cond) { input.classList.add("invalido"); err.textContent = msg; ok = false; }
    else { input.classList.remove("invalido"); err.textContent = ""; }
  };
  marcar("nombre", datos.nombre.length < 3, "Ingresa tu nombre completo");
  marcar("email", !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(datos.email), "Email invalido");
  marcar("telefono", datos.telefono.length < 6, "Telefono invalido");
  marcar("direccion", datos.direccion.length < 5, "Ingresa una direccion valida");
  if (!ok) { toast("Revisa los campos marcados", "error"); return; }

  // Aviso temprano si el stock de la copia local ya no alcanza. El control
  // que decide es el del servidor, unas lineas mas abajo.
  for (const item of items) {
    const prod = productos.getById(item.id);
    if (!prod || item.cantidad > prod.stock) {
      toast("Stock insuficiente de " + item.nombre + ". Ajusta tu carrito.", "error");
      return;
    }
  }

  // Se bloquea el boton: confirmar dos veces crearia dos pedidos.
  const boton = form.querySelector("button[type=submit]");
  boton.disabled = true;
  boton.textContent = "Confirmando...";

  try {
    const pedido = await pedidos.create({
      comprador: { nombre: datos.nombre, email: datos.email, telefono: datos.telefono },
      medioPago: datos.medioPago,
      items: items.map(i => ({ id: i.id, cantidad: i.cantidad }))
    });
    carrito.vaciar();
    actualizarBadgeCarrito();
    mostrarConfirmacion(pedido, datos.direccion);
    renderCatalogo();            // el stock bajo: se redibuja el catalogo
  } catch (e) {
    // Aca caen los rechazos del servidor: sin stock, producto dado de baja,
    // email ya registrado. El mensaje es el que escribio el back-end.
    toast(e.message, "error");
    boton.disabled = false;
    boton.textContent = "Confirmar pedido";
  }
}

/**
 * El numero de pedido y el total que se muestran son los que DEVOLVIO el
 * servidor, no los que calculo el navegador: son los que quedaron guardados
 * en MySQL, con el precio de cada vino congelado al momento de la compra.
 *
 * El total del servidor es la suma de los vinos. El envio y el 21% que
 * muestra el carrito se calculan en el navegador y no se guardan, porque la
 * tabla pedidos no tiene columnas para desglosarlos (ver README).
 */
function mostrarConfirmacion(pedido, direccion) {
  const panel = crear("div", { class: "carrito-panel", style: "text-align:center" },
    crear("div", { class: "estado__icono", style: "font-size:56px;color:var(--ok)" }, "✓"),
    crear("h2", { style: "margin:10px 0" }, "¡Pedido confirmado!"),
    crear("p", { style: "color:var(--texto-tenue)" }, "Tu numero de pedido es:"),
    crear("p", { style: "font-size:30px;font-family:var(--fuente-titulo);color:var(--oro);margin:8px 0 20px" },
      "#" + pedido.id),
    crear("p", { style: "color:var(--texto-tenue)" },
      "Vinos: " + pesos(pedido.total) + " · " + pedido.items.reduce((s,i)=>s+i.cantidad,0) + " botellas"),
    crear("p", { style: "color:var(--texto-tenue);margin-top:6px" },
      "Envio a " + direccion),
    crear("p", { style: "color:var(--texto-tenue);margin-top:6px" },
      "Te contactaremos a " + pedido.comprador.email),
    crear("button", { class: "btn btn--primario", style: "margin-top:24px", onclick: () => { cerrarModal(); renderCatalogo(); } },
      "Seguir comprando")
  );
  montarModal(panel);
}

// ------------------------------------------------------------
//  MODAL (infraestructura)
// ------------------------------------------------------------
function montarModal(contenido) {
  cerrarModal();
  const cerrar = crear("button", { class: "modal__cerrar", "aria-label": "Cerrar", onclick: cerrarModal }, "×");
  const modal = crear("div", { class: "modal", role: "dialog", "aria-modal": "true" }, cerrar, contenido);
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
