/*
 * ============================================================
 *  EL BOTON DE GOOGLE DE LA PANTALLA DE INGRESO
 * ============================================================
 *  Es el unico JavaScript que necesita esta pantalla, y existe por una
 *  sola razon: el boton de Google no se puede escribir en HTML. La
 *  libreria de Google lo dibuja adentro de un iframe propio, asi que
 *  hay que pedirselo desde el navegador.
 *
 *  QUE QUEDO AFUERA respecto de cuenta.js:123, que hacia esto mismo:
 *  el fetch, el guardar al usuario en localStorage y el decidir a donde
 *  ir despues. Ahora el token se manda en un formulario normal
 *  (POST /cuenta/google, con su token CSRF) y de ahi en adelante manda
 *  el servidor: el verifica el token, abre la sesion y redirige.
 *
 *  SI ESTE ARCHIVO NO CARGA no se rompe nada: el formulario de email y
 *  contrasena y la solapa de "Crear cuenta" son HTML puro y siguen
 *  funcionando. Solo no aparece el boton de Google.
 */
/*
 * De paso, el agregado mas chico de la pantalla: llevarle al enlace del
 * recupero el email que la persona ya escribio, para no hacerla
 * escribirlo dos veces (era el argumento de abrirRecupero() en
 * cuenta.js:235). El servidor no puede hacerlo: no sabe que se tecleo
 * mientras no se mande el formulario. Sin esto el enlace va igual y la
 * pantalla del recupero pide el email.
 */
(function () {
  const olvide = document.getElementById("enlaceOlvide");
  const email = document.getElementById("iEmail");
  if (olvide && email) {
    olvide.addEventListener("click", function () {
      const escrito = email.value.trim();
      if (escrito) olvide.href = olvide.pathname + "?email=" + encodeURIComponent(escrito);
    });
  }
})();

(function () {
  const caja = document.getElementById("botonGoogle");
  const form = document.getElementById("formGoogle");
  if (!caja || !form) return;

  const clientId = caja.dataset.clientId;
  if (!clientId) return;   // sin client id configurado no hay nada que dibujar

  /*
   * initialize() se llama UNA SOLA VEZ: el reintento de abajo puede
   * ejecutar dibujar() varias veces mientras espera, y Google avisa en
   * consola si se lo inicializa de nuevo.
   */
  let iniciado = false;

  function dibujar() {
    if (!window.google || !window.google.accounts || !window.google.accounts.id) return false;

    /*
     * EL ANCHO SE MIDE, NO SE ADIVINA: el ancho de adentro del iframe no
     * se toca con CSS, hay que pasarlo al dibujar. Si la caja todavia
     * no esta en la pagina mide cero, y conviene volver a intentar sin
     * haber tocado nada de Google.
     */
    const ancho = Math.round(caja.getBoundingClientRect().width);
    if (ancho === 0) return false;

    if (!iniciado) {
      /*
       * De que origen cree Google que viene esta pagina. Si no figura
       * TAL CUAL en "Origenes autorizados de JavaScript" de Google
       * Cloud, el ingreso termina en "Error 400: origin_mismatch", y
       * http://127.0.0.1:8080 y http://localhost:8080 son origenes
       * DISTINTOS para Google aunque sean la misma maquina. Por eso se
       * escribe en la consola: para copiarlo y pegarlo.
       */
      console.info("[Google] Origen de esta pagina: " + location.origin +
        " — tiene que estar cargado tal cual en Google Cloud, en " +
        '"Origenes autorizados de JavaScript".');

      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: function (respuesta) {
          /*
           * Al servidor va el TOKEN, no el email: el email lo saca el
           * back-end de adentro del token ya verificado. Si mandaramos
           * el email, cualquiera podria escribir el del administrador.
           */
          document.getElementById("credentialGoogle").value = respuesta.credential;
          form.submit();
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
  }

  /*
   * Se espera por DOS cosas: que haya cargado el script de Google (viene
   * con defer) y que la caja ya este en la pagina, porque hasta entonces
   * mide cero. A los 6 segundos se deja de esperar: si no cargo, no va a
   * cargar, y un intervalo vivo para siempre es basura corriendo de fondo.
   */
  if (!dibujar()) {
    const esperar = setInterval(function () { if (dibujar()) clearInterval(esperar); }, 150);
    setTimeout(function () { clearInterval(esperar); }, 6000);
  }
})();
