package com.albaenlosandes.inventario.controller;

/**
 * Se lanza cuando se pide por id algo que no existe en la base.
 *
 * POR QUE EXISTE ESTA CLASE:
 * antes cada controller respondia con ResponseEntity.notFound().build(), que
 * devuelve un 404 con el cuerpo VACIO. El resto de los errores de la API si
 * devuelven un JSON con timestamp, estado, error y ruta. Esa diferencia obliga
 * al cliente a tratar el 404 como un caso especial.
 *
 * Con esta excepcion el controller solo dice "esto no existe" y el
 * GlobalExceptionHandler arma la respuesta, igual que con todos los demas
 * errores. Un solo lugar decide como se ve un error de la API.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
