/**
 * js/cuenta.js
 * ============================================================
 * MI CUENTA (cuenta.html). Tiene dos caras:
 *
 *   VISITANTE  ingresar o crear cuenta
 *   CLIENTE    su PERFIL completo, con menu lateral: datos personales,
 *              pedidos, solicitudes de cancelacion, preferencias de vino
 *              y cambio de contrasena
 *
 * COMO SE ELIGE LA SECCION: por el #hash de la direccion
 * (cuenta.html#pedidos). Asi cada seccion tiene su propio enlace, se
 * puede llegar derecho desde el pie del sitio y el boton Atras del
 * navegador funciona como espera cualquiera.
 *
 * A DONDE VA CADA UNO AL INGRESAR: el rol lo decide el servidor
 * (POST /api/usuarios/login). Si vuelve ADMIN, se va derecho al panel de
 * gestion; si vuelve CLIENTE, se queda en la tienda. El navegador no
 * elige: solo obedece lo que contesto la API.
 *
 * SOBRE LA CANCELACION: la regla de si se puede cancelar directo o si
 * hay que revisar el caso vive en PoliticaCancelacion, del lado del
 * servidor. Aca se le pregunta antes de mostrar el formulario y se
 * muestra lo que conteste. Copiar la regla al navegador obligaria a
 * mantenerla en dos lugares, y podrian terminar diciendo cosas distintas.
 * ============================================================
 */

import { montarSitio, avisoSinServidor } from "./modules/sitio.js";
import { sesion, ErrorApi } from "./modules/store.js";
import { api } from "./modules/api.js";
import { pesos, fecha, fechaCorta, $, $$, crear, toast } from "./modules/util.js";
import { errorEmail, errorPassword, requisitosPassword, vigilarPassword }
  from "./modules/validacion.js";

/** Los motivos de cancelacion, como los lee una persona. */
const MOTIVOS = {
  ARREPENTIMIENTO:     "Me arrepentí de la compra",
  ERROR_AL_COMPRAR:    "Me equivoqué de vino o de cantidad",
  PROBLEMA_PAGO:       "Tuve un problema con el pago",
  DEMORA_ENTREGA:      "La entrega se está demorando",
  PRODUCTO_EQUIVOCADO: "Me llegó algo distinto a lo que pedí",
  OTRO:                "Otro motivo"
};
const ESTADOS_SOLICITUD = {
  PENDIENTE:   "Pendiente",
  EN_REVISION: "En revisión",
  APROBADA:    "Aprobada",
  RECHAZADA:   "Rechazada"
};

document.addEventListener("DOMContentLoaded", () => {
  montarSitio("cuenta");
  pintar();
});

function pintar() {
  const caja = $("#vistaCuenta");
  caja.innerHTML = "";
  const u = sesion.actual();
  if (!u) caja.append(pantallaIngreso());
  else    pantallaCliente(caja, u);
}

// ============================================================
//  VISITANTE — ingresar o crear cuenta
// ============================================================

function pantallaIngreso() {
  const envoltorio = crear("div", {});

  envoltorio.append(crear("section", { class: "portada-chica" },
    crear("h1", {}, "Mi cuenta"),
    crear("p", {}, "Ingresá para seguir tus pedidos, o creá tu cuenta en un minuto.")));

  const panelIngreso  = formIngreso();
  const panelRegistro = formRegistro();
  panelRegistro.hidden = true;

  const solapas = crear("div", { class: "solapas" },
    crear("button", { class: "solapa activo", id: "solapaIngreso" }, "Ingresar"),
    crear("button", { class: "solapa", id: "solapaRegistro" }, "Crear cuenta"));

  const mostrar = (cual) => {
    panelIngreso.hidden  = cual !== "ingreso";
    panelRegistro.hidden = cual !== "registro";
    $("#solapaIngreso", solapas).classList.toggle("activo", cual === "ingreso");
    $("#solapaRegistro", solapas).classList.toggle("activo", cual === "registro");
  };
  $("#solapaIngreso", solapas).addEventListener("click", () => mostrar("ingreso"));
  $("#solapaRegistro", solapas).addEventListener("click", () => mostrar("registro"));

  envoltorio.append(crear("section", { class: "seccion" },
    crear("div", { class: "caja-cuenta" }, solapas, panelIngreso, panelRegistro)));

  return envoltorio;
}

/*
 * El MISMO Client ID que usa el back-end (alba.google.client-id en
 * application.properties). El back-end comprueba que el token venga
 * emitido para este client id, asi que si no coinciden, Google firma
 * un token valido y nuestro servidor lo rechaza igual.
 *
 * Es publico por diseno: viaja en el HTML y cualquiera que abra el
 * sitio lo ve. El secreto del cliente no esta aca ni hace falta.
 */
const GOOGLE_CLIENT_ID =
  "417202778791-af38hruvgs1tust27jj050lkll4cm6sm.apps.googleusercontent.com";

/**
 * El boton oficial de Google.
 *
 * POR QUE EL BOTON LO DIBUJA GOOGLE Y NO NOSOTROS: su marca tiene reglas
 * de uso (el logo, los colores, el texto). Dibujarlo a mano con una
 * imagen del logo bajada por ahi las incumple. Ademas el de ellos ya
 * viene traducido y accesible.
 *
 * SI EL SCRIPT NO CARGO (sin internet, o bloqueado), esta funcion no
 * rompe nada: queda el ingreso con email y contrasena, que es el que
 * siempre tiene que funcionar.
 */
