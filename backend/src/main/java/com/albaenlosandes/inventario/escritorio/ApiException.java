package com.albaenlosandes.inventario.escritorio;

import java.util.Map;

/**
 * Traduce un error del servidor a algo que la ventana pueda mostrar.
 *
 * Guarda las tres cosas que el backend devuelve:
 *   estado  -> el codigo HTTP (400, 404, 409, 500, o 0 si no hubo conexion)
 *   mensaje -> el texto para el usuario
 *   campos  -> cuando falla la validacion, que campo fallo y por que
 *
 * Gracias a 'campos', el formulario puede marcar en rojo exactamente
 * los campos mal cargados, porque el servidor los devuelve TODOS juntos.
 */
public class ApiException extends RuntimeException {

    private final int estado;
    private final Map<String, String> campos;

    public ApiException(int estado, String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.estado = estado;
        this.campos = campos == null ? Map.of() : campos;
    }

    public int getEstado() { return estado; }
    public Map<String, String> getCampos() { return campos; }
    public boolean esErrorDeValidacion() { return !campos.isEmpty(); }
}
