-- ============================================================
--  PAGO POR MERCADO PAGO
--
--  Es el primer medio de pago de la tienda que cobra de verdad: la
--  tarjeta del checkout es simulada y la transferencia la acredita un
--  administrador a mano. Eso agrega dos cosas que hay que guardar.
--
--  1) UN VALOR NUEVO EN medio_pago.
--     'MERCADO_PAGO' se suma al ENUM. Los tres valores viejos quedan
--     tal cual, incluido EFECTIVO: hay pedidos guardados asi y si se
--     borrara del ENUM esos pedidos no se podrian ni leer.
--
--  2) EL NUMERO DE PAGO QUE DEVUELVE MERCADO PAGO.
--     POR QUE NO ALCANZA CON pago_confirmado = TRUE: un pedido dado por
--     pagado sin ningun numero es una afirmacion sin respaldo. Con el
--     numero de pago se busca el cobro en el panel de Mercado Pago, se
--     pide una devolucion y se contesta un reclamo. Sin el, no hay a
--     donde ir.
--
--     POR QUE TAMBIEN LA PREFERENCIA: es el "carrito" que se le manda a
--     Mercado Pago antes de pagar. Guardarla distingue un pedido que
--     nunca llego a la pantalla de pago de uno que llego y se abandono,
--     que son dos problemas distintos.
--
--  QUIEN CONFIRMA EL PAGO. En una transferencia lo tilda una persona y
--  queda en pago_confirmado_por. En Mercado Pago no hay ninguna
--  persona: lo confirma la respuesta del servicio, asi que
--  pago_confirmado_por queda NULO y el respaldo es pago_externo_id. Por
--  eso las dos columnas conviven en vez de reemplazarse.
--
--  Las dos columnas nuevas admiten nulo, asi que los pedidos que ya
--  existen siguen funcionando sin tocarlos, igual que la app de
--  escritorio, que no sabe que estas columnas existen.
--
--  Este script NO borra nada: solo agrega.
--  Ejecutar:
--    mysql -u root -p gestor_inventario < agregar_mercado_pago.sql
-- ============================================================
USE gestor_inventario;

-- ------------------------------------------------------------
--  1) El valor nuevo del ENUM.
--
--  MODIFY COLUMN reescribe la definicion entera, asi que hay que
--  repetir los valores viejos: el que se olvide se borra, y con el los
--  pedidos que lo usaban.
-- ------------------------------------------------------------
ALTER TABLE pedidos
    MODIFY COLUMN medio_pago
        ENUM('TARJETA','TRANSFERENCIA','MERCADO_PAGO','EFECTIVO') NOT NULL;

-- ------------------------------------------------------------
--  2) El rastro del cobro.
--
--  Los dos son texto: los id de Mercado Pago son largos, pueden traer
--  guiones y no se hacen cuentas con ellos.
-- ------------------------------------------------------------
ALTER TABLE pedidos
    ADD COLUMN pago_externo_id     VARCHAR(40) NULL AFTER pago_confirmado_por,
    ADD COLUMN pago_preferencia_id VARCHAR(64) NULL AFTER pago_externo_id;

-- ------------------------------------------------------------
--  Indice por numero de pago.
--
--  POR QUE: la pregunta que se hace en la vida real es al revez de la
--  que resuelve la clave primaria. Llega un reclamo o una devolucion
--  con el numero de pago de Mercado Pago y hay que encontrar DE QUE
--  PEDIDO era. Sin indice eso recorre la tabla entera.
-- ------------------------------------------------------------
CREATE INDEX idx_pedidos_pago_externo ON pedidos (pago_externo_id);
