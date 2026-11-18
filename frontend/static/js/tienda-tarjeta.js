/**
 * js/tienda-tarjeta.js
 * ============================================================
 * LO QUE SIGUE SIENDO JAVASCRIPT EN EL CHECKOUT, Y POR QUE.
 *
 * La migracion a Thymeleaf + HTMX movio al servidor todo lo que el
 * servidor podia hacer: el catalogo, los filtros, el carrito, las
 * cuentas y el marcado del formulario. Este archivo es lo que queda, y
 * queda por una razon concreta: son cosas que SOLO el navegador puede
 * hacer.
 *
 *   1. La vista previa de la tarjeta, que se completa mientras se
 *      escribe. Una vuelta al servidor por tecla seria absurda.
 *   2. El formateo del numero en grupos de cuatro y la barra del
 *      vencimiento, que tambien ocurren por tecla.
 *   3. El boton que inventa un numero de prueba valido.
 *   4. Copiar el alias al portapapeles, que es una API del navegador.
 *   5. Dibujar el QR, que es pintura en el cliente.
 *
 * NINGUNA DE LAS CINCO ES DIBUJAR DATOS DEL SERVIDOR. Esa es la linea:
 * al servidor lo que el servidor sabe, al navegador lo que solo el
 * navegador puede hacer.
 *
 * TODO ES OPCIONAL. Si este archivo no carga, el checkout sigue
 * funcionando: los campos son campos, el alias y el CVU estan escritos
 * en la pagina y la validacion de verdad corre en el servidor. Por eso
 * no hay ningun return temprano que rompa la pagina: cada bloque mira
 * si su elemento existe y si no, no hace nada.
 *
 * Reemplaza a bloqueTarjeta() de tienda.js:538 y a las funciones
 * copiar() y qrDelAlias() de modules/transferencia.js.
 * ============================================================
 */

document.addEventListener("DOMContentLoaded", () => {
  prepararTarjeta();
  prepararBancarios();
});

/*
 * El checkout cambia el bloque del medio de pago con HTMX, asi que
 * despues de cada intercambio hay que volver a enganchar lo de arriba:
 * el bloque de la tarjeta que acaba de llegar es marcado nuevo, sin
 * ningun evento puesto.
 *
 * htmx:afterSwap es el evento que avisa eso. Sin esto, elegir
 * "Transferencia" y volver a "Tarjeta" dejaba los campos sin formateo
 * y el boton de generar sin hacer nada.
 */
document.body.addEventListener("htmx:afterSwap", () => {
  prepararTarjeta();
  prepararBancarios();
});

