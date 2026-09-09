-- ============================================================
--  GESTOR DE INVENTARIO — ALBA EN LOS ANDES
--  Script de creacion de la base de datos (MySQL 8.x)
--  Trabajo integrador — App de gestion + e-commerce
--
--  El catalogo cargado abajo es el catalogo REAL de la bodega,
--  tomado de https://albaenlosandes.com (nombres, precios en
--  pesos, varietales, notas de cata e imagenes oficiales).
--
--  Como ejecutarlo:
--    Opcion A (Workbench): File > Open SQL Script > Run (rayo)
--    Opcion B (Terminal):  mysql -u root -p < gestor_inventario_alba.sql
-- ============================================================

-- Se elimina la base si existiera, para poder re-ejecutar el script
-- desde cero en cualquier maquina del equipo (idempotencia).
DROP DATABASE IF EXISTS gestor_inventario;
CREATE DATABASE gestor_inventario
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE gestor_inventario;

-- ============================================================
--  TABLA: usuarios
--  Cubre ambos roles: ADMIN (panel de gestion) y CLIENTE (tienda).
--  La contrasena se guarda hasheada (nunca en texto plano).
--  Mapea a la entidad JPA Usuario.
-- ============================================================
CREATE TABLE usuarios (
    id_usuario      INT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(100)  NOT NULL,
    apellido        VARCHAR(100)  NOT NULL,
    email           VARCHAR(150)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255)  NOT NULL,
    telefono        VARCHAR(30),
    rol             ENUM('ADMIN','CLIENTE') NOT NULL DEFAULT 'CLIENTE',
    fecha_alta      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activo          BOOLEAN       NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- ============================================================
