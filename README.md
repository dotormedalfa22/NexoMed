# NexoMed — logística médica

Logística y distribución de insumos médicos (insumos, curación y equipo). Tiene el inicio de sesión con usuarios,
perfiles y roles, el menú configurable y el catálogo de proveedores (captura y carga por Excel). Usa las mismas
herramientas que SALPE.

| Pieza | Elección |
|---|---|
| Lenguaje | Java 17 |
| Framework | Spring Boot 4.1 (Spring Security 7, JDBC con `JdbcClient`) |
| Servidor | Tomcat 11 embebido; el WAR también se despliega en Tomcat 11 / WildFly |
| Base de datos | MariaDB, base `NexoMed`, migraciones Flyway (`src/main/resources/db/migration`) |
| Seguridad | JWT de 15 min, renovación solo con actividad, bloqueo de 15 min tras 5 intentos, bitácora |
| API | Swagger UI en `/nexomed/swagger-ui.html` |
| Interfaz | HTML + CSS + JS sin dependencias (`src/main/resources/static`) |

## Arranque local

1. Una vez: `powershell -ExecutionPolicy Bypass -File crear-bd.ps1` (pide la contraseña de root de MariaDB;
   crea la base `NexoMed` y el usuario `app_nexomed` con la contraseña de `.env`). Equivale a `sql/00_crear_bd_y_usuario.sql`.
2. Copie `.env.example` a `.env` y llene los valores (en este equipo ya está lleno).
3. `powershell -ExecutionPolicy Bypass -File iniciar.ps1 -Compilar`
4. Abra http://localhost:8098/nexomed/

Al primer arranque Flyway crea las tablas y se da de alta `USUARIO_INICIAL` con perfil ADMINISTRADOR
(en local: `dotor`, contraseña de prueba en `.env`; cámbiela antes de producción).

## Base de datos

| Tabla | Contenido |
|---|---|
| `perfil` | Tipo de usuario: ADMINISTRADOR, ALMACEN, DISTRIBUCION, COMPRAS, CONSULTA, AUDITOR |
| `rol` | Permiso puntual (USUARIOS_ADMINISTRAR, INVENTARIO_CONSULTAR, …) con su módulo |
| `perfil_rol` | Roles que incluye cada perfil (ADMINISTRADOR tiene todos) |
| `usuario` | Cuenta: usuario, contraseña BCrypt, perfil, activo, intentos fallidos, bloqueo, último acceso |
| `bitacora` | Accesos (LOGIN, LOGIN_FALLIDO, LOGOUT) y cambios; un trigger impide modificarla o borrarla |
| `menu` | Menús del menú lateral (orden alfabético). Sin opciones se muestran como «En preparación» |
| `menu_opcion` | Pantalla del menú: nombre, ruta del front (`#ruta`), rol que exige su API, activa |
| `menu_opcion_perfil` | Perfiles que ven cada opción |
| `proveedor` | Datos de la Constancia de Situación Fiscal (RFC único, razón social, régimen capital, nombre comercial, régimen fiscal, actividad, inicio de operaciones, estatus en el padrón, idCIF y domicilio fiscal) más contacto, activo, origen (CAPTURA o CARGA) y quién lo creó o cambió |
| `regimen_fiscal` | Catálogo c_RegimenFiscal del SAT y si aplica a persona física o moral |
| `entidad_federativa` | Los 32 estados con clave INEGI |

`usuario` también guarda el PIN de acceso (BCrypt), la fecha del último cambio de contraseña y
`cambiar_contrasena` (los usuarios creados por un administrador cambian su contraseña al entrar).

Los datos se guardan sin acentos; los acentos se ponen solo en los textos de la pantalla.

## Menú

Menús: Almacén, Carga de archivos, Catálogos, Consultas, Dashboard, Entradas, Impresos, Inventario, Reportes,
Salidas y Usuario, en orden alfabético y con el diseño de SALPE (secciones desplegables, modo solo iconos, menú
deslizable en móvil). Los menús sin opciones aparecen «En preparación». Opciones:

| Opción | Ruta | Quién la ve |
|---|---|---|
| Carga de archivos › Cargar proveedores | `#carga/proveedores` | Administrador y Compras (rol PROVEEDORES_ADMINISTRAR) |
| Catálogos › Catálogo de proveedores | `#catalogos/proveedores` | Administrador y Compras (rol PROVEEDORES_ADMINISTRAR) |
| Configurar menú | `#menu` | Administrador (rol MENU_CONFIGURAR) |
| Crear PIN de acceso | `#pin` | Todos |
| Crear usuario | `#usuarios/crear` | Administrador (rol USUARIOS_ADMINISTRAR) |
| Mi perfil | `#perfil` | Todos |
| Modificar contraseña | `#contrasena` | Todos |

