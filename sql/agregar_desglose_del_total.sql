-- ============================================================
--  EL DESGLOSE DEL TOTAL
--
--  EL PROBLEMA QUE ARREGLA. Hasta hoy la columna 'total' guardaba la
--  suma de los vinos y nada mas. El envio ($2.500), el 21% de
--  impuestos y el descuento del cupon se calculaban en el navegador
--  (js/tienda.js:337, calcularTotales) y no se guardaban en ningun
--  lado. Resultado: la pantalla de confirmacion le mostraba al cliente
--  un numero, el comprobante que sale por correo mostraba OTRO, y la
--  base guardaba el segundo. El README lo tenia anotado como
--  "diferencia conocida"; era, en realidad, una venta registrada por
--  un importe distinto al que se cobro.
--
--  QUE QUEDA GUARDADO AHORA. Las cuatro partes de la cuenta, cada una
--  en su columna, mas el codigo del cupon que se uso:
--
--      total = subtotal - descuento + envio + impuestos
--
--  POR QUE 'cupon' ES TEXTO Y NO UNA TABLA. No hay tabla de cupones:
--  los tres codigos viven en el codigo del servidor. Guardar el codigo
--  tal como se tecleo alcanza para contestar "con que descuento se
--  vendio este pedido", que es la pregunta que se le hace a un pedido
--  viejo. Una tabla de cupones (con vigencia, tope de usos, alcance)
--  es otra funcionalidad, no esta.
--
--  ============================================================
--  ATENCION: ESTE SCRIPT CAMBIA EL SIGNIFICADO DE 'total'
--  ============================================================
--
--  Desde aca en adelante 'total' es LO QUE PAGA EL CLIENTE, con envio
--  e impuestos adentro. Antes era la suma de los vinos sola.
--
--  Quien lee esa columna: la app de escritorio (PanelPedidos y
--  PanelSolicitudes) y el comprobante por correo (ServicioCorreo). Las
--  tres la muestran con el rotulo "Total". O sea que las tres pasan a
--  mostrar el numero correcto sin tocarles una linea: hoy muestran el
--  subtotal llamandolo total.
--
--  LOS PEDIDOS VIEJOS NO SE PUEDEN ARREGLAR, Y NO SE INVENTAN.
--  De un pedido anterior a este script se sabe el subtotal y nada mas.
--  El envio y el 21% nunca se guardaron, y el cupon tampoco: no hay
--  forma de saber si ese cliente uso ALBA10. Entonces el UPDATE de
--  abajo copia total -> subtotal y deja envio, impuestos y descuento
--  en cero, que es exactamente lo que la base sabe de esas ventas.
--
--  Consecuencia, dicha en voz alta: los pedidos viejos quedan
--  registrados por menos de lo que probablemente se cobro. Es
--  irreparable con los datos que hay. Rellenar $2.500 de envio y un
--  21% calculado al reves seria inventar numeros de plata, que es peor
--  que tenerlos incompletos y saberlo.
--
--  Para distinguir unos de otros: los pedidos con envio = 0 son los
--  anteriores a esta migracion. Ninguna venta nueva puede tener envio
--  en cero, porque un carrito vacio no llega a ser pedido.
--
--  Este script NO borra nada: agrega columnas y completa 'subtotal'.
--  Ejecutar:
--    mysql -u root -p gestor_inventario < agregar_desglose_del_total.sql
-- ============================================================
USE gestor_inventario;

ALTER TABLE pedidos
    ADD COLUMN subtotal  DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER medio_pago,
    ADD COLUMN descuento DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER subtotal,
    ADD COLUMN cupon     VARCHAR(30)   NULL              AFTER descuento,
    ADD COLUMN envio     DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER cupon,
    ADD COLUMN impuestos DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER envio;

-- Los tres importes nuevos no pueden ser negativos, igual que 'total'.
-- Un descuento se guarda en positivo y se RESTA: si se guardara en
-- negativo, la suma de la formula daria de mas y nadie lo notaria hasta
-- cerrar la caja.
ALTER TABLE pedidos
    ADD CONSTRAINT chk_pedidos_subtotal  CHECK (subtotal  >= 0),
    ADD CONSTRAINT chk_pedidos_descuento CHECK (descuento >= 0),
    ADD CONSTRAINT chk_pedidos_envio     CHECK (envio     >= 0),
    ADD CONSTRAINT chk_pedidos_impuestos CHECK (impuestos >= 0);

-- Los pedidos que ya existian: lo que la base llamaba 'total' era el
-- subtotal. Se copia ahi y 'total' queda igual, porque sin envio ni
-- impuestos la formula se cumple sola (subtotal - 0 + 0 + 0 = total).
UPDATE pedidos SET subtotal = total;

-- Verificacion: las dos consultas tienen que dar 0 filas.
--
--   -- 1. ningun pedido donde la formula no cierre
--   SELECT id_pedido, subtotal, descuento, envio, impuestos, total
--     FROM pedidos
--    WHERE total <> subtotal - descuento + envio + impuestos;
--
--   -- 2. ningun descuento que se coma mas que los vinos
--   SELECT id_pedido, subtotal, descuento FROM pedidos WHERE descuento > subtotal;
