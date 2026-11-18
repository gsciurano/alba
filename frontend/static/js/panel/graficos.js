/**
 * js/modules/graficos.js
 * ============================================================
 * GRAFICOS DEL TABLERO, dibujados a mano.
 *
 * El proyecto no usa npm ni librerias, asi que no hay Chart.js: cada
 * grafico se arma con SVG generado aca. Son dos formas nada mas, que
 * es lo que el tablero necesita de verdad:
 *
 *   barrasMensuales()    barras verticales con escala. Para una serie
 *                        de tiempo corta (los ultimos meses).
 *   barrasRanking()      barras horizontales. Para comparar items de
 *                        una lista (los vinos mas vendidos).
 *
 * POR QUE EL RANKING NO ES SVG: son barras con un rotulo largo al lado
 * (el nombre del vino) y un numero. En SVG habria que medir el texto a
 * mano para que no se desborde, y el texto no se podria seleccionar ni
 * lo leeria bien un lector de pantalla. Con elementos normales y una
 * barra de ancho porcentual se resuelve igual y se comporta mejor. El
 * de meses si es SVG, porque ahi si hace falta una escala dibujada.
 *
 * NO HAY TORTAS A PROPOSITO: para tres o cuatro categorias, los numeros
 * con su porcentaje se comparan mejor que los angulos de una torta, y
 * dibujar arcos a mano cuesta bastante mas.
 * ============================================================
 */

import { crear } from "./util.js";

const SVG = "http://www.w3.org/2000/svg";

/** Como crear(), pero para elementos SVG, que necesitan su namespace. */
function svg(tag, attrs = {}, ...hijos) {
  const el = document.createElementNS(SVG, tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v !== null && v !== undefined) el.setAttribute(k, String(v));
  }
  hijos.flat().forEach(h => el.append(h?.nodeType ? h : document.createTextNode(h ?? "")));
  return el;
}

/**
 * Barras verticales con escala.
 *
 * datos: [{ etiqueta, valor }]  — ya ordenados como se quieren mostrar.
 * formato: como se escribe un valor (por ejemplo pesos()).
 *
 * El SVG lleva viewBox y ancho 100%, asi que se achica solo con la
 * tarjeta sin recalcular nada en JavaScript.
 */
export function barrasMensuales(datos, { formato = String, titulo = "",
                                         ancho = 620, alto = 240 } = {}) {
  /*
   * El viewBox define la PROPORCION, no el tamanio: el SVG se estira al
   * ancho del contenedor y el alto sale solo. Por eso una tarjeta a
   * ancho completo necesita una proporcion mas apaisada que una de
   * media pantalla; si no, el grafico crece hacia abajo hasta ocupar
   * la pantalla entera. El tope final lo pone max-height en el CSS.
   */
  const ANCHO = ancho, ALTO = alto;
  const IZQ = 16, DER = 16, ARRIBA = 26, ABAJO = 34;
  const areaAncho = ANCHO - IZQ - DER;
  const areaAlto  = ALTO - ARRIBA - ABAJO;

  if (!datos.length) {
    return crear("p", { class: "grafico__vacio" }, "Todavía no hay ventas que mostrar.");
  }

  // La escala arranca siempre en cero: si arrancara en el minimo, una
  // diferencia del 3% pareceria que el mes se duplico.
  const maximo = Math.max(...datos.map(d => d.valor), 1);
  const paso       = areaAncho / datos.length;
  const anchoBarra = Math.min(paso * 0.58, 54);

  const piezas = [];

  // Tres lineas de referencia, para poder leer un valor sin contar pixeles.
  [0, 0.5, 1].forEach(f => {
    const y = ARRIBA + areaAlto * (1 - f);
    piezas.push(svg("line", { x1: IZQ, x2: ANCHO - DER, y1: y, y2: y,
      stroke: f === 0 ? "#c9d4d9" : "#e8eef0", "stroke-width": 1 }));
  });

  datos.forEach((d, i) => {
    const altoBarra = Math.max((d.valor / maximo) * areaAlto, d.valor > 0 ? 2 : 0);
    const x = IZQ + paso * i + (paso - anchoBarra) / 2;
    const y = ARRIBA + areaAlto - altoBarra;

    piezas.push(svg("rect", {
      x, y, width: anchoBarra, height: altoBarra, rx: 4,
      fill: d.destacado ? "#0b2c38" : "#388497"
    }));
    // El valor arriba de la barra: con pocas barras entra siempre y
    // evita tener que mirar la escala.
    if (d.valor > 0) {
      piezas.push(svg("text", {
        x: x + anchoBarra / 2, y: y - 7, "text-anchor": "middle",
        "font-size": 12, fill: "#5a6670", "font-weight": 600
      }, d.corto || formato(d.valor)));
    }
    piezas.push(svg("text", {
      x: x + anchoBarra / 2, y: ALTO - 12, "text-anchor": "middle",
      "font-size": 12.5, fill: d.destacado ? "#0b2c38" : "#5a6670",
      "font-weight": d.destacado ? 700 : 400
    }, d.etiqueta));
  });

  const grafico = svg("svg", {
    viewBox: `0 0 ${ANCHO} ${ALTO}`, class: "grafico__svg",
    role: "img", "aria-label": titulo ||
      datos.map(d => d.etiqueta + ": " + formato(d.valor)).join(", ")
  }, ...piezas);

  return crear("div", { class: "grafico" }, grafico);
}

