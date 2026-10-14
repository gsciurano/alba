/**
 * js/modules/sitio.js
 * ============================================================
 * ENCABEZADO Y PIE compartidos por todas las paginas publicas.
 *
 * POR QUE SE ARMAN DESDE JAVASCRIPT Y NO SE ESCRIBEN EN CADA HTML:
 * el sitio tiene cinco paginas publicas. Si la barra de navegacion
 * estuviera copiada en cada archivo, agregar un enlace obligaria a tocar
 * cinco lugares y cualquier olvido dejaria una pagina distinta de las
 * otras. Aca se escribe una vez.
 *
 * Ademas el encabezado cambia segun quien este mirando: un visitante ve
 * "Ingresar", un cliente ve su nombre y "Mis pedidos", y un administrador
 * ve ademas el acceso al panel. Esa decision tiene que vivir en un solo
 * lugar, no repetida en cinco archivos.
 *
 * La estructura del menu sigue la del sitio real de la bodega
 * (albaenlosandes.com): Inicio · Vinos · Comprar · Finca · Contacto.
 * ============================================================
 */

import { sesion, carrito } from "./store.js";
import { crear, $, $$ } from "./util.js";

/** Las paginas del sitio, en el orden en que van en la barra. */
const PAGINAS = [
  { id: "inicio",   texto: "Inicio",   href: "index.html"    },
  { id: "vinos",    texto: "Vinos",    href: "vinos.html"    },
  { id: "tienda",   texto: "Comprar",  href: "tienda.html"   },
  { id: "finca",    texto: "Finca",    href: "finca.html"    },
  { id: "contacto", texto: "Contacto", href: "contacto.html" }
];

/**
 * El sello de la marca: es el logo real de la bodega, un PNG blanco sobre
 * fondo transparente, asi que se apoya tal cual sobre la banda vino del
 * encabezado y del pie sin necesidad de recortarlo de nuevo.
 */
function sello(tamano = 44) {
  return `<img class="sello" src="assets/img/sitio/logo-sello.png"
           width="${tamano}" height="${tamano}" alt="" aria-hidden="true">`;
}

const ICONOS = {
  carrito: '<circle cx="9" cy="20" r="1.4"/><circle cx="18" cy="20" r="1.4"/><path d="M2 3h3l2.5 12h11L21 7H6"/>',
  persona: '<circle cx="12" cy="8" r="3.6"/><path d="M4.5 20c0-3.6 3.4-5.6 7.5-5.6s7.5 2 7.5 5.6"/>',
  panel:   '<rect x="3" y="3" width="7.5" height="7.5" rx="1"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="1"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="1"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1"/>',
  salir:   '<path d="M15 4h4a1 1 0 011 1v14a1 1 0 01-1 1h-4"/><path d="M10 8l-4 4 4 4M6 12h10"/>'
};
const icono = (n) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
  stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">${ICONOS[n]}</svg>`;

/**
 * Dibuja el encabezado y el pie, y marca cual pagina esta abierta.
 * 'actual' es el id de la pagina (inicio, vinos, tienda, finca, contacto).
 */
