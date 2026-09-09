package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.Usuario;
import com.albaenlosandes.inventario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;

    public UsuarioService(UsuarioRepository repository) {
        this.repository = repository;
    }

    public List<Usuario> obtenerTodos() {
        return repository.findAll();
    }

    public Optional<Usuario> obtenerPorId(Integer id) {
        return repository.findById(id);
    }

    public Usuario registrar(Usuario usuario) {
        if (repository.existsByEmail(usuario.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con el email " + usuario.getEmail());
        }
        // En el alta la contrasena SI es obligatoria (en la edicion no: ver actualizar).
        if (usuario.getPasswordHash() == null || usuario.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("La contrasena es obligatoria para dar de alta un usuario");
        }
        // NOTA para la etapa de seguridad: aca ira el hash BCrypt de la contrasena.
        return repository.save(usuario);
    }

    /**
     * Modificacion que conserva lo que no vino en el JSON.
     *
     * Lo mas importante es la CONTRASENA: si no se manda, se conserva la que
     * ya estaba. Antes habia que reescribirla en cada edicion, y si alguien
     * la olvidaba el usuario quedaba sin poder entrar. Como el servidor nunca
     * devuelve la contrasena, un formulario de edicion no la tiene para
     * reenviarla: por eso "no la mande" tiene que significar "dejala como esta".
     */
    @Transactional
    public Usuario actualizar(Integer id, Usuario datos) {
        Usuario actual = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + id));

        if (datos.getEmail() != null && !datos.getEmail().equalsIgnoreCase(actual.getEmail())
                && repository.existsByEmail(datos.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con el email " + datos.getEmail());
        }

        if (datos.getNombre() != null)   actual.setNombre(datos.getNombre());
        if (datos.getApellido() != null) actual.setApellido(datos.getApellido());
        if (datos.getEmail() != null)    actual.setEmail(datos.getEmail());
        if (datos.getTelefono() != null) actual.setTelefono(datos.getTelefono().isBlank() ? null : datos.getTelefono());
        if (datos.getRol() != null)      actual.setRol(datos.getRol());
        if (datos.getActivo() != null)   actual.setActivo(datos.getActivo());
        // La contrasena solo se toca si vino una nueva y no esta vacia.
        if (datos.getPasswordHash() != null && !datos.getPasswordHash().isBlank()) {
            actual.setPasswordHash(datos.getPasswordHash());
        }
        return repository.save(actual);
    }

    /** Baja logica: conserva el historial de pedidos del cliente */
    public void eliminar(Integer id) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + id));
        usuario.setActivo(false);
        repository.save(usuario);
    }
}
