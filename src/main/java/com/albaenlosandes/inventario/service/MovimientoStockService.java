package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.MovimientoStock;
import com.albaenlosandes.inventario.repository.MovimientoStockRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MovimientoStockService {

    private final MovimientoStockRepository repository;

    public MovimientoStockService(MovimientoStockRepository repository) {
        this.repository = repository;
    }

    /** Trae los movimientos con su producto y su usuario en UNA sola consulta. */
    public List<MovimientoStock> obtenerTodos() {
        return repository.buscarTodosConDetalle();
    }

    /** Trazabilidad completa de un vino */
    public List<MovimientoStock> obtenerPorProducto(Integer idProducto) {
        return repository.buscarPorProductoConDetalle(idProducto);
    }
}