function botonGoogle(alError) {
  const caja = crear("div", { class: "ingreso-google" });

  /*
   * initialize() se llama UNA SOLA VEZ. El bucle de reintento de mas
   * abajo puede ejecutar dibujar() varias veces mientras espera, y
   * Google avisa en consola si se lo inicializa de nuevo ("only the
   * last initialized instance will be used").
   */
  let iniciado = false;

  const dibujar = () => {
    if (!window.google?.accounts?.id) return false;

    /*
     * EL ANCHO SE MIDE, NO SE ADIVINA.
     *
     * Google dibuja su boton adentro de un iframe, y el ancho de adentro
     * no se toca con CSS: hay que pedirselo al dibujarlo. Con un numero
     * fijo, el boton queda mas angosto que el de "Ingresar" de arriba y
     * los bordes no coinciden.
     *
     * Se mide ANTES de inicializar: si la caja todavia no esta en la
     * pagina mide cero, y conviene volver a intentar sin haber tocado
     * nada de Google.
     */
    const ancho = Math.round(caja.getBoundingClientRect().width);
    if (ancho === 0) return false;

    if (!iniciado) {
      /*
       * De que origen cree Google que viene esta pagina.
       *
       * Si no figura TAL CUAL en "Origenes autorizados de JavaScript" de
       * Google Cloud, el ingreso termina en "Error 400: origin_mismatch".
       * Dos trampas que no se ven:
       *   - http://127.0.0.1:5500 y http://localhost:5500 son origenes
       *     DISTINTOS para Google, aunque sean la misma maquina;
       *   - Live Server (5500) y python3 -m http.server (8099) tambien.
       * Por eso se escribe en la consola: para copiarlo y pegarlo.
       */
      console.info("[Google] Origen de esta pagina: " + location.origin +
        " — tiene que estar cargado tal cual en Google Cloud, en " +
        '"Origenes autorizados de JavaScript".');

      window.google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: async (respuesta) => {
          alError("");
          try {
            // Al servidor va el TOKEN, no el email: el email lo saca el
            // back-end de adentro del token ya verificado. Si mandaramos
            // el email, cualquiera podria escribir el del administrador.
            const u = await sesion.loginGoogle(respuesta.credential);
            location.href = u.rol === "ADMIN" ? "admin.html" : "index.html";
          } catch (e) {
            alError(e.message);
          }
        }
      });
      iniciado = true;
    }

    // El tope de 400 es de Google: mas que eso lo ignora.
    window.google.accounts.id.renderButton(caja, {
      theme: "outline", size: "large",
      width: Math.min(400, Math.max(200, ancho)),
      text: "signin_with", shape: "rectangular", locale: "es"
    });
    return true;
  };

  /*
   * Se espera por DOS cosas: que haya cargado el script de Google (viene
   * con defer) y que la caja ya este en la pagina, porque hasta entonces
   * mide cero y no se puede calcular el ancho.
   */
  if (!dibujar()) {
    const esperar = setInterval(() => { if (dibujar()) clearInterval(esperar); }, 150);
    // A los 6 segundos se deja de esperar: si no cargo, no va a cargar, y
    // un intervalo vivo para siempre es basura corriendo de fondo.
    setTimeout(() => clearInterval(esperar), 6000);
  }

  return crear("div", { class: "otro-ingreso" },
    crear("div", { class: "separador-o" }, crear("span", {}, "o")),
    caja);
}

function formIngreso() {
  const error = crear("p", { class: "form-error", "aria-live": "polite" });

  const form = crear("form", { class: "form-cuenta", novalidate: "true" },
    crear("div", { class: "form-grupo" },
      crear("label", { for: "iEmail" }, "Email"),
      crear("input", { type: "email", id: "iEmail", autocomplete: "email" })),
    crear("div", { class: "form-grupo" },
      crear("label", { for: "iPass" }, "Contraseña"),
      crear("input", { type: "password", id: "iPass", autocomplete: "current-password" })),
    error,
    crear("button", { type: "submit", class: "btn btn--primario btn--bloque" }, "Ingresar"),
    botonGoogle((msg) => { error.textContent = msg; }),
    crear("div", { class: "usuarios-demo" },
      crear("p", { class: "usuarios-demo__titulo" }, "Usuarios de prueba"),
      crear("div", { class: "usuarios-demo__fila" },
        crear("span", { class: "usuarios-demo__rol" }, "Admin"),
        crear("code", {}, "admin@albaenlosandes.com / Admin123")),
      crear("div", { class: "usuarios-demo__fila" },
        crear("span", { class: "usuarios-demo__rol" }, "Cliente"),
        crear("code", {}, "juan@mail.com / Cliente123"))));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    error.textContent = "";
    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Ingresando...";

    try {
      const u = await sesion.login($("#iEmail", form).value.trim(), $("#iPass", form).value);
      // El servidor dijo quien es: un administrador va al panel.
      // Un administrador va derecho a su panel; un cliente entra al sitio
      // por la puerta principal, igual que cualquier visitante, pero ya con
      // su nombre arriba. Mandarlo a "Mis pedidos" era asumir que entra solo
      // a revisar una compra, cuando lo habitual es que venga a mirar vinos.
      location.href = u.rol === "ADMIN" ? "admin.html" : "index.html";
    } catch (err) {
      error.textContent = err.message;
      boton.disabled = false; boton.textContent = "Ingresar";
    }
  });

  return form;
}

