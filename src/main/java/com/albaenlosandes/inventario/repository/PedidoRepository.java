package com.albaenlosandes.inventario.repository;

import com.albaenlosandes.inventario.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {

    /**
     * ==================================================================
     *  EL ARREGLO DEL PROBLEMA "N+1".
     * ==================================================================
     *
     * QUE PASABA ANTES: con findAll(), Hibernate traia los pedidos en una
     * consulta y despues disparaba UNA CONSULTA MAS por cada pedido para
     * traer sus renglones. Con 7 pedidos eran 10 consultas; con 500 pedidos
     * serian mas de 500. Eso es el problema "N+1": una consulta, mas N.
     *
     * COMO SE ARREGLA: "JOIN FETCH" le dice a Hibernate que traiga el pedido
     * Y sus relaciones en la MISMA consulta, con un JOIN de SQL comun.
     *
     * Por que DISTINCT: el JOIN repite la fila del pedido una vez por cada
     * renglon. Sin DISTINCT, un pedido de 3 vinos aparece 3 veces.
     *
     * OJO para mas adelante: JOIN FETCH sobre una coleccion no se lleva bien
     * con la paginacion (Hibernate tendria que paginar en memoria). Cuando se
     * agregue paginacion habra que traer primero los ids y despues el detalle.
     */
    @Query("""
           SELECT DISTINCT p FROM Pedido p
           LEFT JOIN FETCH p.usuario
           LEFT JOIN FETCH p.detalles d
           LEFT JOIN FETCH d.producto
           ORDER BY p.fecha DESC
           """)
    List<Pedido> buscarTodosConDetalle();

    @Query("""
           SELECT DISTINCT p FROM Pedido p
           LEFT JOIN FETCH p.usuario
           LEFT JOIN FETCH p.detalles d
           LEFT JOIN FETCH d.producto
           WHERE p.idPedido = :id
           """)
    Optional<Pedido> buscarPorIdConDetalle(@Param("id") Integer id);

    /** Historial de pedidos de un usuario ("Mi cuenta"), tambien en una sola consulta. */
    @Query("""
           SELECT DISTINCT p FROM Pedido p
           LEFT JOIN FETCH p.usuario u
           LEFT JOIN FETCH p.detalles d
           LEFT JOIN FETCH d.producto
           WHERE u.idUsuario = :idUsuario
           ORDER BY p.fecha DESC
           """)
    List<Pedido> buscarPorUsuarioConDetalle(@Param("idUsuario") Integer idUsuario);
}
