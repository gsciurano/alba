package com.albaenlosandes.inventario.model;

/**
 * Medio de pago del checkout (pago simulado).
 *
 * EFECTIVO ya no se puede elegir: la bodega dejo de cobrar contra
 * entrega y PedidoService rechaza los pedidos nuevos que lo manden. Se
 * queda en el enum porque hay pedidos viejos guardados asi, y si se
 * borrara no se podrian ni leer.
 */
public enum MedioPago { TARJETA, TRANSFERENCIA, EFECTIVO }
