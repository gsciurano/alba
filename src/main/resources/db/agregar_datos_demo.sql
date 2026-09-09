-- ============================================================
--  DATOS DE DEMOSTRACION
--
--  Los scripts anteriores dejan la base funcionando, pero con un
--  solo pedido y ninguna solicitud de cancelacion. Con eso no se
--  puede mostrar el circuito de cancelaciones, que es la parte mas
--  interesante de la logica de negocio.
--
--  Este script agrega pedidos en distintos estados y solicitudes en
--  los cuatro estados posibles, para que cualquiera del grupo pueda
--  demostrar el sistema completo apenas clona el repositorio.
--
--  IMPORTANTE: las fechas son RELATIVAS al momento en que se corre
--  el script (NOW() - INTERVAL ...). Si estuvieran escritas a mano,
--  dentro de un mes el pedido "de hace 3 horas" tendria 30 dias y la
--  regla de las 24 horas dejaria de poder mostrarse.
--
--  Este script NO borra nada. Se puede correr sobre una base que ya
--  tenga datos.
--
--  Ejecutar despues de los otros tres:
--    mysql -u root -p gestor_inventario < agregar_datos_demo.sql
-- ============================================================

USE gestor_inventario;

-- ------------------------------------------------------------
--  PEDIDOS
--  Cuatro pedidos que cubren los casos que hay que poder mostrar.
-- ------------------------------------------------------------

-- Pedido reciente (3 horas). Sirve para la CANCELACION AUTOMATICA:
-- esta dentro de la ventana de 24 horas.
INSERT INTO pedidos (id_usuario, fecha, estado, medio_pago, total) VALUES
  (2, NOW() - INTERVAL 3 HOUR, 'CONFIRMADO', 'TARJETA', 50000.00);
SET @pedido_reciente = LAST_INSERT_ID();

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
  (@pedido_reciente, 5, 2, 25000.00);

INSERT INTO movimientos_stock (id_producto, id_usuario, tipo, cantidad, motivo, id_pedido, fecha) VALUES
  (5, 2, 'SALIDA', -2, CONCAT('Venta - pedido #', @pedido_reciente), @pedido_reciente, NOW() - INTERVAL 3 HOUR);

UPDATE productos SET stock_actual = stock_actual - 2 WHERE id_producto = 5;


-- Pedido viejo (5 dias). Fuera de la ventana: su cancelacion tiene
-- que pasar por revision de un administrador.
INSERT INTO pedidos (id_usuario, fecha, estado, medio_pago, total) VALUES
  (3, NOW() - INTERVAL 5 DAY, 'CONFIRMADO', 'TRANSFERENCIA', 71000.00);
SET @pedido_viejo = LAST_INSERT_ID();

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
  (@pedido_viejo, 9, 1, 35000.00),
  (@pedido_viejo, 3, 2, 18000.00);

INSERT INTO movimientos_stock (id_producto, id_usuario, tipo, cantidad, motivo, id_pedido, fecha) VALUES
  (9, 3, 'SALIDA', -1, CONCAT('Venta - pedido #', @pedido_viejo), @pedido_viejo, NOW() - INTERVAL 5 DAY),
  (3, 3, 'SALIDA', -2, CONCAT('Venta - pedido #', @pedido_viejo), @pedido_viejo, NOW() - INTERVAL 5 DAY);

UPDATE productos SET stock_actual = stock_actual - 1 WHERE id_producto = 9;
UPDATE productos SET stock_actual = stock_actual - 2 WHERE id_producto = 3;


-- Pedido ya entregado (12 dias). Sobre este NO se puede pedir
-- cancelacion: sirve para mostrar que la regla lo rechaza.
INSERT INTO pedidos (id_usuario, fecha, estado, medio_pago, total) VALUES
  (2, NOW() - INTERVAL 12 DAY, 'ENTREGADO', 'EFECTIVO', 30000.00);
SET @pedido_entregado = LAST_INSERT_ID();

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
  (@pedido_entregado, 1, 2, 15000.00);

INSERT INTO movimientos_stock (id_producto, id_usuario, tipo, cantidad, motivo, id_pedido, fecha) VALUES
  (1, 2, 'SALIDA', -2, CONCAT('Venta - pedido #', @pedido_entregado), @pedido_entregado, NOW() - INTERVAL 12 DAY);

