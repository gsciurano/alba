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

/** Ficha ancha: foto a la izquierda, notas de cata a la derecha. */
function fichaVino(p) {
  const img = crear("img", { src: p.imagen, alt: "Botella de " + p.nombre, loading: "lazy" });
  img.addEventListener("error", () => { img.src = placeholderVino(p.nombre); });

  return crear("article", { class: "ficha" },
    crear("div", { class: "ficha__foto" }, img),
    crear("div", { class: "ficha__texto" },
      crear("h3", {}, p.nombre),
      crear("p", { class: "ficha__meta" },
        p.varietal + " · Cosecha " + p.cosecha + " · " + pesos(p.precio)),
      crear("p", { class: "ficha__notas" },
        p.notasCata || "Notas de cata en preparación."),
      crear("a", { class: "btn btn--fantasma btn--chico", href: "tienda.html#vino-" + p.id },
        p.stock > 0 ? "Comprar" : "Ver en la tienda")));
}
