package org.example.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Caché de similitudes entre nombres para un único análisis (no se comparte entre peticiones).
 *
 * <p>Cada nombre se normaliza una sola vez y recibe un identificador. Los pares entre clases del
 * modelo se guardan en una matriz {@code n × n}; los pares con nombres que solo aparecen en la
 * memoria van a un mapa. Cada par se calcula como mucho una vez.</p>
 */
final class NameSimilarityCache {

    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String> normalized = new ArrayList<>();
    private final int matrixSize;
    private final double[] matrix;
    private final Map<Long, Double> extra = new HashMap<>();

    /**
     * @param classNames nombres de las clases del modelo (ocupan la matriz)
     */
    NameSimilarityCache(List<String> classNames) {
        for (String name : classNames) {
            id(name);
        }
        this.matrixSize = ids.size();
        this.matrix = new double[matrixSize * matrixSize];
        Arrays.fill(matrix, Double.NaN);
    }

    /** @return identificador del nombre (lo registra si es nuevo) */
    int id(String name) {
        Integer existing = ids.get(name);
        if (existing != null) {
            return existing;
        }
        int id = normalized.size();
        ids.put(name, id);
        normalized.add(SemanticSimilarity.normalize(name));
        return id;
    }

    /** @return {@code SemanticSimilarity.compare(nombre a, nombre b)} calculado una sola vez */
    double sim(int a, int b) {
        if (a < matrixSize && b < matrixSize) {
            int idx = a * matrixSize + b;
            double v = matrix[idx];
            if (Double.isNaN(v)) {
                v = SemanticSimilarity.compareNormalized(normalized.get(a), normalized.get(b));
                matrix[idx] = v;
            }
            return v;
        }
        long key = ((long) a << 32) | (b & 0xFFFFFFFFL);
        Double v = extra.get(key);
        if (v == null) {
            v = SemanticSimilarity.compareNormalized(normalized.get(a), normalized.get(b));
            extra.put(key, v);
        }
        return v;
    }
}
