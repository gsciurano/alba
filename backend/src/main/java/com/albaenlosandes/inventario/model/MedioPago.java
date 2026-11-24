package com.albaenlosandes.inventario.model;

/**
 * Medio de pago del checkout.
 *
 * TARJETA es SIMULADA: no hay cobro real, el numero se valida con Luhn y
 * se descarta. Esta para mostrar el circuito completo sin depender de
 * nada de afuera.
 *
 * MERCADO_PAGO es un cobro DE VERDAD contra la API de Mercado Pago (con
 * credenciales de prueba mientras esto sea un trabajo de facultad: la
 * plata no se mueve, pero el camino es el real). Nace sin confirmar: se
 * confirma recien cuando Mercado Pago dice que el pago esta aprobado.
 *
 * EFECTIVO ya no se puede elegir: la bodega dejo de cobrar contra
 * entrega y PedidoService rechaza los pedidos nuevos que lo manden. Se
 * queda en el enum porque hay pedidos viejos guardados asi, y si se
 * borrara no se podrian ni leer.
 */
public enum MedioPago { TARJETA, TRANSFERENCIA, MERCADO_PAGO, EFECTIVO }
