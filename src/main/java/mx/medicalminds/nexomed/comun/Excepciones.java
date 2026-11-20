package mx.medicalminds.nexomed.comun;

import java.util.Map;

/** Excepciones de negocio. El manejador global las traduce a ProblemDetail (RFC 9457). */
public final class Excepciones {

    private Excepciones() {
    }

    /** 404 */
    public static class NoEncontrado extends RuntimeException {
        public NoEncontrado(String recurso, Object id) {
            super(recurso + " " + id + " no existe");
        }
    }

    /** 422: la petición es válida en forma pero viola una regla de negocio. */
    public static class ReglaNegocio extends RuntimeException {
        public ReglaNegocio(String mensaje) {
            super(mensaje);
        }
    }

    /**
     * Respuesta con un estado HTTP propio y datos adicionales para la pantalla
     * (por ejemplo errores por campo, "parecido" o "cancelada").
     */
    public static class ConDatos extends RuntimeException {
        private final int estado;
        private final transient Map<String, Object> datos;

        public ConDatos(int estado, String mensaje, Map<String, Object> datos) {
            super(mensaje);
            this.estado = estado;
            this.datos = datos == null ? Map.of() : datos;
        }

        public int getEstado() {
            return estado;
        }

        public Map<String, Object> getDatos() {
            return datos;
        }
    }
}
