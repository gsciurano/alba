/**
 * js/inicio.js
 * ============================================================
 * Pagina de INICIO (index.html).
 *
 * Es casi toda contenido fijo, escrito en el HTML. Lo unico que sale de
 * la base son los vinos destacados de la seccion "Nuestros vinos": se
 * piden a la API para que lo que se muestra en la portada sea siempre lo
 * que de verdad hay a la venta, y no una lista que haya que mantener a
 * mano cada vez que entra o sale una etiqueta.
 * ============================================================
 */

import { montarSitio, avisoSinServidor } from "./modules/sitio.js";
import { productos, cargar } from "./modules/store.js";
import { grillaVinos } from "./modules/vino.js";
import { $, crear } from "./modules/util.js";

document.addEventListener("DOMContentLoaded", async () => {
  montarSitio("inicio");

  const caja = $("#vinosDestacados");
  caja.innerHTML = '<div class="cargando">' + '<div class="esqueleto"></div>'.repeat(4) + '</div>';

  try {
    await cargar();
  } catch (e) {
    caja.innerHTML = "";
    caja.append(avisoSinServidor(e.message));
    return;
  }

  // Se muestran hasta ocho; el resto esta en la tienda.
  const destacados = productos.getActivos().slice(0, 8);
  caja.innerHTML = "";

  if (!destacados.length) {
    caja.append(crear("p", { style: "text-align:center;color:var(--texto-tenue)" },
      "Por el momento no hay vinos disponibles."));
    return;
  }

  caja.append(grillaVinos(destacados, {
    comprable: false,
    alAbrir: (id) => { location.href = "tienda.html#vino-" + id; }
  }));
});
