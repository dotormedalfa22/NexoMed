package mx.medicalminds.nexomed.cargas;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Revisiones de archivos que esperan la confirmación con contraseña. Una por usuario y carga; caducan a los
 * 30 minutos. Viven en memoria (la sesión es por token): con varias instancias del servidor habría que
 * pasarlas a la base.
 */
@Component
public class CargasPendientes {

    public static final int INTENTOS = 3;
    private static final Duration VIGENCIA = Duration.ofMinutes(30);

    public record Pendiente(String token, long usuarioId, String carga, String archivo, Object datos,
                            Instant creada, AtomicInteger fallidos) {
    }

    private final Map<String, Pendiente> pendientes = new ConcurrentHashMap<>();

    public String guardar(long usuarioId, String carga, String archivo, Object datos) {
        limpiar();
        cancelar(usuarioId, carga);
        String token = UUID.randomUUID().toString();
        pendientes.put(token, new Pendiente(token, usuarioId, carga, archivo, datos, Instant.now(), new AtomicInteger()));
        return token;
    }

    public Optional<Pendiente> obtener(String token, long usuarioId, String carga) {
        limpiar();
        Pendiente p = token == null ? null : pendientes.get(token);
        return p != null && p.usuarioId() == usuarioId && p.carga().equals(carga) ? Optional.of(p) : Optional.empty();
    }

    /** Quita la revisión; true si todavía estaba (evita confirmar dos veces la misma). */
    public boolean quitar(String token) {
        return pendientes.remove(token) != null;
    }

    public void cancelar(long usuarioId, String carga) {
        pendientes.values().removeIf(p -> p.usuarioId() == usuarioId && p.carga().equals(carga));
    }

    private void limpiar() {
        Instant limite = Instant.now().minus(VIGENCIA);
        pendientes.values().removeIf(p -> p.creada().isBefore(limite));
    }
}
