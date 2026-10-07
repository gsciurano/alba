package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.model.EstadoPedido;
import com.albaenlosandes.inventario.model.MotivoCancelacion;
import com.albaenlosandes.inventario.model.Pedido;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * ==========================================================================
 *  LA POLITICA DE CANCELACIONES: la regla que decide QUE pasa con cada pedido.
 * ==========================================================================
 *
 * Esta clase aislada tiene una sola responsabilidad: dado un pedido y un
 * motivo, decir si la cancelacion se aprueba sola o si tiene que mirarla
 * una persona. Nada mas.
 *
 * POR QUE ESTA APARTE Y NO ADENTRO DEL SERVICE:
 * es la regla que mas va a cambiar con el tiempo (hoy son 24 horas, maniana
 * pueden ser 48, o depender del monto). Tenerla en su propia clase permite
 * cambiarla sin tocar el circuito de solicitudes, y permite explicarla sola.
 *
 * La misma politica la usan las dos interfaces: la ventana de escritorio y
 * la futura pagina web. La regla vive en el servidor, en un solo lugar.
 */
@Component
public class PoliticaCancelacion {

    /** Ventana de arrepentimiento. Fuera de este plazo, decide una persona. */
    public static final int HORAS_PARA_CANCELACION_DIRECTA = 24;

    /** El resultado de evaluar un caso: que se puede hacer y por que. */
    public record Evaluacion(
            boolean sePuedePedir,      // ¿tiene sentido pedir la cancelacion?
            boolean cancelacionDirecta,// ¿se aprueba sola?
            String explicacion         // el motivo, en palabras, para el cliente
    ) { }

    /**
     * Evalua un caso concreto. No toca la base ni guarda nada:
     * solo mira los datos y devuelve la decision.
     */
    public Evaluacion evaluar(Pedido pedido, MotivoCancelacion motivo) {

        // ---- Sobre que pedidos NO tiene sentido pedir una cancelacion ----
        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            return new Evaluacion(false, false,
                    "El pedido #" + pedido.getIdPedido() + " ya esta cancelado.");
        }
        if (pedido.getEstado() == EstadoPedido.ENTREGADO) {
            return new Evaluacion(false, false,
                    "El pedido #" + pedido.getIdPedido() + " ya fue entregado y no se puede cancelar. "
                    + "Si hubo un problema con lo recibido, corresponde un cambio.");
        }

        long horas = horasDesde(pedido.getFecha());
        boolean esReciente = horas < HORAS_PARA_CANCELACION_DIRECTA;
        boolean motivoSimple = esMotivoSimple(motivo);

        // ---- La regla central: reciente + motivo simple = se cancela solo ----
        if (esReciente && motivoSimple) {
            return new Evaluacion(true, true,
                    "El pedido tiene " + horas + " horas (menos de " + HORAS_PARA_CANCELACION_DIRECTA
                    + ") y el motivo es de los simples: la cancelacion se hace en el momento.");
        }

        // ---- Todo lo demas: lo mira una persona ----
        String porQue = !esReciente
                ? "pasaron " + horas + " horas desde la compra, mas de las "
                  + HORAS_PARA_CANCELACION_DIRECTA + " de la ventana automatica"
                : "el motivo elegido necesita que alguien de la bodega lo revise";

        return new Evaluacion(true, false,
                "La solicitud queda registrada y pasa a revision porque " + porQue + ".");
    }

    /**
     * Motivos que dependen solo de la voluntad del cliente. Los otros
     * (demora, producto equivocado) requieren verificar algo del lado
     * de la bodega, asi que no se pueden aprobar automaticamente.
     */
    private boolean esMotivoSimple(MotivoCancelacion motivo) {
        if (motivo == null) return false;
        return switch (motivo) {
            case ARREPENTIMIENTO, ERROR_AL_COMPRAR, PROBLEMA_PAGO -> true;
            case DEMORA_ENTREGA, PRODUCTO_EQUIVOCADO, OTRO -> false;
        };
    }

    private long horasDesde(LocalDateTime fecha) {
        if (fecha == null) return 0;
        return Math.max(Duration.between(fecha, LocalDateTime.now()).toHours(), 0);
    }
}