function formRegistro() {
  const error = crear("p", { class: "form-error", "aria-live": "polite" });
  // Los requisitos se ven desde antes de escribir: enterarse de que la
  // contrasena no sirve recien al mandar el formulario obliga a pensarla
  // dos veces. Asi se elige una que sirva a la primera.
  const requisitos = requisitosPassword();

  const form = crear("form", { class: "form-cuenta", novalidate: "true" },
    crear("div", { class: "form-fila-2" },
      campo("rNombre", "Nombre", "text"),
      campo("rApellido", "Apellido", "text")),
    campo("rEmail", "Email", "email"),
    campo("rTelefono", "Teléfono", "tel"),
    campo("rPass", "Contraseña", "password"),
    requisitos.nodo,
    error,
    crear("button", { type: "submit", class: "btn btn--primario btn--bloque" }, "Crear mi cuenta"));

  vigilarPassword($("#rPass", form), requisitos);

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    error.textContent = "";

    const datos = {
      nombre:   $("#rNombre", form).value.trim(),
      apellido: $("#rApellido", form).value.trim(),
      email:    $("#rEmail", form).value.trim(),
      telefono: $("#rTelefono", form).value.trim(),
      password: $("#rPass", form).value
    };

    if (datos.nombre.length < 2 || datos.apellido.length < 2) {
      error.textContent = "Completá tu nombre y apellido."; return;
    }
    const malEmail = errorEmail(datos.email);
    if (malEmail) { error.textContent = malEmail; return; }

    if (!datos.password) {
      error.textContent = "Escribí una contraseña."; return;
    }
    const malPassword = errorPassword(datos.password);
    if (malPassword) { error.textContent = malPassword; return; }

    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Creando...";

    try {
      await sesion.registrar(datos);   // registra y deja ingresado
      location.href = "index.html";
    } catch (err) {
      // Aca cae, por ejemplo, "Ya existe un usuario con el email ..."
      error.textContent = err.message;
      boton.disabled = false; boton.textContent = "Crear mi cuenta";
    }
  });

  return form;
}

const campo = (id, rotulo, tipo) => crear("div", { class: "form-grupo" },
  crear("label", { for: id }, rotulo),
  crear("input", { type: tipo, id }));

// ============================================================
//  CLIENTE — el perfil, con menu lateral
// ============================================================

/**
 * Las secciones del perfil, en el orden del menu. Cada una sabe dibujarse
 * sola dentro del panel de la derecha, y su id es el que viaja en la
 * direccion: cuenta.html#pedidos.
 *
 * POR QUE UNA LISTA Y NO CINCO IF: el menu, el ruteo por #hash y el panel
 * se arman los tres recorriendo esta lista. Agregar una seccion es sumar
 * un renglon aca, en vez de tocar tres lugares que podrian quedar
 * diciendo cosas distintas.
 *
 * QUE NO ESTA, Y POR QUE: no hay seccion de notificaciones (el sitio
 * todavia no manda ningun correo), ni de medios de pago guardados (la
 * tarjeta del checkout es de prueba y no se guarda nada), ni de datos
 * personales sueltos como la fecha de nacimiento, que no se usa en ningun
 * circuito. Serian formularios que no hacen nada.
 */
const SECCIONES = [
  { id: "datos",        texto: "Datos principales",    pintar: seccionDatos },
  { id: "pedidos",      texto: "Mis pedidos",          pintar: seccionPedidos },
  { id: "solicitudes",  texto: "Mis solicitudes",      pintar: seccionSolicitudes },
  { id: "preferencias", texto: "Preferencias de vino", pintar: seccionPreferencias },
  { id: "seguridad",    texto: "Seguridad",            pintar: seccionSeguridad }
];

/**
 * Lo que se trajo del servidor para esta visita: el usuario al dia, sus
 * pedidos, sus solicitudes y (solo si hizo falta) el catalogo.
 *
 * Lo comparten las secciones para no volver a pedir los mismos pedidos
 * cada vez que se cambia de solapa. El catalogo se suma recien cuando se
 * abre Preferencias, que es la unica que lo necesita: traerlo de entrada
 * seria hacer esperar a todos por algo que la mayoria no va a mirar.
 */
let misDatos = null;

