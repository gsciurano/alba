package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.Rol;
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

    /** Solo los usuarios de alta: es lo que consumen los desplegables. */
    public List<Usuario> obtenerTodos() {
        return repository.findByActivoTrue();
    }

    /** Incluye los dados de baja. Lo usa la pestana de usuarios para poder reactivarlos. */
    public List<Usuario> obtenerTodosIncluyendoInactivos() {
        return repository.findAll();
    }

    public Optional<Usuario> obtenerPorId(Integer id) {
        return repository.findById(id);
    }

    /**
     * ==================================================================
     *  INGRESO A LA CUENTA
     * ==================================================================
     *
     * Busca el usuario por email y compara la contrasena. Devuelve vacio
     * si el email no existe, si la contrasena no coincide o si el usuario
     * esta dado de baja.
     *
     * POR QUE DEVUELVE LO MISMO EN LOS TRES CASOS: si contestara "ese
     * email no existe" estaria confirmando, a cualquiera que pregunte,
     * que direcciones estan registradas. Un mensaje unico no regala esa
     * informacion.
     *
     * LIMITACION IMPORTANTE, A LA VISTA:
     * la contrasena se compara EN TEXTO PLANO, porque asi se guardo en la
     * base. El campo se llama passwordHash pero todavia no guarda un hash.
     * Esto NO es aceptable en un sistema real: si alguien lee la tabla
     * usuarios, se lleva todas las contrasenas. La forma correcta es
     * guardar un hash con BCrypt al registrar y comparar con el hash al
     * ingresar, de manera que la contrasena original no quede en ningun
     * lado. Queda asi porque el alcance de esta entrega no incluye
     * seguridad, y se deja senalado para no darlo por resuelto.
     *
     * Tampoco hay sesiones ni tokens: el servidor confirma quien es y el
     * navegador lo recuerda. Cualquiera que llame a la API directamente
     * puede operar sin pasar por aca.
     */
    public Optional<Usuario> autenticar(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            return Optional.empty();
        }
        return repository.findByEmail(email.trim())
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .filter(u -> password.equals(u.getPasswordHash()));
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
        if (datos.getVarietalesFavoritos() != null) actual.setVarietalesFavoritos(limpiar(datos.getVarietalesFavoritos()));
        if (datos.getLineasFavoritas() != null)     actual.setLineasFavoritas(limpiar(datos.getLineasFavoritas()));
        if (datos.getRol() != null)      actual.setRol(datos.getRol());
        if (datos.getActivo() != null)   actual.setActivo(datos.getActivo());
        // La contrasena solo se toca si vino una nueva y no esta vacia.
        if (datos.getPasswordHash() != null && !datos.getPasswordHash().isBlank()) {
            actual.setPasswordHash(datos.getPasswordHash());
        }
        return repository.save(actual);
    }

    /**
     * ==================================================================
     *  EDICION DEL PROPIO PERFIL — la hace el cliente desde "Mi cuenta"
     * ==================================================================
     *
     * Toca solo lo que una persona puede cambiarse a si misma: nombre,
     * apellido, email, telefono y las preferencias de vino.
     *
     * POR QUE NO REUSA actualizar(): ese metodo aplica tambien 'rol' y
     * 'activo' si vienen en el JSON, porque lo usa el panel de gestion y
     * ahi hace falta. Puesto detras de una pantalla de cliente seria un
     * agujero: alcanzaria con mandar rol: "ADMIN" para autoascenderse, o
     * activo: false para darse de baja sin querer. Aca esos dos campos se
     * ignoran, vengan o no en el cuerpo. La contrasena tampoco se toca:
     * tiene su propio metodo, que exige la actual.
     *
     * LO QUE ESTO NO ARREGLA: el servidor no verifica QUIEN pide el
     * cambio, porque todavia no hay sesiones (ver autenticar()). Cualquiera
     * que llame a la API directamente puede editar el perfil de otro
     * usuario. Lo que se evita aca es que la pantalla del cliente sea el
     * camino para hacerlo. Cerrarlo de verdad es trabajo de Spring Security.
     */
    @Transactional
    public Usuario actualizarPerfil(Integer id, Usuario datos) {
        Usuario actual = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + id));

        if (datos.getEmail() != null && !datos.getEmail().equalsIgnoreCase(actual.getEmail())
                && repository.existsByEmail(datos.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con el email " + datos.getEmail());
        }

        if (datos.getNombre() != null)   actual.setNombre(datos.getNombre());
        if (datos.getApellido() != null) actual.setApellido(datos.getApellido());
        if (datos.getEmail() != null)    actual.setEmail(datos.getEmail());
        if (datos.getTelefono() != null) actual.setTelefono(limpiar(datos.getTelefono()));
        if (datos.getVarietalesFavoritos() != null) actual.setVarietalesFavoritos(limpiar(datos.getVarietalesFavoritos()));
        if (datos.getLineasFavoritas() != null)     actual.setLineasFavoritas(limpiar(datos.getLineasFavoritas()));
        return repository.save(actual);
    }

    /**
     * ==================================================================
     *  CAMBIO DE CONTRASENA — exige la actual
     * ==================================================================
     *
     * POR QUE NO ALCANZA CON actualizar(): ese metodo guarda la contrasena
     * nueva sin preguntar por la anterior, porque esta pensado para un
     * admin que da de alta o repone una cuenta. Para un formulario de
     * "cambiar contrasena" eso no sirve: cualquiera que quede frente a una
     * sesion abierta podria cambiarla y quedarse con la cuenta.
     *
     * El minimo de 6 caracteres es el mismo que pide el registro, para no
     * tener dos reglas distintas segun por donde se entre.
     *
     * Sigue valiendo la limitacion de autenticar(): la comparacion es en
     * TEXTO PLANO porque asi esta guardada la contrasena. Cuando se sume
     * BCrypt, este metodo es uno de los tres lugares a cambiar (los otros
     * son autenticar y registrar).
     */
    @Transactional
    public void cambiarPassword(Integer id, String actual, String nueva) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + id));

        if (actual == null || !actual.equals(usuario.getPasswordHash())) {
            throw new IllegalArgumentException("La contrasena actual no es correcta.");
        }
        if (nueva == null || nueva.length() < 6) {
            throw new IllegalArgumentException("La contrasena nueva tiene que tener al menos 6 caracteres.");
        }
        if (nueva.equals(actual)) {
            throw new IllegalArgumentException("La contrasena nueva tiene que ser distinta de la actual.");
        }

        usuario.setPasswordHash(nueva);
        repository.save(usuario);
    }

    /**
     * Un campo de texto opcional que llego vacio se guarda como null y no
     * como "". Son la misma cosa para quien lo lee, y tener las dos formas
     * obligaria a preguntar por las dos en cada consulta.
     */
    private String limpiar(String valor) {
        return valor.isBlank() ? null : valor.trim();
    }

    /** Baja logica: conserva el historial de pedidos del cliente */
    public void eliminar(Integer id) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario con id " + id));
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new IllegalArgumentException("El usuario " + id + " ya estaba dado de baja.");
        }
        // No se puede dar de baja al ultimo ADMIN: quedaria un sistema donde
        // nadie puede ingresar mercaderia ni resolver una cancelacion.
        if (usuario.getRol() == Rol.ADMIN && repository.countByRolAndActivoTrue(Rol.ADMIN) <= 1) {
            throw new IllegalArgumentException(
                    "No se puede dar de baja al unico administrador activo: sin ADMIN nadie "
                    + "podria registrar ingresos ni resolver cancelaciones.");
        }
        usuario.setActivo(false);
        repository.save(usuario);
    }
}
