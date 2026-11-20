package mx.medicalminds.nexomed.cargas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.cargas.Excel.Columna;
import mx.medicalminds.nexomed.catalogos.Proveedor;
import mx.medicalminds.nexomed.catalogos.ProveedorService;
import mx.medicalminds.nexomed.comun.Bitacora;
import mx.medicalminds.nexomed.comun.Db;
import mx.medicalminds.nexomed.comun.Excepciones;
import mx.medicalminds.nexomed.comun.Parecidos;
import mx.medicalminds.nexomed.seguridad.Sesion;
import mx.medicalminds.nexomed.seguridad.UsuarioActual;

/**
 * Carga de archivos › Cargar proveedores (modelo de las cargas de SIALSS MDF):
 * plantilla con catálogos → revisión de todo el archivo sin escribir (errores por renglón, repetidos que se
 * omiten y vista previa) → confirmación con la contraseña del usuario (3 intentos) → una transacción.
 * Un proveedor con el mismo RFC o con razón social igual o parecida a otro (ya registrado o más arriba en el
 * archivo) no es error: se omite y se carga solo lo nuevo.
 */
@RestController
@RequestMapping("/api/cargas/proveedores")
@RequiredArgsConstructor
@Tag(name = "Carga de archivos")
@PreAuthorize("hasRole('PROVEEDORES_ADMINISTRAR')")
public class CargaProveedoresController {

    private static final String CARGA = "proveedores";
    private static final int MOSTRAR = 200;

    private final ProveedorService servicio;
    private final CargasPendientes pendientes;
    private final UsuarioActual usuarioActual;
    private final Bitacora bitacora;
    private final Db db;
    private final PasswordEncoder encoder;

    static final List<Columna> COLUMNAS = List.of(
            new Columna("RFC", true, 16, "EDM240226AB1", "12 caracteres persona moral, 13 persona física"),
            new Columna("Razón social", true, 40, "EJEMPLO DISTRIBUIDORA MEDICA",
                    "Denominación o razón social, como en la Constancia de Situación Fiscal"),
            new Columna("Régimen capital", false, 34, "SOCIEDAD ANONIMA DE CAPITAL VARIABLE", "Ej. SA DE CV, S DE RL DE CV"),
            new Columna("Nombre comercial", false, 30, "EJEMPLO MEDICA", null),
            new Columna("Régimen fiscal", true, 44, "601 - General de Ley Personas Morales",
                    "Clave del SAT (601) o su descripción; lista en la hoja Catalogos", false, "Catalogos!$A$2:$A$20"),
            new Columna("Actividad económica", false, 40, "Comercio al por mayor de productos farmaceuticos",
                    "Actividad principal de la CSF"),
            new Columna("Fecha inicio de operaciones", false, 16, "26/02/2024", "dd/mm/aaaa", true, null),
            new Columna("Estatus en el padrón", false, 14, "ACTIVO", "ACTIVO, SUSPENDIDO o CANCELADO; vacío = ACTIVO",
                    false, "Catalogos!$E$2:$E$4"),
            new Columna("idCIF", false, 14, "", "Número idCIF de la cédula (opcional)"),
            new Columna("Código postal", true, 10, "45134", "5 dígitos"),
            new Columna("Tipo de vialidad", false, 14, "AVENIDA", null),
            new Columna("Nombre de vialidad", false, 30, "PASEO DE LOS ROBLES", null),
            new Columna("Número exterior", false, 10, "100", null),
            new Columna("Número interior", false, 10, "", null),
            new Columna("Colonia", false, 24, "LOS ROBLES", null),
            new Columna("Localidad", false, 20, "ZAPOPAN", null),
            new Columna("Municipio", false, 20, "ZAPOPAN", "Municipio o demarcación territorial"),
            new Columna("Entidad federativa", false, 22, "JALISCO", "Nombre del estado o clave INEGI (14)",
                    false, "Catalogos!$C$2:$C$33"),
            new Columna("Entre calle", false, 24, "", null),
            new Columna("Y calle", false, 24, "", null),
            new Columna("Contacto", false, 26, "", "Persona de contacto"),
            new Columna("Teléfono", false, 14, "", "10 dígitos"),
            new Columna("Correo", false, 28, "", null));

