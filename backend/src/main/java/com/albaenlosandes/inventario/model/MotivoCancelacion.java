package com.albaenlosandes.inventario.model;

/**
 * Por que el cliente quiere cancelar. No es decorativo: este valor decide
 * dos cosas. Primero, que preguntas frecuentes se le muestran en el
 * formulario (tabla preguntas_frecuentes). Segundo, si la cancelacion
 * califica para aprobarse sola (ver PoliticaCancelacion).
 */
public enum MotivoCancelacion {
    ARREPENTIMIENTO,      // se arrepintio de la compra
    ERROR_AL_COMPRAR,     // se equivoco de vino o de cantidad
    PROBLEMA_PAGO,        // problemas con el medio de pago
    DEMORA_ENTREGA,       // esta tardando mas de lo esperado
    PRODUCTO_EQUIVOCADO,  // le llego algo distinto a lo que pidio
    OTRO
}
