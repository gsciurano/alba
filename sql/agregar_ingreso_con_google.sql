-- ============================================================
--  INGRESO CON GOOGLE
--
--  Hoy 'password_hash' es NOT NULL, y una cuenta de Google NO TIENE
--  contrasena: la persona se identifica contra Google y nosotros
--  recibimos la prueba. Mientras la columna sea obligatoria, esa
--  cuenta no entra en la tabla.
--
--  QUE SE AGREGA:
--
--  1. password_hash pasa a admitir NULL.
--     Una cuenta de Google queda con NULL, que es la verdad: no hay
--     contrasena. Poner una cadena vacia o un "google" de relleno
--     seria peor, porque el dia que alguien compare contrasenas esa
--     cuenta entraria con cualquier cosa.
--
--  2. 'proveedor' dice como ingresa cada cuenta: LOCAL o GOOGLE.
--     Sin esta columna no se puede distinguir "no tiene contrasena
--     porque usa Google" de "quedo sin contrasena por un error".
--     Todas las cuentas que ya existen son LOCAL.
--
--  3. 'google_sub' guarda el identificador que Google le da a la
--     persona. POR QUE NO ALCANZA CON EL EMAIL: el email se puede
--     cambiar, y dos personas distintas pueden terminar con el mismo
--     con el tiempo. El 'sub' no cambia nunca y es unico. Es lo que
--     la propia documentacion de Google dice que hay que usar como
--     clave.
--     Es UNIQUE para que dos filas no puedan reclamar la misma
--     cuenta de Google. Admite NULL porque las cuentas LOCAL no
--     tienen (en MySQL, UNIQUE permite varios NULL).
--
--  Este script NO borra nada.
--  Ejecutar:
--    mysql -u root -p gestor_inventario < agregar_ingreso_con_google.sql
-- ============================================================
USE gestor_inventario;

ALTER TABLE usuarios
    MODIFY COLUMN password_hash VARCHAR(255) NULL,
    ADD COLUMN proveedor  ENUM('LOCAL','GOOGLE') NOT NULL DEFAULT 'LOCAL' AFTER password_hash,
    ADD COLUMN google_sub VARCHAR(64) NULL AFTER proveedor,
    ADD CONSTRAINT uq_usuarios_google_sub UNIQUE (google_sub);

-- ------------------------------------------------------------
--  Las cuentas que ya existen entran con contrasena: son LOCAL.
--  El DEFAULT ya las deja asi, pero se deja explicito para que el
--  script sea claro leido solo.
-- ------------------------------------------------------------
UPDATE usuarios SET proveedor = 'LOCAL' WHERE proveedor IS NULL;

-- ------------------------------------------------------------
--  RED DE SEGURIDAD.
--
--  Una cuenta LOCAL sin contrasena no podria ingresar nunca, y una
--  cuenta GOOGLE sin 'sub' tampoco: las dos combinaciones son un
--  error de programa, no un estado posible. El CHECK las frena en la
--  base, que es el unico lugar que no se puede saltear.
-- ------------------------------------------------------------
ALTER TABLE usuarios
    ADD CONSTRAINT chk_usuarios_credencial CHECK (
        (proveedor = 'LOCAL'  AND password_hash IS NOT NULL) OR
        (proveedor = 'GOOGLE' AND google_sub    IS NOT NULL)
    );