    private static final List<String> MUESTRA = List.of("Renglón", "RFC", "Razón social", "Régimen fiscal",
            "C.P.", "Municipio", "Entidad", "Teléfono", "Correo");

    /** Lo que espera la confirmación. */
    record Datos(List<Proveedor> nuevos, List<String> omitidos) {
    }

    public record Confirmacion(String token, String contrasena) {
    }

    @GetMapping("/plantilla")
    @Operation(summary = "Plantilla de Excel con catálogos e instrucciones")
    public ResponseEntity<byte[]> plantilla() {
        ProveedorService.Catalogos cat = servicio.catalogos();
        Map<String, List<String>> catalogos = new LinkedHashMap<>();
        catalogos.put("Régimen fiscal (SAT)", cat.regimenes().values().stream()
                .map(r -> r.get("clave") + " - " + r.get("descripcion")).toList());
        catalogos.put("Entidad federativa", cat.entidades().values().stream().toList());
        catalogos.put("Estatus en el padrón", List.of("ACTIVO", "SUSPENDIDO", "CANCELADO"));
        List<String> instrucciones = new ArrayList<>(List.of(
                "Carga de proveedores · NexoMed",
                "Llene un renglón por proveedor con los datos de su Constancia de Situación Fiscal (CSF).",
                "Obligatorios: RFC, Razón social, Régimen fiscal y Código postal. Lo demás puede ir vacío.",
                "No se cargan dos veces: si el RFC ya está registrado, o la razón social es igual o parecida a la de otro"
                        + " proveedor (sin acentos ni 'S.A. de C.V.'), el renglón se omite con aviso y se cargan los demás.",
                "Primero se revisa todo el archivo sin guardar nada. Si hay errores no se carga ningún renglón.",
                "Si está correcto, la carga se confirma con la contraseña del usuario (3 intentos).",
                ""));
        COLUMNAS.forEach(c -> instrucciones.add(c.titulo() + (c.obligatoria() ? " (obligatorio)" : "")
                + (c.ayuda() == null ? "" : ": " + c.ayuda())));
        byte[] xlsx = Excel.plantilla("Proveedores", COLUMNAS, catalogos, instrucciones);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("plantilla_proveedores.xlsx").build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(xlsx.length)
                .body(xlsx);
    }

