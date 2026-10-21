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

import { productos, carrito, pedidos, sesion, cargar, alCambiarEnOtraPestana }
  from "./modules/store.js";
import { montarSitio, actualizarCarrito, avisoSinServidor } from "./modules/sitio.js";
import { tarjetaVino } from "./modules/vino.js";
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
  montarSitio("tienda");         // encabezado y pie compartidos
  conectarEventos();

  // Mientras viaja el pedido HTTP se muestran los esqueletos de carga.
  const cont = $("#contenedorCatalogo");
  cont.innerHTML = '<div class="cargando">' + '<div class="esqueleto"></div>'.repeat(4) + '</div>';

  try {
    await cargar();              // una sola ida al servidor
  } catch (e) {
    cont.innerHTML = "";
    cont.append(avisoSinServidor(e.message));
    return;                      // sin catalogo no hay nada que filtrar
  }

  poblarSelectoresDeFiltro();    // las cosechas salen de los datos reales
  renderCatalogo();
  actualizarBadgeCarrito();

  // Si en otra pestana se vacia el carrito, se actualiza el contador.
  alCambiarEnOtraPestana(() => actualizarBadgeCarrito());

  atenderEnlaceDirecto();
});

/**
 * Las otras paginas enlazan a tienda.html#vino-5 o a #carrito. Aca se
 * atiende ese ancla para que el enlace lleve a donde dice, en vez de
 * dejar al visitante arriba de todo buscando el vino a mano.
 */
function atenderEnlaceDirecto() {
  const ancla = location.hash;
  if (ancla === "#carrito") { abrirCarrito(); return; }

  const coincide = ancla.match(/^#vino-(\d+)$/);
  if (coincide) {
    const id = Number(coincide[1]);
    if (productos.getById(id)) abrirDetalle(id);
  }
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

/**
 * La tarjeta la dibuja modules/vino.js, que comparten el inicio, la
 * pagina de vinos y la tienda. Aca solo se dice que, ademas, se puede
 * comprar y que pasa al tocarla.
 */
function tarjetaProducto(p) {
  // Editar y dar de baja solo se ofrecen a un administrador. A un
  // visitante o a un cliente ni siquiera se le pasan las funciones, asi
  // que los botones no se dibujan.
  const admin = sesion.esAdmin();
  return tarjetaVino(p, {
    comprable: true,
    alAgregar: (id) => agregarAlCarrito(id, 1),
    alAbrir:   (id) => abrirDetalle(id),
    alEditar:  admin ? abrirEdicion : undefined,
    alBorrar:  admin ? confirmarBaja : undefined
  });
}

// ------------------------------------------------------------
//  GESTION RAPIDA DESDE EL LISTADO (solo administradores)
// ------------------------------------------------------------

/**
 * Edicion de los datos que se ven en el listado, sin salir de la tienda.
 * El formulario completo, con notas de cata y foto, esta en el panel.
 *
 * Se manda el vino COMPLETO y no solo lo que cambio: el controlador
 * valida el cuerpo con @Valid y rechaza un PUT al que le falten los
 * campos obligatorios. store.productos.update() se encarga de fusionar.
 */
function abrirEdicion(p) {
  const error = crear("p", { class: "form-error", "aria-live": "polite" });

  const form = crear("form", { class: "carrito-panel", novalidate: "true" },
    crear("h2", {}, "Editar " + p.nombre),
    crear("p", { class: "nota-tenue", style: "margin-bottom:18px" },
      "Para las notas de cata y la foto, está el panel de gestión."),
    campo("eNombre", "Nombre", "text", p.nombre),
    crear("div", { class: "form-fila-2" },
      campo("ePrecio", "Precio", "number", p.precio),
      campo("eStock", "Stock", "number", p.stock)),
    crear("div", { class: "form-fila-2" },
      campo("eCosecha", "Añada", "number", p.cosecha),
      campo("eMinimo", "Stock mínimo", "number", p.stockMinimo)),
    error,
    crear("div", { style: "display:flex;gap:12px;flex-wrap:wrap;margin-top:8px" },
      crear("button", { type: "submit", class: "btn btn--primario" }, "Guardar"),
      crear("button", { type: "button", class: "btn btn--fantasma",
                        onclick: cerrarModal }, "Cancelar")));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    error.textContent = "";
    const datos = {
      nombre:      $("#eNombre", form).value.trim(),
      precio:      Number($("#ePrecio", form).value),
      stock:       Number($("#eStock", form).value),
      cosecha:     Number($("#eCosecha", form).value),
      stockMinimo: Number($("#eMinimo", form).value)
    };
    if (datos.nombre.length < 3) { error.textContent = "El nombre es obligatorio."; return; }
    if (!(datos.precio >= 0) || !(datos.stock >= 0)) {
      error.textContent = "El precio y el stock no pueden ser negativos."; return;
    }

    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Guardando...";
    try {
      await productos.update(p.id, datos);
      cerrarModal();
      toast("Vino actualizado");
      renderCatalogo();
    } catch (err) {
      error.textContent = err.message;
      boton.disabled = false; boton.textContent = "Guardar";
    }
  });

  montarModal(form);
}

