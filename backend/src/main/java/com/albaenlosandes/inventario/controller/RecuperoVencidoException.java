package com.albaenlosandes.inventario.controller;

/**
 * El recupero de contrasena que se estaba haciendo ya no sirve: vencio,
 * se gastaron los intentos o el token ya se uso.
 *
 * POR QUE NO ES UN IllegalArgumentException COMO EL RESTO DE LOS
 * RECHAZOS DEL RECUPERO: los otros son "lo que escribiste no esta bien,
 * proba de nuevo" y la pantalla se queda donde esta. Este es "esto ya no
 * existe, hay que empezar otra vez", y la pantalla tiene que volver al
 * primer paso sola.
 *
 * Distinguirlos por el TEXTO del mensaje seria atarse a la redaccion: el
 * dia que alguien corrige una palabra, el formulario deja de volver al
 * inicio y nadie entiende por que. Con un 410 GONE la pantalla pregunta
 * por el codigo de estado, que no cambia cuando se reescribe un mensaje.
 *
 * 410 y no 404: no es que la direccion no exista, es que ese recupero
 * existio y ya no esta disponible. Es exactamente lo que significa GONE.
 */
public class RecuperoVencidoException extends RuntimeException {

    public RecuperoVencidoException(String mensaje) {
        super(mensaje);
    }
}