    @PostMapping(path = "/revisar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Revisa todo el archivo sin guardar nada")
    public Map<String, Object> revisar(@RequestParam("archivo") MultipartFile archivo) {
        Sesion s = usuarioActual.obtener();
        pendientes.cancelar(s.id(), CARGA);
        List<Excel.Fila> filas = Excel.leer(archivo, COLUMNAS);
        int leidos = filas.size();
        filas = filas.stream().filter(f -> !esEjemplo(f)).toList();
        if (leidos == 0) {
            throw new Excepciones.ReglaNegocio("El archivo está vacío: no tiene renglones con datos debajo de los títulos.");
        }
        if (filas.isEmpty()) {
            throw new Excepciones.ReglaNegocio("El archivo solo trae el renglón de ejemplo de la plantilla. "
                    + "Escriba los proveedores en lugar del ejemplo.");
        }

        ProveedorService.Catalogos cat = servicio.catalogos();
        List<ProveedorService.Existente> existentes = new ArrayList<>(servicio.existentes(false));
        List<String> errores = new ArrayList<>();
        List<String> omitidos = new ArrayList<>();
        List<Proveedor> nuevos = new ArrayList<>();
        List<List<Object>> muestra = new ArrayList<>();

        for (Excel.Fila f : filas) {
            List<String> e = new ArrayList<>();
            java.time.LocalDate inicio = null;
            try {
                inicio = Excel.fecha(f.valores().get(6));
            } catch (IllegalArgumentException ex) {
                e.add("Fecha de inicio de operaciones: " + ex.getMessage());
            }
            Proveedor crudo = new Proveedor(f.texto(0), f.texto(1), f.texto(2), f.texto(3), f.texto(4), f.texto(5),
                    inicio, f.texto(7), f.texto(8), f.texto(9), f.texto(10), f.texto(11), f.texto(12), f.texto(13),
                    f.texto(14), f.texto(15), f.texto(16), f.texto(17), f.texto(18), f.texto(19), f.texto(20),
                    f.texto(21), f.texto(22));
            ProveedorService.Revisado r = servicio.revisar(crudo, cat);
            e.addAll(r.errores());
            if (!e.isEmpty()) {
                e.forEach(x -> errores.add("Renglón " + f.renglon() + ": " + x));
                continue;
            }
            Proveedor p = r.proveedor();
            String motivo = ProveedorService.duplicado(p, existentes, null);
            if (motivo != null) {
                omitidos.add("Renglón " + f.renglon() + ": " + p.rfc() + " " + p.razonSocial() + " — " + motivo);
                continue;
            }
            existentes.add(new ProveedorService.Existente(-f.renglon(), p.rfc(), Parecidos.preparar(p.razonSocial()),
                    "el renglón " + f.renglon() + " del archivo"));
            nuevos.add(p);
            if (muestra.size() < MOSTRAR) {
                muestra.add(java.util.Arrays.asList(f.renglon(), p.rfc(), p.razonSocial(),
                        p.regimenFiscal() + " " + cat.regimenes().get(p.regimenFiscal()).get("descripcion"),
                        p.codigoPostal(), p.municipio(), p.entidad() == null ? null : cat.entidades().get(p.entidad()),
                        p.telefono(), p.correo()));
            }
        }

        boolean valida = errores.isEmpty() && !nuevos.isEmpty();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("valida", valida);
        r.put("sinNuevos", errores.isEmpty() && nuevos.isEmpty());
        r.put("leidos", filas.size());
        r.put("nuevos", nuevos.size());
        r.put("errores", errores.subList(0, Math.min(MOSTRAR, errores.size())));
        r.put("totalErrores", errores.size());
        r.put("omitidos", omitidos.subList(0, Math.min(MOSTRAR, omitidos.size())));
        r.put("totalOmitidos", omitidos.size());
        r.put("columnas", MUESTRA);
        r.put("muestra", errores.isEmpty() ? muestra : List.of());
        r.put("resumen", cuantos(nuevos.size(), "proveedor nuevo", "proveedores nuevos") + " de "
                + cuantos(filas.size(), "renglón", "renglones")
                + (omitidos.isEmpty() ? "." : "; " + cuantos(omitidos.size(), "se omite por repetido", "se omiten por repetidos") + "."));
        if (valida) {
            r.put("token", pendientes.guardar(s.id(), CARGA, archivo.getOriginalFilename(), new Datos(nuevos, omitidos)));
        }
        return r;
    }

