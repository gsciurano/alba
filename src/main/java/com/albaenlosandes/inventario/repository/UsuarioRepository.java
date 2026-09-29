package com.albaenlosandes.inventario.repository;

import com.albaenlosandes.inventario.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de usuarios (clientes y administradores).
 *
 * Igual que el resto de los repositorios del proyecto, es una INTERFAZ SIN
 * CODIGO. Al extender JpaRepository<Usuario, Integer> ya vienen resueltos
 * save(), findById(), findAll(), deleteById(), count() y existsById(): la
 * implementacion la genera Spring Data al arrancar la aplicacion.
 *
 * Los dos parametros entre los signos menor y mayor son el tipo de la
 * entidad y el tipo de su clave primaria.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    /**
     * Consulta derivada del NOMBRE del metodo: Spring lo lee, reconoce el
     * prefijo "existsBy" y el campo "Email", y arma solo el
     * SELECT COUNT(*) > 0 FROM usuarios WHERE email = ?
     *
     * La usa UsuarioService para no permitir dos usuarios con el mismo
     * email, tanto al dar de alta como al editar uno existente.
     *
     * La base tambien tiene la restriccion UNIQUE sobre esa columna. Son
     * dos redes distintas a proposito: esta da un mensaje claro al usuario,
     * y la de la base actua aunque alguien escriba por fuera de la aplicacion.
     */
    boolean existsByEmail(String email);
}
