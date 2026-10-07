package com.albaenlosandes.inventario.repository;

import com.albaenlosandes.inventario.model.Rol;
import com.albaenlosandes.inventario.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

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

    /**
     * Trae el usuario con ese email, si existe.
     *
     * La usa el ingreso a la cuenta: el email es la identificacion del
     * cliente (la columna es UNIQUE), asi que alcanza para encontrarlo.
     *
     * Devuelve Optional y no Usuario a proposito: que no haya ninguno con
     * ese email es un caso normal (alguien se equivoco al escribirlo), no
     * un error. Optional obliga a quien llama a decidir que hacer, en vez
     * de dejar pasar un null que despues explota en otro lado.
     */
    Optional<Usuario> findByEmail(String email);

    /**
     * Los usuarios que siguen de alta. Es el listado que usan los
     * desplegables de la aplicacion: un usuario dado de baja no puede ser
     * elegido como responsable de un movimiento ni como cliente de un pedido.
     *
     * findAll() sigue existiendo para la pestana de usuarios, que necesita
     * ver tambien los inactivos para poder reactivarlos.
     */
    List<Usuario> findByActivoTrue();

    /**
     * Cuenta los administradores todavia de alta. La usa UsuarioService para
     * no dejar el sistema sin ningun ADMIN: sin admins no se podria registrar
     * un ingreso de mercaderia ni resolver una cancelacion.
     */
    long countByRolAndActivoTrue(Rol rol);
}
