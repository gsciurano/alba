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
 * Quedan las dos listas de valores posibles (varietales y lineas), que en
 * la base son columnas enum o texto acotado y que alimentan los filtros
 * y los formularios de las pantallas que todavia no se migraron.
 *
 * TAMBIEN SE FUERON, en la limpieza de la migracion:
 *   BODEGAS        no lo importaba ningun archivo.
 *   USUARIOS_SEED  la lista de usuarios y contrasenas en texto plano con
 *                  la que se validaba el ingreso en el navegador, antes
 *                  de que existiera POST /api/usuarios/login. No se
 *                  importaba en ningun lado y era, literalmente, la
 *                  contrasena del administrador escrita en un archivo
 *                  que baja cualquiera que entre al sitio.
 * ============================================================
 */

export const VARIETALES = [
  "Chardonnay", "Malbec", "Cabernet Franc", "Cabernet Sauvignon", "Blend"
];

export const LINEAS = ["Finca", "Estate Reserve", "Gran Reserva", "Ediciones Especiales"];