/**
 * Baja LOGICA: el back-end pone activo = false. El vino deja de verse en
 * la tienda pero conserva su historial de ventas y movimientos, que es lo
 * que haria imposible un borrado fisico sin romper los pedidos viejos.
 */
async function confirmarBaja(p) {
  if (!confirm(`Dar de baja "${p.nombre}"?\n\n` +
               `Deja de verse en la tienda, pero se conserva su historial ` +
               `de ventas. Se puede reactivar desde el panel.`)) return;
  try {
    await productos.remove(p.id);
    toast("Vino dado de baja");
    renderCatalogo();
  } catch (e) { toast(e.message, "error"); }
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

/**
 * El contador del carrito vive en el encabezado compartido, asi que lo
 * actualiza sitio.js. Esta funcion queda como nombre unico para que el
 * resto de la pagina no tenga que saber de donde sale.
 */
function actualizarBadgeCarrito() { actualizarCarrito(); }

/**
 * Las cuentas del pedido. El descuento del cupon se resta de los vinos y
 * el 21% se calcula sobre lo que queda, que es el orden en que se cobra:
 * primero se descuenta y despues se grava.
 */
function calcularTotales(items, cupon = null) {
  const subtotal = items.reduce((s, i) => s + i.precio * i.cantidad, 0);

  let descuento = 0;
  if (cupon) {
    descuento = cupon.porcentaje ? Math.round(subtotal * cupon.porcentaje) : cupon.monto;
    descuento = Math.min(descuento, subtotal);   // nunca puede dejar el subtotal negativo
  }

  const gravado = subtotal - descuento;
  const envio = items.length ? COSTO_ENVIO : 0;
  const impuestos = Math.round(gravado * TASA_IMPUESTO);
  return {
    subtotal, descuento, envio, impuestos,
    cupon: cupon?.codigo || null,
    total: gravado + envio + impuestos
  };
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
//  CHECKOUT
// ------------------------------------------------------------
/**
 * El checkout es la pantalla mas larga del sitio: datos del comprador,
 * direccion, medio de pago y resumen. Antes era una sola columna de
 * campos con el total al final, asi que para saber cuanto se estaba por
 * pagar habia que llegar hasta abajo.
 *
 * Ahora son dos columnas: a la izquierda lo que hay que completar,
 * numerado en tres pasos, y a la derecha el resumen del pedido, que
 * queda a la vista todo el tiempo con el detalle, el cupon y el total.
 * En pantalla angosta las dos columnas se apilan solas.
 */

/** Cupon aplicado en el checkout que esta abierto. Se limpia al abrirlo. */
let cuponAplicado = null;

/** Los medios de pago, como fichas elegibles en vez de una lista desplegable. */
const MEDIOS = [
  { valor: "TARJETA",       titulo: "Tarjeta",       detalle: "Crédito o débito" },
  { valor: "TRANSFERENCIA", titulo: "Transferencia", detalle: "Te pasamos el CBU" },
  { valor: "EFECTIVO",      titulo: "Efectivo",      detalle: "Al recibir el pedido" }
];

function abrirCheckout() {
  const items = carrito.getItems();
  if (items.length === 0) { toast("El carrito esta vacio", "error"); return; }

  cuponAplicado = null;
  const u = sesion.actual();
  const nombreUsuario = u ? [u.nombre, u.apellido].filter(Boolean).join(" ") : "";

  const tarjeta = bloqueTarjeta(() => form.nombre.value);

  // Las fichas de pago muestran u ocultan los campos de la tarjeta: pedir
  // un numero de tarjeta a quien paga en efectivo no tiene sentido.
  const medios = crear("div", { class: "medios" },
    ...MEDIOS.map((m, i) => crear("label", { class: "medio", for: "medio-" + m.valor },
      crear("input", {
        type: "radio", name: "medioPago", value: m.valor, id: "medio-" + m.valor,
        checked: i === 0 ? "checked" : null,
        onchange: () => tarjeta.nodo.hidden = m.valor !== "TARJETA"
      }),
      crear("span", { class: "medio__titulo" }, m.titulo),
      crear("span", { class: "medio__detalle" }, m.detalle))));

  const columnaDatos = crear("div", { class: "pago__columna" },
    crear("header", { class: "pago__encabezado" },
      crear("h2", {}, "Finalizar compra"),
      u
        ? crear("p", { class: "pago__nota" }, "Comprando como " + nombreUsuario + ".")
        : crear("p", { class: "pago__nota" }, "¿Ya tenés cuenta? ",
            crear("a", { href: "cuenta.html" }, "Ingresá"),
            " y tus datos se completan solos.")),

    paso("01", "Tus datos",
      crear("div", { class: "form-fila" },
        campo("nombre", "Nombre y apellido", "text", nombreUsuario),
        campo("email", "Email", "email", u?.email || "")),
      campo("telefono", "Teléfono", "tel", u?.telefono || "", "11 5555 5555")),

    paso("02", "Entrega",
      campo("direccion", "Dirección de entrega", "text", "", "Calle, número, piso y localidad"),
      crear("p", { class: "pago__ayuda" }, "Enviamos a todo el país en 3 a 7 días hábiles.")),

    paso("03", "Pago", medios, tarjeta.nodo)
  );

  const resumen = panelResumen(items);

  const form = crear("form", { class: "pago", novalidate: "true", id: "formCheckout" },
    columnaDatos, resumen.nodo);

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    procesarCheckout(form, items, calcularTotales(items, cuponAplicado));
  });

  montarModal(form, { ancho: true });
}

