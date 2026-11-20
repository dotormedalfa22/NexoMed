package mx.medicalminds.nexomed.catalogos;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.medicalminds.nexomed.comun.Db;
import mx.medicalminds.nexomed.comun.Parecidos;

/**
 * Reglas del catálogo de proveedores, las mismas para la captura en pantalla y para la carga por Excel:
 * limpieza (mayúsculas, espacios), validación por campo y la regla de no duplicar (mismo RFC, o razón social
 * igual o parecida).
 */
@Service
@RequiredArgsConstructor
public class ProveedorService {

    /** RFC: 3 letras (moral) o 4 (física), fecha aammdd y homoclave. */
    static final Pattern RFC = Pattern.compile("^([A-ZÑ&]{3,4})(\\d{2})(\\d{2})(\\d{2})([A-Z\\d]{3})$");
    static final List<String> ESTATUS = List.of("ACTIVO", "SUSPENDIDO", "CANCELADO");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Map<String, String> ENTIDAD_ALIAS = Map.of(
            "CDMX", "09", "DF", "09", "DISTRITO FEDERAL", "09", "MEXICO DF", "09",
            "ESTADO DE MEXICO", "15", "EDO MEX", "15", "EDOMEX", "15", "EDO DE MEXICO", "15");

    private final Db db;

    /** Catálogos para validar sin consultar la base por renglón. */
    public record Catalogos(Map<String, Map<String, Object>> regimenes, Map<String, String> entidades) {
    }

    public record Revisado(Proveedor proveedor, List<String> errores) {
    }

    /** Proveedor ya registrado (o renglón anterior del mismo archivo) contra el que se compara. */
    public record Existente(long id, String rfc, Parecidos.Nombre nombre, String donde) {
    }

    public Catalogos catalogos() {
        Map<String, Map<String, Object>> reg = new LinkedHashMap<>();
        db.lista("SELECT clave, descripcion, persona_fisica, persona_moral FROM regimen_fiscal ORDER BY clave")
                .forEach(r -> reg.put((String) r.get("clave"), r));
        Map<String, String> ent = new LinkedHashMap<>();
        db.lista("SELECT clave, nombre FROM entidad_federativa ORDER BY clave")
                .forEach(r -> ent.put((String) r.get("clave"), (String) r.get("nombre")));
        return new Catalogos(reg, ent);
    }

    /** Todos los proveedores para la regla de no duplicar. Con {@code bloquear} se toma con FOR UPDATE. */
    public List<Existente> existentes(boolean bloquear) {
        List<Existente> lista = new ArrayList<>();
        db.lista("SELECT id, rfc, razon_social FROM proveedor" + (bloquear ? " FOR UPDATE" : ""), Map.of())
                .forEach(r -> lista.add(new Existente(((Number) r.get("id")).longValue(), (String) r.get("rfc"),
                        Parecidos.preparar((String) r.get("razon_social")),
                        r.get("razon_social") + " (" + r.get("rfc") + ")")));
        return lista;
    }

    /** Motivo por el que se repite, o null. Revisa primero el RFC y luego la razón social. */
    public static String duplicado(Proveedor p, List<Existente> lista, Long excluirId) {
        return duplicado(p, lista, excluirId, true);
    }

    /** {@code compararNombre} false: solo el RFC (al editar sin cambiar la razón social). */
    public static String duplicado(Proveedor p, List<Existente> lista, Long excluirId, boolean compararNombre) {
        for (Existente x : lista) {
            if ((excluirId == null || x.id() != excluirId) && x.rfc().equals(p.rfc())) {
                return "Mismo RFC que " + x.donde();
            }
        }
        if (!compararNombre) {
            return null;
        }
        Parecidos.Nombre n = Parecidos.preparar(p.razonSocial());
        for (Existente x : lista) {
            if (excluirId != null && x.id() == excluirId) {
                continue;
            }
            String c = Parecidos.comparar(n, x.nombre());
            if (c != null) {
                return "Razón social " + c + (x.donde().startsWith("el ") ? " a" + x.donde().substring(1) : " a " + x.donde());
            }
        }
        return null;
    }

    /** Limpia y valida. Los errores dicen el campo; el proveedor devuelto ya va en mayúsculas y con claves. */
    public Revisado revisar(Proveedor p, Catalogos cat) {
        List<String> e = new ArrayList<>();

        String rfc = p.rfc() == null ? null : p.rfc().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-_.]", "");
        boolean moral = false;
        boolean rfcValido = false;
        if (rfc == null || rfc.isEmpty()) {
            e.add("Falta el RFC");
            rfc = null;
        } else {
            Matcher m = RFC.matcher(rfc);
            if (!m.matches()) {
                e.add("RFC " + rfc + " no es válido (12 caracteres persona moral, 13 persona física)");
            } else if (rfc.startsWith("XAXX") || rfc.startsWith("XEXX")) {
                e.add("RFC " + rfc + " es genérico (público en general o extranjero); use el RFC del proveedor");
            } else if (!fechaValida(m.group(2), m.group(3), m.group(4))) {
                e.add("RFC " + rfc + ": la fecha " + m.group(4) + "/" + m.group(3) + "/" + m.group(2) + " no existe");
            } else {
                rfcValido = true;
                moral = m.group(1).length() == 3;
            }
        }

