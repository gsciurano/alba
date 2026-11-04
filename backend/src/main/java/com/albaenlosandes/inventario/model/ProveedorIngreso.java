package com.albaenlosandes.inventario.model;

/**
 * Como ingresa una cuenta: coincide con el ENUM de usuarios.proveedor.
 *
 *  LOCAL  -> email y contrasena guardados por nosotros.
 *  GOOGLE -> se identifica contra Google; no tiene contrasena, y por eso
 *            password_hash queda en NULL (ver db/agregar_ingreso_con_google.sql).
 */
public enum ProveedorIngreso { LOCAL, GOOGLE }