async function pantallaCliente(caja, u) {
  caja.append(crear("section", { class: "portada-chica" },
    crear("h1", {}, "Hola, " + u.nombre),
    crear("p", {}, u.email)));

  const cuerpo = crear("section", { class: "seccion" },
    crear("div", { class: "seccion__interior" },
      crear("p", { class: "nota-tenue" }, "Cargando tu cuenta...")));
  caja.append(cuerpo);

  /*
   * El usuario se vuelve a pedir al servidor en vez de usar el que guarda
   * la sesion: esa es una copia del momento en que ingreso, y aca se van a
   * mostrar y editar sus datos. Si alguien cambio algo (el propio cliente
   * desde otra pestania, o un admin desde el panel), lo que vale es lo que
   * dice la base, no lo que quedo en el navegador.
   */
  try {
    const [usuario, pedidos, solicitudes] = await Promise.all([
      api.usuarios.porId(u.idUsuario),
      api.pedidos.deUsuario(u.idUsuario),
      api.solicitudes.deUsuario(u.idUsuario)
    ]);
    misDatos = { usuario, pedidos, solicitudes, catalogo: null };
  } catch (e) {
    cuerpo.innerHTML = "";
    cuerpo.append(avisoSinServidor(e.message));
    return;
  }

  const interior = crear("div", { class: "seccion__interior" });
  cuerpo.innerHTML = "";
  cuerpo.append(interior);

  // Un administrador tambien puede comprar, pero su lugar es el panel.
  if (u.rol === "ADMIN") {
    interior.append(crear("div", { class: "aviso-panel" },
      crear("p", {}, "Entraste como administrador."),
      crear("a", { class: "btn btn--primario", href: "admin.html" }, "Ir al panel de gestión")));
  }

  const menu  = crear("nav", { class: "perfil__menu", "aria-label": "Secciones de mi cuenta" });
  const panel = crear("div", { class: "perfil__panel", id: "perfilPanel" });
  interior.append(crear("div", { class: "perfil" }, menu, panel));

  menu.append(...SECCIONES.map(sec =>
    crear("a", { class: "perfil__item", href: "#" + sec.id, "data-seccion": sec.id },
      crear("span", {}, sec.texto))));

  /*
   * Al cambiar el #hash se redibuja nada mas que el panel, no la pagina
   * entera. El listener se registra UNA sola vez aunque pantallaCliente()
   * vuelva a correr (pasa al pedir una cancelacion, que llama a pintar()):
   * si se agregara de nuevo, cada cambio de seccion la dibujaria dos veces.
   */
  if (!window.__cuentaEscucha) {
    window.addEventListener("hashchange", mostrarSeccion);
    window.__cuentaEscucha = true;
  }
  mostrarSeccion();
}

/** Pinta en el panel la seccion que pide el #hash. */
function mostrarSeccion() {
  const panel = $("#perfilPanel");
  if (!panel) return;                       // se cambio de pagina

  const pedida = location.hash.replace("#", "");
  // Un #hash escrito a mano o viejo no tiene que dejar la pagina en blanco.
  const seccion = SECCIONES.find(s => s.id === pedida) || SECCIONES[0];

  $$(".perfil__item").forEach(a => {
    const activo = a.dataset.seccion === seccion.id;
    a.classList.toggle("activo", activo);
    if (activo) a.setAttribute("aria-current", "true");
    else        a.removeAttribute("aria-current");
  });

  /*
   * En pantallas chicas el menu es una fila que se desliza, y la seccion
   * activa puede quedar fuera de la vista: se llega con un enlace a
   * #seguridad y el menu sigue mostrando "Datos principales", como si no
   * hubiera pasado nada. Se corre lo justo para dejarla centrada.
   *
   * Se toca scrollLeft a mano y no scrollIntoView() porque este ultimo
   * tambien moveria la pagina entera, y la vertical ya la resuelve la
   * linea de mas abajo.
   */
  const activo = $(".perfil__item.activo");
  if (activo && activo.parentElement.scrollWidth > activo.parentElement.clientWidth) {
    const menu = activo.parentElement;
    menu.scrollLeft = activo.offsetLeft - (menu.clientWidth - activo.offsetWidth) / 2;
  }

  panel.innerHTML = "";
  seccion.pintar(panel);
  // El panel esta abajo del menu en pantallas chicas: si no se lleva el foco
  // visual hasta ahi, tocar una seccion no parece haber hecho nada.
  if (pedida) panel.scrollIntoView({ block: "nearest", behavior: "smooth" });
}

/** Encabezado comun a todas las secciones del panel. */
const tituloSeccion = (titulo, bajada) => crear("header", { class: "perfil__cabeza" },
  crear("h2", {}, titulo),
  bajada ? crear("p", { class: "nota-tenue" }, bajada) : "");

// ------------------------------------------------------------
//  Seccion: DATOS PRINCIPALES
// ------------------------------------------------------------

function seccionDatos(panel) {
  const u = misDatos.usuario;
  const error = crear("p", { class: "form-error", "aria-live": "polite" });

  const form = crear("form", { class: "perfil__form", novalidate: "true" },
    crear("div", { class: "form-fila-2" },
      campoValor("pNombre", "Nombre", "text", u.nombre),
      campoValor("pApellido", "Apellido", "text", u.apellido)),
    campoValor("pEmail", "Email", "email", u.email),
    campoValor("pTelefono", "Teléfono", "tel", u.telefono || ""),
    crear("p", { class: "nota-tenue" },
      "El email es con el que ingresás a tu cuenta."),
    error,
    crear("div", { class: "perfil__acciones" },
      crear("button", { type: "submit", class: "btn btn--primario" }, "Guardar cambios")));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    error.textContent = "";

    const datos = {
      nombre:   $("#pNombre", form).value.trim(),
      apellido: $("#pApellido", form).value.trim(),
      email:    $("#pEmail", form).value.trim(),
      telefono: $("#pTelefono", form).value.trim()
    };

    // Las mismas reglas que el registro: si valen para crear la cuenta,
    // valen para editarla.
    if (datos.nombre.length < 2 || datos.apellido.length < 2) {
      error.textContent = "Completá tu nombre y apellido."; return;
    }
    const malEmail = errorEmail(datos.email);
    if (malEmail) { error.textContent = malEmail; return; }

    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Guardando...";

    try {
      /*
       * Se manda el perfil completo y no solo lo que cambio, porque el
       * back-end valida el cuerpo con @Valid contra la entidad Usuario y
       * nombre, apellido y email son obligatorios. Las preferencias de
       * vino no viajan: el servicio solo toca los campos que recibe, asi
       * que guardar los datos no las borra.
       */
      misDatos.usuario = await api.usuarios.actualizarPerfil(u.idUsuario, datos);
      // La sesion guarda una copia para el encabezado: hay que ponerla al dia
      // o la barra de arriba sigue saludando con el nombre anterior.
      sesion.actualizar(datos);
      const nombreEnBarra = $(".cinta__usuario");
      if (nombreEnBarra) nombreEnBarra.textContent = datos.nombre;
      toast("Tus datos quedaron guardados");
      mostrarSeccion();                     // se redibuja con lo que contesto el servidor
    } catch (e) {
      // Aca cae, por ejemplo, "Ya existe un usuario con el email ..."
      error.textContent = e.message;
      boton.disabled = false; boton.textContent = "Guardar cambios";
    }
  });

  panel.append(
    tituloSeccion("Datos principales", "Tus datos de contacto para los pedidos."),
    form);
}