        String razon = mayus(p.razonSocial(), 250, "Razón social", e);
        if (razon == null) {
            e.add("Falta la razón social");
        }

        String regimen = null;
        String r = texto(p.regimenFiscal());
        if (r == null) {
            e.add("Falta el régimen fiscal");
        } else {
            Matcher m = Pattern.compile("^(\\d{3})\\b").matcher(r);
            if (m.find()) {
                regimen = m.group(1);
            } else {
                String buscado = Parecidos.normalizar(r).replaceFirst("^REGIMEN ", "");
                for (var x : cat.regimenes().values()) {
                    String d = Parecidos.normalizar((String) x.get("descripcion")).replaceFirst("^REGIMEN ", "");
                    if (d.equals(buscado)) {
                        regimen = (String) x.get("clave");
                    }
                }
            }
            Map<String, Object> reg = regimen == null ? null : cat.regimenes().get(regimen);
            if (reg == null) {
                e.add("Régimen fiscal " + r + " no está en el catálogo del SAT (use la clave, por ejemplo 601)");
                regimen = null;
            } else if (rfcValido && !verdadero(reg.get(moral ? "persona_moral" : "persona_fisica"))) {
                e.add("Régimen fiscal " + regimen + " no aplica a persona " + (moral ? "moral" : "física"));
            }
        }

        LocalDate fecha = p.fechaInicioOperaciones();
        if (fecha != null && (fecha.isAfter(LocalDate.now()) || fecha.getYear() < 1900)) {
            e.add("Fecha de inicio de operaciones fuera de rango");
        }

        String estatus = mayus(p.estatusPadron(), 20, "Estatus en el padrón", e);
        if (estatus == null) {
            estatus = "ACTIVO";
        } else if (!ESTATUS.contains(estatus)) {
            e.add("Estatus en el padrón " + estatus + " no es válido (ACTIVO, SUSPENDIDO o CANCELADO)");
        }

        String idCif = texto(p.idCif());
        if (idCif != null && !idCif.matches("\\d{1,20}")) {
            e.add("idCIF debe ser solo números (hasta 20)");
        }

        String cp = texto(p.codigoPostal());
        if (cp == null) {
            e.add("Falta el código postal");
        } else {
            cp = cp.replaceAll("\\s", "");
            if (cp.matches("\\d{4}")) {
                cp = "0" + cp;
            }
            if (!cp.matches("\\d{5}")) {
                e.add("Código postal " + cp + " debe tener 5 dígitos");
            }
        }

        String entidad = null;
        String ent = texto(p.entidad());
        if (ent != null) {
            entidad = entidad(ent, cat);
            if (entidad == null) {
                e.add("Entidad federativa " + ent + " no existe");
            }
        }

        String telefono = texto(p.telefono());
        if (telefono != null) {
            telefono = telefono.replaceAll("\\D", "");
            if (telefono.length() == 12 && telefono.startsWith("52")) {
                telefono = telefono.substring(2);
            }
            if (!telefono.matches("\\d{10}")) {
                e.add("Teléfono " + p.telefono().trim() + " debe tener 10 dígitos");
            }
        }

        String correo = texto(p.correo());
        if (correo != null) {
            correo = correo.toLowerCase(Locale.ROOT);
            if (correo.length() > 150 || !CORREO.matcher(correo).matches()) {
                e.add("Correo " + correo + " no es válido");
            }
        }

