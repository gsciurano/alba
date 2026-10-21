/**
 * js/vino.js
 * ============================================================
 * Pagina FICHA DE UN VINO (vino.html?id=N).
 *
 * Es la pagina a la que se llega al tocar un vino en cualquier lado del
 * sitio: en el inicio, en "Nuestros vinos" o en la tienda.
 *
 * DE DONDE SALE CADA COSA:
 *   - de la API (store.js): nombre, linea, varietal, cosecha, precio,
 *     stock y si esta activo. Son los datos que cambian y que el panel
 *     edita, asi que tienen que salir de la base si o si.
 *   - de data/fichas-vinos.js: notas de cata, vinificacion, crianza,
 *     vinedo y analitica. Son texto editorial fijo de la bodega.
 *
 * Si un vino no tiene ficha cargada (por ejemplo uno dado de alta desde
 * el panel), la pagina igual se dibuja: muestra lo que da la base y
 * saltea las secciones que no tiene, en vez de dejar titulos vacios.
 * ============================================================
 */

import { montarSitio, avisoSinServidor, actualizarCarrito } from "./modules/sitio.js";
import { productos, carrito, cargar } from "./modules/store.js";
import { fichaDe, VINEDO, CERTIFICACION } from "../data/fichas-vinos.js";
import { pesos, placeholderVino, toast, $, crear } from "./modules/util.js";

document.addEventListener("DOMContentLoaded", async () => {
  montarSitio("vinos");   // en la barra queda marcado "Vinos"

  const caja = $("#detalleVino");
  caja.innerHTML = '<div class="cargando"><div class="esqueleto"></div></div>';

  try {
    await cargar();
  } catch (e) {
    caja.innerHTML = "";
    caja.append(avisoSinServidor(e.message));
    return;
  }

  // El id viaja en la direccion: vino.html?id=3
  const id = new URLSearchParams(location.search).get("id");
  const vino = productos.getById(id);

  caja.innerHTML = "";
  if (!vino) { caja.append(noEncontrado()); return; }

  document.title = vino.nombre + " — Alba en los Andes";
  dibujar(caja, vino);
});

// ============================================================
//  LA PAGINA
// ============================================================

function dibujar(caja, p) {
  const ficha = fichaDe(p.nombre);   // null si este vino todavia no tiene ficha

  caja.append(encabezado(p, ficha));

  // El cuerpo va en dos columnas: el texto a la izquierda y la ficha
  // tecnica fija a la derecha, como en la ficha impresa de la bodega.
  const columnas = crear("div", { class: "vino-cuerpo" });

  const texto = crear("div", { class: "vino-cuerpo__texto" });
  if (ficha) {
    texto.append(bloqueCata(ficha));
    texto.append(bloqueElaboracion(ficha));
    texto.append(bloqueVinedo());
    texto.append(bloqueMaridaje(ficha));
  } else {
    // Vino sin ficha: al menos se muestra la descripcion que si esta en la base.
    texto.append(bloque("Sobre este vino",
      crear("p", { class: "vino-parrafo" },
        p.notasCata || "La ficha de cata de este vino todavía está en preparación.")));
  }
  columnas.append(texto);

  if (ficha) columnas.append(fichaTecnica(p, ficha));
  caja.append(crear("section", { class: "seccion" },
    crear("div", { class: "seccion__interior" }, columnas)));

  caja.append(masDeLaLinea(p));
}

/** La banda de arriba: foto, nombre, datos basicos y compra. */
function encabezado(p, ficha) {
  const img = crear("img", { src: p.imagen, alt: "Botella de " + p.nombre });
  img.addEventListener("error", () => { img.src = placeholderVino(p.nombre); });

  const agotado = p.stock <= 0;
  const ultimas = !agotado && p.stock <= p.stockMinimo;

  const estado = agotado
    ? crear("span", { class: "etiqueta-stock etiqueta-stock--agotado" }, "Agotado")
    : ultimas
      ? crear("span", { class: "etiqueta-stock etiqueta-stock--bajo" }, "Últimas botellas")
      : "";

  const info = crear("div", { class: "vino-hero__info" },
    crear("nav", { class: "miga", "aria-label": "Dónde estás" },
      crear("a", { href: "vinos.html" }, "Nuestros vinos"),
      crear("span", {}, " / "),
      crear("span", {}, p.nombre)),
    crear("p", { class: "vino-hero__linea" }, p.linea),
    crear("h1", { class: "vino-hero__nombre" }, p.nombre),
    crear("p", { class: "vino-hero__meta" },
      [ficha?.composicion || p.varietal,
       p.cosecha ? "Cosecha " + p.cosecha : null,
       VINEDO.region].filter(Boolean).join(" · ")),
    crear("p", { class: "vino-hero__presentacion" },
      ficha?.presentacion || p.notasCata || ""),
    crear("div", { class: "vino-hero__compra" },
      crear("span", { class: "vino-hero__precio" }, pesos(p.precio)),
      estado,
      botonAgregar(p)),
    // El numero exacto de botellas es informacion de la bodega, no del
    // visitante: saber que quedan 549 no le cambia nada y expone el
    // inventario. Solo se avisa cuando queda poco, que es lo unico que
    // de verdad le sirve a quien esta por comprar.
    crear("p", { class: "vino-hero__nota" },
      agotado ? "Podés consultarnos por la próxima partida desde Contacto."
              : ultimas ? "¡Quedan pocas botellas de esta cosecha! Si lo querés, compralo ahora."
                        : ""));

  return crear("section", { class: "vino-hero" },
    crear("div", { class: "vino-hero__interior" },
      crear("div", { class: "vino-hero__foto" }, img),
      info));
}

