package com.albaenlosandes.inventario.repository;

import com.albaenlosandes.inventario.model.MovimientoStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Integer> {

    /**
     * Igual que en pedidos: cada movimiento apunta a un producto, un usuario
     * y a veces un pedido. Sin el JOIN FETCH, Hibernate pedia cada uno por
     * separado y una lista de 100 movimientos disparaba cientos de consultas.
     */
    @Query("""
           SELECT m FROM MovimientoStock m
           LEFT JOIN FETCH m.producto
           LEFT JOIN FETCH m.usuario
           LEFT JOIN FETCH m.pedidoRelacionado
           ORDER BY m.fecha DESC, m.idMovimiento DESC
           """)
    List<MovimientoStock> buscarTodosConDetalle();

    /** Trazabilidad de un producto: todos sus movimientos, del mas nuevo al mas viejo */
    @Query("""
           SELECT m FROM MovimientoStock m
           LEFT JOIN FETCH m.producto p
           LEFT JOIN FETCH m.usuario
           LEFT JOIN FETCH m.pedidoRelacionado
           WHERE p.idProducto = :idProducto
           ORDER BY m.fecha DESC, m.idMovimiento DESC
           """)
    List<MovimientoStock> buscarPorProductoConDetalle(@Param("idProducto") Integer idProducto);
}
