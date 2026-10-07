/**
 * data/productos.js
 * ============================================================
 * LISTAS FIJAS que usan los filtros y los formularios.
 *
 * Antes este archivo tambien traia el catalogo de vinos y pedidos de
 * ejemplo, porque los datos vivian en el navegador. Ahora los vinos y los
 * pedidos salen de MySQL a traves de la API, asi que esas dos listas se
 * eliminaron: tener un catalogo duplicado aca solo podia desincronizarse
 * con la base y confundir.
 *
 * Quedan las tres listas de valores posibles (que en la base son columnas
 * enum o texto acotado) y los usuarios del panel, que siguen siendo del
 * navegador porque el back-end todavia no tiene endpoint de login.
 * ============================================================
 */

export const BODEGAS = ["Alba en los Andes"];

export const VARIETALES = [
  "Chardonnay", "Malbec", "Cabernet Franc", "Cabernet Sauvignon", "Blend"
];

export const LINEAS = ["Finca", "Estate Reserve", "Gran Reserva", "Edicion Especial"];

export const USUARIOS_SEED = [
  { usuario: "admin",     password: "admin123",  nombre: "Admin Bodega",    rol: "administrador" },
  { usuario: "ejecutivo", password: "ventas123", nombre: "Ana Ejecutiva",   rol: "ejecutivo" }
];