/**
 * Barras horizontales para comparar items de una lista.
 *
 * datos: [{ etiqueta, valor, detalle }]
 * La barra mas larga es el maximo: lo que se compara es entre ellos,
 * no contra un objetivo.
 */
export function barrasRanking(datos, { formato = String, vacio = "Sin datos." } = {}) {
  if (!datos.length) return crear("p", { class: "grafico__vacio" }, vacio);

  const maximo = Math.max(...datos.map(d => d.valor), 1);

  return crear("ol", { class: "ranking" },
    ...datos.map(d => crear("li", { class: "ranking__fila" },
      crear("div", { class: "ranking__cabeza" },
        crear("span", { class: "ranking__etiqueta" }, d.etiqueta),
        crear("span", { class: "ranking__valor" }, formato(d.valor))),
      crear("div", { class: "ranking__riel" },
        crear("div", { class: "ranking__barra",
                       style: "width:" + Math.max((d.valor / maximo) * 100, 2) + "%" })),
      d.detalle ? crear("div", { class: "ranking__detalle" }, d.detalle) : "")));
}

/**
 * La variacion contra el periodo anterior, con su flecha y su color.
 *
 * 'bueno' dice si subir es bueno: para las ventas si, para la tasa de
 * cancelacion no. Sin eso, un rojo podria significar cosas opuestas en
 * dos indicadores de la misma fila.
 */
export function variacion(actual, anterior, { bueno = true, enPuntos = false } = {}) {
  if (anterior === null || anterior === undefined) return "";
  if (!anterior && !actual) return crear("span", { class: "variacion" }, "sin datos antes");
  if (!anterior) return crear("span", { class: "variacion variacion--neutra" }, "sin comparación");

  const delta = enPuntos ? actual - anterior : ((actual - anterior) / anterior) * 100;
  if (Math.abs(delta) < 0.05) {
    return crear("span", { class: "variacion variacion--neutra" }, "igual que el mes anterior");
  }
  const subio = delta > 0;
  const clase = (subio === bueno) ? "variacion--bien" : "variacion--mal";
  const texto = (subio ? "▲ " : "▼ ") +
    Math.abs(delta).toFixed(enPuntos ? 1 : 0).replace(".", ",") +
    (enPuntos ? " pts" : "%") + " vs. el mes anterior";
  return crear("span", { class: "variacion " + clase }, texto);
}