/**
 * Agregar al carrito desde la ficha.
 *
 * El control de stock de carrito.agregar() es por comodidad: el que
 * cuenta es el del servidor al confirmar la compra. Ver store.js.
 */
function botonAgregar(p) {
  if (p.stock <= 0) {
    return crear("button", { class: "btn btn--primario", disabled: "true" }, "Sin stock");
  }
  return crear("button", {
    class: "btn btn--primario",
    onclick: () => {
      const r = carrito.agregar(p.id, 1);
      if (!r.ok) { toast(r.mensaje, "error"); return; }
      actualizarCarrito();
      toast("Agregaste " + p.nombre + " al carrito");
    }
  }, "Agregar al carrito");
}

// ------------------------- los bloques de texto -------------------------

/** Un titulo con su contenido. Todas las secciones tienen esta forma. */
function bloque(titulo, ...contenido) {
  return crear("section", { class: "vino-bloque" },
    crear("h2", { class: "vino-bloque__titulo" }, titulo),
    ...contenido);
}

function bloqueCata(f) {
  const filas = [
    ["Vista", f.cata?.vista],
    ["Nariz", f.cata?.nariz],
    ["Boca",  f.cata?.boca]
  ].filter(([, v]) => v);

  return bloque("Notas de cata",
    crear("dl", { class: "cata" },
      ...filas.flatMap(([k, v]) => [
        crear("dt", {}, k),
        crear("dd", {}, v)
      ])),
    f.servicio ? crear("p", { class: "vino-parrafo vino-parrafo--destacado" }, f.servicio) : "");
}

function bloqueElaboracion(f) {
  const partes = [];
  if (f.vinificacion) partes.push(crear("p", { class: "vino-parrafo" }, f.vinificacion));
  if (f.crianza) partes.push(
    crear("p", { class: "vino-parrafo" },
      crear("strong", {}, "Crianza. "), f.crianza));
  if (!partes.length) return "";
  return bloque("Elaboración y crianza", ...partes);
}

/**
 * El vinedo es el mismo para las diez etiquetas, asi que se arma desde la
 * constante compartida y no desde la ficha de cada vino.
 */
function bloqueVinedo() {
  const datos = [
    ["Región",      VINEDO.region],
    ["Antigüedad",  VINEDO.antiguedad],
    ["Altura",      VINEDO.altura],
    ["Conducción",  VINEDO.conduccion],
    ["Orientación", VINEDO.orientacion],
    ["Cosecha",     VINEDO.cosecha]
  ];
  return bloque("El viñedo",
    crear("p", { class: "vino-parrafo" }, VINEDO.suelo),
    crear("dl", { class: "cata cata--compacta" },
      ...datos.flatMap(([k, v]) => [crear("dt", {}, k), crear("dd", {}, v)])));
}

function bloqueMaridaje(f) {
  if (!f.maridaje) return "";
  return bloque("Para acompañar", crear("p", { class: "vino-parrafo" }, f.maridaje));
}

/** La columna lateral con los numeros del laboratorio. */
function fichaTecnica(p, f) {
  const a = f.analitica || {};
  const filas = [
    ["Varietal",     f.composicion],
    ["Cosecha",      p.cosecha ? String(p.cosecha) : null],
    ["Línea",        p.linea],
    ["Alcohol",      a.alcohol],
    ["Acidez total", a.acidez],
    ["pH",           a.ph],
    ["Azúcar residual", a.azucar]
  ].filter(([, v]) => v);

  return crear("aside", { class: "vino-tecnica" },
    crear("h2", { class: "vino-tecnica__titulo" }, "Ficha técnica"),
    crear("dl", { class: "vino-tecnica__lista" },
      ...filas.flatMap(([k, v]) => [crear("dt", {}, k), crear("dd", {}, v)])),
    crear("p", { class: "vino-tecnica__sello" }, CERTIFICACION),
    f.origen
      ? crear("a", { class: "vino-tecnica__origen", href: f.origen,
                     target: "_blank", rel: "noopener" },
              "Ver la ficha en albaenlosandes.com")
      : "");
}

/** Las otras etiquetas de la misma linea, para seguir mirando. */
function masDeLaLinea(p) {
  const hermanos = productos.getActivos()
    .filter(o => o.linea === p.linea && o.id !== p.id);
  if (!hermanos.length) return "";

  return crear("section", { class: "seccion seccion--gris" },
    crear("div", { class: "seccion__interior" },
      crear("h2", { class: "seccion__titulo" }, "Más de la línea " + p.linea),
      crear("div", { class: "vino-hermanos" },
        ...hermanos.map(o => crear("a", { class: "vino-hermano", href: "vino.html?id=" + o.id },
          crear("span", { class: "vino-hermano__nombre" }, o.nombre),
          crear("span", { class: "vino-hermano__meta" },
            o.varietal + (o.cosecha ? " · " + o.cosecha : "")))))));
}

/** Cuando la direccion trae un id que no existe. */
function noEncontrado() {
  return crear("div", { class: "aviso-grande" },
    crear("h2", {}, "No encontramos ese vino"),
    crear("p", {}, "Puede que ya no esté en el catálogo o que el enlace esté incompleto."),
    crear("a", { class: "btn btn--primario", href: "vinos.html" }, "Ver todos los vinos"));
}
