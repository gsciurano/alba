/**
 * js/vinos.js
 * ============================================================
 * Pagina NUESTROS VINOS (vinos.html).
 *
 * Presenta el catalogo agrupado por linea, con las notas de cata que
 * vienen de la columna 'descripcion' de la base. Es la cara informativa
 * del catalogo: no se compra desde aca, se compra en tienda.html.
 *
 * Las dos paginas leen de la misma API, asi que si entra un vino nuevo
 * aparece en las dos sin tocar nada.
 * ============================================================
 */

import { montarSitio, avisoSinServidor } from "./modules/sitio.js";
import { productos, cargar } from "./modules/store.js";
import { pesos, placeholderVino, $, crear } from "./modules/util.js";

/** Que cuenta cada linea. El texto es editorial, no sale de la base. */
const LINEAS = [
  { nombre: "Finca",
    texto: "La puerta de entrada a la finca. Vinos jóvenes, frescos y frutados, " +
           "pensados para la mesa de todos los días." },
  { nombre: "Estate Reserve",
    texto: "Un paso más en estructura y complejidad. Uvas seleccionadas de los " +
           "mejores cuadros del viñedo, con paso por madera." },
  { nombre: "Gran Reserva",
    texto: "Los vinos de guarda de la bodega. Gran cuerpo e identidad, con crianza " +
           "en barrica de roble francés y final prolongado." },
  { nombre: "Ediciones Especiales",
    texto: "Blends de partida limitada, donde se busca la expresión más alta de la " +
           "finca. Se sugiere decantar." }
];

document.addEventListener("DOMContentLoaded", async () => {
  montarSitio("vinos");

  const caja = $("#lineas");
  caja.innerHTML = '<div class="cargando">' + '<div class="esqueleto"></div>'.repeat(3) + '</div>';

  try {
    await cargar();
  } catch (e) {
    caja.innerHTML = "";
    caja.append(avisoSinServidor(e.message));
    return;
  }

  const activos = productos.getActivos();
  caja.innerHTML = "";

  LINEAS.forEach((linea, i) => {
    const vinos = activos.filter(p => p.linea === linea.nombre);
    if (!vinos.length) return;   // si una linea no tiene vinos, no se muestra vacia

    caja.append(crear("section", { class: "linea" + (i ? " linea--separada" : "") },
      crear("header", { class: "linea__encabezado" },
        crear("h2", { class: "seccion__titulo" }, linea.nombre),
        crear("p", { class: "seccion__bajada" }, linea.texto)),
      crear("div", { class: "linea__vinos" }, ...vinos.map(fichaVino))));
  });
});

/**
 * Ficha ancha: foto a la izquierda, notas de cata a la derecha.
 *
 * Toda la ficha es clickeable y lleva a vino.html?id=N, que es la pagina
 * con la cata completa, la elaboracion, el vinedo y la analitica. Lo que
 * se ve aca es el resumen; el detalle largo no entra en un listado de
 * diez vinos sin volverlo ilegible.
 *
 * SOBRE EL CLICK EN TODA LA FICHA: el enlace de verdad esta en el titulo,
 * asi que quien navega con teclado o lector de pantalla llega igual. El
 * click sobre el resto es una comodidad para el mouse, no el unico camino.
 * El boton de comprar corta la propagacion para que no se lo lleve puesto.
 */
function fichaVino(p) {
  const img = crear("img", { src: p.imagen, alt: "Botella de " + p.nombre, loading: "lazy" });
  img.addEventListener("error", () => { img.src = placeholderVino(p.nombre); });

  const destino = "vino.html?id=" + p.id;

  return crear("article", {
      class: "ficha ficha--enlace",
      onclick: () => { location.href = destino; }
    },
    crear("div", { class: "ficha__foto" }, img),
    crear("div", { class: "ficha__texto" },
      crear("h3", {}, crear("a", { class: "ficha__titulo", href: destino }, p.nombre)),
      crear("p", { class: "ficha__meta" },
        p.varietal + " · Cosecha " + p.cosecha + " · " + pesos(p.precio)),
      crear("p", { class: "ficha__notas" },
        p.notasCata || "Notas de cata en preparación."),
      crear("div", { class: "ficha__acciones" },
        crear("span", { class: "ficha__ver" }, "Ver la ficha completa →"),
        crear("a", {
          class: "btn btn--fantasma btn--chico",
          href: "tienda.html#vino-" + p.id,
          onclick: (e) => e.stopPropagation()
        }, p.stock > 0 ? "Comprar" : "Ver en la tienda"))));
}