UPDATE productos SET stock_actual = stock_actual - 2 WHERE id_producto = 1;


-- Pedido ya cancelado (8 dias), con su AJUSTE de devolucion de stock.
-- Muestra el inventario perpetuo: la SALIDA y el AJUSTE que la revierte.
INSERT INTO pedidos (id_usuario, fecha, estado, medio_pago, total) VALUES
  (3, NOW() - INTERVAL 8 DAY, 'CANCELADO', 'TARJETA', 36000.00);
SET @pedido_cancelado = LAST_INSERT_ID();

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
  (@pedido_cancelado, 4, 2, 18000.00);

INSERT INTO movimientos_stock (id_producto, id_usuario, tipo, cantidad, motivo, id_pedido, fecha) VALUES
  (4, 3, 'SALIDA', -2, CONCAT('Venta - pedido #', @pedido_cancelado), @pedido_cancelado, NOW() - INTERVAL 8 DAY),
  (4, 3, 'AJUSTE',  2, CONCAT('Cancelacion - pedido #', @pedido_cancelado), @pedido_cancelado, NOW() - INTERVAL 7 DAY);
-- el stock de ese producto no cambia: salio y volvio


-- ------------------------------------------------------------
--  SOLICITUDES DE CANCELACION
--  Una de cada estado, para que la bandeja del administrador tenga
--  algo que mostrar y el seguimiento del cliente tambien.
-- ------------------------------------------------------------

-- 1) EN_REVISION: espera respuesta. Es la que aparece en
--    GET /api/solicitudes/pendientes
INSERT INTO solicitudes_cancelacion
  (id_pedido, id_usuario, motivo, comentario, estado, fecha_solicitud,
   fecha_resolucion, respuesta, id_resuelto_por, resuelta_automaticamente)
VALUES
  (@pedido_viejo, 3, 'DEMORA_ENTREGA',
   'Hace cinco dias que hice la compra y todavia no me llego nada. Necesito saber si va a llegar o me devuelven la plata.',
   'EN_REVISION', NOW() - INTERVAL 2 DAY,
   NULL, NULL, NULL, 0);

-- 2) APROBADA automaticamente: el pedido era reciente y el motivo
--    simple, asi que la aprobo el sistema sin intervencion humana.
INSERT INTO solicitudes_cancelacion
  (id_pedido, id_usuario, motivo, comentario, estado, fecha_solicitud,
   fecha_resolucion, respuesta, id_resuelto_por, resuelta_automaticamente)
VALUES
  (@pedido_cancelado, 3, 'ARREPENTIMIENTO',
   'Me arrepenti de la compra.',
   'APROBADA', NOW() - INTERVAL 7 DAY,
   NOW() - INTERVAL 7 DAY,
   'Aprobada automaticamente. El pedido tenia menos de 24 horas y el motivo es de los simples: la cancelacion se hizo en el momento. El pedido quedo cancelado y las botellas volvieron al stock.',
   NULL, 1);

-- 3) RECHAZADA por un administrador, con su respuesta escrita.
INSERT INTO solicitudes_cancelacion
  (id_pedido, id_usuario, motivo, comentario, estado, fecha_solicitud,
   fecha_resolucion, respuesta, id_resuelto_por, resuelta_automaticamente)
VALUES
  (@pedido_entregado, 2, 'PRODUCTO_EQUIVOCADO',
   'Pedi dos Finca Chardonnay y me parece que me mandaron otra cosa.',
   'RECHAZADA', NOW() - INTERVAL 10 DAY,
   NOW() - INTERVAL 9 DAY,
   'Verificamos el envio contra el remito y los dos Finca Chardonnay salieron correctamente. El pedido figura entregado y conforme, asi que no corresponde la cancelacion. Si el contenido no coincide, escribinos y gestionamos el cambio.',
   1, 0);


-- ------------------------------------------------------------
--  CONTROL
-- ------------------------------------------------------------
SELECT 'Datos de demostracion cargados' AS resultado;
SELECT estado, COUNT(*) AS cantidad FROM pedidos GROUP BY estado;
SELECT estado, COUNT(*) AS cantidad FROM solicitudes_cancelacion GROUP BY estado;