**Configurar menú** cambia nombre e icono de los menús, crea menús nuevos y, por opción, su nombre, el menú donde
aparece, si está activa y qué perfiles la ven. Un perfil sin el rol que exige la opción no la ve aunque se marque.
No deja guardar un cambio que quite al Administrador el acceso a «Configurar menú».

**Agregar una pantalla nueva**: una migración `INSERT INTO menu_opcion` (con su `ruta` y `rol_requerido`) y sus
`menu_opcion_perfil`, más la vista `VISTAS['<ruta>']` en `static/js/app.js`.
Los roles del perfil viajan en el token como `ROLE_<clave>`: un endpoint nuevo se protege con
`@PreAuthorize("hasRole('INVENTARIO_CONSULTAR')")`. Un usuario o perfil desactivado no puede entrar.

## Proveedores

**Catálogo de proveedores**: búsqueda por RFC, razón social, nombre comercial o municipio; activos, inactivos o todos;
alta y cambios con los datos de la CSF en tres secciones (identificación, domicilio fiscal y contacto); desactivar o
activar (no se borra). El régimen fiscal se filtra por el tipo de persona del RFC (12 caracteres moral, 13 física).
Se guarda en mayúsculas y sin acentos; el teléfono solo con sus 10 dígitos.

**Regla de no duplicar** (la de las cargas de catálogos de SIALSS, `comun/Parecidos`): mismo RFC, o razón social igual o
parecida a la de otro proveedor, comparada sin acentos, signos, espacios ni el régimen de capital («S.A. de C.V.»,
«S de RL de CV»…); parecida = con los mismos números y a una letra de distancia (10 % en nombres de 20 letras o más).
En la captura, el RFC repetido no se guarda y la razón social parecida pide confirmar «Es un proveedor distinto».

**Cargar proveedores** (modelo de las cargas de SIALSS MDF, `cargas/CargaProveedoresController`):

1. *Descargar plantilla*: hoja Proveedores (23 columnas de la CSF; en verde las obligatorias: RFC, Razón social,
   Régimen fiscal y Código postal; un renglón de ejemplo; listas de régimen, entidad y estatus), hoja Catalogos e
   Instrucciones. Las columnas se buscan por su título, sin importar el orden ni los acentos.
2. *Revisar archivo*: lee todo sin guardar (máx. 5,000 renglones). Los errores se listan por renglón y bloquean todo
   el archivo. Lo repetido (contra el catálogo o contra renglones anteriores del archivo) no es error: se omite con
   aviso y se cargan los demás; si no queda nada nuevo: «No hay proveedores nuevos». El archivo vacío, sin títulos o
   con solo el renglón de ejemplo se rechaza explicando por qué. La fecha acepta dd/mm/aaaa, fecha de Excel o
   «26 DE FEBRERO DE 2024» (como en la CSF).
3. *Confirmar* con la contraseña del usuario de la sesión: 3 intentos y la carga se cancela. Se vuelve a revisar con la
   tabla bloqueada y se guarda todo en una transacción, a nombre del usuario, con un registro en la bitácora.

La revisión pendiente vive 30 minutos en memoria del servidor (`cargas/CargasPendientes`); con varias instancias habría
que pasarla a la base. `cargas/Excel` sirve para las siguientes cargas (lectura, fechas y plantilla).

## Indicadores de espera

- **Consultando** (círculo giratorio, `img/nexomed-consultando.svg`): `api()` lo muestra solo si la consulta tarda
  más de 400 ms. El botón «Entrar» tiene su propio círculo. Con `{ silencioso: true }` no aparece.
- **Descargando** (barra de progreso): `descargar(ruta, nombreArchivo)` muestra el avance real si el servidor manda
  `Content-Length`; si no, una barra indeterminada como `img/nexomed-descargando.svg`.

## Pendientes

- Modificar, desactivar o desbloquear usuarios; administrar perfiles y roles.
- La contraseña de `dotor` es de prueba y no cumple la regla de 12+ caracteres: cámbiela en «Modificar contraseña».
- El PIN se guarda pero ningún módulo lo pide todavía.
- El cambio obligatorio de contraseña inicial se exige en la pantalla, no en la API.
- Módulos de logística (almacén, distribución, compras, reportes). Los proveedores aún no se usan en otro módulo.
- El RFC genérico de extranjeros (XEXX010101000) no se acepta: un proveedor extranjero necesitaría otra llave.