/** Un paso numerado de la columna izquierda. */
function paso(numero, titulo, ...contenido) {
  return crear("section", { class: "pago__bloque" },
    crear("h3", { class: "pago__titulo" },
      crear("span", { class: "pago__paso" }, numero), titulo),
    ...contenido);
}

function campo(id, etiqueta, tipo, valor = "", pista = "") {
  return crear("div", { class: "form-grupo" },
    crear("label", { for: id }, etiqueta),
    crear("input", { type: tipo, id, name: id, value: valor, placeholder: pista || null }),
    crear("span", { class: "form-error", id: "err-" + id, "aria-live": "polite" })
  );
}

// ------------------------------------------------------------
//  TARJETA
// ------------------------------------------------------------
/**
 * Los datos de la tarjeta, con una vista previa que se va completando a
 * medida que se escribe, y un boton que inventa un numero de prueba.
 *
 * POR QUE EL BOTON: esto es un trabajo practico, no hay pasarela de pago
 * de verdad. Para probar la compra hace falta un numero que pase la
 * validacion, y pedirle a quien prueba que se acuerde de uno (o que use
 * el suyo real) seria peor. El boton genera uno valido al instante.
 *
 * El numero NO se manda al servidor: ver la nota en procesarCheckout().
 */
function bloqueTarjeta(nombreTitular) {
  const numero = crear("input", {
    type: "text", id: "tarjetaNumero", name: "tarjetaNumero", inputmode: "numeric",
    autocomplete: "off", placeholder: "0000 0000 0000 0000", maxlength: "19" });
  const vence = crear("input", {
    type: "text", id: "tarjetaVence", name: "tarjetaVence", inputmode: "numeric",
    autocomplete: "off", placeholder: "MM/AA", maxlength: "5" });
  const cvv = crear("input", {
    type: "text", id: "tarjetaCvv", name: "tarjetaCvv", inputmode: "numeric",
    autocomplete: "off", placeholder: "123", maxlength: "4" });

  // La vista previa: la tarjeta dibujada que se completa sola.
  const vistaNumero = crear("div", { class: "plastico__numero" }, "•••• •••• •••• ••••");
  const vistaTitular = crear("div", { class: "plastico__dato" }, "NOMBRE DEL TITULAR");
  const vistaVence = crear("div", { class: "plastico__dato" }, "MM/AA");
  const plastico = crear("div", { class: "plastico", "aria-hidden": "true" },
    crear("div", { class: "plastico__chip" }),
    vistaNumero,
    crear("div", { class: "plastico__pie" },
      crear("div", {}, crear("span", { class: "plastico__rotulo" }, "Titular"), vistaTitular),
      crear("div", {}, crear("span", { class: "plastico__rotulo" }, "Vence"), vistaVence)));

  function pintar() {
    vistaNumero.textContent = numero.value || "•••• •••• •••• ••••";
    vistaTitular.textContent = (nombreTitular() || "Nombre del titular").toUpperCase();
    vistaVence.textContent = vence.value || "MM/AA";
  }

  // Formateo mientras se escribe: el numero en grupos de cuatro y el
  // vencimiento con la barra puesta, para no tener que tipearla.
  numero.addEventListener("input", () => {
    numero.value = soloDigitos(numero.value, 16).replace(/(\d{4})(?=\d)/g, "$1 ");
    pintar();
  });
  vence.addEventListener("input", () => {
    const d = soloDigitos(vence.value, 4);
    vence.value = d.length > 2 ? d.slice(0, 2) + "/" + d.slice(2) : d;
    pintar();
  });
  cvv.addEventListener("input", () => { cvv.value = soloDigitos(cvv.value, 4); });

  const botonGenerar = crear("button", {
    type: "button", class: "btn btn--fantasma btn--chico generar",
    onclick: () => {
      numero.value = generarNumeroTarjeta();
      vence.value = generarVencimiento();
      cvv.value = String(Math.floor(Math.random() * 900) + 100);
      [numero, vence, cvv].forEach(i => i.classList.remove("invalido"));
      pintar();
      toast("Tarjeta de prueba generada");
    }
  }, "Generar número de tarjeta");

  const nodo = crear("div", { class: "tarjeta-pago", id: "bloqueTarjeta" },
    plastico,
    crear("div", { class: "tarjeta-pago__campos" },
      crear("div", { class: "form-grupo" },
        crear("label", { for: "tarjetaNumero" }, "Número de tarjeta"),
        crear("div", { class: "campo-con-boton" }, numero, botonGenerar),
        crear("span", { class: "form-error", id: "err-tarjetaNumero", "aria-live": "polite" })),
      crear("p", { class: "pago__ayuda" },
        "No hay cobro real: el botón arma un número de prueba válido junto con el vencimiento y el código."),
      crear("div", { class: "form-fila" },
        crear("div", { class: "form-grupo" },
          crear("label", { for: "tarjetaVence" }, "Vencimiento"),
          vence,
          crear("span", { class: "form-error", id: "err-tarjetaVence", "aria-live": "polite" })),
        crear("div", { class: "form-grupo" },
          crear("label", { for: "tarjetaCvv" }, "Código de seguridad"),
          cvv,
          crear("span", { class: "form-error", id: "err-tarjetaCvv", "aria-live": "polite" })))));

  // El nombre del titular sale del campo de arriba, asi que se repinta al
  // abrir el bloque y cada vez que la tarjeta recibe el foco.
  nodo.addEventListener("focusin", pintar);
  return { nodo, pintar };
}