export function montarSitio(actual) {
  const u = sesion.actual();

  // ---------- barra fina de arriba ----------
  const cuenta = u
    ? crear("div", { class: "cinta__cuenta" },
        crear("a", { href: "cuenta.html", class: "cinta__enlace", html: icono("persona") + "<span>" + u.nombre + "</span>" }),
        u.rol === "ADMIN"
          ? crear("a", { href: "admin.html", class: "cinta__enlace", html: icono("panel") + "<span>Panel</span>" })
          : "",
        crear("button", {
          class: "cinta__enlace cinta__enlace--boton",
          html: icono("salir") + "<span>Salir</span>",
          onclick: () => { sesion.logout(); location.href = "index.html"; }
        }))
    : crear("a", { href: "cuenta.html", class: "cinta__enlace",
                   html: icono("persona") + "<span>Ingresar</span>" });

  const cinta = crear("div", { class: "cinta" },
    crear("div", { class: "cinta__interior" },
      crear("span", { class: "cinta__dato" }, "Valle de Uco · Tupungato, Mendoza"),
      cuenta));

  // ---------- marca y navegacion ----------
  const marca = crear("a", { class: "sitio-marca", href: "index.html",
    html: sello(46) + '<span class="sitio-marca__texto">Alba en los Andes</span>' });

  const nav = crear("nav", { class: "sitio-nav", "aria-label": "Navegación principal" },
    ...PAGINAS.map(p => crear("a", {
      href: p.href,
      class: "sitio-nav__item" + (p.id === actual ? " activo" : ""),
      "aria-current": p.id === actual ? "page" : null
    }, p.texto)));

  const btnCarrito = crear("button", {
    class: "sitio-carrito", id: "btnCarrito", "aria-label": "Abrir carrito",
    html: icono("carrito") + '<span class="sitio-carrito__cuenta" id="cuentaCarrito" hidden></span>'
  });
  // La tienda maneja el carrito; desde el resto de las paginas se va a ella.
  if (actual !== "tienda") {
    btnCarrito.addEventListener("click", () => { location.href = "tienda.html#carrito"; });
  }

  const cabecera = crear("header", { class: "sitio-cabecera" },
    cinta,
    crear("div", { class: "sitio-cabecera__fila" }, marca, nav, btnCarrito));

  // ---------- pie ----------
  const pie = crear("footer", { class: "sitio-pie" },
    crear("div", { class: "sitio-pie__interior" },
      crear("div", {},
        crear("div", { class: "sitio-pie__marca", html: sello(38) }),
        crear("p", { class: "sitio-pie__nombre" }, "Alba en los Andes"),
        crear("p", {}, "Viñedos de altura en el Valle de Uco, a 1.100 metros sobre el nivel del mar.")),
      crear("div", {},
        crear("h3", {}, "El sitio"),
        ...PAGINAS.map(p => crear("a", { href: p.href }, p.texto))),
      crear("div", {},
        crear("h3", {}, "Contacto"),
        crear("a", { href: "mailto:contacto@albaenlosandes.com" }, "contacto@albaenlosandes.com"),
        crear("a", { href: "contacto.html" }, "Formulario de contacto"),
        crear("p", {}, "Tupungato, Mendoza, Argentina")),
      crear("div", {},
        crear("h3", {}, "Tu cuenta"),
        crear("a", { href: "cuenta.html" }, u ? "Mis pedidos" : "Ingresar o crear cuenta"),
        u?.rol === "ADMIN" ? crear("a", { href: "admin.html" }, "Panel de gestión") : "")),
    crear("div", { class: "sitio-pie__legal" },
      crear("span", {}, "Beber con moderación. Prohibida su venta a menores de 18 años."),
      crear("span", {}, "Trabajo Integrador de Aplicaciones Interactivas · UADE")));

  document.body.prepend(cabecera);
  document.body.append(pie);
  actualizarCarrito();
}

/** Pone el numero de botellas en la burbuja del carrito. */
export function actualizarCarrito() {
  const n = carrito.cantidadTotal();
  const burbuja = $("#cuentaCarrito");
  if (!burbuja) return;
  burbuja.textContent = String(n);
  burbuja.hidden = n === 0;
}

/**
 * Pantalla para cuando el back-end no responde. Se avisa explicitamente
 * en vez de mostrar una pagina vacia, que haria pensar que no hay nada.
 */
export function avisoSinServidor(mensaje) {
  return crear("div", { class: "aviso-grande" },
    crear("h2", {}, "No se puede conectar con el servidor"),
    crear("p", {}, mensaje),
    crear("p", { class: "aviso-grande__pista" },
      "Los datos se leen de la API en localhost:8080."),
    crear("button", { class: "btn btn--primario", onclick: () => location.reload() },
      "Reintentar"));
}