/** Como campo(), pero con un valor ya escrito adentro. */
const campoValor = (id, rotulo, tipo, valor) => crear("div", { class: "form-grupo" },
  crear("label", { for: id }, rotulo),
  crear("input", { type: tipo, id, value: valor ?? "" }));

// ------------------------------------------------------------
//  Seccion: MIS PEDIDOS
// ------------------------------------------------------------

function seccionPedidos(panel) {
  panel.append(tituloSeccion("Mis pedidos",
    "Todo lo que compraste, de lo más nuevo a lo más viejo."));

  if (!misDatos.pedidos.length) {
    panel.append(crear("div", { class: "vacio-cuenta" },
      crear("p", {}, "Todavía no hiciste ningún pedido."),
      crear("a", { class: "btn btn--primario", href: "tienda.html" }, "Ver el catálogo")));
    return;
  }

  // Del mas nuevo al mas viejo: lo ultimo que compro es lo que viene a mirar.
  [...misDatos.pedidos].sort((a, b) => b.id - a.id)
    .forEach(p => panel.append(tarjetaPedido(p, misDatos.solicitudes, misDatos.usuario)));
}

function tarjetaPedido(p, solicitudes, u) {
  // Si ya pidió cancelar este pedido, se muestra en que quedo.
  const solicitud = solicitudes.find(s => s.pedido?.idPedido === p.id);

  const renglones = p.items.map(i => crear("li", {},
    crear("span", {}, i.cantidad + " × " + i.nombre),
    crear("span", { class: "reng__precio" }, pesos(i.precio * i.cantidad))));

  const acciones = crear("div", { class: "pedido__acciones" });

  if (solicitud) {
    acciones.append(crear("div", { class: "solicitud-estado" },
      crear("span", { class: "marca-sol marca-sol--" + solicitud.estado.toLowerCase() },
        ESTADOS_SOLICITUD[solicitud.estado] || solicitud.estado),
      crear("span", { class: "nota-tenue" },
        "Pediste cancelar el " + fechaCorta(solicitud.fechaSolicitud)),
      solicitud.respuesta
        ? crear("p", { class: "solicitud-respuesta" }, solicitud.respuesta)
        : crear("p", { class: "nota-tenue" }, "Te vamos a responder a la brevedad.")));
  } else if (p.estado === "confirmado") {
    const btn = crear("button", { class: "btn btn--fantasma btn--chico" }, "Quiero cancelarlo");
    btn.addEventListener("click", () => abrirCancelacion(p, u));
    acciones.append(btn);
  }

  return crear("article", { class: "pedido" },
    crear("header", { class: "pedido__cabeza" },
      crear("div", {},
        crear("span", { class: "pedido__numero" }, "Pedido #" + p.id),
        crear("span", { class: "nota-tenue" }, " · " + fecha(p.fecha))),
      crear("span", { class: "marca-sol marca-sol--" + p.estado }, capitalizar(p.estado))),
    crear("ul", { class: "pedido__items" }, ...renglones),
    crear("div", { class: "pedido__pie" },
      crear("span", { class: "nota-tenue" }, capitalizar(p.medioPago || "")),
      crear("strong", {}, pesos(p.total))),
    acciones);
}

const capitalizar = (s) => s ? s.charAt(0).toUpperCase() + s.slice(1).toLowerCase() : "";

// ------------------------------------------------------------
//  Seccion: MIS SOLICITUDES DE CANCELACION
// ------------------------------------------------------------

/**
 * Las solicitudes tambien se ven dentro de cada pedido, pero ahi estan
 * mezcladas con el historial de compras. Como seccion propia responden la
 * pregunta con la que vuelve alguien que pidio cancelar: "en que quedo mi
 * pedido", sin tener que buscarlo entre los demas.
 */
