package org.example.analysis;

import org.example.model.RelationKey;

/**
 * Puntuaciones de una relación por fuente, inmutable.
 * Versión Valhalla (value record, perfil {@code valhalla}).
 */
value record ScoreInfo(RelationKey key, double mlScore, double structuralScore, double semanticScore) {

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
