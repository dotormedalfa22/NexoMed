-- =====================================================================
-- Perfiles y roles iniciales (sin acentos). Se ajustan cuando se definan los modulos.
-- =====================================================================

INSERT INTO perfil (clave, nombre, descripcion) VALUES
    ('ADMINISTRADOR', 'Administrador',          'Todo el sistema: usuarios, perfiles, catalogos y bitacora'),
    ('ALMACEN',       'Almacen',                'Recepcion, existencias, surtido y caducidades'),
    ('DISTRIBUCION',  'Distribucion',           'Rutas, embarques y entregas a unidades'),
    ('COMPRAS',       'Compras',                'Ordenes de compra y proveedores'),
    ('CONSULTA',      'Consulta',               'Solo lectura de existencias y reportes'),
    ('AUDITOR',       'Auditor',                'Solo lectura de todo, incluida la bitacora');

INSERT INTO rol (clave, nombre, modulo, descripcion) VALUES
    ('USUARIOS_ADMINISTRAR',  'Administrar usuarios',        'SEGURIDAD',    'Alta, cambios y bloqueo de usuarios'),
    ('PERFILES_ADMINISTRAR',  'Administrar perfiles y roles','SEGURIDAD',    'Perfiles y roles que incluye cada uno'),
    ('BITACORA_CONSULTAR',    'Consultar bitacora',          'SEGURIDAD',    'Accesos y cambios'),
    ('CATALOGOS_ADMINISTRAR', 'Administrar catalogos',       'CATALOGOS',    'Insumos, proveedores, unidades'),
    ('INVENTARIO_CONSULTAR',  'Consultar inventario',        'ALMACEN',      'Existencias, lotes y caducidades'),
    ('INVENTARIO_MOVIMIENTOS','Registrar movimientos',       'ALMACEN',      'Entradas, salidas y ajustes'),
    ('DISTRIBUCION_OPERAR',   'Operar distribucion',         'DISTRIBUCION', 'Embarques, rutas y entregas'),
    ('COMPRAS_OPERAR',        'Operar compras',              'COMPRAS',      'Ordenes de compra'),
    ('REPORTES_CONSULTAR',    'Consultar reportes',          'REPORTES',     'Reportes y descargas');

-- ADMINISTRADOR: todos los roles.
INSERT INTO perfil_rol (perfil_id, rol_id)
SELECT p.id, r.id FROM perfil p CROSS JOIN rol r WHERE p.clave = 'ADMINISTRADOR';

INSERT INTO perfil_rol (perfil_id, rol_id)
SELECT p.id, r.id FROM perfil p JOIN rol r ON r.clave IN ('INVENTARIO_CONSULTAR','INVENTARIO_MOVIMIENTOS','REPORTES_CONSULTAR')
 WHERE p.clave = 'ALMACEN';

INSERT INTO perfil_rol (perfil_id, rol_id)
SELECT p.id, r.id FROM perfil p JOIN rol r ON r.clave IN ('INVENTARIO_CONSULTAR','DISTRIBUCION_OPERAR','REPORTES_CONSULTAR')
 WHERE p.clave = 'DISTRIBUCION';

INSERT INTO perfil_rol (perfil_id, rol_id)
SELECT p.id, r.id FROM perfil p JOIN rol r ON r.clave IN ('INVENTARIO_CONSULTAR','COMPRAS_OPERAR','REPORTES_CONSULTAR')
 WHERE p.clave = 'COMPRAS';

INSERT INTO perfil_rol (perfil_id, rol_id)
SELECT p.id, r.id FROM perfil p JOIN rol r ON r.clave IN ('INVENTARIO_CONSULTAR','REPORTES_CONSULTAR')
 WHERE p.clave = 'CONSULTA';

INSERT INTO perfil_rol (perfil_id, rol_id)
SELECT p.id, r.id FROM perfil p JOIN rol r ON r.clave IN ('BITACORA_CONSULTAR','INVENTARIO_CONSULTAR','REPORTES_CONSULTAR')
 WHERE p.clave = 'AUDITOR';
