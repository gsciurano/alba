-- ============================================================
--  CONFIRMACION DE PAGO POR TRANSFERENCIA
--
--  Cuando alguien paga con transferencia, el pedido queda hecho pero
--  la plata todavia no esta. Hace falta un lugar donde anotar si el
--  pago se acredito, y quien lo dio por acreditado.
--
--  POR QUE NO UN ESTADO NUEVO ('ESPERANDO_PAGO'):
--  'estado' es un ENUM de MySQL, un enum de Java, un mapa de textos en
--  api.js, un filtro del tablero y una columna de la app de escritorio.
--  Un valor nuevo obliga a tocar las cinco cosas y lo que se olvide
--  falla en silencio.
--
--  Pero sobre todo: "esperando comprobante" NO es un estado del pedido,
--  es un estado del PAGO. Un pedido puede estar CONFIRMADO y la
--  transferencia sin acreditar: son dos ejes distintos y meterlos en
--  una sola columna es lo que despues no se puede desarmar.
--
--  Con estas columnas la frase se deduce sola:
--     esperando comprobante
--        = medio_pago = 'TRANSFERENCIA' AND pago_confirmado = FALSE
--
--  POR QUE 'pago_confirmado_por':
--  tildar un pago es decir "entro plata". Sin saber quien lo tildo no
--  hay a quien preguntarle cuando el banco no muestra el deposito.
--
--  Las tres columnas admiten nulo o traen DEFAULT, asi que los pedidos
--  que ya existen siguen funcionando sin tocarlos, igual que la app de
--  escritorio, que no sabe que estas columnas existen.
--
--  Este script NO borra nada: solo agrega.
--  Ejecutar:
--    mysql -u root -p gestor_inventario < agregar_pago_transferencia.sql
-- ============================================================
USE gestor_inventario;

ALTER TABLE pedidos
    ADD COLUMN pago_confirmado     BOOLEAN  NOT NULL DEFAULT FALSE AFTER medio_pago,
    ADD COLUMN pago_confirmado_en  DATETIME NULL                   AFTER pago_confirmado,
    ADD COLUMN pago_confirmado_por INT      NULL                   AFTER pago_confirmado_en,
    ADD CONSTRAINT fk_pedido_pago_confirmado_por
        FOREIGN KEY (pago_confirmado_por) REFERENCES usuarios(id_usuario);

-- ------------------------------------------------------------
--  Los pagos que YA estan cobrados quedan en TRUE.
--
--  Tarjeta y efectivo no esperan comprobante: el DEFAULT FALSE los
--  dejaria a todos como "pendientes" y la bandeja del panel arrancaria
--  llena de pedidos viejos que nadie tiene que revisar.
--
--  Las transferencias que ya existen SI quedan en FALSE, que es la
--  verdad: nunca se registro si esa plata entro.
-- ------------------------------------------------------------
UPDATE pedidos
   SET pago_confirmado = TRUE
 WHERE medio_pago IN ('TARJETA', 'EFECTIVO');

-- ------------------------------------------------------------
--  Indice para la bandeja "pagos pendientes" del panel, que filtra
--  justo por estas dos columnas juntas.
-- ------------------------------------------------------------
CREATE INDEX idx_pedidos_pago ON pedidos (medio_pago, pago_confirmado);
