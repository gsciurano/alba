/**
 * js/modules/util.js
 * ============================================================
 * Utilidades compartidas por la tienda y el admin: formateo de
 * precios y fechas, placeholder de imagen, y ayudas de DOM.
 * ============================================================
 */

/** Formatea un numero como precio en pesos argentinos. */
export function pesos(n) {
  return "$ " + Number(n).toLocaleString("es-AR", { minimumFractionDigits: 0 });
}

/** Fecha y hora: "07/10/2026, 11:16 a. m." */
export function fecha(iso) {
  if (!iso) return "";
  return new Date(iso).toLocaleString("es-AR", {
    day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit"
  });
}

/** Solo la fecha: "07/10/2026". Para columnas donde la hora no aporta. */
export function fechaCorta(iso) {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString("es-AR", {
    day: "2-digit", month: "2-digit", year: "numeric"
  });
}

/** Solo la hora: "11:16 a. m." */
export function hora(iso) {
  if (!iso) return "";
  return new Date(iso).toLocaleTimeString("es-AR", { hour: "2-digit", minute: "2-digit" });
}

/**
 * Placeholder SVG para cuando una imagen no carga.
 * Se genera en el momento, consistente con la paleta del diseno.
 * Devuelve un data URI listo para poner en src.
 */
export function placeholderVino(nombre = "Vino") {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="400" height="520" viewBox="0 0 400 520">
      <rect width="400" height="520" fill="#2a211c"/>
      <path d="M185 110 h30 v70 q0 40 20 60 v150 q0 15 -15 15 h-40 q-15 0 -15 -15 v-150 q20 -20 20 -60 z"
            fill="none" stroke="#b08d57" stroke-width="3"/>
      <text x="200" y="470" fill="#b08d57" font-family="Georgia, serif" font-size="20"
            text-anchor="middle">${nombre.slice(0, 22)}</text>
      <text x="200" y="498" fill="#8a7256" font-family="Arial" font-size="12"
            text-anchor="middle">sin fotografia</text>
    </svg>`;
  return "data:image/svg+xml;utf8," + encodeURIComponent(svg.trim());
}

/** Atajo para document.querySelector. */
export const $ = (sel, raiz = document) => raiz.querySelector(sel);
/** Atajo para querySelectorAll como array. */
export const $$ = (sel, raiz = document) => Array.from(raiz.querySelectorAll(sel));

/**
 * Crea un elemento con atributos e hijos en una sola llamada.
 * Ej: crear("button", {class:"btn", onclick:fn}, "Comprar")
 */
export function crear(tag, attrs = {}, ...hijos) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === "class") el.className = v;
    else if (k === "html") el.innerHTML = v;
    else if (k.startsWith("on") && typeof v === "function") el.addEventListener(k.slice(2), v);
    else if (v !== null && v !== undefined) el.setAttribute(k, v);
  }
  hijos.flat().forEach(h => el.append(h?.nodeType ? h : document.createTextNode(h ?? "")));
  return el;
}

/** Muestra un aviso flotante (toast) que se va solo. */
export function toast(mensaje, tipo = "ok") {
  let cont = $("#toasts");
  if (!cont) {
    cont = crear("div", { id: "toasts", class: "toasts" });
    document.body.append(cont);
  }
  const t = crear("div", { class: `toast toast--${tipo}`, role: "status" }, mensaje);
  cont.append(t);
  setTimeout(() => t.classList.add("toast--saliendo"), 2500);
  setTimeout(() => t.remove(), 3000);
}
