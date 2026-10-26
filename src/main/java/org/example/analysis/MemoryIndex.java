package org.example.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.model.RelationKey;

/**
 * Índice de las decisiones de la memoria para un análisis.
 *
 * <p>Cada clave {@code "A -> B : Tipo"} se parsea una sola vez (antes se hacía un {@code split}
 * con expresión regular por cada par relación × decisión). Las entradas se agrupan por origen y
 * conservan el orden de la memoria, así que la suma de ajustes se hace en el mismo orden que antes.</p>
 */
final class MemoryIndex {

    /** Entradas en el orden de la memoria. */
    private final List<Entry> entries = new ArrayList<>();
    /** origen (tal y como aparece en la clave) → índices de sus entradas, en orden. */
    private final Map<String, List<Integer>> byOrigin = new LinkedHashMap<>();

    record Entry(RelationKey key, int originId, int destinoId, boolean accepted, boolean rejected) {
    }

    private MemoryIndex() {
    }

    static MemoryIndex build(Map<String, String> memoria, NameSimilarityCache names) {
        MemoryIndex index = new MemoryIndex();
        for (Map.Entry<String, String> e : memoria.entrySet()) {
            RelationKey key = parse(e.getKey());
            if (key == null) {
                continue;
            }
            boolean accepted = "aceptada".equals(e.getValue());
            boolean rejected = "rechazada".equals(e.getValue());
            if (!accepted && !rejected) {
                continue; // no modifica la confianza
            }
            index.byOrigin.computeIfAbsent(key.origen(), k -> new ArrayList<>()).add(index.entries.size());
            index.entries.add(new Entry(key, names.id(key.origen()), names.id(key.destino()), accepted, rejected));
        }
        return index;
    }

    static RelationKey parse(String texto) {
        return MemoryKeys.parse(texto);
    }

    Entry entry(int i) {
        return entries.get(i);
    }

    /**
     * Entradas que pueden superar el umbral para un origen dado, en el orden de la memoria.
     * Una entrada solo puede pasar si {@code (simOrigen + 1 + 1) / 3 > umbral}: el resto se descarta
     * sin calcular la similitud del destino.
     */
    int[] candidates(int relationOriginId, NameSimilarityCache names, double threshold) {
        List<Integer> selected = new ArrayList<>();
        for (List<Integer> group : byOrigin.values()) {
            Entry first = entries.get(group.get(0));
            double simOrigen = names.sim(relationOriginId, first.originId());
            if ((simOrigen + 1.0 + 1.0) / 3.0 > threshold) {
                selected.addAll(group);
            }
        }
        int[] result = new int[selected.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = selected.get(i);
        }
        Arrays.sort(result);
        return result;
    }

    int size() {
        return entries.size();
    }
}
