/**
 * DATOS BANCARIOS Y BLOQUE DE TRANSFERENCIA DEL CHECKOUT.
 *
 * Son los datos REALES de la cuenta de la bodega. El CVU se verifico
 * con sus dos digitos verificadores antes de cargarlo: si alguna vez se
 * cambia, hay que volver a verificarlo, porque un digito mal escrito
 * manda la plata a otra cuenta que existe.
 *
 * ES UN CVU, NO UN CBU. El CVU es la cuenta de una billetera virtual
 * (el prefijo 000 y el alias terminado en ".mp" lo delatan), no la de
 * un banco. Para transferir da igual, pero el rotulo dice CVU porque
 * es lo que la persona va a buscar en su homebanking.
 *
 * POR QUE EL CVU VIVE EN EL CODIGO Y NO EN LA BASE:
 * no es un secreto. Es exactamente el dato que se le entrega a quien
 * tiene que pagarte; esta pensado para ser publico. Guardarlo en una
 * tabla agregaria una entidad, un repositorio y una pantalla de
 * edicion para un dato que cambia una vez cada varios anios.
 */

import { crear } from "./util.js";

/**
 * Los datos de la cuenta. Las filas con valor vacio NO se dibujan, asi
 * que para sumar el CUIT alcanza con completarlo aca: no hay que tocar
 * el resto del archivo. Preferir una fila de menos antes que un dato
 * inventado, que es lo peor que puede pasar en una pantalla de pago.
 */
export const DATOS_BANCARIOS = {
  ejemplo:  false,
  titular:  "ALBA EN LOS ANDES SA",
  cvu:      "0000003100019063106767",
  alias:    "tour.saxo.suma.mp",
  cuit:     "",                       // falta: cuando lo tengamos, aparece solo
  contacto: "pagos@albaenlosandes.com"
};

/**
 * Copia un texto al portapapeles y avisa en el boton mismo.
 *
 * navigator.clipboard SOLO existe en HTTPS o en localhost. En el TP se
 * trabaja en localhost, asi que anda; si el sitio se publicara sin
 * HTTPS dejaria de andar. Por eso hay respaldo: si no esta disponible,
 * se selecciona el texto para que la persona copie con Ctrl+C en vez
 * de quedarse sin nada.
 */
async function copiar(texto, boton) {
  const original = boton.textContent;
  try {
    await navigator.clipboard.writeText(texto);
    boton.textContent = "¡Copiado!";
    boton.classList.add("copiado");
  } catch {
    boton.textContent = "Copialo a mano";
    const campo = boton.closest(".banco__fila")?.querySelector(".banco__valor");
    if (campo) {
      const rango = document.createRange();
      rango.selectNodeContents(campo);
      const sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(rango);
    }
  }
  setTimeout(() => {
    boton.textContent = original;
    boton.classList.remove("copiado");
  }, 1800);
}

/**
 * Un renglon de dato bancario con su boton de copiar.
 * Si el valor esta vacio devuelve "" y la fila no se dibuja.
 */
function fila(rotulo, valor, copiable = true) {
  if (!valor) return "";
  const texto = crear("span", { class: "banco__valor" }, valor);
  return crear("div", { class: "banco__fila" },
    crear("span", { class: "banco__rotulo" }, rotulo),
    texto,
    copiable
      ? crear("button", {
          type: "button",             // sin esto enviaria el formulario del checkout
          class: "banco__copiar",
          onclick: (e) => copiar(valor, e.currentTarget)
        }, "Copiar")
      : "");
}

/**
 * Dibuja un QR con el alias de la cuenta.
 *
 * QUE ES Y QUE NO ES, PORQUE IMPORTA:
 * este QR lleva TEXTO, el alias y nada mas. Quien lo escanea ve
 * "tour.saxo.suma.mp" y lo copia. NO abre ninguna billetera, NO carga el
 * monto y NO cobra: sirve para no tipear el alias a mano, que ya es algo.
 *
 * Los QR que cobran de verdad son los interoperables (Transferencias
 * 3.0): los emite un banco o un proveedor de pagos contra la cuenta, con
 * un formato firmado que no se puede fabricar desde afuera. Si
 * invtentaramos uno, ninguna app lo leeria como un cobro. Por eso el
 * titulo dice "escanealo para copiar el alias" y no "paga con QR".
 *
 * Se dibuja con <table> (lo que devuelve la libreria) en vez de <canvas>
 * porque escala con CSS sin verse borroso y se puede imprimir.
 */
function qrDelAlias(alias) {
  // Si el <script> de la libreria no cargo, el resto del bloque tiene que
  // seguir funcionando: el alias y el CVU estan escritos igual mas arriba.
  if (typeof qrcode !== "function") {
    console.warn("No cargo js/vendor/qrcode.js: se muestra el bloque sin QR.");
    return "";
  }
  // tipo 0 = que la libreria elija el tamanio mas chico que entre;
  // "M" = nivel medio de correccion de errores, el habitual.
  const codigo = qrcode(0, "M");
  codigo.addData(alias);
  codigo.make();

  const caja = crear("div", { class: "banco__qr" });
  caja.innerHTML = codigo.createTableTag(4, 0);   // 4px por modulo, sin margen extra
  return crear("div", { class: "banco__qr-caja" },
    caja,
    crear("p", { class: "banco__qr-pie" },
      crear("strong", {}, "Escaneá para copiar el alias. "),
      "No es un QR de pago: no abre la billetera ni carga el monto."));
}

/**
 * El bloque que aparece cuando se elige TRANSFERENCIA.
 *
 * POR QUE SE MUESTRA ANTES DE CONFIRMAR Y NO SOLO DESPUES:
 * quien paga por transferencia quiere ver a donde va la plata antes de
 * comprometerse. Esconder el CBU hasta despues de confirmar obliga a
 * decidir a ciegas.
 *
 * Los datos se repiten en la pantalla de confirmacion y en el mail,
 * porque nadie transfiere en el mismo segundo en que compra.
 */
export function bloqueTransferencia() {
  const d = DATOS_BANCARIOS;

  const nodo = crear("div", { class: "banco", id: "bloqueTransferencia", hidden: "hidden" },
    d.ejemplo
      ? crear("p", { class: "banco__aviso" },
          "⚠ Datos de ejemplo: todavía no son la cuenta real de la bodega.")
      : "",
    crear("h4", { class: "banco__titulo" }, "Datos para transferir"),
    /*
     * El alias va PRIMERO: son cuatro palabras contra veintidos digitos,
     * y es lo que casi todo el mundo usa para transferir. El CVU queda
     * de respaldo para quien prefiera pegarlo.
     */
    fila("Alias", d.alias),
    fila("CVU", d.cvu),
    fila("Titular", d.titular),
    fila("CUIT", d.cuit),
    qrDelAlias(d.alias),
    crear("p", { class: "banco__paso" },
      crear("strong", {}, "Después de transferir: "),
      "envianos el comprobante a ",
      crear("a", { href: "mailto:" + d.contacto }, d.contacto),
      " con el número de pedido. Lo revisamos y te confirmamos el pago."),
    crear("p", { class: "banco__demora" },
      "Tu pedido queda reservado. Preparamos el envío cuando vemos la transferencia acreditada."));

  return nodo;
}