function soloDigitos(texto, maximo) {
  return texto.replace(/\D/g, "").slice(0, maximo);
}

/**
 * Arma un numero de 16 digitos que pasa el algoritmo de Luhn, igual que
 * una tarjeta de verdad: 15 digitos al azar y el ultimo calculado para
 * que la suma cierre. Empieza con 4, el prefijo de Visa.
 */
function generarNumeroTarjeta() {
  const digitos = ["4"];
  while (digitos.length < 15) digitos.push(String(Math.floor(Math.random() * 10)));

  // Digito verificador: se doblan las posiciones impares contando desde
  // la derecha del numero terminado, que son las pares de este arreglo.
  let suma = 0;
  [...digitos].reverse().forEach((c, i) => {
    let n = Number(c);
    if (i % 2 === 0) { n *= 2; if (n > 9) n -= 9; }
    suma += n;
  });
  digitos.push(String((10 - suma % 10) % 10));
  return digitos.join("").replace(/(\d{4})(?=\d)/g, "$1 ");
}

/** Un vencimiento entre uno y cuatro anios para adelante. */
function generarVencimiento() {
  const hoy = new Date();
  const mes = Math.floor(Math.random() * 12) + 1;
  const anio = hoy.getFullYear() + 1 + Math.floor(Math.random() * 4);
  return String(mes).padStart(2, "0") + "/" + String(anio).slice(2);
}

