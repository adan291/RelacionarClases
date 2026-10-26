package org.example.ml;

/**
 * Mejor tipo de relación para un par de clases: índice del tipo y confianza.
 * Es un valor pequeño que se crea y descarta en el bucle de predicción.
 * Versión Java 25 (record); la del perfil valhalla está en {@code src/main/java28}.
 */
public record PairScore(int typeIndex, double confidence) {
}