function seccionSolicitudes(panel) {
  panel.append(tituloSeccion("Mis solicitudes de cancelación",
    "Los pedidos que pediste cancelar y qué se te respondió."));

  if (!misDatos.solicitudes.length) {
    panel.append(crear("div", { class: "vacio-cuenta" },
      crear("p", {}, "No pediste cancelar ningún pedido."),
      crear("a", { class: "btn btn--fantasma", href: "#pedidos" }, "Ver mis pedidos")));
    return;
  }

  [...misDatos.solicitudes]
    .sort((a, b) => new Date(b.fechaSolicitud) - new Date(a.fechaSolicitud))
    .forEach(sol => {
      const nroPedido = sol.pedido?.idPedido;
      panel.append(crear("article", { class: "pedido" },
        crear("header", { class: "pedido__cabeza" },
          crear("div", {},
            crear("span", { class: "pedido__numero" },
              nroPedido ? "Pedido #" + nroPedido : "Solicitud #" + sol.idSolicitud),
            crear("span", { class: "nota-tenue" }, " · " + fecha(sol.fechaSolicitud))),
          crear("span", { class: "marca-sol marca-sol--" + sol.estado.toLowerCase() },
            ESTADOS_SOLICITUD[sol.estado] || sol.estado)),
        crear("p", { class: "solicitud-motivo" },
          crear("strong", {}, "Motivo: "), MOTIVOS[sol.motivo] || sol.motivo),
        sol.comentario
          ? crear("p", { class: "nota-tenue" }, "Escribiste: " + sol.comentario)
          : "",
        sol.respuesta
          ? crear("p", { class: "solicitud-respuesta" }, sol.respuesta)
          : crear("p", { class: "nota-tenue" }, "Te vamos a responder a la brevedad."),
        nroPedido
          ? crear("div", { class: "pedido__acciones" },
              crear("a", { class: "btn btn--fantasma btn--chico", href: "#pedidos" },
                "Ver el pedido"))
          : ""));
    });
}

// ------------------------------------------------------------
//  Seccion: PREFERENCIAS DE VINO
// ------------------------------------------------------------

/**
 * Las opciones NO estan escritas a mano: salen de los vinos que hay en el
 * catalogo. Si la bodega suma un varietal, aparece solo; si deja de
 * trabajarlo, deja de ofrecerse. Una lista fija al lado del catalogo real
 * terminaria ofreciendo cosas que no se venden.
 *
 * Los blends se juntan en una sola opcion "Blend": en la base cada uno
 * lleva su mezcla completa en el nombre del varietal ("Blend (Malbec,
 * Cabernet Franc...)"), y como opciones para tildar serian dos renglones
 * larguisimos que dicen casi lo mismo.
 */
const BLEND = "Blend";
const etiquetaVarietal = (v) => String(v || "").startsWith(BLEND) ? BLEND : v;

async function seccionPreferencias(panel) {
  panel.append(tituloSeccion("Preferencias de vino",
    "Marcá lo que te gusta y te mostramos los vinos del catálogo que van con eso."));

  if (!misDatos.catalogo) {
    const cargando = crear("p", { class: "nota-tenue" }, "Buscando el catálogo...");
    panel.append(cargando);
    try {
      misDatos.catalogo = await api.productos.activos();
    } catch (e) {
      cargando.replaceWith(crear("p", { class: "form-error" },
        "No se pudo traer el catálogo: " + e.message));
      return;
    }
    /*
     * Mientras se esperaba el catalogo, la persona pudo haber cambiado de
     * seccion. Si eso paso, el panel ya se vacio y lo esta usando otra
     * seccion: seguir dibujando aca meteria las preferencias adentro de,
     * por ejemplo, Mis pedidos. Se sale sin tocar nada; el catalogo quedo
     * guardado, asi que al volver ya esta.
     */
    if (!cargando.isConnected) return;
    cargando.remove();
  }

  const varietales = [...new Set(misDatos.catalogo.map(p => etiquetaVarietal(p.varietal)))].sort();
  const lineas     = [...new Set(misDatos.catalogo.map(p => p.linea))];

  const elegidos = {
    varietales: aLista(misDatos.usuario.varietalesFavoritos),
    lineas:     aLista(misDatos.usuario.lineasFavoritas)
  };

  const error = crear("p", { class: "form-error", "aria-live": "polite" });
  const sugeridos = crear("div", { class: "perfil__sugeridos" });

  const grupoVarietales = grupoTildes("varietal", varietales, elegidos.varietales);
  const grupoLineas     = grupoTildes("linea", lineas, elegidos.lineas);

  const form = crear("form", { class: "perfil__form", novalidate: "true" },
    crear("fieldset", { class: "perfil__grupo" },
      crear("legend", {}, "Varietales"), grupoVarietales),
    crear("fieldset", { class: "perfil__grupo" },
      crear("legend", {}, "Líneas"), grupoLineas),
    error,
    crear("div", { class: "perfil__acciones" },
      crear("button", { type: "submit", class: "btn btn--primario" }, "Guardar preferencias")));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    error.textContent = "";
    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Guardando...";

    const u = misDatos.usuario;
    try {
      /*
       * Igual que en Datos principales: el back-end valida con @Valid, asi
       * que nombre, apellido y email tienen que viajar aunque no cambien.
       */
      misDatos.usuario = await api.usuarios.actualizarPerfil(u.idUsuario, {
        nombre: u.nombre, apellido: u.apellido, email: u.email,
        varietalesFavoritos: tildados(grupoVarietales).join(","),
        lineasFavoritas:     tildados(grupoLineas).join(",")
      });
      toast("Listo, vamos a tener en cuenta lo que te gusta");
      pintarSugeridos();
    } catch (e) {
      error.textContent = e.message;
    } finally {
      boton.disabled = false; boton.textContent = "Guardar preferencias";
    }
  });

  panel.append(form, sugeridos);
  pintarSugeridos();

  /**
   * Los vinos del catalogo que coinciden con lo guardado. Es la razon de
   * que esta seccion exista: sin esto, el formulario seria un par de
   * listas de tildes que no hacen nada.
   */
  function pintarSugeridos() {
    const favVarietales = aLista(misDatos.usuario.varietalesFavoritos);
    const favLineas     = aLista(misDatos.usuario.lineasFavoritas);

    sugeridos.innerHTML = "";
    if (!favVarietales.length && !favLineas.length) {
      sugeridos.append(crear("p", { class: "nota-tenue" },
        "Todavía no elegiste nada. Cuando guardes, acá te vamos a mostrar " +
        "los vinos que coinciden."));
      return;
    }

    // Primero los que coinciden en las dos cosas: son los mas parecidos a
    // lo que la persona dijo que le gusta.
    const puntuados = misDatos.catalogo
      .map(p => ({
        vino: p,
        puntos: (favVarietales.includes(etiquetaVarietal(p.varietal)) ? 1 : 0) +
                (favLineas.includes(p.linea) ? 1 : 0)
      }))
      .filter(x => x.puntos > 0)
      .sort((a, b) => b.puntos - a.puntos || a.vino.nombre.localeCompare(b.vino.nombre))
      .slice(0, 6);

    if (!puntuados.length) {
      sugeridos.append(crear("p", { class: "nota-tenue" },
        "Por ahora no tenemos vinos en stock que coincidan con lo que elegiste."));
      return;
    }

    sugeridos.append(crear("h3", { class: "perfil__subtitulo" }, "Para vos"));
    sugeridos.append(crear("div", { class: "vino-hermanos" },
      ...puntuados.map(({ vino }) => crear("a",
        { class: "vino-hermano", href: "vino.html?id=" + vino.id },
        crear("span", { class: "vino-hermano__nombre" }, vino.nombre),
        crear("span", { class: "vino-hermano__meta" },
          [vino.linea, vino.varietal, vino.cosecha].filter(Boolean).join(" · ")),
        crear("span", { class: "vino-hermano__meta" }, pesos(vino.precio))))));
  }
}

