package com.albaenlosandes.inventario.repository;

import com.albaenlosandes.inventario.model.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Integer> {

    /** Los renglones de un pedido, con el vino de cada uno en la misma consulta. */
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

    /** Cuantos renglones le quedan al pedido. Se usa para no dejarlo vacio. */
    long countByPedidoIdPedido(Integer idPedido);
}
