package mx.medicalminds.nexomed.comun;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Acceso a datos con JdbcClient. Las filas se devuelven como mapas con el nombre
 * de columna (o alias) y las fechas como texto ISO, listas para JSON.
 */
@Component
@RequiredArgsConstructor
public class Db {

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate plantilla;

    public List<Map<String, Object>> lista(String sql, Map<String, ?> params) {
        return jdbc.sql(sql).params(params).query(Db::fila).list();
    }

    public List<Map<String, Object>> lista(String sql) {
        return jdbc.sql(sql).query(Db::fila).list();
    }

    public Optional<Map<String, Object>> uno(String sql, Map<String, ?> params) {
        return jdbc.sql(sql).params(params).query(Db::fila).optional();
    }

    public Map<String, Object> exigir(String sql, Map<String, ?> params, String recurso, Object id) {
        return uno(sql, params).orElseThrow(() -> new Excepciones.NoEncontrado(recurso, id));
    }

    public <T> Optional<T> valor(String sql, Map<String, ?> params, Class<T> tipo) {
        return jdbc.sql(sql).params(params).query(tipo).optional();
    }

    public long contar(String sql, Map<String, ?> params) {
        Number n = jdbc.sql(sql).params(params).query(Number.class).single();
        return n == null ? 0 : n.longValue();
    }

    public int ejecutar(String sql, Map<String, ?> params) {
        return jdbc.sql(sql).params(sinAcentos(params)).update();
    }

    /** El mismo INSERT o UPDATE para muchas filas en un solo viaje (también sin acentos). */
    public void lote(String sql, List<Map<String, Object>> filas) {
        if (filas.isEmpty()) {
            return;
        }
        plantilla.batchUpdate(sql, filas.stream().map(Db::sinAcentos)
                .map(MapSqlParameterSource::new).toArray(MapSqlParameterSource[]::new));
    }

    /** INSERT que devuelve el id generado. */
    public long insertar(String sql, Map<String, ?> params) {
        KeyHolder llave = new GeneratedKeyHolder();
        jdbc.sql(sql).params(sinAcentos(params)).update(llave, "id");
        Number id = llave.getKey();
        if (id == null) {
            throw new IllegalStateException("El INSERT no devolvió id");
        }
        return id.longValue();
    }

    /**
     * Los datos se guardan sin acentos (la ñ se conserva); los acentos van solo en los textos del front.
     * Se aplica a todo texto que se escribe con {@link #ejecutar} o {@link #insertar}.
     */
    public static String sinAcentos(String texto) {
        if (texto == null) {
            return null;
        }
        StringBuilder b = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            int k = CON_ACENTO.indexOf(c);
            b.append(k < 0 ? c : SIN_ACENTO.charAt(k));
        }
        return b.toString();
    }

    private static final String CON_ACENTO = "áéíóúüÁÉÍÓÚÜ";
    private static final String SIN_ACENTO = "aeiouuAEIOUU";

    private static Map<String, Object> sinAcentos(Map<String, ?> params) {
        Map<String, Object> limpio = new LinkedHashMap<>();
        params.forEach((k, v) -> limpio.put(k, v instanceof String s ? sinAcentos(s) : v));
        return limpio;
    }

    static Map<String, Object> fila(ResultSet rs, int n) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            mapa.put(md.getColumnLabel(i), normalizar(rs.getObject(i)));
        }
        return mapa;
    }

    private static Object normalizar(Object v) {
        if (v instanceof Timestamp t) {
            return t.toLocalDateTime().toString();
        }
        if (v instanceof LocalDateTime t) {
            return t.toString();
        }
        if (v instanceof Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof LocalDate d) {
            return d.toString();
        }
        return v;
    }
}