/** Validacion de Luhn: la misma que hace el banco antes de cobrar. */
function numeroDeTarjetaValido(valor) {
  const d = valor.replace(/\D/g, "");
  if (d.length !== 16) return false;
  let suma = 0;
  [...d].reverse().forEach((c, i) => {
    let n = Number(c);
    if (i % 2 === 1) { n *= 2; if (n > 9) n -= 9; }
    suma += n;
  });
  return suma % 10 === 0;
}

/** El vencimiento tiene que ser MM/AA y no estar vencido. */
function vencimientoValido(valor) {
  const m = valor.match(/^(0[1-9]|1[0-2])\/(\d{2})$/);
  if (!m) return false;
  const hoy = new Date();
  const vence = new Date(2000 + Number(m[2]), Number(m[1]), 0);  // ultimo dia del mes
  return vence >= new Date(hoy.getFullYear(), hoy.getMonth(), 1);
}

// ------------------------------------------------------------
//  RESUMEN Y CUPONES
// ------------------------------------------------------------
/**
 * Los codigos promocionales viven aca, en el navegador, y no en la base:
 * el back-end de la Etapa 1 no tiene tabla de cupones ni columna de
 * descuento en 'pedidos'. Se comportan como el envio y el 21%, que ya se
 * calculan de este lado (ver README, "Diferencias conocidas").
 */
const CUPONES = {
  ALBA10:     { etiqueta: "10% en los vinos",  porcentaje: 0.10 },
  VALLEUCO15: { etiqueta: "15% en los vinos",  porcentaje: 0.15 },
  BIENVENIDA: { etiqueta: "$5.000 en los vinos", monto: 5000 }
};

