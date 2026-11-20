package mx.medicalminds.nexomed.comun;

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
}
