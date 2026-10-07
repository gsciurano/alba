package com.albaenlosandes.inventario.model;

/**
 * Ciclo de vida de una solicitud de cancelacion:
 *
 *   PENDIENTE ----> APROBADA    (el pedido se cancela y vuelve el stock)
 *        |
 *        +--------> EN_REVISION -+--> APROBADA
 *                                +--> RECHAZADA
 *

 */
public enum EstadoSolicitud { PENDIENTE, EN_REVISION, APROBADA, RECHAZADA }
