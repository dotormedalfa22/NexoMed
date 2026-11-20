package mx.medicalminds.nexomed.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.comun.Bitacora;
import mx.medicalminds.nexomed.comun.Db;
import mx.medicalminds.nexomed.comun.Excepciones;
import mx.medicalminds.nexomed.seguridad.Sesion;
import mx.medicalminds.nexomed.seguridad.UsuarioActual;

/**
 * Menú lateral guardado en la base (menu, menu_opcion, menu_opcion_perfil).
 * Las pantallas (ruta y rol que exige su API) las da de alta el sistema en migraciones;
 * desde la configuración se cambian nombres, el menú al que pertenecen, si están activas
 * y qué perfiles las ven. También se crean menús nuevos. Menús y opciones van en orden alfabético.
 */
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
@Tag(name = "Menú")
public class MenuController {

    /** Opción que no puede quedar fuera del alcance del ADMINISTRADOR (si no, nadie podría corregir el menú). */
    private static final String OPCION_CONFIGURAR = "CONFIGURAR_MENU";
    private static final String PERFIL_ADMIN = "ADMINISTRADOR";
    static final Set<String> ICONOS = Set.of("almacen", "carga", "catalogos", "consultas", "dashboard", "entradas",
            "impresos", "inventario", "reportes", "salidas", "usuario", "carpeta");

    private final Db db;
    private final UsuarioActual usuarioActual;
    private final Bitacora bitacora;

    @GetMapping
    @Operation(summary = "Menú del usuario en sesión, en orden alfabético: solo las opciones que su perfil puede ver; "
            + "los menús activos que aún no tienen opciones se muestran vacíos (en preparación)")
    public List<Map<String, Object>> miMenu() {
        Sesion s = usuarioActual.obtener();
        Map<Object, Map<String, Object>> menus = new LinkedHashMap<>();
        for (Map<String, Object> m : db.lista("""
                SELECT m.id, m.nombre, m.icono,
                       (SELECT COUNT(*) FROM menu_opcion o WHERE o.menu_id = m.id AND o.activo) AS opciones
                  FROM menu m WHERE m.activo ORDER BY m.nombre""")) {
            Map<String, Object> menu = new LinkedHashMap<>();
            menu.put("nombre", m.get("nombre"));
            menu.put("icono", m.get("icono"));
            menu.put("enPreparacion", ((Number) m.get("opciones")).longValue() == 0);
            menu.put("opciones", new ArrayList<Map<String, Object>>());
            menus.put(m.get("id"), menu);
        }
        List<Map<String, Object>> filas = db.lista("""
                SELECT o.menu_id, o.clave, o.nombre, o.descripcion, o.ruta, o.rol_requerido
                  FROM menu_opcion o
                  JOIN menu_opcion_perfil mop ON mop.opcion_id = o.id
                  JOIN perfil p ON p.id = mop.perfil_id AND p.clave = :perfil
                 WHERE o.activo
                 ORDER BY o.nombre""", Map.of("perfil", s.perfil()));
        for (Map<String, Object> f : filas) {
            String rol = (String) f.get("rol_requerido");
            Map<String, Object> menu = menus.get(f.get("menu_id"));
            if (menu == null || (rol != null && !s.tiene(rol))) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> opciones = (List<Map<String, Object>>) menu.get("opciones");
            opciones.add(Map.of("clave", f.get("clave"), "nombre", f.get("nombre"), "ruta", f.get("ruta"),
                    "descripcion", f.get("descripcion") == null ? "" : f.get("descripcion")));
        }
        // Un menú con opciones que este perfil no ve no se muestra; uno sin opciones todavía, sí.
        return menus.values().stream()
                .filter(m -> Boolean.TRUE.equals(m.get("enPreparacion")) || !((List<?>) m.get("opciones")).isEmpty())
                .toList();
    }

    // ------------------------------------------------------------------ Configuración

    @GetMapping("/configuracion")
    @PreAuthorize("hasRole('MENU_CONFIGURAR')")
    @Operation(summary = "Todos los menús y opciones, con los perfiles que ven cada opción")
    public Map<String, Object> configuracion() {
        List<Map<String, Object>> opciones = db.lista("""
                SELECT o.id, o.menu_id, o.clave, o.nombre, o.descripcion, o.ruta, o.rol_requerido, r.nombre AS rol_nombre,
                       o.activo,
                       (SELECT GROUP_CONCAT(mop.perfil_id) FROM menu_opcion_perfil mop WHERE mop.opcion_id = o.id) AS perfiles
                  FROM menu_opcion o LEFT JOIN rol r ON r.clave = o.rol_requerido
                 ORDER BY o.nombre""");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("menus", db.lista("SELECT id, clave, nombre, icono, activo FROM menu ORDER BY nombre"));
        r.put("opciones", opciones);
        r.put("perfiles", db.lista("SELECT id, clave, nombre, activo FROM perfil ORDER BY nombre"));
        r.put("perfilesConRol", db.lista("""
                SELECT r.clave AS rol, pr.perfil_id FROM perfil_rol pr JOIN rol r ON r.id = pr.rol_id"""));
        r.put("iconos", ICONOS.stream().sorted().toList());
        return r;
    }

    public record DatosMenu(@NotBlank @Size(max = 60) String nombre,
                            @NotBlank @Pattern(regexp = "^[a-z]+$") String icono,
                            boolean activo) {
    }

