package mx.medicalminds.nexomed.catalogos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.comun.Bitacora;
import mx.medicalminds.nexomed.comun.Db;
import mx.medicalminds.nexomed.comun.Excepciones;
import mx.medicalminds.nexomed.comun.Parecidos;
import mx.medicalminds.nexomed.seguridad.Sesion;
import mx.medicalminds.nexomed.seguridad.UsuarioActual;

/** Catálogos › Catálogo de proveedores. */
@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
@Tag(name = "Proveedores")
@PreAuthorize("hasRole('PROVEEDORES_ADMINISTRAR')")
public class ProveedorController {

    private final Db db;
    private final ProveedorService servicio;
    private final UsuarioActual usuarioActual;
    private final Bitacora bitacora;

    public record CambioActivo(boolean activo) {
    }

    private static final String COLUMNAS = """
            p.id, p.rfc, p.tipo_persona, p.razon_social, p.regimen_capital, p.nombre_comercial, p.regimen_fiscal,
            r.descripcion AS regimen_descripcion, p.actividad_economica, p.fecha_inicio_operaciones, p.estatus_padron,
            p.id_cif, p.codigo_postal, p.tipo_vialidad, p.vialidad, p.numero_exterior, p.numero_interior, p.colonia,
            p.localidad, p.municipio, p.entidad_clave, e.nombre AS entidad, p.entre_calle, p.y_calle, p.contacto,
            p.telefono, p.correo, p.activo, p.origen, p.creado_en, p.actualizado_en
            """;
    private static final String DESDE = """
              FROM proveedor p
              JOIN regimen_fiscal r ON r.clave = p.regimen_fiscal
              LEFT JOIN entidad_federativa e ON e.clave = p.entidad_clave""";

    @GetMapping("/catalogos")
    @Operation(summary = "Regímenes fiscales del SAT, entidades federativas y estatus del padrón")
    public Map<String, Object> catalogos() {
        return Map.of(
                "regimenes", db.lista("SELECT clave, descripcion, persona_fisica, persona_moral FROM regimen_fiscal ORDER BY clave"),
                "entidades", db.lista("SELECT clave, nombre FROM entidad_federativa ORDER BY nombre"),
                "estatus", ProveedorService.ESTATUS);
    }

    @GetMapping
    @Operation(summary = "Buscar proveedores por RFC, razón social o nombre comercial (hasta 500)")
    public Map<String, Object> listar(@RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "activos") String estado) {
        String filtro = switch (estado) {
            case "inactivos" -> " AND NOT p.activo";
            case "todos" -> "";
            default -> " AND p.activo";
        };
        Map<String, Object> params = new HashMap<>();
        String buscar = "";
        if (q != null && !q.isBlank()) {
            buscar = " AND (p.rfc LIKE :q OR p.razon_social LIKE :q OR p.nombre_comercial LIKE :q OR p.municipio LIKE :q)";
            params.put("q", "%" + Db.sinAcentos(q.trim()).replace("%", "").replace("_", "") + "%");
        }
        String where = " WHERE 1 = 1" + filtro + buscar;
        List<Map<String, Object>> filas = db.lista("SELECT " + COLUMNAS + DESDE + where + " ORDER BY p.razon_social LIMIT 500", params);
        long total = db.contar("SELECT COUNT(*) FROM proveedor p" + where, params);
        return Map.of("proveedores", filas, "total", total,
                "activos", db.contar("SELECT COUNT(*) FROM proveedor WHERE activo", Map.of()),
                "inactivos", db.contar("SELECT COUNT(*) FROM proveedor WHERE NOT activo", Map.of()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Un proveedor")
    public Map<String, Object> obtener(@PathVariable long id) {
        return db.exigir("SELECT " + COLUMNAS + DESDE + " WHERE p.id = :id", Map.of("id", id), "Proveedor", id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Alta de proveedor. Con una razón social parecida a otra pide aunqueParecido=true")
    public Map<String, Object> crear(@RequestBody Proveedor datos,
                                     @RequestParam(defaultValue = "false") boolean aunqueParecido) {
        Proveedor p = revisar(datos, null, aunqueParecido, null);
        Sesion s = usuarioActual.obtener();
        long id = servicio.insertar(p, "CAPTURA", s.id());
        bitacora.registrar(s, Bitacora.Accion.CREACION, "proveedor", id, null, p);
        return Map.of("id", id, "rfc", p.rfc());
    }

    @PutMapping("/{id}")
    @Transactional
    @Operation(summary = "Cambios a un proveedor")
    public Map<String, Object> actualizar(@PathVariable long id, @RequestBody Proveedor datos,
                                          @RequestParam(defaultValue = "false") boolean aunqueParecido) {
        Map<String, Object> antes = obtener(id);
        Proveedor p = revisar(datos, id, aunqueParecido, (String) antes.get("razon_social"));
        Sesion s = usuarioActual.obtener();
        servicio.actualizar(id, p, s.id());
        bitacora.registrar(s, Bitacora.Accion.MODIFICACION, "proveedor", id, antes, p);
        return Map.of("id", id, "rfc", p.rfc());
    }

    @PutMapping("/{id}/activo")
    @Transactional
    @Operation(summary = "Activar o desactivar un proveedor (no se borra)")
    public void cambiarActivo(@PathVariable long id, @RequestBody CambioActivo c) {
        obtener(id);
        Sesion s = usuarioActual.obtener();
        db.ejecutar("UPDATE proveedor SET activo = :a, actualizado_por = :u WHERE id = :id",
                Map.of("a", c.activo(), "u", s.id(), "id", id));
        bitacora.registrar(s, c.activo() ? Bitacora.Accion.MODIFICACION : Bitacora.Accion.BAJA, "proveedor", id, null,
                Map.of("activo", c.activo()));
    }

    /** Valida y aplica la regla de no duplicar: mismo RFC nunca; razón social igual o parecida, solo si se confirma. */
    private Proveedor revisar(Proveedor datos, Long id, boolean aunqueParecido, String razonAnterior) {
        ProveedorService.Revisado r = servicio.revisar(datos, servicio.catalogos());
        if (!r.errores().isEmpty()) {
            throw new Excepciones.ConDatos(400, "Revise los datos del proveedor", Map.of("errores", r.errores()));
        }
        Proveedor p = r.proveedor();
        // Al editar sin cambiar la razón social (sin acentos, signos ni régimen de capital) solo se revisa el RFC.
        boolean mismoNombre = razonAnterior != null
                && Parecidos.preparar(razonAnterior).compacto().equals(Parecidos.preparar(p.razonSocial()).compacto());
        String motivo = ProveedorService.duplicado(p, servicio.existentes(true), id, !mismoNombre);
        if (motivo != null && (motivo.startsWith("Mismo RFC") || !aunqueParecido)) {
            throw new Excepciones.ConDatos(409, motivo + ".",
                    Map.of("parecido", !motivo.startsWith("Mismo RFC")));
        }
        return p;
    }
}
