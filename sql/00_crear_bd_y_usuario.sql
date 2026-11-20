-- =====================================================================
-- Ejecutar UNA vez como root. Crea la base NexoMed y su usuario de aplicacion.
-- Las tablas (usuario, perfil, rol, perfil_rol, bitacora) las crea Flyway al arrancar.
-- Mas facil: powershell -ExecutionPolicy Bypass -File crear-bd.ps1  (toma la contrasena de .env)
-- A mano: reemplace CAMBIAR_CONTRASENA por la misma contrasena de BD_CONTRASENA en .env.
-- =====================================================================
CREATE DATABASE IF NOT EXISTS NexoMed CHARACTER SET utf8mb4 COLLATE utf8mb4_spanish_ci;

CREATE USER IF NOT EXISTS 'app_nexomed'@'%' IDENTIFIED BY 'CAMBIAR_CONTRASENA';
CREATE USER IF NOT EXISTS 'app_nexomed'@'localhost' IDENTIFIED BY 'CAMBIAR_CONTRASENA';

-- Sin DROP: la aplicacion no borra tablas.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, TRIGGER ON NexoMed.* TO 'app_nexomed'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, TRIGGER ON NexoMed.* TO 'app_nexomed'@'localhost';
FLUSH PRIVILEGES;

-- Los triggers de la bitacora lo requieren cuando hay binlog activo.
SET GLOBAL log_bin_trust_function_creators = 1;
