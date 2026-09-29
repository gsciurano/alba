package com.albaenlosandes.inventario.controller;

import com.albaenlosandes.inventario.model.MovimientoStock;
import com.albaenlosandes.inventario.service.MovimientoStockService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API de la trazabilidad del inventario.
 *
 * Tiene las cuatro operaciones, pero ninguna se comporta como en una tabla
 * comun, porque un movimiento es un HECHO que ya ocurrio y el historial de
 * auditoria no se reescribe:
 *
 *   POST   -> es un AJUSTE de inventario (conteo fisico), y corrige el stock
 *   GET    -> normal, con una vista aparte que incluye los anulados
 *   PUT    -> solo corrige el MOTIVO, nunca la cantidad ni el tipo
 *   DELETE -> ANULA con contra-asiento: crea el movimiento opuesto y los enlaza
 */
@RestController
@RequestMapping("/api/movimientos")
@CrossOrigin(origins = "*")
public class MovimientoStockController {

    private final MovimientoStockService service;

    public MovimientoStockController(MovimientoStockService service) {
        this.service = service;
    }

    /** GET /api/movimientos -> trazabilidad de los movimientos vigentes */
    @GetMapping
    public List<MovimientoStock> obtenerTodos() {
        return service.obtenerTodos();
    }

    /** GET /api/movimientos/auditoria -> incluye los anulados y sus contra-asientos */
    @GetMapping("/auditoria")
    public List<MovimientoStock> auditoriaCompleta() {
        return service.obtenerTodosIncluyendoAnulados();
    }

    /** GET /api/movimientos/7 -> un movimiento puntual */
    @GetMapping("/{id}")
    public MovimientoStock obtenerPorId(@PathVariable Integer id) {
        return service.obtenerPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el movimiento con id " + id));
    }

    /** GET /api/movimientos/producto/2 -> historial de un vino */
    @GetMapping("/producto/{idProducto}")
    public List<MovimientoStock> obtenerPorProducto(@PathVariable Integer idProducto) {
        return service.obtenerPorProducto(idProducto);
    }

    /**
     * POST /api/movimientos?idProducto=5&cantidad=-3&motivo=Rotura&idUsuario=1
     *
     * AJUSTE MANUAL DE INVENTARIO. Es la operacion del conteo fisico: se
     * cuentan las botellas del deposito, no coinciden con el sistema, y se
     * corrige la diferencia dejando constancia.
     *
     * La cantidad puede ser negativa (faltan botellas) o positiva
     * (aparecieron), pero nunca cero. El motivo es obligatorio.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoStock registrarAjuste(@RequestParam Integer idProducto,
                                           @RequestParam Integer cantidad,
                                           @RequestParam String motivo,
                                           @RequestParam Integer idUsuario) {
        return service.registrarAjuste(idProducto, cantidad, motivo, idUsuario);
    }

    /**
     * PUT /api/movimientos/7?motivo=Devolucion de un cliente
     *
     * Corrige SOLO la redaccion del motivo. La cantidad, el tipo, el producto
     * y la fecha no se pueden tocar: eso seria reescribir lo que paso, y el
     * stock dejaria de coincidir con la suma de sus movimientos.
     */
    @PutMapping("/{id}")
    public MovimientoStock actualizarMotivo(@PathVariable Integer id,
                                            @RequestParam String motivo) {
        return service.actualizarMotivo(id, motivo);
    }

    /**
     * DELETE /api/movimientos/7?idUsuario=1&motivo=Se cargo dos veces
     *
     * ANULA el movimiento con un contra-asiento: lo marca inactivo, crea uno
     * nuevo con la cantidad opuesta enlazado al original, y corrige el stock.
     * El historial conserva las dos filas.
     *
     * Devuelve 201 y no 204 porque la operacion CREA un movimiento nuevo:
     * el contra-asiento es parte de la respuesta.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoStock anular(@PathVariable Integer id,
                                  @RequestParam Integer idUsuario,
                                  @RequestParam(required = false) String motivo) {
        return service.anular(id, motivo, idUsuario);
    }
}