    @PostMapping("/grupos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MENU_CONFIGURAR')")
    @Transactional
    @Operation(summary = "Crear un menú nuevo (grupo de opciones)")
    public Map<String, Object> crearMenu(@Valid @RequestBody DatosMenu d) {
        validarIcono(d.icono());
        String clave = claveDesde(d.nombre());
        if (db.contar("SELECT COUNT(*) FROM menu WHERE clave = :c", Map.of("c", clave)) > 0) {
            throw new Excepciones.ReglaNegocio("Ya existe un menú con ese nombre");
        }
        Map<String, Object> p = new HashMap<>();
        p.put("clave", clave);
        p.put("nombre", d.nombre().trim());
        p.put("icono", d.icono());
        p.put("activo", d.activo());
        long id = db.insertar("INSERT INTO menu (clave, nombre, icono, activo) VALUES (:clave, :nombre, :icono, :activo)", p);
        bitacora.registrar(usuarioActual.obtener(), Bitacora.Accion.CREACION, "menu", id, null, p);
        return Map.of("id", id);
    }

    @PutMapping("/grupos/{id}")
    @PreAuthorize("hasRole('MENU_CONFIGURAR')")
    @Transactional
    @Operation(summary = "Cambiar nombre, icono o estado de un menú")
    public void modificarMenu(@PathVariable long id, @Valid @RequestBody DatosMenu d) {
        validarIcono(d.icono());
        Map<String, Object> antes = db.exigir("SELECT nombre, icono, activo FROM menu WHERE id = :id", Map.of("id", id), "Menú", id);
        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        p.put("nombre", d.nombre().trim());
        p.put("icono", d.icono());
        p.put("activo", d.activo());
        db.ejecutar("UPDATE menu SET nombre = :nombre, icono = :icono, activo = :activo WHERE id = :id", p);
        exigirAccesoAdministrador();
        bitacora.registrar(usuarioActual.obtener(), Bitacora.Accion.MODIFICACION, "menu", id, antes, p);
    }

    public record DatosOpcion(@NotNull Long menuId,
                              @NotBlank @Size(max = 60) String nombre,
                              @Size(max = 200) String descripcion,
                              boolean activo,
                              @NotNull List<Long> perfiles) {
    }

    @PutMapping("/opciones/{id}")
    @PreAuthorize("hasRole('MENU_CONFIGURAR')")
    @Transactional
    @Operation(summary = "Cambiar nombre, menú, estado y perfiles que ven una opción")
    public void modificarOpcion(@PathVariable long id, @Valid @RequestBody DatosOpcion d) {
        Map<String, Object> antes = db.exigir("""
                SELECT o.menu_id, o.nombre, o.descripcion, o.activo,
                       (SELECT GROUP_CONCAT(perfil_id ORDER BY perfil_id) FROM menu_opcion_perfil WHERE opcion_id = o.id) AS perfiles
                  FROM menu_opcion o WHERE o.id = :id""", Map.of("id", id), "Opción", id);
        db.exigir("SELECT id FROM menu WHERE id = :id", Map.of("id", d.menuId()), "Menú", d.menuId());
        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        p.put("menu", d.menuId());
        p.put("nombre", d.nombre().trim());
        p.put("descripcion", d.descripcion() == null || d.descripcion().isBlank() ? null : d.descripcion().trim());
        p.put("activo", d.activo());
        db.ejecutar("""
                UPDATE menu_opcion SET menu_id = :menu, nombre = :nombre, descripcion = :descripcion,
                       activo = :activo WHERE id = :id""", p);
        db.ejecutar("DELETE FROM menu_opcion_perfil WHERE opcion_id = :id", Map.of("id", id));
        for (Long perfil : d.perfiles().stream().distinct().toList()) {
            if (db.contar("SELECT COUNT(*) FROM perfil WHERE id = :p", Map.of("p", perfil)) == 0) {
                throw new Excepciones.NoEncontrado("Perfil", perfil);
            }
            db.ejecutar("INSERT INTO menu_opcion_perfil (opcion_id, perfil_id) VALUES (:o, :p)", Map.of("o", id, "p", perfil));
        }
        exigirAccesoAdministrador();
        Map<String, Object> despues = new HashMap<>(p);
        despues.put("perfiles", d.perfiles());
        bitacora.registrar(usuarioActual.obtener(), Bitacora.Accion.MODIFICACION, "menu_opcion", id, antes, despues);
    }

    /** Después de cada cambio: el ADMINISTRADOR debe seguir viendo «Configurar menú» (si no, se revierte). */
    private void exigirAccesoAdministrador() {
        long visible = db.contar("""
                SELECT COUNT(*) FROM menu_opcion o
                  JOIN menu m ON m.id = o.menu_id AND m.activo
                  JOIN menu_opcion_perfil mop ON mop.opcion_id = o.id
                  JOIN perfil p ON p.id = mop.perfil_id AND p.clave = :admin
                 WHERE o.clave = :opcion AND o.activo""", Map.of("admin", PERFIL_ADMIN, "opcion", OPCION_CONFIGURAR));
        if (visible == 0) {
            throw new Excepciones.ReglaNegocio("No se guardó: el perfil Administrador debe seguir viendo «Configurar menú» "
                    + "(opción activa, en un menú activo y con el perfil Administrador marcado).");
        }
    }

    private static void validarIcono(String icono) {
        if (!ICONOS.contains(icono)) {
            throw new Excepciones.ReglaNegocio("Icono no válido: " + icono);
        }
    }

    /** USUARIO, CATALOGOS_GENERALES… a partir del nombre (sin acentos ni signos). */
    private static String claveDesde(String nombre) {
        String c = Db.sinAcentos(nombre.trim()).toUpperCase().replace('Ñ', 'N').replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (c.isEmpty()) {
            throw new Excepciones.ReglaNegocio("Escriba un nombre con letras o números");
        }
        return c.length() > 30 ? c.substring(0, 30) : c;
    }
}