// ============================================================
//  LA TARJETA
// ============================================================
function prepararTarjeta() {
  const bloque = document.getElementById("bloqueTarjeta");
  if (!bloque || bloque.dataset.listo === "si") return;
  bloque.dataset.listo = "si";          // no enganchar dos veces

  const numero = bloque.querySelector("#tarjetaNumero");
  const vence  = bloque.querySelector("#tarjetaVence");
  const cvv    = bloque.querySelector("#tarjetaCvv");
  const nombre = document.getElementById("nombre");
  if (!numero || !vence || !cvv) return;

  const vistaNumero  = bloque.querySelector(".plastico__numero");
  const vistaTitular = bloque.querySelectorAll(".plastico__dato")[0];
  const vistaVence   = bloque.querySelectorAll(".plastico__dato")[1];

  function pintar() {
    if (vistaNumero)  vistaNumero.textContent  = numero.value || "•••• •••• •••• ••••";
    if (vistaTitular) vistaTitular.textContent = (nombre?.value || "Nombre del titular").toUpperCase();
    if (vistaVence)   vistaVence.textContent   = vence.value || "MM/AA";
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
  nombre?.addEventListener("input", pintar);

  /*
   * POR QUE EXISTE EL BOTON DE GENERAR: esto es un trabajo practico,
   * no hay pasarela de pago. Para probar la compra hace falta un
   * numero que pase la validacion de Luhn, y pedirle a quien prueba
   * que se acuerde de uno (o que use el suyo real) seria peor.
   */
  bloque.querySelector("#generarTarjeta")?.addEventListener("click", () => {
    numero.value = generarNumeroTarjeta();
    vence.value  = generarVencimiento();
    cvv.value    = String(Math.floor(Math.random() * 900) + 100);
    [numero, vence, cvv].forEach(i => i.classList.remove("invalido"));
    pintar();
  });

  pintar();
}

function soloDigitos(texto, maximo) {
  return texto.replace(/\D/g, "").slice(0, maximo);
}

/**
 * Arma un numero de 16 digitos que pasa el algoritmo de Luhn, igual
 * que una tarjeta de verdad: 15 digitos al azar y el ultimo calculado
 * para que la suma cierre. Empieza con 4, el prefijo de Visa.
 *
 * El servidor hace la MISMA cuenta al recibirlo (DatosDeCompra
 * .isNumeroDeTarjetaValido), asi que un numero generado aca pasa el
 * control de alla. Si las dos cuentas se separaran, el boton generaria
 * numeros que el servidor rechaza.
 */
function generarNumeroTarjeta() {
  const digitos = ["4"];
  while (digitos.length < 15) digitos.push(String(Math.floor(Math.random() * 10)));

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

// ============================================================
//  LOS DATOS BANCARIOS: COPIAR Y EL QR
// ============================================================
function prepararBancarios() {
  const banco = document.getElementById("bloqueTransferencia");
  if (!banco || banco.dataset.listo === "si") return;
  banco.dataset.listo = "si";

  // ---- el boton de copiar en cada fila ----
  banco.querySelectorAll(".banco__fila").forEach(fila => {
    const valor = fila.querySelector(".banco__valor");
    if (!valor) return;
    const boton = document.createElement("button");
    boton.type = "button";          // sin esto enviaria el formulario del checkout
    boton.className = "banco__copiar";
    boton.textContent = "Copiar";
    boton.addEventListener("click", () => copiar(valor.textContent.trim(), boton, valor));
    fila.append(boton);
  });

  // ---- el QR del alias ----
  const caja = banco.querySelector(".banco__qr");
  const alias = caja?.dataset.qr;
  if (!caja || !alias) return;
  if (typeof qrcode !== "function") {
    // La libreria no cargo. El alias sigue escrito arriba, asi que la
    // pagina sirve igual: se deja la caja vacia y no se avisa nada.
    console.warn("No cargo js/vendor/qrcode.js: el bloque va sin QR.");
    return;
  }
  // tipo 0 = que la libreria elija el tamanio mas chico que entre;
  // "M" = nivel medio de correccion de errores, el habitual.
  const codigo = qrcode(0, "M");
  codigo.addData(alias);
  codigo.make();
  caja.innerHTML = codigo.createTableTag(4, 0);   // 4px por modulo, sin margen
}

/**
 * Copia un texto al portapapeles y avisa en el boton mismo.
 *
 * navigator.clipboard SOLO existe en HTTPS o en localhost. En el TP se
 * trabaja en localhost, asi que anda; si el sitio se publicara sin
 * HTTPS dejaria de andar. Por eso hay respaldo: si no esta disponible,
 * se selecciona el texto para que la persona copie con Ctrl+C en vez
 * de quedarse sin nada.
 */
async function copiar(texto, boton, campo) {
  const original = boton.textContent;
  try {
    await navigator.clipboard.writeText(texto);
    boton.textContent = "¡Copiado!";
    boton.classList.add("copiado");
  } catch {
    boton.textContent = "Copialo a mano";
    const rango = document.createRange();
    rango.selectNodeContents(campo);
    const sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(rango);
  }
  setTimeout(() => {
    boton.textContent = original;
    boton.classList.remove("copiado");
  }, 1800);
}
