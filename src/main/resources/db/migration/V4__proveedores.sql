-- =====================================================================
-- Catalogo de proveedores, con los datos de la Constancia de Situacion Fiscal (CSF) del SAT:
-- identificacion del contribuyente, regimen fiscal, actividad, domicilio fiscal y contacto.
--   regimen_fiscal      : catalogo c_RegimenFiscal del SAT (CFDI 4.0).
--   entidad_federativa  : claves INEGI.
--   proveedor           : un registro por RFC (unico). La razon social no se repite ni se parece (lo revisa la aplicacion).
-- Menus: Catalogos > Catalogo de proveedores y Carga de archivos > Cargar proveedores (rol PROVEEDORES_ADMINISTRAR).
-- =====================================================================

CREATE TABLE regimen_fiscal (
    clave          CHAR(3)      NOT NULL PRIMARY KEY,
    descripcion    VARCHAR(150) NOT NULL,
    persona_fisica BOOLEAN      NOT NULL,
    persona_moral  BOOLEAN      NOT NULL
) ENGINE=InnoDB;

INSERT INTO regimen_fiscal (clave, descripcion, persona_fisica, persona_moral) VALUES
    ('601', 'General de Ley Personas Morales', FALSE, TRUE),
    ('603', 'Personas Morales con Fines no Lucrativos', FALSE, TRUE),
    ('605', 'Sueldos y Salarios e Ingresos Asimilados a Salarios', TRUE, FALSE),
    ('606', 'Arrendamiento', TRUE, FALSE),
    ('607', 'Regimen de Enajenacion o Adquisicion de Bienes', TRUE, FALSE),
    ('608', 'Demas ingresos', TRUE, FALSE),
    ('610', 'Residentes en el Extranjero sin Establecimiento Permanente en Mexico', TRUE, TRUE),
    ('611', 'Ingresos por Dividendos (socios y accionistas)', TRUE, FALSE),
    ('612', 'Personas Fisicas con Actividades Empresariales y Profesionales', TRUE, FALSE),
    ('614', 'Ingresos por intereses', TRUE, FALSE),
    ('615', 'Regimen de los ingresos por obtencion de premios', TRUE, FALSE),
    ('616', 'Sin obligaciones fiscales', TRUE, FALSE),
    ('620', 'Sociedades Cooperativas de Produccion que optan por diferir sus ingresos', FALSE, TRUE),
    ('621', 'Incorporacion Fiscal', TRUE, FALSE),
    ('622', 'Actividades Agricolas, Ganaderas, Silvicolas y Pesqueras', TRUE, TRUE),
    ('623', 'Opcional para Grupos de Sociedades', FALSE, TRUE),
    ('624', 'Coordinados', FALSE, TRUE),
    ('625', 'Regimen de las Actividades Empresariales con ingresos a traves de Plataformas Tecnologicas', TRUE, FALSE),
    ('626', 'Regimen Simplificado de Confianza', TRUE, TRUE);

