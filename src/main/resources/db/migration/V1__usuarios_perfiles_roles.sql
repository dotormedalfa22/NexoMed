-- =====================================================================
-- NexoMed - usuarios, perfiles y roles para el inicio de sesion.
--   perfil      : puesto o tipo de usuario (ADMINISTRADOR, ALMACEN, ...).
--   rol         : permiso puntual que se revisa en la API (USUARIOS_ADMINISTRAR, ...).
--   perfil_rol  : roles que incluye cada perfil.
--   usuario     : cuenta de acceso; tiene un perfil.
--   bitacora    : accesos y cambios; solo insercion.
-- Los datos se guardan sin acentos; los acentos van solo en los textos del front.
-- =====================================================================

CREATE TABLE perfil (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    clave        VARCHAR(30)  NOT NULL UNIQUE,
    nombre       VARCHAR(80)  NOT NULL,
    descripcion  VARCHAR(255),
    activo       BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE rol (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    clave        VARCHAR(40)  NOT NULL UNIQUE,
    nombre       VARCHAR(80)  NOT NULL,
    descripcion  VARCHAR(255),
    modulo       VARCHAR(40)  NOT NULL,
    activo       BOOLEAN      NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

CREATE TABLE perfil_rol (
    perfil_id    BIGINT NOT NULL,
    rol_id       BIGINT NOT NULL,
    PRIMARY KEY (perfil_id, rol_id),
    CONSTRAINT fk_pr_perfil FOREIGN KEY (perfil_id) REFERENCES perfil (id),
    CONSTRAINT fk_pr_rol    FOREIGN KEY (rol_id)    REFERENCES rol (id)
) ENGINE=InnoDB;

CREATE TABLE usuario (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_usuario    VARCHAR(50)  NOT NULL UNIQUE,
    contrasena_hash   VARCHAR(100) NOT NULL COMMENT 'BCrypt',
    nombre_completo   VARCHAR(150) NOT NULL,
    correo            VARCHAR(150),
    perfil_id         BIGINT       NOT NULL,
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    intentos_fallidos INT          NOT NULL DEFAULT 0,
    bloqueado_hasta   DATETIME,
    ultimo_acceso     DATETIME,
    creado_en         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usuario_perfil FOREIGN KEY (perfil_id) REFERENCES perfil (id)
) ENGINE=InnoDB;

CREATE TABLE bitacora (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_id      BIGINT,
    nombre_usuario  VARCHAR(50),
    accion          ENUM('LOGIN','LOGIN_FALLIDO','LOGOUT','CREACION','MODIFICACION','CONSULTA','BAJA') NOT NULL,
    tabla           VARCHAR(40),
    registro_id     VARCHAR(40),
    antes           TEXT,
    despues         TEXT,
    ip              VARCHAR(45),
    INDEX ix_bit_fecha (fecha),
    INDEX ix_bit_usuario (nombre_usuario, fecha)
) ENGINE=InnoDB;

-- La bitacora no se modifica ni se borra.
DELIMITER $$

CREATE TRIGGER trg_bitacora_no_update BEFORE UPDATE ON bitacora FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'NEXOMED: la bitacora no se modifica';
END$$

CREATE TRIGGER trg_bitacora_no_delete BEFORE DELETE ON bitacora FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'NEXOMED: la bitacora no se elimina';
END$$

DELIMITER ;
