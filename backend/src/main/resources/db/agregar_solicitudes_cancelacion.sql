-- ============================================================
--  NUEVA TABLA: solicitudes_cancelacion
--
--  Agrega el circuito de cancelacion pedida por el CLIENTE, con
--  seguimiento. Antes solo el admin podia cancelar, de golpe y sin
--  registro de quien lo habia pedido ni por que.
--
--  Este script NO borra nada: solo agrega la tabla nueva.
--  Ejecutar:
--    mysql -u root -p gestor_inventario < agregar_solicitudes_cancelacion.sql
-- ============================================================
USE gestor_inventario;

CREATE TABLE IF NOT EXISTS solicitudes_cancelacion (
    id_solicitud    INT AUTO_INCREMENT PRIMARY KEY,

    id_pedido       INT           NOT NULL,   -- que pedido se quiere cancelar
    id_usuario      INT           NOT NULL,   -- quien lo pide (debe ser el duenio)

    motivo          ENUM('ARREPENTIMIENTO','ERROR_AL_COMPRAR','PROBLEMA_PAGO',
                         'DEMORA_ENTREGA','PRODUCTO_EQUIVOCADO','OTRO') NOT NULL,
    comentario      VARCHAR(500),             -- lo que escribe el cliente

    estado          ENUM('PENDIENTE','EN_REVISION','APROBADA','RECHAZADA')
                        NOT NULL DEFAULT 'PENDIENTE',

    fecha_solicitud DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_resolucion DATETIME,                -- null mientras siga abierta

    respuesta       VARCHAR(1000),            -- que se le contesto al cliente
    id_resuelto_por INT,                      -- null si la resolvio el sistema solo

    -- Deja constancia de si la aprobo la regla automatica o una persona.
    resuelta_automaticamente BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_solicitud_pedido
        FOREIGN KEY (id_pedido)       REFERENCES pedidos(id_pedido),
    CONSTRAINT fk_solicitud_usuario
        FOREIGN KEY (id_usuario)      REFERENCES usuarios(id_usuario),
    CONSTRAINT fk_solicitud_resuelta_por
        FOREIGN KEY (id_resuelto_por) REFERENCES usuarios(id_usuario)
) ENGINE=InnoDB;

-- Indice para la consulta mas frecuente: "las solicitudes de este pedido".
CREATE INDEX idx_solicitud_pedido  ON solicitudes_cancelacion (id_pedido);
CREATE INDEX idx_solicitud_estado  ON solicitudes_cancelacion (estado);

-- Verificacion
SELECT 'Tabla creada. Estructura:' AS aviso;
DESCRIBE solicitudes_cancelacion;
