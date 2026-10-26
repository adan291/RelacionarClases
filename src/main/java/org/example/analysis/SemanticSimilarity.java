package org.example.analysis;

import java.util.Locale;
import java.util.Map;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

/**
 * Combina similitud léxica (Jaro-Winkler) y semántica (sinónimos, Levenshtein).
 * Devuelve una puntuación entre 0 y 1.
 *
 * <p>Para comparar muchos pares, normaliza cada nombre una vez con {@link #normalize(String)} y
 * usa {@link #compareNormalized(String, String)}: el resultado es el mismo que {@link #compare}.</p>
 */
public final class SemanticSimilarity {

    private static final JaroWinklerSimilarity jw = new JaroWinklerSimilarity();

    // Diccionario de sinónimos básicos
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("oficina", "sucursal"),
            Map.entry("empleado", "trabajador"),
            Map.entry("empresa", "compañia"),
            Map.entry("ciudad", "localidad"),
            Map.entry("cliente", "usuario")
    );

    private SemanticSimilarity() {
    }

    public static double compare(String a, String b) {
        if (a == null || b == null) return 0.0;
        return compareNormalized(normalize(a), normalize(b));
    }

    /**
     * Igual que {@link #compare(String, String)} pero con los nombres ya normalizados.
     */
    public static double compareNormalized(String a, String b) {
        // 1️⃣ Igualdad exacta
        if (a.equals(b)) return 1.0;

        // 2️⃣ Sinónimos conocidos
        if (SYNONYMS.getOrDefault(a, "").equals(b) || SYNONYMS.getOrDefault(b, "").equals(a)) {
            return 0.9;
        }

        // 3️⃣ Coincidencia parcial (subcadena)
        if (a.contains(b) || b.contains(a)) return 0.8;

        // 4️⃣ Similaridad léxica (Jaro-Winkler)
        double lexical = jw.apply(a, b);

        // 5️⃣ Ajuste por distancia de Levenshtein
        double lev = 1.0 - (Levenshtein.distance(a, b) / (double) Math.max(a.length(), b.length()));

        // Combinar pesos (70% Jaro-Winkler + 30% Levenshtein)
        return (lexical * 0.7) + (lev * 0.3);
    }

    /**
     * Deja solo las letras ASCII y las pasa a minúsculas.
     *
     * <p>Equivale a la versión anterior con expresiones regulares
     * ({@code replaceAll("([a-z])([A-Z])", "$1_$2").replaceAll("[^a-zA-Z]", "").toLowerCase()}):
     * el '_' que insertaba el primer reemplazo lo borraba el segundo.</p>
     */
    public static String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
