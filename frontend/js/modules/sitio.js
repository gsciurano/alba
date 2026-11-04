/**
 * js/modules/sitio.js
 * ============================================================
 * ENCABEZADO Y PIE compartidos por todas las paginas publicas.
 *
 * POR QUE SE ARMAN DESDE JAVASCRIPT Y NO SE ESCRIBEN EN CADA HTML:
 * el sitio tiene seis paginas publicas. Si la barra de navegacion
 * estuviera copiada en cada archivo, agregar un enlace obligaria a tocar
 * seis lugares y cualquier olvido dejaria una pagina distinta de las
 * otras. Aca se escribe una vez.
 *
 * Ademas el encabezado cambia segun quien este mirando: un visitante ve
 * "Ingresar", un cliente ve su nombre y "Mis pedidos", y un administrador
 * ve ademas el acceso al panel. Esa decision tiene que vivir en un solo
 * lugar, no repetida en cinco archivos.
 *
 * La estructura del menu sigue la del sitio real de la bodega
 * (albaenlosandes.com), con "Nosotros" sumado para el equipo del
 * proyecto: Inicio · Vinos · Comprar · Finca · Nosotros · Contacto.
 * ============================================================
 */

import { sesion, carrito } from "./store.js";
import { crear, $, $$ } from "./util.js";

/*
 * Texto OFICIAL de las normas que se citan en el pie. Son las paginas
 * del Estado, no resumenes de terceros:
 *
 *   Ley 24.788 (Lucha contra el Alcoholismo). Su art. 1 prohibe el
 *   expendio de bebidas alcoholicas a menores de 18 anios en todo el
 *   pais; el art. 5 exige las leyendas "Beber con moderación" y
 *   "Prohibida su venta a menores de 18 años" en los envases, y el
 *   art. 6 inc. e) las exige tambien en la publicidad.
 *
 *   Resolución 424/2020 de la Secretaría de Comercio Interior. Obliga a
 *   quien venda por web o aplicacion a publicar el link "Botón de
 *   Arrepentimiento", con acceso facil y directo desde la pagina de
 *   inicio y en lugar destacado. Remite al art. 34 de la Ley 24.240 y
 *   al art. 1110 del Codigo Civil y Comercial.
 */
const LEY_ALCOHOL =
  "https://www.argentina.gob.ar/normativa/nacional/ley-24788-42480/texto";
const BOTON_ARREPENTIMIENTO =
  "https://www.argentina.gob.ar/normativa/nacional/norma-342869/texto";
const LEY_CONSUMIDOR =
  "https://www.argentina.gob.ar/normativa/nacional/ley-24240-638/texto";

