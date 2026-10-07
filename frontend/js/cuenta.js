/**
 * js/cuenta.js
 * ============================================================
 * MI CUENTA (cuenta.html). Tiene dos caras:
 *
 *   VISITANTE  ingresar o crear cuenta
 *   CLIENTE    sus pedidos, y desde ahi pedir una cancelacion
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
import { pesos, fecha, fechaCorta, $, crear, toast } from "./modules/util.js";

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
    crear("p", { class: "nota-tenue", style: "margin-top:18px;text-align:center" },
      "Para la demostración: ",
      crear("strong", {}, "admin@albaenlosandes.com / admin123"),
      " (administrador) · ",
      crear("strong", {}, "juan@mail.com / cliente123"),
      " (cliente)"));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    error.textContent = "";
    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Ingresando...";

    try {
      const u = await sesion.login($("#iEmail", form).value.trim(), $("#iPass", form).value);
      // El servidor dijo quien es: un administrador va al panel.
      location.href = u.rol === "ADMIN" ? "admin.html" : "cuenta.html";
    } catch (err) {
      error.textContent = err.message;
      boton.disabled = false; boton.textContent = "Ingresar";
    }
  });

  return form;
}

function formRegistro() {
  const error = crear("p", { class: "form-error", "aria-live": "polite" });

  const form = crear("form", { class: "form-cuenta", novalidate: "true" },
    crear("div", { class: "form-fila-2" },
      campo("rNombre", "Nombre", "text"),
      campo("rApellido", "Apellido", "text")),
    campo("rEmail", "Email", "email"),
    campo("rTelefono", "Teléfono", "tel"),
    campo("rPass", "Contraseña", "password"),
    error,
    crear("button", { type: "submit", class: "btn btn--primario btn--bloque" }, "Crear mi cuenta"),
    // Aviso honesto: no hay que dar por sentado que esto es seguro.
    crear("p", { class: "nota-tenue", style: "margin-top:16px" },
      "Este es un trabajo práctico: las contraseñas se guardan sin cifrar. " +
      "No uses una que uses en otro sitio."));

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
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(datos.email)) {
      error.textContent = "El email no parece válido."; return;
    }
    if (datos.password.length < 6) {
      error.textContent = "La contraseña tiene que tener al menos 6 caracteres."; return;
    }

    const boton = form.querySelector("button[type=submit]");
    boton.disabled = true; boton.textContent = "Creando...";

    try {
      await sesion.registrar(datos);   // registra y deja ingresado
      location.href = "cuenta.html";
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
//  CLIENTE — sus pedidos
// ============================================================

async function pantallaCliente(caja, u) {
  caja.append(crear("section", { class: "portada-chica" },
    crear("h1", {}, "Hola, " + u.nombre),
    crear("p", {}, u.email)));

  const cuerpo = crear("section", { class: "seccion" },
    crear("div", { class: "seccion__interior" },
      crear("p", { class: "nota-tenue" }, "Cargando tus pedidos...")));
  caja.append(cuerpo);

  // Un administrador tambien puede comprar, pero su lugar es el panel.
  if (u.rol === "ADMIN") {
    cuerpo.querySelector(".seccion__interior").prepend(
      crear("div", { class: "aviso-panel" },
        crear("p", {}, "Entraste como administrador."),
        crear("a", { class: "btn btn--primario", href: "admin.html" }, "Ir al panel de gestión")));
  }

  let pedidos, solicitudes;
  try {
    [pedidos, solicitudes] = await Promise.all([
      api.pedidos.deUsuario(u.idUsuario),
      api.solicitudes.deUsuario(u.idUsuario)
    ]);
  } catch (e) {
    cuerpo.innerHTML = "";
    cuerpo.append(avisoSinServidor(e.message));
    return;
  }

  const interior = crear("div", { class: "seccion__interior" });
  cuerpo.innerHTML = "";
  cuerpo.append(interior);

  if (u.rol === "ADMIN") {
    interior.append(crear("div", { class: "aviso-panel" },
      crear("p", {}, "Entraste como administrador."),
      crear("a", { class: "btn btn--primario", href: "admin.html" }, "Ir al panel de gestión")));
  }

  interior.append(crear("h2", { class: "seccion__titulo" }, "Mis pedidos"));

  if (!pedidos.length) {
    interior.append(crear("div", { class: "vacio-cuenta" },
      crear("p", {}, "Todavía no hiciste ningún pedido."),
      crear("a", { class: "btn btn--primario", href: "tienda.html" }, "Ver el catálogo")));
    return;
  }

  // Del mas nuevo al mas viejo: lo ultimo que compro es lo que viene a mirar.
  [...pedidos].sort((a, b) => b.id - a.id)
    .forEach(p => interior.append(tarjetaPedido(p, solicitudes, u)));
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
