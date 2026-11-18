/**
 * js/modules/validacion.js
 * ============================================================
 * Las reglas de EMAIL y CONTRASENA, en un solo lugar.
 *
 * POR QUE UN MODULO APARTE: las mismas reglas hacen falta en tres
 * pantallas distintas (el registro y Seguridad en cuenta.js, y el alta
 * de usuarios del panel en admin.js). Copiadas en tres lados, tarde o
 * temprano quedan diciendo cosas distintas y el usuario recibe un
 * mensaje en una pantalla y otro en la otra.
 *
 * LO QUE ESTO NO REEMPLAZA: el back-end valida lo mismo por su cuenta
 * (UsuarioService). Todo esto corre en el navegador y se saltea con
 * cualquier cliente HTTP, asi que sirve para avisarle rapido y claro a
 * una persona, no para proteger la base.
 * ============================================================
 */

import { crear, $ } from "./util.js";

// ============================================================
//  CONTRASENA
// ============================================================

/**
 * Las cuatro condiciones, con el texto con el que se muestran y con el
 * que se nombran cuando faltan. Agregar una regla es agregar un renglon:
 * la lista de la pantalla y el mensaje de error salen los dos de aca.
 */
export const REGLAS_PASSWORD = [
  { id: "largo",     texto: "al menos 8 caracteres", cumple: (v) => v.length >= 8 },
  { id: "mayuscula", texto: "una mayúscula",         cumple: (v) => /[A-ZÁÉÍÓÚÜÑ]/.test(v) },
  { id: "minuscula", texto: "una minúscula",         cumple: (v) => /[a-záéíóúüñ]/.test(v) },
  { id: "numero",    texto: "un número",             cumple: (v) => /[0-9]/.test(v) }
];

/** Las condiciones que todavia no cumple, con su texto. */
export const faltaEnPassword = (valor) =>
  REGLAS_PASSWORD.filter(r => !r.cumple(String(valor || ""))).map(r => r.texto);

/**
 * "una mayúscula y un número" / "al menos 8 caracteres, una mayúscula y un número".
 * Se enumera como se enumera hablando, con coma y una "y" antes del ultimo.
 */
function enumerar(lista) {
  if (lista.length <= 1) return lista.join("");
  return lista.slice(0, -1).join(", ") + " y " + lista[lista.length - 1];
}

/**
 * El error para mostrar al mandar el formulario, o "" si esta bien.
 *
 * Dice QUE LE FALTA y no la regla entera: quien ya puso ocho caracteres
 * y un numero no necesita que le repitan las cuatro condiciones, necesita
 * saber que le falta una mayuscula.
 */
export function errorPassword(valor, cual = "la contraseña") {
  const falta = faltaEnPassword(valor);
  if (!falta.length) return "";
  return "A " + cual + " le falta: " + enumerar(falta) + ".";
}

/**
 * La lista de requisitos que se ve ANTES de escribir y se va marcando
 * mientras se tipea. Devuelve el nodo y la funcion para refrescarlo.
 *
 * SOBRE LECTORES DE PANTALLA: cada renglon lleva su estado en el
 * aria-label en vez de poner la lista entera en aria-live. Con aria-live
 * se anunciaria la lista completa en cada tecla, que es inusable; asi,
 * quien recorre los requisitos escucha cual cumplio y cual no.
 */
export function requisitosPassword(titulo = "La contraseña tiene que tener:") {
  const renglones = REGLAS_PASSWORD.map(r =>
    crear("li", { class: "requisito", "data-regla": r.id },
      crear("span", { class: "requisito__marca", "aria-hidden": "true" }, "○"),
      crear("span", { class: "requisito__texto" }, r.texto)));

  const nodo = crear("div", { class: "requisitos" },
    crear("p", { class: "requisitos__titulo" }, titulo),
    crear("ul", { class: "requisitos__lista" }, ...renglones));

  function revisar(valor) {
    const v = String(valor || "");
    REGLAS_PASSWORD.forEach((r, i) => {
      const ok = r.cumple(v);
      renglones[i].classList.toggle("requisito--cumple", ok);
      $(".requisito__marca", renglones[i]).textContent = ok ? "✓" : "○";
      renglones[i].setAttribute("aria-label", r.texto + (ok ? ", cumplido" : ", falta"));
    });
  }

  revisar("");           // arranca con las cuatro sin cumplir, a la vista
  return { nodo, revisar };
}

/** Engancha la lista a un campo para que se marque sola al tipear. */
export function vigilarPassword(input, requisitos) {
  input.addEventListener("input", () => requisitos.revisar(input.value));
}

// ============================================================
//  EMAIL
// ============================================================

/*
 * HASTA DONDE LLEGA ESTO: solo comprueba la FORMA. Que el dominio
 * exista necesita una consulta DNS, que la casilla exista solo lo sabe
 * el servidor de correo destino (y casi todos se niegan a contestarlo,
 * justamente para que nadie averigue que direcciones estan registradas),
 * y que la persona sea duenia de esa casilla no lo prueba nada salvo
 * mandarle un correo de confirmacion.
 *
 * Tampoco se sigue el RFC 5322 al pie: ese estandar acepta cosas como
 * "juan perez"@[192.168.1.1] y su expresion regular completa tiene miles
 * de caracteres. Se valida el subconjunto practico, que es el que atrapa
 * los errores de tipeo reales.
 */
const DOMINIO = /^[a-zA-Z0-9-]+(\.[a-zA-Z0-9-]+)*\.[a-zA-Z]{2,}$/;
const LOCAL   = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+$/;

/** El error para mostrar, o "" si la forma esta bien. */
export function errorEmail(valor) {
  const v = String(valor || "").trim();

  if (!v) return "Escribí tu email.";
  if (/\s/.test(v)) return "El email no puede tener espacios.";

  const partes = v.split("@");
  if (partes.length !== 2 || !partes[0] || !partes[1]) {
    return "Falta el @: un email se escribe nombre@dominio.com.";
  }

  const [local, dominio] = partes;
  if (v.includes("..") || local.startsWith(".") || local.endsWith(".")) {
    return "El email tiene un punto de más.";
  }
  // Cubre "juan@mail" (sin punto), "juan@mail." (cortado) y "juan@mail.c".
  if (!DOMINIO.test(dominio)) {
    return "Al dominio le falta el final, como .com o .com.ar.";
  }
  if (!LOCAL.test(local)) {
    return "El email tiene caracteres que no van antes del @.";
  }
  return "";
}
