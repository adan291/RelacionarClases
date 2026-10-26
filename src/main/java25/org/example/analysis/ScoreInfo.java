package org.example.analysis;

import org.example.model.RelationKey;

/**
 * Puntuaciones de una relación por fuente, inmutable. Cada fuente crea una copia nueva.
 * Versión Java 25 (record); la del perfil valhalla está en {@code src/main/java28}.
 */
record ScoreInfo(RelationKey key, double mlScore, double structuralScore, double semanticScore) {

    static ScoreInfo empty(RelationKey key) {
        return new ScoreInfo(key, 0.0, 0.0, 0.0);
    }

    ScoreInfo withStructural(double score) {
        return new ScoreInfo(key, mlScore, score, semanticScore);
    }

    ScoreInfo withSemanticMax(double score) {
        return new ScoreInfo(key, mlScore, structuralScore, Math.max(semanticScore, score));
    }

    ScoreInfo withMlMax(double score) {
        return new ScoreInfo(key, Math.max(mlScore, score), structuralScore, semanticScore);
    }
}
