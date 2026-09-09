package com.albaenlosandes.inventario.controller;

import com.albaenlosandes.inventario.model.MovimientoStock;
import com.albaenlosandes.inventario.service.MovimientoStockService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
@CrossOrigin(origins = "*")
public class MovimientoStockController {

    private final MovimientoStockService service;

    public MovimientoStockController(MovimientoStockService service) {
        this.service = service;
    }

    /** GET /api/movimientos -> trazabilidad completa del inventario */
    @GetMapping
    public List<MovimientoStock> obtenerTodos() {
        return service.obtenerTodos();
    }

    /** GET /api/movimientos/producto/2 -> historial de un vino */
    @GetMapping("/producto/{idProducto}")
    public List<MovimientoStock> obtenerPorProducto(@PathVariable Integer idProducto) {
        return service.obtenerPorProducto(idProducto);
    }
}
