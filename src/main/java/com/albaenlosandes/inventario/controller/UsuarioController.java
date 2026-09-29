package com.albaenlosandes.inventario.controller;

import com.albaenlosandes.inventario.model.Usuario;
import com.albaenlosandes.inventario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * API REST de usuarios: cubre tanto a los CLIENTES que compran como a los
 * ADMIN que administran la bodega. Los dos viven en la misma tabla y se
 * distinguen por el campo rol.
 *
 * Como todos los controladores del proyecto, este NO toma decisiones: recibe
 * la peticion HTTP, se la pasa al servicio y devuelve la respuesta con su
 * codigo de estado. Las reglas (email repetido, contrasena obligatoria al
 * dar de alta pero opcional al editar) viven en UsuarioService.
 *
 * DETALLE IMPORTANTE PARA LA DEFENSA:
 * la contrasena NUNCA sale en las respuestas de esta API. No es que el
 * controlador la filtre: la entidad Usuario la tiene marcada como
 * "solo escritura", asi que puede entrar por JSON pero nunca se serializa
 * de vuelta. Se comprueba pidiendo GET /api/usuarios y viendo que el campo
 * no aparece.
 */
@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    private final UsuarioService service;

    /** Inyeccion por constructor: Spring pasa el servicio al crear el bean. */
    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    /** GET /api/usuarios -> todos los usuarios, sin su contrasena */
    @GetMapping
    public List<Usuario> obtenerTodos() {
        return service.obtenerTodos();
    }

    /**
     * GET /api/usuarios/2 -> un usuario puntual.
     * Si no existe se lanza la excepcion propia y el GlobalExceptionHandler
     * la convierte en un 404 con el mismo formato que el resto de los errores.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Usuario> obtenerPorId(@PathVariable Integer id) {
        return service.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + id));
    }

    /**
     * POST /api/usuarios -> registro de cliente (o alta de admin).
     *
     * @Valid dispara las validaciones de la entidad ANTES de entrar al
     * metodo: nombre y apellido obligatorios, email con formato valido.
     * 201 CREATED es el codigo que corresponde al crear un recurso nuevo.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Usuario registrar(@Valid @RequestBody Usuario usuario) {
        return service.registrar(usuario);
    }

    /**
     * PUT /api/usuarios/2 -> modificacion.
     *
     * El servicio mezcla los datos nuevos con los que ya estaban, para no
     * borrar en silencio los campos que el JSON no trajo. Lo mas importante
     * es la contrasena: si no viene, se conserva la que estaba. Tiene que ser
     * asi porque el servidor nunca la devuelve, y entonces un formulario de
     * edicion no la tiene para reenviarla.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Usuario> actualizar(@PathVariable Integer id,
                                              @Valid @RequestBody Usuario usuario) {
        if (service.obtenerPorId(id).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe el usuario con id " + id);
        }
        return ResponseEntity.ok(service.actualizar(id, usuario));
    }

    /**
     * DELETE /api/usuarios/2 -> BAJA LOGICA (activo = false).
     *
     * No se borra la fila: si se borrara, los pedidos historicos de ese
     * cliente quedarian apuntando a un usuario inexistente y las claves
     * foraneas lo impedirian. El cliente deja de figurar como activo pero
     * su historial de compras queda entero.
     *
     * 204 NO CONTENT: la operacion se hizo y no hay nada que devolver.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
