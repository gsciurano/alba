package com.albaenlosandes.inventario.repository;

import com.albaenlosandes.inventario.model.Linea;
import com.albaenlosandes.inventario.model.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de productos.
 * Al extender JpaRepository<Producto, Integer> Spring Data genera
 * automaticamente: findAll(), findById(), save(), deleteById(), count()...
 * (Conceptualmente, esto ES el patron DAO: una interfaz que define el
 *  contrato de acceso a datos; la implementacion la provee el framework.)
 */
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    /**
     * ==================================================================
     *  BUSQUEDA CON BLOQUEO, PARA VENDER SIN VENDER DE MAS.
     * ==================================================================
     *
     * @Lock(PESSIMISTIC_WRITE) genera un "SELECT ... FOR UPDATE": la fila del
     * producto queda BLOQUEADA hasta que termine la transaccion. Si otro
     * cliente esta comprando el mismo vino al mismo tiempo, espera su turno
     * en vez de leer un stock que ya no es cierto.
     *
     * SIN esto pasaba lo siguiente: dos compras simultaneas leian "quedan 2",
     * las dos daban por buena la venta, y al descontar el stock chocaban.
     * MySQL respondia con un deadlock y la API devolvia 500.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.idProducto = :id")
    Optional<Producto> buscarParaActualizar(@Param("id") Integer id);

    /** Consulta derivada del NOMBRE del metodo: WHERE activo = true */
    List<Producto> findByActivoTrue();

    /** WHERE linea = ? AND activo = true */
    List<Producto> findByLineaAndActivoTrue(Linea linea);

    /** WHERE nombre LIKE %?% AND activo = true (buscador del catalogo) */
    List<Producto> findByNombreContainingIgnoreCaseAndActivoTrue(String texto);

    /**
     * EJEMPLO DE CONSULTA DERIVADA CON RANGO.
     * "vinos de X pesos para arriba, solo activos, del mas caro al mas barato".
     * No hay que escribir NADA de consulta: Spring lee el nombre del metodo,
     * lo parte en pedazos (findBy + Precio + GreaterThanEqual + And + ActivoTrue
     * + OrderBy + Precio + Desc) y arma el SQL solo.
     */
    List<Producto> findByPrecioGreaterThanEqualAndActivoTrueOrderByPrecioDesc(BigDecimal precio);

    /**
     * Comparar dos columnas entre si no se puede derivar del nombre:
     * para eso existe @Query (JPQL). Alerta de stock minimo.
     */
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.stockActual < p.stockMinimo")
    List<Producto> alertasStockMinimo();
}
