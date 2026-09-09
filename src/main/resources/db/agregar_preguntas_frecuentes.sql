-- ============================================================
--  NUEVA TABLA: preguntas_frecuentes
--
--  Las preguntas que se le muestran al cliente cuando tiene un
--  problema con un pedido. Estan en la BASE y no escritas dentro
--  del codigo por dos motivos:
--    1. la bodega puede editarlas sin que un programador recompile;
--    2. la ventana de escritorio y la futura pagina web leen las
--       MISMAS preguntas desde el mismo lugar.
--
--  'motivo' NULL = pregunta general, se muestra siempre.
--  'motivo' con valor = solo aparece cuando el cliente elige ese motivo.
--
--  Ejecutar:
--    mysql -u root -p gestor_inventario < agregar_preguntas_frecuentes.sql
-- ============================================================
USE gestor_inventario;

CREATE TABLE IF NOT EXISTS preguntas_frecuentes (
    id_pregunta INT AUTO_INCREMENT PRIMARY KEY,
    pregunta    VARCHAR(200)  NOT NULL,
    respuesta   VARCHAR(1000) NOT NULL,
    motivo      ENUM('ARREPENTIMIENTO','ERROR_AL_COMPRAR','PROBLEMA_PAGO',
                     'DEMORA_ENTREGA','PRODUCTO_EQUIVOCADO','OTRO'),  -- NULL = general
    orden       INT           NOT NULL DEFAULT 0,
    activa      BOOLEAN       NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

CREATE INDEX idx_faq_motivo ON preguntas_frecuentes (motivo, orden);

DELETE FROM preguntas_frecuentes;

-- ---------- GENERALES (se muestran siempre) ----------
INSERT INTO preguntas_frecuentes (pregunta, respuesta, motivo, orden) VALUES
('¿Hasta cuando puedo cancelar sin tramite?',
 'Si tu pedido tiene menos de 24 horas y el motivo es simple (te arrepentiste, te equivocaste al comprar o hubo un problema con el pago), la cancelacion es automatica: se hace en el momento y no espera la aprobacion de nadie.',
 NULL, 1),

('¿Puedo cancelar un pedido que ya me entregaron?',
 'No. Un pedido ENTREGADO no se puede cancelar. Si hubo un problema con lo que recibiste, se resuelve como un cambio: elegi el motivo "Me llego un producto equivocado" y contanos que paso.',
 NULL, 2),

('¿Donde sigo mi solicitud?',
 'Todas tus solicitudes quedan listadas con su estado, la fecha y la respuesta de la bodega. No hace falta que llames ni que escribas un mail: entras y ves en que anda.',
 NULL, 3),

('¿Que significa cada estado?',
 'PENDIENTE: recien la mandaste. EN REVISION: la esta mirando alguien de la bodega. APROBADA: se cancelo el pedido y las botellas volvieron al stock. RECHAZADA: no se pudo cancelar, y en la respuesta te explicamos por que.',
 NULL, 4);

-- ---------- ARREPENTIMIENTO ----------
INSERT INTO preguntas_frecuentes (pregunta, respuesta, motivo, orden) VALUES
('Me arrepenti de la compra, ¿que hago?',
 'Si pasaron menos de 24 horas, cancelas ahora mismo desde este formulario y listo. Si pasaron mas, la solicitud queda en revision porque el pedido puede estar preparado o ya en camino.',
 'ARREPENTIMIENTO', 1),

('¿Cuando me devuelven el dinero?',
 'El reintegro se hace por el mismo medio con el que pagaste, una vez que la cancelacion queda aprobada. El plazo depende del banco o la tarjeta.',
 'ARREPENTIMIENTO', 2);

-- ---------- ERROR AL COMPRAR ----------
INSERT INTO preguntas_frecuentes (pregunta, respuesta, motivo, orden) VALUES
('Me equivoque de vino o de cantidad',
 'Lo mas rapido es cancelar el pedido y volver a comprar bien. Si tiene menos de 24 horas, la cancelacion es inmediata y podes hacer el pedido nuevo enseguida.',
 'ERROR_AL_COMPRAR', 1),

('¿Puedo cambiar la cantidad en vez de cancelar todo?',
 'Por ahora no se puede modificar un pedido ya confirmado. Hay que cancelarlo y volver a hacerlo con las cantidades correctas.',
 'ERROR_AL_COMPRAR', 2);

-- ---------- PROBLEMA CON EL PAGO ----------
INSERT INTO preguntas_frecuentes (pregunta, respuesta, motivo, orden) VALUES
('Me cobraron dos veces el mismo pedido',
 'Si ves dos cobros por un mismo pedido, mandanos la solicitud con el detalle. Uno de los dos se anula y se reintegra.',
 'PROBLEMA_PAGO', 1),

('El pago no se acredito',
 'Si el pago quedo pendiente, el pedido igual figura CONFIRMADO y las botellas quedan reservadas. Si preferis no esperar, podes cancelar.',
 'PROBLEMA_PAGO', 2);

-- ---------- DEMORA EN LA ENTREGA ----------
INSERT INTO preguntas_frecuentes (pregunta, respuesta, motivo, orden) VALUES
('¿Cuanto tarda un envio?',
 'Dentro de Mendoza, entre 48 y 72 horas habiles. Al resto del pais, entre 5 y 7 dias habiles. Los pedidos hechos un viernes salen el lunes siguiente.',
 'DEMORA_ENTREGA', 1),

('Mi pedido figura CONFIRMADO hace varios dias',
 'CONFIRMADO quiere decir que esta en preparacion o ya viajando. Antes de cancelar conviene consultar el estado del envio: en muchos casos ya salio de la bodega.',
 'DEMORA_ENTREGA', 2),

('Quiero cancelar por la demora',
 'Se puede, pero la solicitud pasa por revision: si el pedido ya salio hay que coordinar la devolucion. Contanos el detalle y te responden a la brevedad.',
 'DEMORA_ENTREGA', 3);

-- ---------- PRODUCTO EQUIVOCADO ----------
INSERT INTO preguntas_frecuentes (pregunta, respuesta, motivo, orden) VALUES
('Me llego un vino distinto al que pedi',
 'Te lo cambiamos sin cargo y la bodega se hace cargo del envio. Conviene el cambio antes que la cancelacion: no perdes la compra ni el precio que pagaste.',
 'PRODUCTO_EQUIVOCADO', 1),

('¿Tengo que devolver la botella equivocada?',
 'Si, pero no tenes que ir a ningun lado: se retira por el mismo canal por el que llego. Coordinamos con vos cuando aprobamos la solicitud.',
 'PRODUCTO_EQUIVOCADO', 2),

('Me llego una botella rota o en mal estado',
 'Mandanos la solicitud con el detalle. Se reemplaza sin cargo. Si podes, guarda la botella hasta que te contactemos.',
 'PRODUCTO_EQUIVOCADO', 3);

-- ---------- OTRO ----------
INSERT INTO preguntas_frecuentes (pregunta, respuesta, motivo, orden) VALUES
('Mi problema no esta en la lista',
 'Contanos con tus palabras que paso en el campo de comentario. La solicitud queda registrada y alguien de la bodega la revisa.',
 'OTRO', 1);

SELECT CONCAT(COUNT(*), ' preguntas cargadas') AS resultado FROM preguntas_frecuentes;
