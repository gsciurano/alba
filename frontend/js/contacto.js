/**
 * js/contacto.js
 * ============================================================
 * Formulario de CONTACTO (contacto.html).
 *
 * Valida en el navegador y confirma en pantalla. NO manda correos: no hay
 * servidor de mail en el proyecto, y la pagina lo dice en vez de simular
 * un envio que no ocurre.
 *
 * Si alguien escribe por un pedido ya hecho, el camino real es el
 * circuito de cancelaciones desde "Mis pedidos", que si llega al panel.
 * ============================================================
 */

import { montarSitio } from "./modules/sitio.js";
import { sesion } from "./modules/store.js";
import { $, crear, toast } from "./modules/util.js";

document.addEventListener("DOMContentLoaded", () => {
  montarSitio("contacto");

  const form = $("#formContacto");

  // Si ya entro a su cuenta, no tiene por que volver a escribir sus datos.
  const u = sesion.actual();
  if (u) {
    $("#nombre").value = [u.nombre, u.apellido].filter(Boolean).join(" ");
    $("#email").value  = u.email || "";
  }

  form.addEventListener("submit", (e) => {
    e.preventDefault();

    const datos = {
      nombre:  $("#nombre").value.trim(),
      email:   $("#email").value.trim(),
      mensaje: $("#mensaje").value.trim()
    };

    let ok = true;
    const marcar = (id, falla, mensaje) => {
      const campo = $("#" + id), error = $("#err-" + id);
      if (falla) { campo.classList.add("invalido"); error.textContent = mensaje; ok = false; }
      else { campo.classList.remove("invalido"); error.textContent = ""; }
    };
    marcar("nombre", datos.nombre.length < 3, "Ingresá tu nombre completo");
    marcar("email", !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(datos.email), "Email inválido");
    marcar("mensaje", datos.mensaje.length < 10, "Contanos un poco más (mínimo 10 caracteres)");

    if (!ok) { toast("Revisá los campos marcados", "error"); return; }

    form.innerHTML = "";
    form.append(
      crear("div", { class: "estado__icono", style: "font-size:52px;color:var(--ok);text-align:center" }, "✓"),
      crear("h2", { style: "text-align:center;margin:10px 0;font-size:24px" }, "¡Gracias!"),
      crear("p", { style: "text-align:center;color:var(--texto-tenue);line-height:1.7" },
        "Registramos tu consulta a nombre de " + datos.nombre + "."),
      crear("p", { style: "text-align:center;color:var(--texto-tenue);font-size:14px;margin-top:14px" },
        "Recordá que este formulario todavía no envía correos: es parte del " +
        "trabajo práctico."));
  });
});