/**
 * Las preferencias se guardan como texto separado por coma (asi estan en
 * la columna de MySQL) y se usan como lista. Estas dos funciones son el
 * unico lugar donde se pasa de una forma a la otra.
 */
const aLista = (texto) => String(texto || "").split(",").map(t => t.trim()).filter(Boolean);
const tildados = (grupo) => $$("input:checked", grupo).map(i => i.value);

/** Las tildes de un grupo, con el mismo aspecto que los medios de pago del checkout. */
function grupoTildes(nombre, opciones, elegidas) {
  return crear("div", { class: "tildes" },
    ...opciones.map((op, i) => crear("label", { class: "tilde" },
      crear("input", {
        type: "checkbox", name: nombre, id: nombre + i, value: op,
        checked: elegidas.includes(op) ? "checked" : null
      }),
      crear("span", { class: "tilde__texto" }, op))));
}

// ------------------------------------------------------------
//  Seccion: SEGURIDAD
// ------------------------------------------------------------

function seccionSeguridad(panel) {
  const error = crear("p", { class: "form-error", "aria-live": "polite" });
  const requisitos = requisitosPassword("La contraseña nueva tiene que tener:");

  const form = crear("form", { class: "perfil__form", novalidate: "true" },
    campoValor("sActual", "Contraseña actual", "password", ""),
    crear("div", { class: "form-fila-2" },
      campoValor("sNueva", "Contraseña nueva", "password", ""),
      campoValor("sRepetir", "Repetir la nueva", "password", "")),
    requisitos.nodo,
    error,
    crear("div", { class: "perfil__acciones" },
      crear("button", { type: "submit", class: "btn btn--primario" }, "Cambiar contraseña")));

  vigilarPassword($("#sNueva", form), requisitos);

  // Los tres campos son de contrasena: el navegador no tiene que ofrecer
  // completarlos con la guardada, porque dos de ellos son nuevos.
  $("#sActual", form).setAttribute("autocomplete", "current-password");
  $("#sNueva", form).setAttribute("autocomplete", "new-password");
  $("#sRepetir", form).setAttribute("autocomplete", "new-password");

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    error.textContent = "";

    const actual  = $("#sActual", form).value;
    const nueva   = $("#sNueva", form).value;
    const repetir = $("#sRepetir", form).value;

    // Lo que se puede comprobar sin molestar al servidor, se comprueba aca.
    // Que la actual sea la correcta solo lo sabe el servidor.
    if (!actual) { error.textContent = "Escribí tu contraseña actual."; return; }
    if (!nueva)  { error.textContent = "Escribí la contraseña nueva."; return; }
    const malPassword = errorPassword(nueva, "la contraseña nueva");
    if (malPassword) { error.textContent = malPassword; return; }
    if (nueva !== repetir) {
      error.textContent = "Las dos contraseñas nuevas no coinciden."; return;
    }
    if (nueva === actual) {
      error.textContent = "La contraseña nueva tiene que ser distinta de la actual."; return;
    }

    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Cambiando...";

    try {
      await api.usuarios.cambiarPassword(misDatos.usuario.idUsuario, actual, nueva);
      toast("Tu contraseña quedó cambiada");
      mostrarSeccion();            // deja el formulario limpio
    } catch (e) {
      // Aca cae "La contrasena actual no es correcta."
      error.textContent = e.message;
      boton.disabled = false; boton.textContent = "Cambiar contraseña";
    }
  });

  panel.append(
    tituloSeccion("Seguridad", "Cambiá la contraseña con la que ingresás."),
    form);
}

