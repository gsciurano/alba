-- ============================================================
--  COMPLETAR EL CRUD DE TODAS LAS ENTIDADES
--
--  Agrega lo que falta para que las 7 entidades tengan las cuatro
--  operaciones, SIN romper la coherencia del inventario:
--
--   1. Baja logica en pedidos, solicitudes y movimientos.
--      Igual que en productos y usuarios: no se borra la fila,
--      se marca inactiva y deja de aparecer en los listados.
--
--   2. Un campo en movimientos_stock para enlazar un movimiento
--      con el que lo anula (contra-asiento). Asi "borrar" un
--      movimiento no borra el historial: agrega el movimiento
--      contrario y los deja enlazados, como en contabilidad.
--
--  Este script NO borra nada. Se puede correr sobre una base cargada.
--
--  Ejecutar:
--    mysql -u root -p gestor_inventario < agregar_crud_completo.sql
-- ============================================================
USE gestor_inventario;

-- ---------- 1. Baja logica ----------
ALTER TABLE pedidos
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE solicitudes_cancelacion
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE movimientos_stock
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

-- ---------- 2. El enlace del contra-asiento ----------
-- Cuando se anula un movimiento, se crea uno nuevo con la cantidad
-- opuesta y este campo apunta al original. El historial queda entero:
-- se ve que paso, y se ve que se corrigio.
ALTER TABLE movimientos_stock
    ADD COLUMN id_movimiento_anulado INT NULL,
    ADD CONSTRAINT fk_mov_anulado
        FOREIGN KEY (id_movimiento_anulado) REFERENCES movimientos_stock(id_movimiento);

-- ---------- Indices para los listados que ahora filtran por activo ----------
CREATE INDEX idx_pedidos_activo     ON pedidos (activo);
CREATE INDEX idx_solicitudes_activo ON solicitudes_cancelacion (activo);
CREATE INDEX idx_movimientos_activo ON movimientos_stock (activo);

-- ---------- Verificacion ----------
SELECT 'Columnas agregadas:' AS aviso;
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'gestor_inventario'
  AND COLUMN_NAME IN ('activo', 'id_movimiento_anulado')
ORDER BY TABLE_NAME, COLUMN_NAME;
