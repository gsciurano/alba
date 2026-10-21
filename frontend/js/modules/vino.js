/**
 * js/modules/vino.js
 * ============================================================
 * La TARJETA DE VINO, en un solo lugar.
 *
 * La usan tres paginas: el inicio (los destacados), vinos (el catalogo
 * informativo) y la tienda (donde ademas se compra). Si cada una la
 * dibujara por su cuenta, un cambio de diseno habria que hacerlo tres
 * veces y es facil que queden distintas.
 *
 * Lo que cambia entre pantallas se pasa por parametro: si lleva boton de
 * compra y que hacer al tocarla.
 * ============================================================
 */

import { pesos, placeholderVino, crear } from "./util.js";

/**
 * Devuelve el <article> de un vino.
 *
 * opciones:
 *   comprable  true agrega el boton "Agregar"
 *   alAgregar  funcion que recibe el id, al tocar "Agregar"
 *   alAbrir    funcion que recibe el id, al tocar la tarjeta
 *   alEditar   funcion que recibe el vino; si viene, suma "Editar"
 *   alBorrar   funcion que recibe el vino; si viene, suma "Dar de baja"
 *
 * SOBRE EDITAR Y BORRAR: solo se pasan cuando quien mira es un
 * administrador. Un visitante o un cliente nunca reciben esas funciones,
 * asi que los botones ni siquiera se dibujan.
 *
 * Es un control de PANTALLA, no de seguridad: evita que esas acciones
 * esten a mano de cualquiera, pero los endpoints del back-end siguen
 * abiertos a quien llame a la API directamente. Cerrarlos es trabajo de
 * Spring Security, fuera del alcance de esta entrega.
 */
export function tarjetaVino(p, { comprable = false, alAgregar, alAbrir,
                                 alEditar, alBorrar } = {}) {
  const agotado = p.stock <= 0;
  const ultimas = !agotado && p.stock <= p.stockMinimo;

  const img = crear("img", { src: p.imagen, alt: "Botella de " + p.nombre, loading: "lazy" });
  // Si la foto no existe, se dibuja una botella en su lugar.
  img.addEventListener("error", () => { img.src = placeholderVino(p.nombre); });

  let etiqueta = "";
  if (comprable && agotado) etiqueta = crear("span", { class: "etiqueta-stock etiqueta-stock--agotado" }, "Agotado");
  else if (comprable && ultimas) etiqueta = crear("span", { class: "etiqueta-stock etiqueta-stock--bajo" }, "Últimas botellas");

  const cuerpo = crear("div", { class: "tarjeta__cuerpo" },
    crear("span", { class: "tarjeta__linea" }, p.linea),
    crear("h3", { class: "tarjeta__nombre" }, p.nombre),
    crear("span", { class: "tarjeta__meta" }, p.varietal + " · Cosecha " + p.cosecha),
    crear("div", { class: "tarjeta__pie" },
      crear("span", { class: "tarjeta__precio" }, pesos(p.precio)),
      etiqueta || ""));

  if (comprable) {
    const btn = crear("button", {
      class: "btn btn--primario btn--chico",
      disabled: agotado ? "true" : null,
      onclick: (e) => { e.stopPropagation(); alAgregar?.(p.id); }
    }, agotado ? "Sin stock" : "Agregar");
    cuerpo.append(btn);
  }

  // Acciones de gestion, solo si quien mira es administrador.
  if (alEditar || alBorrar) {
    const barra = crear("div", { class: "tarjeta__gestion" });
    if (alEditar) barra.append(crear("button", {
      class: "btn btn--fantasma btn--chico",
      onclick: (e) => { e.stopPropagation(); alEditar(p); }
    }, "Editar"));
    if (alBorrar) barra.append(crear("button", {
      class: "btn btn--peligro btn--chico",
      onclick: (e) => { e.stopPropagation(); alBorrar(p); }
    }, "Dar de baja"));
    cuerpo.append(barra);
  }

  const tarjeta = crear("article", {
    class: "tarjeta" + (alAbrir ? "" : " tarjeta--quieta"),
    tabindex: alAbrir ? "0" : null,
    role: alAbrir ? "button" : null,
    "aria-label": alAbrir ? "Ver detalle de " + p.nombre : null,
    onclick: alAbrir ? () => alAbrir(p.id) : null,
    onkeydown: alAbrir ? (e) => { if (e.key === "Enter") alAbrir(p.id); } : null
  },
    crear("div", { class: "tarjeta__foto" }, img),
    cuerpo);

  return tarjeta;
}

/** Una grilla con las tarjetas de una lista de vinos. */
export function grillaVinos(lista, opciones) {
  const grilla = crear("div", { class: "grilla" });
  lista.forEach(p => grilla.append(tarjetaVino(p, opciones)));
  return grilla;
}
