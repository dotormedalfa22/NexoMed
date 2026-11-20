package mx.medicalminds.nexomed.seguridad;

/** Reglas de contraseñas y PIN, compartidas por las validaciones de la API. */
public final class Reglas {

    private Reglas() {
    }

    /** 12+ caracteres con mayúscula, minúscula y número. */
    public static final String CONTRASENA = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{12,}$";
    public static final String CONTRASENA_MENSAJE = "debe tener 12 o más caracteres, con mayúscula, minúscula y número";

    /** PIN de 4 a 6 dígitos. */
    public static final String PIN = "^\\d{4,6}$";
    public static final String PIN_MENSAJE = "debe tener de 4 a 6 dígitos";

    /** PIN fácil de adivinar: todos iguales (1111) o consecutivos (1234, 9876). */
    public static boolean pinDebil(String pin) {
        boolean iguales = true;
        boolean sube = true;
        boolean baja = true;
        for (int i = 1; i < pin.length(); i++) {
            int d = pin.charAt(i) - pin.charAt(i - 1);
            iguales &= d == 0;
            sube &= d == 1;
            baja &= d == -1;
        }
        return iguales || sube || baja;
    }
}
