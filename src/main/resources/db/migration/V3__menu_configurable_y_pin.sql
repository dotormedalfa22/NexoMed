-- =====================================================================
-- Menu configurable desde el sistema y PIN de acceso.
--   menu               : grupo del menu lateral (Usuario, ...).
--   menu_opcion        : pantalla del menu. ruta = pantalla del front (#ruta).
--                        rol_requerido: rol que exige la API de esa pantalla (lo fija el sistema).
--   menu_opcion_perfil : perfiles que ven la opcion.
-- Menus y opciones se ordenan alfabeticamente. Una opcion se muestra si esta activa, su menu esta activo, el perfil del usuario esta en
-- menu_opcion_perfil y (si tiene rol_requerido) el perfil incluye ese rol.
-- =====================================================================

CREATE TABLE menu (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    clave     VARCHAR(30)  NOT NULL UNIQUE,
    nombre    VARCHAR(60)  NOT NULL,
    icono     VARCHAR(30)  NOT NULL DEFAULT 'carpeta',
    orden     INT          NOT NULL DEFAULT 0,
    activo    BOOLEAN      NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

CREATE TABLE menu_opcion (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_id        BIGINT       NOT NULL,
    clave          VARCHAR(40)  NOT NULL UNIQUE,
    nombre         VARCHAR(60)  NOT NULL,
    descripcion    VARCHAR(200),
    ruta           VARCHAR(60)  NOT NULL UNIQUE,
    rol_requerido  VARCHAR(40),
    orden          INT          NOT NULL DEFAULT 0,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_mo_menu FOREIGN KEY (menu_id) REFERENCES menu (id),
    CONSTRAINT fk_mo_rol  FOREIGN KEY (rol_requerido) REFERENCES rol (clave)
) ENGINE=InnoDB;

CREATE TABLE menu_opcion_perfil (
    opcion_id  BIGINT NOT NULL,
    perfil_id  BIGINT NOT NULL,
    PRIMARY KEY (opcion_id, perfil_id),
    CONSTRAINT fk_mop_opcion FOREIGN KEY (opcion_id) REFERENCES menu_opcion (id),
    CONSTRAINT fk_mop_perfil FOREIGN KEY (perfil_id) REFERENCES perfil (id)
) ENGINE=InnoDB;

-- PIN de acceso (BCrypt) y control de contrasena.
ALTER TABLE usuario
    ADD COLUMN pin_hash               VARCHAR(100) NULL COMMENT 'BCrypt del PIN de acceso' AFTER contrasena_hash,
    ADD COLUMN pin_actualizado        DATETIME     NULL AFTER pin_hash,
    ADD COLUMN contrasena_actualizada DATETIME     NULL AFTER pin_actualizado,
    ADD COLUMN cambiar_contrasena     BOOLEAN      NOT NULL DEFAULT FALSE
        COMMENT 'Usuario creado por un administrador: cambia su contrasena al entrar' AFTER contrasena_actualizada;

-- Rol para configurar el menu.
INSERT INTO rol (clave, nombre, modulo, descripcion)
VALUES ('MENU_CONFIGURAR', 'Configurar menu', 'SEGURIDAD', 'Opciones del menu y perfiles que las ven');
INSERT INTO perfil_rol (perfil_id, rol_id)
SELECT p.id, r.id FROM perfil p JOIN rol r ON r.clave = 'MENU_CONFIGURAR' WHERE p.clave = 'ADMINISTRADOR';

-- Menus (se muestran en orden alfabetico). Los que aun no tienen opciones aparecen como "en preparacion".
INSERT INTO menu (clave, nombre, icono) VALUES
    ('ALMACEN',           'Almacen',           'almacen'),
    ('CARGA_DE_ARCHIVOS', 'Carga de archivos', 'carga'),
    ('CATALOGOS',         'Catalogos',         'catalogos'),
    ('CONSULTAS',         'Consultas',         'consultas'),
    ('DASHBOARD',         'Dashboard',         'dashboard'),
    ('ENTRADAS',          'Entradas',          'entradas'),
    ('IMPRESOS',          'Impresos',          'impresos'),
    ('INVENTARIO',        'Inventario',        'inventario'),
    ('REPORTES',          'Reportes',          'reportes'),
    ('SALIDAS',           'Salidas',           'salidas'),
    ('USUARIO',           'Usuario',           'usuario');

INSERT INTO menu_opcion (menu_id, clave, nombre, descripcion, ruta, rol_requerido, orden)
SELECT m.id, o.clave, o.nombre, o.descripcion, o.ruta, o.rol, o.orden
  FROM menu m
  JOIN (SELECT 'MI_PERFIL' clave, 'Mi perfil' nombre, 'Sus datos, perfil y permisos' descripcion,
               'perfil' ruta, NULL rol, 10 orden
        UNION ALL SELECT 'CAMBIAR_CONTRASENA', 'Modificar contraseña', 'Cambiar su contraseña', 'contrasena', NULL, 20
        UNION ALL SELECT 'MI_PIN', 'Crear PIN de acceso', 'PIN numerico personal', 'pin', NULL, 30
        UNION ALL SELECT 'CREAR_USUARIO', 'Crear usuario', 'Alta de usuarios con su perfil', 'usuarios/crear', 'USUARIOS_ADMINISTRAR', 40
        UNION ALL SELECT 'CONFIGURAR_MENU', 'Configurar menu', 'Opciones del menu y perfiles que las ven', 'menu', 'MENU_CONFIGURAR', 50) o
 WHERE m.clave = 'USUARIO';

-- Todos los perfiles ven Mi perfil, Modificar contrasena y PIN; Crear usuario y Configurar menu, solo ADMINISTRADOR.
INSERT INTO menu_opcion_perfil (opcion_id, perfil_id)
SELECT o.id, p.id FROM menu_opcion o CROSS JOIN perfil p
 WHERE o.clave IN ('MI_PERFIL', 'CAMBIAR_CONTRASENA', 'MI_PIN');

INSERT INTO menu_opcion_perfil (opcion_id, perfil_id)
SELECT o.id, p.id FROM menu_opcion o JOIN perfil p ON p.clave = 'ADMINISTRADOR'
 WHERE o.clave IN ('CREAR_USUARIO', 'CONFIGURAR_MENU');
