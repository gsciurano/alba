package com.albaenlosandes.inventario.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manejo central de errores de toda la API (requisito 9 del TP).
 *
 * Sin esta clase, cualquier problema sale como un 500 con un texto generico:
 * el cliente no sabe si se equivoco el o si se rompio el servidor.
 *
 * @RestControllerAdvice = "esta clase vigila a TODOS los @RestController".
 * Cuando alguno lanza una excepcion, Spring busca aca el metodo que la atienda.
 *
 * Criterio de codigos HTTP:
 *   400 Bad Request -> el cliente mando algo mal (falta un dato, formato invalido).
 *   404 Not Found   -> lo pedido no existe (lo resuelve cada controller).
 *   409 Conflict    -> el dato es valido pero choca con la base (email repetido).
 *   500 Server Error-> se rompio algo nuestro. Es el unico que es "culpa" del servidor.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Arma siempre la misma forma de respuesta, para que el front sepa que esperar. */
    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus estado, String mensaje,
                                                          HttpServletRequest req) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", LocalDateTime.now().toString());
        cuerpo.put("estado", estado.value());
        cuerpo.put("error", mensaje);
        cuerpo.put("ruta", req.getRequestURI());
        return ResponseEntity.status(estado).body(cuerpo);
    }

    /**
     * 400 - Falla una anotacion de validacion (@NotBlank, @Positive, etc.).
     * Devuelve TODOS los campos que fallaron de una vez, no solo el primero:
     * asi el formulario del front puede marcarlos todos juntos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex,
                                                          HttpServletRequest req) {
        Map<String, String> campos = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));

        String mensaje = campos.size() == 1
                ? "Hay 1 campo con error"
                : "Hay " + campos.size() + " campos con errores";

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", LocalDateTime.now().toString());
        cuerpo.put("estado", HttpStatus.BAD_REQUEST.value());
        cuerpo.put("error", mensaje);
        cuerpo.put("campos", campos);
        cuerpo.put("ruta", req.getRequestURI());
        return ResponseEntity.badRequest().body(cuerpo);
    }

    /**
     * 404 - Se pidio por id algo que no existe. Sale con el MISMO formato que
     * los demas errores, para que el cliente no tenga que tratarlo aparte.
     */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(RecursoNoEncontradoException ex,
                                                            HttpServletRequest req) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    /** 400 - Reglas de negocio: stock insuficiente, usuario inexistente, etc. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> negocio(IllegalArgumentException ex,
                                                       HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    /** 400 - El JSON esta roto o un valor no entra en el tipo esperado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonIlegible(HttpMessageNotReadableException ex,
                                                            HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "El cuerpo de la peticion no se pudo leer: revisa que sea JSON valido "
                        + "y que los valores de enum esten bien escritos (por ejemplo "
                        + "medioPago: TARJETA, TRANSFERENCIA o EFECTIVO).", req);
    }

    /** 400 - Un valor de la URL no es del tipo esperado (/productos/abc, /linea/INVENTADA). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> tipoInvalido(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest req) {
        Class<?> tipo = ex.getRequiredType();
        String esperado = (tipo != null && tipo.isEnum())
                ? "uno de estos valores: " + String.join(", ",
                    java.util.Arrays.stream(tipo.getEnumConstants()).map(Object::toString).toList())
                : "un valor de tipo " + (tipo != null ? tipo.getSimpleName() : "desconocido");
        return respuesta(HttpStatus.BAD_REQUEST,
                "El valor '" + ex.getValue() + "' no sirve para '" + ex.getName()
                        + "'. Se esperaba " + esperado + ".", req);
    }

    /** 400 - Falta un parametro obligatorio en la URL (?cantidad=..&idUsuario=..). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> faltaParametro(MissingServletRequestParameterException ex,
                                                              HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "Falta el parametro obligatorio '" + ex.getParameterName() + "' en la URL.", req);
    }

    /**
     * 409 - El dato paso la validacion pero la base lo rechaza:
     * email repetido, borrar algo que otra tabla esta usando, etc.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException ex,
                                                          HttpServletRequest req) {
        return respuesta(HttpStatus.CONFLICT,
                "La operacion choca con una regla de la base de datos. "
                        + "Suele pasar por un valor duplicado (por ejemplo un email ya registrado) "
                        + "o por intentar borrar algo que esta en uso.", req);
    }

    /**
     * 409 - Dos operaciones quisieron tocar el mismo producto al mismo tiempo
     * y la base no pudo darles el turno a las dos.
     *
     * Con los bloqueos ordenados de PedidoService esto ya casi no pasa, pero
     * bajo mucha carga MySQL puede rendirse igual. Antes salia como 500
     * ("se rompio el servidor"), que es enganioso: no se rompio nada y la
     * operacion se puede reintentar tal cual. Por eso ahora es 409.
     */
    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> conflictoDeConcurrencia(Exception ex,
                                                                       HttpServletRequest req) {
        return respuesta(HttpStatus.CONFLICT,
                "Otra operacion esta modificando estos mismos vinos en este momento. "
                        + "No se guardo nada: volve a intentarlo.", req);
    }

    /**
     * 500 - Red de seguridad. Si algo no previsto se rompe, el cliente recibe
     * un mensaje limpio y el detalle tecnico queda en la consola del servidor.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inesperado(Exception ex, HttpServletRequest req) {
        ex.printStackTrace();
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno del servidor. Revisar la consola de la aplicacion.", req);
    }
}