/** El panel derecho: detalle del pedido, cupon y totales. */
function panelResumen(items) {
  const cuentas = crear("div", { class: "resumen__cuentas" });
  const estadoCupon = crear("p", { class: "cupon__estado", "aria-live": "polite" });
  const botonPagar = crear("button", { type: "submit", class: "btn btn--primario btn--bloque" }, "Pagar");

  const entradaCupon = crear("input", {
    type: "text", id: "cupon", name: "cupon", autocomplete: "off",
    placeholder: "Ingresá tu código", "aria-label": "Código promocional" });

  // Enter dentro del cupon aplica el cupon; sin esto el navegador daria
  // por enviado todo el formulario con los campos a medio completar.
  entradaCupon.addEventListener("keydown", (e) => {
    if (e.key === "Enter") { e.preventDefault(); aplicarCupon(); }
  });

  function aplicarCupon() {
    const codigo = entradaCupon.value.trim().toUpperCase();
    if (!codigo) { estadoCupon.className = "cupon__estado"; estadoCupon.textContent = ""; return; }
    const cupon = CUPONES[codigo];
    if (!cupon) {
      cuponAplicado = null;
      estadoCupon.className = "cupon__estado cupon__estado--error";
      estadoCupon.textContent = "El código " + codigo + " no existe o venció.";
    } else {
      cuponAplicado = { codigo, ...cupon };
      estadoCupon.className = "cupon__estado cupon__estado--ok";
      estadoCupon.textContent = "Listo: " + cupon.etiqueta + ".";
    }
    repintar();
  }

  function quitarCupon() {
    cuponAplicado = null;
    entradaCupon.value = "";
    estadoCupon.className = "cupon__estado";
    estadoCupon.textContent = "";
    repintar();
  }

  function repintar() {
    const t = calcularTotales(items, cuponAplicado);
    cuentas.innerHTML = "";
    cuentas.append(fila("Subtotal", pesos(t.subtotal)));
    if (t.descuento > 0) {
      cuentas.append(crear("div", { class: "carrito-resumen__fila resumen__descuento" },
        crear("span", {}, "Descuento " + cuponAplicado.codigo,
          crear("button", { type: "button", class: "cupon__quitar",
            "aria-label": "Quitar el cupón", onclick: quitarCupon }, "quitar")),
        crear("span", {}, "− " + pesos(t.descuento))));
    }
    cuentas.append(
      fila("Envío", pesos(t.envio)),
      fila("Impuestos (21%)", pesos(t.impuestos)),
      crear("div", { class: "carrito-resumen__total" },
        crear("span", {}, "Total"), crear("span", {}, pesos(t.total))));
    botonPagar.textContent = "Pagar " + pesos(t.total);
  }

  const nodo = crear("aside", { class: "pago__resumen" },
    crear("h3", { class: "resumen__titulo" }, "Tu pedido"),
    crear("ul", { class: "resumen__items" },
      ...items.map(i => crear("li", {},
        crear("span", { class: "resumen__cantidad" }, i.cantidad + "×"),
        crear("span", { class: "resumen__nombre" }, i.nombre),
        crear("span", { class: "resumen__importe" }, pesos(i.precio * i.cantidad))))),
    crear("div", { class: "cupon" },
      crear("label", { for: "cupon" }, "¿Tenés un código promocional?"),
      crear("div", { class: "campo-con-boton" },
        entradaCupon,
        crear("button", { type: "button", class: "btn btn--fantasma btn--chico", onclick: aplicarCupon },
          "Aplicar")),
      estadoCupon),
    cuentas,
    botonPagar,
    crear("p", { class: "resumen__aviso" },
      "Al confirmar, el pedido queda registrado y te escribimos para coordinar la entrega."));

  repintar();
  return { nodo, repintar };
}

