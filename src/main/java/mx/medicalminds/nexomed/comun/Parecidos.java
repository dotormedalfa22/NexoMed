package mx.medicalminds.nexomed.comun;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Nombres iguales o parecidos (regla de las cargas de catálogos de SIALSS).
 * <ul>
 * <li>Se compara sin acentos, en mayúsculas, solo letras y números, y sin el régimen de capital al final
 * ("S.A. de C.V.", "S de RL de CV", "SAPI", "SC", "AC"…).</li>
 * <li><b>Igual</b>: coinciden así normalizados, con o sin espacios.</li>
 * <li><b>Parecido</b>: con los mismos números, a una letra de distancia (de 4 a 19 letras) o al 10 % (20 o más).
 * Con menos de 4 letras solo cuenta igual.</li>
 * </ul>
 */
public final class Parecidos {

    private Parecidos() {
    }

    private static final Set<String> SOCIEDAD = Set.of(
            "S", "A", "DE", "C", "V", "R", "L", "P", "I", "U", "SA", "CV", "RL", "SAPI", "SAB", "SC", "AC", "SRL",
            "SPR", "SAS", "MI", "RI", "SOCIEDAD", "ANONIMA", "CAPITAL", "VARIABLE", "RESPONSABILIDAD", "LIMITADA",
            "CIVIL", "ASOCIACION");

    /** Nombre preparado una vez para compararlo contra muchos. */
    public record Nombre(String original, String normalizado, String compacto, String numeros, int[] letras) {
    }

    /** Sin acentos, mayúsculas, solo letras y números separados por un espacio. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Z0-9]+", " ")
                .trim();
    }

    public static Nombre preparar(String texto) {
        String[] t = normalizar(texto).split(" ");
        int fin = t.length;
        while (fin > 1 && SOCIEDAD.contains(t[fin - 1])) {
            fin--;
        }
        String n = String.join(" ", Arrays.copyOf(t, fin));
        String compacto = n.replace(" ", "");
        int[] letras = new int[36];
        for (char c : compacto.toCharArray()) {
            letras[c <= '9' ? c - '0' + 26 : c - 'A']++;
        }
        return new Nombre(texto, n, compacto, compacto.replaceAll("[^0-9]+", " ").trim(), letras);
    }

    /** "igual", "parecida" o null. */
    public static String comparar(Nombre a, Nombre b) {
        if (a.compacto().isEmpty() || b.compacto().isEmpty()) {
            return null;
        }
        if (a.normalizado().equals(b.normalizado()) || a.compacto().equals(b.compacto())) {
            return "igual";
        }
        int corto = Math.min(a.compacto().length(), b.compacto().length());
        int largo = Math.max(a.compacto().length(), b.compacto().length());
        if (corto < 4 || !a.numeros().equals(b.numeros())) {
            return null;
        }
        int limite = largo < 20 ? 1 : largo / 10;
        if (largo - corto > limite) {
            return null;
        }
        // Cota inferior barata: cada cambio mueve a lo más dos conteos de letras.
        int dif = 0;
        for (int i = 0; i < 36; i++) {
            dif += Math.abs(a.letras()[i] - b.letras()[i]);
        }
        if ((dif + 1) / 2 > limite) {
            return null;
        }
        return distancia(a.compacto(), b.compacto()) <= limite ? "parecida" : null;
    }

    /** Distancia de Levenshtein. */
    static int distancia(String x, String y) {
        int[] previo = new int[y.length() + 1];
        int[] actual = new int[y.length() + 1];
        for (int j = 0; j <= y.length(); j++) {
            previo[j] = j;
        }
        for (int i = 1; i <= x.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= y.length(); j++) {
                int costo = x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, previo[j] + 1), previo[j - 1] + costo);
            }
            int[] t = previo;
            previo = actual;
            actual = t;
        }
        return previo[y.length()];
    }
}