        Proveedor limpio = new Proveedor(rfc, razon,
                mayus(p.regimenCapital(), 150, "Régimen capital", e),
                mayus(p.nombreComercial(), 250, "Nombre comercial", e),
                regimen,
                mayus(p.actividadEconomica(), 250, "Actividad económica", e),
                fecha, estatus, idCif, cp,
                mayus(p.tipoVialidad(), 50, "Tipo de vialidad", e),
                mayus(p.vialidad(), 150, "Nombre de vialidad", e),
                mayus(p.numeroExterior(), 30, "Número exterior", e),
                mayus(p.numeroInterior(), 30, "Número interior", e),
                mayus(p.colonia(), 150, "Colonia", e),
                mayus(p.localidad(), 150, "Localidad", e),
                mayus(p.municipio(), 150, "Municipio", e),
                entidad,
                mayus(p.entreCalle(), 150, "Entre calle", e),
                mayus(p.yCalle(), 150, "Y calle", e),
                mayus(p.contacto(), 150, "Contacto", e),
                telefono, correo);
        return new Revisado(limpio, e);
    }

    public long insertar(Proveedor p, String origen, long usuarioId) {
        return db.insertar(INSERT, parametros(p, origen, usuarioId));
    }

    public void insertarVarios(List<Proveedor> lista, String origen, long usuarioId) {
        db.lote(INSERT, lista.stream().map(p -> parametros(p, origen, usuarioId)).toList());
    }

    public void actualizar(long id, Proveedor p, long usuarioId) {
        Map<String, Object> params = parametros(p, null, usuarioId);
        params.put("id", id);
        db.ejecutar("""
                UPDATE proveedor SET rfc = :rfc, tipo_persona = :tipo, razon_social = :razon, regimen_capital = :regCapital,
                       nombre_comercial = :comercial, regimen_fiscal = :regimen, actividad_economica = :actividad,
                       fecha_inicio_operaciones = :inicio, estatus_padron = :estatus, id_cif = :idCif, codigo_postal = :cp,
                       tipo_vialidad = :tipoVialidad, vialidad = :vialidad, numero_exterior = :numExt, numero_interior = :numInt,
                       colonia = :colonia, localidad = :localidad, municipio = :municipio, entidad_clave = :entidad,
                       entre_calle = :entreCalle, y_calle = :yCalle, contacto = :contacto, telefono = :telefono, correo = :correo,
                       actualizado_por = :usuario
                 WHERE id = :id""", params);
    }

    private static final String INSERT = """
            INSERT INTO proveedor (rfc, tipo_persona, razon_social, regimen_capital, nombre_comercial, regimen_fiscal,
                   actividad_economica, fecha_inicio_operaciones, estatus_padron, id_cif, codigo_postal, tipo_vialidad, vialidad,
                   numero_exterior, numero_interior, colonia, localidad, municipio, entidad_clave, entre_calle, y_calle,
                   contacto, telefono, correo, origen, creado_por)
            VALUES (:rfc, :tipo, :razon, :regCapital, :comercial, :regimen, :actividad, :inicio, :estatus, :idCif, :cp,
                   :tipoVialidad, :vialidad, :numExt, :numInt, :colonia, :localidad, :municipio, :entidad, :entreCalle, :yCalle,
                   :contacto, :telefono, :correo, :origen, :usuario)""";

    private static Map<String, Object> parametros(Proveedor p, String origen, long usuarioId) {
        Map<String, Object> m = new HashMap<>();
        m.put("rfc", p.rfc());
        m.put("tipo", p.rfc().length() == 12 ? "M" : "F");
        m.put("razon", p.razonSocial());
        m.put("regCapital", p.regimenCapital());
        m.put("comercial", p.nombreComercial());
        m.put("regimen", p.regimenFiscal());
        m.put("actividad", p.actividadEconomica());
        m.put("inicio", p.fechaInicioOperaciones());
        m.put("estatus", p.estatusPadron());
        m.put("idCif", p.idCif());
        m.put("cp", p.codigoPostal());
        m.put("tipoVialidad", p.tipoVialidad());
        m.put("vialidad", p.vialidad());
        m.put("numExt", p.numeroExterior());
        m.put("numInt", p.numeroInterior());
        m.put("colonia", p.colonia());
        m.put("localidad", p.localidad());
        m.put("municipio", p.municipio());
        m.put("entidad", p.entidad());
        m.put("entreCalle", p.entreCalle());
        m.put("yCalle", p.yCalle());
        m.put("contacto", p.contacto());
        m.put("telefono", p.telefono());
        m.put("correo", p.correo());
        m.put("origen", origen);
        m.put("usuario", usuarioId);
        return m;
    }

    private static String entidad(String texto, Catalogos cat) {
        if (texto.matches("\\d{1,2}")) {
            String clave = texto.length() == 1 ? "0" + texto : texto;
            return cat.entidades().containsKey(clave) ? clave : null;
        }
        String n = Parecidos.normalizar(texto);
        if (ENTIDAD_ALIAS.containsKey(n)) {
            return ENTIDAD_ALIAS.get(n);
        }
        for (var x : cat.entidades().entrySet()) {
            if (x.getValue().equals(n)) {
                return x.getKey();
            }
        }
        // "VERACRUZ", "COAHUILA", "MICHOACAN": el nombre corto del estado
        for (var x : cat.entidades().entrySet()) {
            if (x.getValue().startsWith(n + " DE ")) {
                return x.getKey();
            }
        }
        return null;
    }

    private static boolean fechaValida(String aa, String mm, String dd) {
        try {
            LocalDate.of(2000 + Integer.parseInt(aa), Integer.parseInt(mm), Integer.parseInt(dd));
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }

    private static boolean verdadero(Object v) {
        return v instanceof Boolean b ? b : v instanceof Number n && n.intValue() != 0;
    }

    static String texto(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
        return t.isEmpty() || t.equals("-") ? null : t;
    }

    private static String mayus(String s, int max, String campo, List<String> errores) {
        String t = texto(s);
        if (t == null) {
            return null;
        }
        t = t.toUpperCase(Locale.forLanguageTag("es-MX"));
        if (t.length() > max) {
            errores.add(campo + ": máximo " + max + " caracteres (trae " + t.length() + ")");
        }
        return t;
    }
}
