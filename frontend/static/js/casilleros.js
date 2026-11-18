/*
 * ============================================================
 *  LOS CASILLEROS DE A UN NUMERO
 * ============================================================
 *  Viene de casilleros() en cuenta.js:333, y hace lo mismo: que
 *  escribir un codigo de a un numero no sea una pelea.
 *
 *  LO QUE AGREGA, Y QUE SIN ESTO NO PASA:
 *    - escribir de corrido sin tocar nada (salta solo al siguiente),
 *    - borrar con la tecla de retroceso (vuelve al anterior),
 *    - PEGAR el codigo entero desde el mail, que se reparte en todos
 *      los casilleros en vez de meterse completo en el primero.
 *
 *  ES UN AGREGADO, NO UN REQUISITO: los casilleros son campos de un
 *  formulario normal y cada uno manda su numero. Si este archivo no
 *  carga, se pasa de uno a otro con el tabulador y el formulario
 *  funciona igual. Por eso el paso del recupero no depende de el.
 */
(function () {
  document.querySelectorAll("[data-casilleros]").forEach(function (caja) {
    const campos = Array.prototype.slice.call(caja.querySelectorAll(".casillero"));
    if (!campos.length) return;

    campos.forEach(function (campo, i) {

      campo.addEventListener("input", function () {
        // Solo numeros: si escribio una letra, el casillero queda vacio.
        campo.value = campo.value.replace(/\D/g, "").slice(0, 1);
        if (campo.value && i < campos.length - 1) campos[i + 1].focus();
      });

      campo.addEventListener("keydown", function (e) {
        /*
         * Retroceso en un casillero YA VACIO: borra el anterior. Sin
         * esto hay que tocar dos veces para empezar a borrar de verdad.
         */
        if (e.key === "Backspace" && !campo.value && i > 0) {
          campos[i - 1].focus();
          campos[i - 1].value = "";
          e.preventDefault();
        }
        if (e.key === "ArrowLeft"  && i > 0)                 campos[i - 1].focus();
        if (e.key === "ArrowRight" && i < campos.length - 1) campos[i + 1].focus();
      });

      campo.addEventListener("paste", function (e) {
        e.preventDefault();
        const numeros = ((e.clipboardData || window.clipboardData).getData("text") || "")
          .replace(/\D/g, "");
        /*
         * Se reparte desde el casillero donde se pego, no siempre desde
         * el primero: si alguien corrige la mitad de atras, se respeta.
         */
        numeros.split("").forEach(function (n, j) {
          if (campos[i + j]) campos[i + j].value = n;
        });
        campos[Math.min(i + numeros.length, campos.length - 1)].focus();
      });
    });

    // El foco arranca en el primero: es el unico lugar donde se puede escribir.
    campos[0].focus();
  });
})();
