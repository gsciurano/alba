package com.albaenlosandes.inventario.repository;

import com.albaenlosandes.inventario.model.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de los renglones de un pedido.
 *
 * Un DetallePedido no tiene vida propia: nace con su pedido y dice que vino
 * se compro, cuantas botellas y a que precio. Este repositorio existe para
 * poder modificar la composicion de un pedido ya confirmado sin tener que
 * cargar y guardar el pedido entero cada vez.
 */
public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Integer> {

    /**
     * Los renglones de un pedido, con el vino de cada uno EN LA MISMA CONSULTA.
     *
     * El JOIN FETCH evita el problema N+1: sin el, Hibernate traeria los
     * renglones en una consulta y despues pediria el producto de cada uno
     * por separado.
     */
    @Query("""
           SELECT d FROM DetallePedido d
           LEFT JOIN FETCH d.producto
           LEFT JOIN FETCH d.pedido
           WHERE d.pedido.idPedido = :idPedido
           ORDER BY d.idDetalle
           """)
    List<DetallePedido> buscarPorPedido(@Param("idPedido") Integer idPedido);

    @Query("""
           SELECT d FROM DetallePedido d
           LEFT JOIN FETCH d.producto
           LEFT JOIN FETCH d.pedido p
           LEFT JOIN FETCH p.usuario
           WHERE d.idDetalle = :id
           """)
    Optional<DetallePedido> buscarPorIdConDetalle(@Param("id") Integer id);

    /**
     * Cuantos renglones le quedan al pedido.
     *
     * Lo usa DetallePedidoService antes de quitar uno: si es el ultimo, no
     * deja borrarlo. Un pedido sin renglones no tiene sentido; si el cliente
     * ya no quiere nada, lo que corresponde es cancelar el pedido entero.
     *
     * Es otra consulta derivada del nombre: "count" + "By" + la ruta del
     * campo "pedido.idPedido" escrita sin puntos.
     */
    long countByPedidoIdPedido(Integer idPedido);
}