/** Las paginas del sitio, en el orden en que van en la barra. */
const PAGINAS = [
  { id: "inicio",   texto: "Inicio",   href: "index.html"    },
  { id: "vinos",    texto: "Vinos",    href: "vinos.html"    },
  { id: "tienda",   texto: "Comprar",  href: "tienda.html"   },
  { id: "finca",    texto: "Finca",    href: "finca.html"    },
  { id: "nosotros", texto: "Nosotros", href: "nosotros.html" },
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
        // El nombre va en mayusculas, pero con CSS (text-transform) y no
        // cambiando el texto: asi lo que se guarda y lo que lee un lector
        // de pantalla sigue siendo el nombre como lo escribio la persona.
        crear("a", { href: "cuenta.html", class: "cinta__enlace",
          html: icono("persona") + '<span class="cinta__usuario">' + u.nombre + "</span>" }),
        u.rol === "ADMIN"
          ? crear("a", { href: "admin.html", class: "cinta__enlace", html: icono("panel") + "<span>Panel</span>" })
          : "",
        crear("button", {
          class: "cinta__enlace cinta__enlace--boton",
          html: icono("salir") + "<span>Salir</span>",
          onclick: () => { sesion.logout(); location.href = "index.html"; }
        }))
    : crear("a", { href: "cuenta.html", class: "cinta__enlace",
                   html: icono("persona")
                       + "<span>Ingresar</span>"
                       + '<span class="cinta__opcional">/ Crear cuenta</span>' });

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
        /*
         * En el pie entra el logo COMPLETO (sello + nombre) y no el sello
         * suelto: antes el nombre iba aparte, escrito como texto, y se
         * repetia lo que el logo ya dice. El alt lleva el nombre de la
         * bodega porque aca la imagen SI es informacion; en el encabezado
         * va vacio, que ahi al lado esta el nombre escrito.
         */
        crear("img", {
          class: "sitio-pie__logo", src: "assets/img/sitio/logo-completo.png",
          width: 884, height: 548, alt: "Alba en los Andes"
        }),
        crear("p", {}, "Viñedos de altura en el Valle de Uco, a 1.100 metros sobre el nivel del mar.")),
      crear("div", {},
        crear("h3", {}, "El sitio"),
        ...PAGINAS.map(p => crear("a", { href: p.href }, p.texto))),
      crear("div", {},
        crear("h3", {}, "Contacto"),
        crear("a", { href: "mailto:contacto@albaenlosandes.com" }, "contacto@albaenlosandes.com"),
        crear("a", { href: "contacto.html" }, "Formulario de contacto"),
        crear("p", {}, "Tupungato, Mendoza, Argentina")),
      /*
       * La columna de la cuenta.
       *
       * Las secciones del perfil van SIEMPRE, con o sin sesion, cada una
       * con su enlace directo: cuenta.html las elige por el #hash, asi
       * que se puede caer en "Mis solicitudes" sin pasar antes por
       * "Datos principales". Un solo enlace a "Mi cuenta" obligaba a
       * entrar y despues buscar.
       *
       * POR QUE TAMBIEN PARA UN VISITANTE: el pie es donde se busca que
       * ofrece un sitio. Esconder las secciones hasta despues de entrar
       * hace que no se sepa que existen. Si todavia no ingreso, el enlace
       * lo lleva a cuenta.html, que le muestra el formulario de ingreso:
       * el mismo camino que si tocara "Ingresar", nada mas que sabiendo
       * a que iba.
       */
      crear("div", {},
        crear("h3", {}, "Tu cuenta"),
        u ? "" : crear("a", { href: "cuenta.html" }, "Ingresar o crear cuenta"),
        crear("a", { href: "cuenta.html#datos" },       "Mi perfil"),
        crear("a", { href: "cuenta.html#pedidos" },     "Mis pedidos"),
        crear("a", { href: "cuenta.html#solicitudes" }, "Mis solicitudes"),
        u?.rol === "ADMIN" ? crear("a", { href: "admin.html" }, "Panel de gestión") : ""),
      /*
       * COLUMNA LEGAL.
       *
       * Los enlaces van al texto OFICIAL de cada norma en
       * argentina.gob.ar, no a un resumen escrito por nosotros: quien
       * quiera verificar que dice la ley tiene que poder leerla en la
       * fuente, no creernos a nosotros.
       *
       * Se abren en una pestania nueva porque sacan a la persona del
       * sitio en medio de una compra; rel="noopener" es obligatorio con
       * target="_blank": sin el, la pagina que se abre puede manipular
       * la nuestra a traves de window.opener.
       */
      crear("div", {},
        crear("h3", {}, "Legal"),
        crear("a", { href: LEY_ALCOHOL, target: "_blank", rel: "noopener noreferrer" },
              "Ley 24.788 — venta de alcohol ↗"),
        crear("a", { href: BOTON_ARREPENTIMIENTO, target: "_blank", rel: "noopener noreferrer" },
              "Resolución 424/2020 — arrepentimiento ↗"),
        crear("a", { href: LEY_CONSUMIDOR, target: "_blank", rel: "noopener noreferrer" },
              "Ley 24.240 — defensa del consumidor ↗"))),

    /*
     * La franja de abajo lleva las dos leyendas que la Ley 24.788 exige
     * en los envases (art. 5) y en la publicidad (art. 6 inc. e), con el
     * numero de ley a la vista y enlazado a su texto.
     */
    crear("div", { class: "sitio-pie__legal" },
      // El numero de ley va al final y sin punto detras: puesto en el
      // medio, el punto que lo seguia caia solo en un renglon aparte.
      crear("span", {},
        "Beber con moderación. Prohibida su venta a menores de 18 años — ",
        crear("a", { href: LEY_ALCOHOL, target: "_blank", rel: "noopener noreferrer" },
              "Ley 24.788"))));

  document.body.prepend(cabecera);
  document.body.append(pie);
  actualizarCarrito();
  activarRevelado();
}

/**
 * Hace que cada seccion aparezca al llegar a ella con el scroll.
 *
 * POR QUE LA CLASE LA PONE EL JAVASCRIPT Y NO EL HTML: si estuviera
 * escrita en el HTML, con el JavaScript apagado o fallado las secciones
 * quedarian invisibles para siempre, porque nadie les sacaria la clase.
 * Poniendola desde aca, el peor caso es que no haya animacion.
 *
 * No se aplica a quien pidio menos movimiento en su sistema operativo,
 * ni en navegadores sin IntersectionObserver: en los dos casos se sale
 * sin tocar nada y el contenido se ve de entrada.
 */
function activarRevelado() {
  const prefiereQuieto = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (prefiereQuieto || !("IntersectionObserver" in window)) return;

  // Solo las secciones de primer nivel: anidar el efecto lo duplica.
  const secciones = $$("#contenido .seccion");
  if (!secciones.length) return;

  const observador = new IntersectionObserver((entradas) => {
    entradas.forEach(e => {
      if (!e.isIntersecting) return;
      e.target.classList.add("revelar--visible");
      observador.unobserve(e.target);   // una sola vez por seccion
    });
  }, { rootMargin: "0px 0px -80px 0px" });

  secciones.forEach(sec => { sec.classList.add("revelar"); observador.observe(sec); });
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
