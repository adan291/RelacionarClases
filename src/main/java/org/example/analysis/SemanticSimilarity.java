package org.example.analysis;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import java.util.Map;

/**
 * Combina similitud léxica (Jaro-Winkler) y semántica (sinónimos, Levenshtein).
 * Devuelve una puntuación entre 0 y 1.
 */
public class SemanticSimilarity {

    private static final JaroWinklerSimilarity jw = new JaroWinklerSimilarity();

    // Diccionario de sinónimos básicos
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("oficina", "sucursal"),
            Map.entry("empleado", "trabajador"),
            Map.entry("empresa", "compañia"),
            Map.entry("ciudad", "localidad"),
            Map.entry("cliente", "usuario")
    );

    public static double compare(String a, String b) {
        if (a == null || b == null) return 0.0;
        a = normalize(a);
        b = normalize(b);

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
        double lev = 1.0 - (levenshteinDistance(a, b) / (double) Math.max(a.length(), b.length()));

        // Combinar pesos (70% Jaro-Winkler + 30% Levenshtein)
        return (lexical * 0.7) + (lev * 0.3);
    }

    private static String normalize(String text) {
        return text.replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[^a-zA-Z]", "")
                .toLowerCase();
    }

    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}