// ============================================================
//  PEDIR UNA CANCELACION
// ============================================================

/**
 * El formulario muestra primero las preguntas frecuentes del motivo
 * elegido. La idea es que si el problema se resuelve leyendo, el cliente
 * no tenga que esperar una respuesta: ese era el pedido original de que
 * cancelar no fuera tedioso.
 */
async function abrirCancelacion(pedido, u) {
  const selMotivo = crear("select", { id: "cMotivo" },
    ...Object.entries(MOTIVOS).map(([k, v]) => crear("option", { value: k }, v)));

  const ayuda   = crear("div", { class: "faq-caja" });
  const aviso   = crear("p", { class: "aviso-politica" });
  const comenta = crear("textarea", { id: "cComentario", rows: "4",
    placeholder: "Contanos qué pasó (opcional, pero ayuda a resolverlo más rápido)" });
  const error   = crear("p", { class: "form-error", "aria-live": "polite" });

  const panel = crear("div", { class: "carrito-panel" },
    crear("h2", {}, "Cancelar el pedido #" + pedido.id),
    crear("p", { class: "nota-tenue", style: "margin-bottom:18px" },
      fecha(pedido.fecha) + " · " + pesos(pedido.total)),
    crear("div", { class: "form-grupo" },
      crear("label", { for: "cMotivo" }, "¿Qué pasó?"), selMotivo),
    ayuda,
    aviso,
    crear("div", { class: "form-grupo" },
      crear("label", { for: "cComentario" }, "Comentario"), comenta),
    error,
    crear("div", { style: "display:flex;gap:12px;flex-wrap:wrap;margin-top:8px" },
      crear("button", { class: "btn btn--primario", id: "btnEnviarCancel" }, "Enviar solicitud"),
      crear("button", { class: "btn btn--fantasma", onclick: cerrarModal }, "Mejor no")));

  montarModal(panel);

  /** Al cambiar el motivo, se traen sus preguntas y lo que dice la politica. */
  async function refrescar() {
    const motivo = selMotivo.value;
    ayuda.innerHTML = '<p class="nota-tenue">Buscando respuestas...</p>';
    aviso.textContent = "";

    try {
      const [preguntas, evaluacion] = await Promise.all([
        api.faq.activas(),
        api.solicitudes.evaluar(pedido.id, motivo)
      ]);

      // Las preguntas del motivo elegido, mas las generales.
      const utiles = preguntas.filter(q => !q.motivo || q.motivo === motivo).slice(0, 4);
      ayuda.innerHTML = "";
      if (utiles.length) {
        ayuda.append(crear("p", { class: "faq-caja__titulo" },
          "Quizás esto te lo resuelve ahora mismo"));
        utiles.forEach(q => ayuda.append(crear("details", { class: "faq" },
          crear("summary", {}, q.pregunta),
          crear("p", {}, q.respuesta))));
      }

      // Lo que contesto el servidor sobre este pedido puntual.
      aviso.textContent = evaluacion.explicacion || "";
      aviso.className = "aviso-politica " +
        (evaluacion.cancelacionDirecta ? "aviso-politica--directa" : "aviso-politica--revision");
      $("#btnEnviarCancel", panel).disabled = evaluacion.sePuedePedir === false;
    } catch (e) {
      ayuda.innerHTML = "";
      aviso.textContent = "No se pudieron traer las preguntas frecuentes: " + e.message;
    }
  }

  selMotivo.addEventListener("change", refrescar);
  refrescar();

  $("#btnEnviarCancel", panel).addEventListener("click", async () => {
    const boton = $("#btnEnviarCancel", panel);
    boton.disabled = true; boton.textContent = "Enviando...";
    error.textContent = "";
    try {
      await api.solicitudes.crear(pedido.id, u.idUsuario, selMotivo.value, comenta.value.trim());
      cerrarModal();
      toast("Tu solicitud quedó registrada");
      pintar();                    // se redibuja con el estado nuevo
    } catch (e) {
      error.textContent = e.message;
      boton.disabled = false; boton.textContent = "Enviar solicitud";
    }
  });
}

// ============================================================
//  MODAL
// ============================================================

function montarModal(contenido) {
  cerrarModal();
  const cerrar = crear("button", { class: "modal__cerrar", "aria-label": "Cerrar",
                                   onclick: cerrarModal }, "×");
  const modal = crear("div", { class: "modal", role: "dialog", "aria-modal": "true" },
                      cerrar, contenido);
  const fondo = crear("div", { class: "modal-fondo", id: "modalFondo",
    onclick: (e) => { if (e.target.id === "modalFondo") cerrarModal(); } }, modal);
  $("#raizModal").append(fondo);
  document.addEventListener("keydown", cerrarConEsc);
}
function cerrarModal() {
  const f = $("#modalFondo");
  if (f) f.remove();
  document.removeEventListener("keydown", cerrarConEsc);
}
function cerrarConEsc(e) { if (e.key === "Escape") cerrarModal(); }
