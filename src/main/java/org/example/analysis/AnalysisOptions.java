package org.example.analysis;

/**
 * Opciones de un análisis en memoria.
 *
 * @param useMemory aplicar el ajuste de confianza por las decisiones guardadas
 * @param useMl usar las predicciones del modelo ML (si hay datos suficientes)
 */
public record AnalysisOptions(boolean useMemory, boolean useMl) {

    /** Memoria y ML activos, como en la UI. */
    public static final AnalysisOptions DEFAULT = new AnalysisOptions(true, true);
}