CREATE TABLE entidad_federativa (
    clave   CHAR(2)     NOT NULL PRIMARY KEY,
    nombre  VARCHAR(60) NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT INTO entidad_federativa (clave, nombre) VALUES
    ('01', 'AGUASCALIENTES'), ('02', 'BAJA CALIFORNIA'), ('03', 'BAJA CALIFORNIA SUR'), ('04', 'CAMPECHE'),
    ('05', 'COAHUILA DE ZARAGOZA'), ('06', 'COLIMA'), ('07', 'CHIAPAS'), ('08', 'CHIHUAHUA'),
    ('09', 'CIUDAD DE MEXICO'), ('10', 'DURANGO'), ('11', 'GUANAJUATO'), ('12', 'GUERRERO'),
    ('13', 'HIDALGO'), ('14', 'JALISCO'), ('15', 'MEXICO'), ('16', 'MICHOACAN DE OCAMPO'),
    ('17', 'MORELOS'), ('18', 'NAYARIT'), ('19', 'NUEVO LEON'), ('20', 'OAXACA'),
    ('21', 'PUEBLA'), ('22', 'QUERETARO'), ('23', 'QUINTANA ROO'), ('24', 'SAN LUIS POTOSI'),
    ('25', 'SINALOA'), ('26', 'SONORA'), ('27', 'TABASCO'), ('28', 'TAMAULIPAS'),
    ('29', 'TLAXCALA'), ('30', 'VERACRUZ DE IGNACIO DE LA LLAVE'), ('31', 'YUCATAN'), ('32', 'ZACATECAS');

CREATE TABLE proveedor (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- Identificacion del contribuyente (CSF)
    rfc                       VARCHAR(13)  NOT NULL UNIQUE,
    tipo_persona              CHAR(1)      NOT NULL COMMENT 'M moral (RFC de 12), F fisica (RFC de 13)',
    razon_social              VARCHAR(250) NOT NULL COMMENT 'Denominacion o razon social, o nombre',
    regimen_capital           VARCHAR(150) NULL COMMENT 'Ej. SOCIEDAD ANONIMA DE CAPITAL VARIABLE',
    nombre_comercial          VARCHAR(250) NULL,
    regimen_fiscal            CHAR(3)      NOT NULL,
    actividad_economica       VARCHAR(250) NULL COMMENT 'Actividad economica principal',
    fecha_inicio_operaciones  DATE         NULL,
    estatus_padron            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO' COMMENT 'Estatus en el padron del SAT',
    id_cif                    VARCHAR(20)  NULL COMMENT 'idCIF de la cedula de identificacion fiscal',
    -- Domicilio fiscal (CSF)
    codigo_postal             CHAR(5)      NOT NULL,
    tipo_vialidad             VARCHAR(50)  NULL,
    vialidad                  VARCHAR(150) NULL,
    numero_exterior           VARCHAR(30)  NULL,
    numero_interior           VARCHAR(30)  NULL,
    colonia                   VARCHAR(150) NULL,
    localidad                 VARCHAR(150) NULL,
    municipio                 VARCHAR(150) NULL COMMENT 'Municipio o demarcacion territorial',
    entidad_clave             CHAR(2)      NULL,
    entre_calle               VARCHAR(150) NULL,
    y_calle                   VARCHAR(150) NULL,
    -- Contacto
    contacto                  VARCHAR(150) NULL,
    telefono                  VARCHAR(20)  NULL,
    correo                    VARCHAR(150) NULL,
    -- Control
    activo                    BOOLEAN      NOT NULL DEFAULT TRUE,
    origen                    VARCHAR(10)  NOT NULL DEFAULT 'CAPTURA' COMMENT 'CAPTURA o CARGA (archivo de Excel)',
    creado_por                BIGINT       NULL,
    creado_en                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por           BIGINT       NULL,
    actualizado_en            DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_proveedor_tipo   CHECK (tipo_persona IN ('M', 'F')),
    CONSTRAINT fk_proveedor_regimen FOREIGN KEY (regimen_fiscal) REFERENCES regimen_fiscal (clave),
    CONSTRAINT fk_proveedor_entidad FOREIGN KEY (entidad_clave) REFERENCES entidad_federativa (clave),
    CONSTRAINT fk_proveedor_creo    FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_proveedor_cambio  FOREIGN KEY (actualizado_por) REFERENCES usuario (id),
    INDEX ix_proveedor_razon (razon_social)
) ENGINE=InnoDB;

-- Rol: alta, cambios y carga de proveedores. Administrador y Compras.
INSERT INTO rol (clave, nombre, modulo, descripcion)
VALUES ('PROVEEDORES_ADMINISTRAR', 'Administrar proveedores', 'CATALOGOS', 'Catalogo y carga de proveedores');
INSERT INTO perfil_rol (perfil_id, rol_id)
SELECT p.id, r.id FROM perfil p JOIN rol r ON r.clave = 'PROVEEDORES_ADMINISTRAR'
 WHERE p.clave IN ('ADMINISTRADOR', 'COMPRAS');

-- Opciones de menu
INSERT INTO menu_opcion (menu_id, clave, nombre, descripcion, ruta, rol_requerido, orden)
SELECT m.id, 'CATALOGO_PROVEEDORES', 'Catalogo de proveedores', 'Alta, consulta y cambios de proveedores',
       'catalogos/proveedores', 'PROVEEDORES_ADMINISTRAR', 10
  FROM menu m WHERE m.clave = 'CATALOGOS';

INSERT INTO menu_opcion (menu_id, clave, nombre, descripcion, ruta, rol_requerido, orden)
SELECT m.id, 'CARGAR_PROVEEDORES', 'Cargar proveedores', 'Alta de proveedores desde un archivo de Excel',
       'carga/proveedores', 'PROVEEDORES_ADMINISTRAR', 10
  FROM menu m WHERE m.clave = 'CARGA_DE_ARCHIVOS';

INSERT INTO menu_opcion_perfil (opcion_id, perfil_id)
SELECT o.id, p.id FROM menu_opcion o JOIN perfil p ON p.clave IN ('ADMINISTRADOR', 'COMPRAS')
 WHERE o.clave IN ('CATALOGO_PROVEEDORES', 'CARGAR_PROVEEDORES');