--  TABLA: productos
--  Catalogo de vinos. stock_minimo es POR PRODUCTO: cuando
--  stock_actual < stock_minimo, el panel dispara la alerta.
--  'activo' permite baja logica (no se borra el historial).
--  Mapea a la entidad JPA Producto.
-- ============================================================
CREATE TABLE productos (
    id_producto     INT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(150)  NOT NULL,
    linea           ENUM('FINCA','ESTATE_RESERVE','GRAN_RESERVA','EDICION_ESPECIAL') NOT NULL,
    varietal        VARCHAR(80)   NOT NULL,
    anada           SMALLINT,                          -- anio de cosecha
    descripcion     TEXT,
    precio          DECIMAL(12,2) NOT NULL CHECK (precio >= 0),
    stock_actual    INT           NOT NULL DEFAULT 0 CHECK (stock_actual >= 0),
    stock_minimo    INT           NOT NULL DEFAULT 10 CHECK (stock_minimo >= 0),
    imagen_url      VARCHAR(300),
    activo          BOOLEAN       NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- ============================================================
--  TABLA: pedidos
--  Nace CONFIRMADO tras el pago simulado. El admin lo pasa a
--  ENTREGADO o CANCELADO (cancelar devuelve stock, via app).
--  Mapea a la entidad JPA Pedido.
-- ============================================================
CREATE TABLE pedidos (
    id_pedido       INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario      INT           NOT NULL,
    fecha           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado          ENUM('CONFIRMADO','ENTREGADO','CANCELADO') NOT NULL DEFAULT 'CONFIRMADO',
    medio_pago      ENUM('TARJETA','TRANSFERENCIA','EFECTIVO') NOT NULL,
    total           DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    CONSTRAINT fk_pedido_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
) ENGINE=InnoDB;

-- ============================================================
--  TABLA: detalle_pedido
--  Renglones del pedido. precio_unitario es una "foto" del
--  precio al momento de la compra (si el vino cambia de precio
--  despues, el pedido historico no se altera).
--  Mapea a la entidad JPA DetallePedido.
-- ============================================================
CREATE TABLE detalle_pedido (
    id_detalle      INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido       INT           NOT NULL,
    id_producto     INT           NOT NULL,
    cantidad        INT           NOT NULL CHECK (cantidad > 0),
    precio_unitario DECIMAL(12,2) NOT NULL CHECK (precio_unitario >= 0),
    CONSTRAINT fk_detalle_pedido
        FOREIGN KEY (id_pedido)   REFERENCES pedidos(id_pedido),
    CONSTRAINT fk_detalle_producto
        FOREIGN KEY (id_producto) REFERENCES productos(id_producto)
) ENGINE=InnoDB;

-- ============================================================
--  TABLA: movimientos_stock
--  Trazabilidad (inventario perpetuo): TODA variacion de stock
--  queda registrada. ENTRADA = ingreso de mercaderia,
--  SALIDA = venta, AJUSTE = correccion/cancelacion.
--  Mapea a la entidad JPA MovimientoStock.
-- ============================================================
CREATE TABLE movimientos_stock (
    id_movimiento   INT AUTO_INCREMENT PRIMARY KEY,
    id_producto     INT           NOT NULL,
    id_usuario      INT           NOT NULL,              -- quien genero el movimiento
    tipo            ENUM('ENTRADA','SALIDA','AJUSTE') NOT NULL,
    cantidad        INT           NOT NULL,              -- positiva o negativa segun tipo
    motivo          VARCHAR(200)  NOT NULL,
    id_pedido       INT,                                 -- opcional: enlaza con la venta
    fecha           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mov_producto
        FOREIGN KEY (id_producto) REFERENCES productos(id_producto),
    CONSTRAINT fk_mov_usuario
        FOREIGN KEY (id_usuario)  REFERENCES usuarios(id_usuario),
    CONSTRAINT fk_mov_pedido
        FOREIGN KEY (id_pedido)   REFERENCES pedidos(id_pedido)
) ENGINE=InnoDB;

-- ============================================================
--  DATOS INICIALES (seed)
-- ============================================================

-- Usuarios: 1 admin + 2 clientes de prueba.
-- NOTA: el hash es un placeholder; la app lo generara con BCrypt
-- cuando se agregue la etapa de seguridad.
INSERT INTO usuarios (nombre, apellido, email, password_hash, telefono, rol) VALUES
('Admin',  'Bodega',  'admin@albaenlosandes.com', '$2a$10$placeholderhashadmin', '261-0000000', 'ADMIN'),
('Juan',   'Perez',   'juan.perez@mail.com',      '$2a$10$placeholderhashjuan',  '261-1111111', 'CLIENTE'),
('Ana',    'Garcia',  'ana.garcia@mail.com',      '$2a$10$placeholderhashana',   '261-2222222', 'CLIENTE');

-- ------------------------------------------------------------
--  CATALOGO REAL DE ALBA EN LOS ANDES
--  Fuente: https://albaenlosandes.com (tienda en pesos).
--  Todos los vinos son de Tupungato, Valle de Uco, Mendoza.
--  Las lineas coinciden con el ENUM 'linea' y con el enum Java Linea.
--  Los valores de stock son la carga inicial del inventario
--  (la bodega no publica su stock, lo definimos nosotros).
-- ------------------------------------------------------------
INSERT INTO productos (nombre, linea, varietal, anada, descripcion, precio, stock_actual, stock_minimo, imagen_url) VALUES

-- LINEA FINCA — vinos jovenes, entrada de gama
('Finca Chardonnay', 'FINCA', 'Chardonnay', 2024,
 'Fresco y frutado. Color amarillo verdoso brillante, aroma intenso a frutos tropicales, citricos y frutos secos. Acidez muy equilibrada. Ideal con ensaladas, pescados, mariscos y pastas con salsas suaves.',
 15000.00, 120, 20, 'https://albaenlosandes.com/wp-content/uploads/2021/04/albaenlosandes-productos-chardonnay.jpg'),

('Finca Malbec', 'FINCA', 'Malbec', 2023,
 'Vino joven de intenso color violaceo. Aromas a frutos rojos y negros (frutilla, mora, ciruela). En boca es jugoso, de refrescante acidez y taninos suaves. Ideal con cerdo o cordero a la parrilla y hamburguesas.',
 15000.00, 150, 20, 'https://albaenlosandes.com/wp-content/uploads/2021/04/albaenlosandes-productos-finca_malbec.jpg'),

-- LINEA ESTATE RESERVE — gama media, 8 meses en barrica de roble frances
('Estate Reserve Malbec', 'ESTATE_RESERVE', 'Malbec', 2022,
 'Malbec de color violeta intenso. Amplio aroma frutal (ciruelas y frutillas), flores, mineral y notas de chocolate y tabaco. Taninos suaves de gran volumen y final prolongado. Ideal con pollo asado, ternera y quesos.',
 18000.00, 80, 15, 'https://albaenlosandes.com/wp-content/uploads/2021/04/albaenlosandes-productos-ER_malbec.jpg'),

('Estate Reserve Cabernet Franc', 'ESTATE_RESERVE', 'Cabernet Franc', 2022,
 'Elegante Cabernet Franc de color rojo granate intenso. Notas de frutas rojas, pimiento, hierbas, especias y mineral, de estructura sedosa. Ideal con platos a base de tomate, legumbres, carnes de caza y estofados.',
 18000.00, 60, 15, 'https://albaenlosandes.com/wp-content/uploads/2021/04/albaenlosandes-productos-ER_cabernet_franc.jpg'),

-- LINEA GRAN RESERVA — premium, 12 meses en barrica de roble frances
('Gran Reserva Malbec', 'GRAN_RESERVA', 'Malbec', 2021,
 'Intenso, de gran cuerpo e identidad. Color rojo violaceo con tonos rubi. Aroma a frutos negros, especias, hierbas, flores y notas minerales. Muy equilibrado, amplio y de final largo. Ideal con ternera asada y pastas.',
 25000.00, 40, 5, 'https://albaenlosandes.com/wp-content/uploads/2025/02/albaenlosandes-productos-GR_malbec-2025.jpg'),

('Gran Reserva Cabernet Franc', 'GRAN_RESERVA', 'Cabernet Franc', 2021,
 'Vino complejo, elegante y profundo. Color rojo granate intenso con tonos rubi. Aroma a frutos negros, morron rojo asado, especias y notas minerales. Ideal con carnes de caza asada, estofados y quesos.',
 25000.00, 35, 5, 'https://albaenlosandes.com/wp-content/uploads/2021/04/albaenlosandes-productos-GR_cabernet_franc.jpg'),

('Gran Reserva Cabernet Sauvignon', 'GRAN_RESERVA', 'Cabernet Sauvignon', 2021,
 'Premium de gran estructura y complejidad, con la impronta calcarea del Valle de Uco. Anejamiento en barrica de roble frances.',
 25000.00, 30, 5, 'https://albaenlosandes.com/wp-content/uploads/2025/02/albaenlosandes-productos-GR_cabernet_sauvignon.jpg'),

('Gran Reserva Chardonnay', 'GRAN_RESERVA', 'Chardonnay', 2022,
 'Blanco premium de guarda, con paso por barrica de roble frances. Expresion de altura del vinedo de Tupungato a 1.100 metros sobre el nivel del mar.',
 25000.00, 25, 5, 'https://albaenlosandes.com/wp-content/uploads/2025/02/albaenlosandes-productos-GR_chardonay.jpg'),

-- EDICIONES ESPECIALES — micro vinificacion, 12 a 18 meses en barrica
('La Mujer', 'EDICION_ESPECIAL', 'Blend (Malbec, Cabernet Franc, Cabernet Sauvignon)', 2020,
 'Blend 70% Malbec, 15% Cabernet Franc y 15% Cabernet Sauvignon. Complejo, elegante y profundo. Color rojo violaceo con tonos rubi. Muy equilibrado, amplio y de final largo. Se sugiere decantar.',
 35000.00, 20, 5, 'https://albaenlosandes.com/wp-content/uploads/2022/10/albaenlosandes-productos-la_mujer-v2.jpg'),

('8M Blend', 'EDICION_ESPECIAL', 'Blend (Cabernet Franc, Malbec)', 2020,
 'Blend 80% Cabernet Franc y 20% Malbec. Intenso, armonioso y expresivo. Notas de frutas negras y pimenton ahumado con especias y chocolate de su paso por barrica. Gran estructura y acidez equilibrada.',
 35000.00, 18, 5, 'https://albaenlosandes.com/wp-content/uploads/2025/02/albaenlosandes-productos-8m_blend-2025-1.jpg');

-- Movimientos iniciales de ENTRADA (carga del stock inicial):
-- asi el inventario arranca con trazabilidad completa desde el dia cero.
INSERT INTO movimientos_stock (id_producto, id_usuario, tipo, cantidad, motivo) VALUES
( 1, 1, 'ENTRADA', 120, 'Carga de stock inicial'),
( 2, 1, 'ENTRADA', 150, 'Carga de stock inicial'),
( 3, 1, 'ENTRADA',  80, 'Carga de stock inicial'),
( 4, 1, 'ENTRADA',  60, 'Carga de stock inicial'),
( 5, 1, 'ENTRADA',  40, 'Carga de stock inicial'),
( 6, 1, 'ENTRADA',  35, 'Carga de stock inicial'),
( 7, 1, 'ENTRADA',  30, 'Carga de stock inicial'),
( 8, 1, 'ENTRADA',  25, 'Carga de stock inicial'),
( 9, 1, 'ENTRADA',  20, 'Carga de stock inicial'),
(10, 1, 'ENTRADA',  18, 'Carga de stock inicial');

-- Un pedido de ejemplo completo (para probar consultas y la demo):
-- Juan compra 2 Finca Malbec + 1 Gran Reserva Malbec = 2*15000 + 25000 = 55000
INSERT INTO pedidos (id_usuario, estado, medio_pago, total) VALUES
(2, 'CONFIRMADO', 'TARJETA', 55000.00);

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
(1, 2, 2, 15000.00),
(1, 5, 1, 25000.00);

-- El pedido descuenta stock y deja su rastro (inventario perpetuo).
-- OJO: esto se hace a mano SOLO para el seed. En la app corriendo,
-- de esto se encarga PedidoService.crearPedido() dentro de una transaccion.
UPDATE productos SET stock_actual = stock_actual - 2 WHERE id_producto = 2;
UPDATE productos SET stock_actual = stock_actual - 1 WHERE id_producto = 5;

INSERT INTO movimientos_stock (id_producto, id_usuario, tipo, cantidad, motivo, id_pedido) VALUES
(2, 2, 'SALIDA', -2, 'Venta - pedido #1', 1),
(5, 2, 'SALIDA', -1, 'Venta - pedido #1', 1);

-- ============================================================
--  CONSULTAS DE VERIFICACION (podes correrlas para probar)
-- ============================================================
-- Catalogo con stock:
--   SELECT nombre, linea, precio, stock_actual FROM productos;
-- Alerta de stock minimo:
--   SELECT nombre, stock_actual, stock_minimo FROM productos
--   WHERE stock_actual < stock_minimo AND activo = TRUE;
-- Detalle de un pedido con JOIN:
--   SELECT p.id_pedido, u.nombre, pr.nombre AS vino, d.cantidad, d.precio_unitario
--   FROM pedidos p
--   JOIN usuarios u       ON u.id_usuario  = p.id_usuario
--   JOIN detalle_pedido d ON d.id_pedido   = p.id_pedido
--   JOIN productos pr     ON pr.id_producto = d.id_producto;
-- Trazabilidad de un producto:
--   SELECT fecha, tipo, cantidad, motivo FROM movimientos_stock
--   WHERE id_producto = 2 ORDER BY fecha;
