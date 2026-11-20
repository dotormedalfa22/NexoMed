# NexoMed — logística médica

Logística y distribución de insumos médicos (insumos, curación y equipo). Por ahora solo tiene el inicio de sesión
con usuarios, perfiles y roles. Usa las mismas herramientas que SALPE.

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

`usuario` también guarda el PIN de acceso (BCrypt), la fecha del último cambio de contraseña y
`cambiar_contrasena` (los usuarios creados por un administrador cambian su contraseña al entrar).

Los datos se guardan sin acentos; los acentos se ponen solo en los textos de la pantalla.

## Menú

Menús: Almacén, Carga de archivos, Catálogos, Consultas, Dashboard, Entradas, Impresos, Inventario, Reportes,
Salidas y Usuario, en orden alfabético y con el diseño de SALPE (secciones desplegables, modo solo iconos, menú
deslizable en móvil). Solo Usuario tiene opciones por ahora:

| Opción | Ruta | Quién la ve |
|---|---|---|
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
- Módulos de logística (almacén, distribución, compras, reportes).
