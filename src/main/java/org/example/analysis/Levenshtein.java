package org.example.analysis;

/**
 * Distancia de Levenshtein con dos filas (memoria O(min(n, m))). Es la única implementación
 * del proyecto; la usan {@link SemanticSimilarity} y {@code FeatureExtractor}.
 */
public final class Levenshtein {

    private Levenshtein() {
    }

    /**
     * @return número mínimo de inserciones, borrados y sustituciones para pasar de a a b
     */
    public static int distance(CharSequence a, CharSequence b) {
        // La fila es la cadena corta: menos memoria y el resultado es simétrico
        if (a.length() < b.length()) {
            CharSequence t = a;
            a = b;
            b = t;
        }
        int m = b.length();
        if (m == 0) {
            return a.length();
        }
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = curr;
            curr = t;
        }
        return prev[m];
    }
}