// ------------------------------------------------------------
//  CONFIRMACION DEL PEDIDO
// ------------------------------------------------------------
/**
 * Valida en el cliente y despues confirma contra el servidor.
 *
 * La validacion de aca es para avisar rapido y no mandar un pedido que se
 * sabe mal. No reemplaza a la del back-end: Bean Validation y PedidoService
 * vuelven a controlar todo del otro lado, porque cualquiera puede saltear
 * el JavaScript del navegador.
 *
 * LOS DATOS DE LA TARJETA NO SALEN DEL NAVEGADOR. Se validan y se usan
 * para mostrar los ultimos cuatro digitos en la confirmacion, nada mas.
 * La API no tiene donde guardarlos, y guardar numeros de tarjeta sin
 * cifrar seria peor que no tenerlos.
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

  if (datos.medioPago === "TARJETA") {
    marcar("tarjetaNumero", !numeroDeTarjetaValido(form.tarjetaNumero.value),
      "Numero invalido. Podes generar uno de prueba con el boton.");
    marcar("tarjetaVence", !vencimientoValido(form.tarjetaVence.value),
      "Vencimiento invalido o vencido (MM/AA)");
    marcar("tarjetaCvv", !/^\d{3,4}$/.test(form.tarjetaCvv.value), "Son 3 o 4 digitos");
  }
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
  const textoBoton = boton.textContent;
  boton.disabled = true;
  boton.textContent = "Confirmando...";

  try {
    const pedido = await pedidos.create({
      comprador: { nombre: datos.nombre, email: datos.email, telefono: datos.telefono },
      medioPago: datos.medioPago,
      items: items.map(i => ({ id: i.id, cantidad: i.cantidad }))
    });
    const ultimos4 = datos.medioPago === "TARJETA"
      ? form.tarjetaNumero.value.replace(/\D/g, "").slice(-4) : "";
    carrito.vaciar();
    actualizarBadgeCarrito();
    mostrarConfirmacion(pedido, datos.direccion, totales, ultimos4);
    renderCatalogo();            // el stock bajo: se redibuja el catalogo
  } catch (e) {
    // Aca caen los rechazos del servidor: sin stock, producto dado de baja,
    // email ya registrado. El mensaje es el que escribio el back-end.
    toast(e.message, "error");
    boton.disabled = false;
    boton.textContent = textoBoton;
  }
}

/**
 * El numero de pedido y el total de los vinos que se muestran son los que
 * DEVOLVIO el servidor, no los que calculo el navegador: son los que
 * quedaron guardados en MySQL, con el precio de cada vino congelado al
 * momento de la compra.
 *
 * El envio, el 21% y el descuento del cupon se calculan en el navegador y
 * no se guardan, porque la tabla pedidos no tiene columnas para
 * desglosarlos (ver README).
 */
function mostrarConfirmacion(pedido, direccion, totales, ultimos4) {
  const botellas = pedido.items.reduce((s, i) => s + i.cantidad, 0);

  const detalle = crear("div", { class: "carrito-resumen" },
    fila("Vinos (" + botellas + " botella" + (botellas === 1 ? "" : "s") + ")", pesos(pedido.total)));
  if (totales.descuento > 0) {
    detalle.append(crear("div", { class: "carrito-resumen__fila resumen__descuento" },
      crear("span", {}, "Descuento " + totales.cupon),
      crear("span", {}, "− " + pesos(totales.descuento))));
  }
  detalle.append(
    fila("Envío", pesos(totales.envio)),
    fila("Impuestos (21%)", pesos(totales.impuestos)),
    crear("div", { class: "carrito-resumen__total" },
      crear("span", {}, "Total"), crear("span", {}, pesos(totales.total))));

  const panel = crear("div", { class: "carrito-panel confirmacion" },
    crear("div", { class: "confirmacion__tilde" }, "✓"),
    crear("h2", {}, "¡Pedido confirmado!"),
    crear("p", { class: "confirmacion__numero" }, "#" + pedido.id),
    crear("p", { class: "pago__nota" }, "Guardá este número para seguir tu pedido."),
    detalle,
    crear("div", { class: "confirmacion__datos" },
      crear("p", {}, "Envío a " + direccion),
      crear("p", {}, "Te escribimos a " + pedido.comprador.email),
      ultimos4 ? crear("p", {}, "Pagado con tarjeta terminada en " + ultimos4) : ""),
    crear("button", { class: "btn btn--primario", style: "margin-top:24px",
      onclick: () => { cerrarModal(); renderCatalogo(); } }, "Seguir comprando")
  );
  montarModal(panel);
}

// ------------------------------------------------------------
//  MODAL (infraestructura)
// ------------------------------------------------------------
function montarModal(contenido, opciones = {}) {
  cerrarModal();
  const cerrar = crear("button", { class: "modal__cerrar", "aria-label": "Cerrar", onclick: cerrarModal }, "×");
  // El checkout necesita mas ancho que el resto: entran dos columnas.
  const modal = crear("div", {
    class: "modal" + (opciones.ancho ? " modal--ancho" : ""),
    role: "dialog", "aria-modal": "true"
  }, cerrar, contenido);
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
