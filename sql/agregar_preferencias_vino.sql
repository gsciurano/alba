-- ============================================================
--  PREFERENCIAS DE VINO DEL CLIENTE
--
--  Dos columnas nuevas en 'usuarios' para que cada cliente guarde los
--  varietales y las lineas que le gustan, y "Mi cuenta" pueda
--  recomendarle vinos del catalogo a partir de eso.
--
--  POR QUE DOS COLUMNAS DE TEXTO Y NO UNA TABLA APARTE:
--  son preferencias blandas. Se leen y se guardan siempre completas,
--  nunca se consultan ni se agrupan por separado, y el catalogo entero
--  tiene cuatro lineas y un punado de varietales. Una tabla hija
--  obligaria a una entidad, un repositorio y un servicio mas para
--  guardar lo que en la practica es una sola lista por cliente.
--  Si en algun momento hubiera que responder "cuantos clientes
--  prefieren Malbec", ahi si conviene normalizarlo.
--
--  Se guardan separados por coma, con los valores tal como los
--  muestra el catalogo: "Malbec,Cabernet Franc" y
--  "Finca,Gran Reserva".
--
--  Este script NO borra nada: solo agrega las dos columnas.
--  Ejecutar:
--    mysql -u root -p gestor_inventario < agregar_preferencias_vino.sql
-- ============================================================
USE gestor_inventario;

ALTER TABLE usuarios
    ADD COLUMN varietales_favoritos VARCHAR(255) NULL AFTER telefono,
    ADD COLUMN lineas_favoritas     VARCHAR(255) NULL AFTER varietales_favoritos;

-- Quedan en NULL para todos los clientes que ya estaban: significa
-- "todavia no elegi ninguna", que es distinto de "no me gusta ninguna".

SELECT id_usuario, email, varietales_favoritos, lineas_favoritas
FROM usuarios
ORDER BY id_usuario;
