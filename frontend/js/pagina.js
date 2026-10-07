/**
 * js/pagina.js
 * ============================================================
 * Para las paginas que son SOLO CONTENIDO FIJO (finca.html).
 *
 * No piden nada a la API: lo unico que necesitan es el encabezado y el
 * pie compartidos. El id de la pagina se toma del atributo
 * data-pagina del <body>, o se deduce del nombre del archivo, para no
 * tener que escribir un archivo JavaScript por cada pagina de texto.
 * ============================================================
 */

import { montarSitio } from "./modules/sitio.js";

document.addEventListener("DOMContentLoaded", () => {
  const archivo = location.pathname.split("/").pop().replace(".html", "");
  montarSitio(document.body.dataset.pagina || archivo || "inicio");
});