    @PostMapping("/confirmar")
    @Transactional
    @Operation(summary = "Guarda la carga revisada; pide la contraseña del usuario (3 intentos)")
    public Map<String, Object> confirmar(@RequestBody Confirmacion c) {
        Sesion s = usuarioActual.obtener();
        CargasPendientes.Pendiente p = pendientes.obtener(c.token(), s.id(), CARGA).orElseThrow(() ->
                new Excepciones.ConDatos(409, "La revisión ya no está vigente (se guardó, se canceló o pasaron 30 minutos). "
                        + "Vuelva a revisar el archivo.", Map.of("cancelada", true)));
        String hash = db.valor("SELECT contrasena_hash FROM usuario WHERE id = :id", Map.of("id", s.id()), String.class)
                .orElseThrow();
        if (c.contrasena() == null || !encoder.matches(c.contrasena(), hash)) {
            int fallidos = p.fallidos().incrementAndGet();
            if (fallidos >= CargasPendientes.INTENTOS) {
                pendientes.quitar(p.token());
                bitacora.registrarAcceso(s.usuario(), s.id(), Bitacora.Accion.LOGIN_FALLIDO,
                        "Carga de proveedores cancelada: contrasena incorrecta 3 veces");
                throw new Excepciones.ConDatos(422, "Contraseña incorrecta " + CargasPendientes.INTENTOS
                        + " veces: la carga se canceló y no se guardó nada.", Map.of("cancelada", true));
            }
            int quedan = CargasPendientes.INTENTOS - fallidos;
            throw new Excepciones.ConDatos(422, "La contraseña no es correcta. Le " + (quedan == 1 ? "queda 1 intento." : "quedan " + quedan + " intentos."),
                    Map.of("cancelada", false));
        }
        if (!pendientes.quitar(p.token())) {
            throw new Excepciones.ConDatos(409, "Esta carga ya se guardó.", Map.of("cancelada", true));
        }

        Datos datos = (Datos) p.datos();
        // Otro usuario pudo dar de alta alguno mientras tanto: se vuelve a revisar con la tabla bloqueada.
        List<ProveedorService.Existente> existentes = servicio.existentes(true);
        List<Proveedor> guardar = new ArrayList<>();
        List<String> omitidosAhora = new ArrayList<>();
        for (Proveedor x : datos.nuevos()) {
            String motivo = ProveedorService.duplicado(x, existentes, null);
            if (motivo != null) {
                omitidosAhora.add(x.rfc() + " " + x.razonSocial() + " — " + motivo);
            } else {
                guardar.add(x);
                existentes.add(new ProveedorService.Existente(-1, x.rfc(), Parecidos.preparar(x.razonSocial()), x.rfc()));
            }
        }
        servicio.insertarVarios(guardar, "CARGA", s.id());
        int omitidos = datos.omitidos().size() + omitidosAhora.size();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("archivo", p.archivo());
        detalle.put("cargados", guardar.size());
        detalle.put("omitidos", omitidos);
        detalle.put("rfc", guardar.stream().map(Proveedor::rfc).toList());
        bitacora.registrar(s, Bitacora.Accion.CREACION, "proveedor", "CARGA", null, detalle);

        String mensaje = guardar.isEmpty()
                ? "No se cargó ningún proveedor: mientras tanto alguien más los registró."
                : "Se " + (guardar.size() == 1 ? "cargó 1 proveedor nuevo" : "cargaron " + guardar.size() + " proveedores nuevos")
                        + " a nombre de " + s.usuario() + ".";
        if (omitidos > 0) {
            mensaje += " Se " + (omitidos == 1 ? "omitió 1 repetido" : "omitieron " + omitidos + " repetidos") + ".";
        }
        return Map.of("mensaje", mensaje, "cargados", guardar.size(), "omitidos", omitidos, "omitidosAlGuardar", omitidosAhora);
    }

    @PostMapping("/cancelar")
    @Operation(summary = "Descarta la revisión pendiente del usuario")
    public void cancelar() {
        pendientes.cancelar(usuarioActual.obtener().id(), CARGA);
    }

    /** El renglón de ejemplo de la plantilla no se carga. */
    private static boolean esEjemplo(Excel.Fila f) {
        return COLUMNAS.get(0).ejemplo().equalsIgnoreCase(String.valueOf(f.texto(0)).trim())
                && COLUMNAS.get(1).ejemplo().equalsIgnoreCase(String.valueOf(f.texto(1)).trim());
    }

    private static String cuantos(int n, String uno, String varios) {
        return n + " " + (n == 1 ? uno : varios);
    }
}
