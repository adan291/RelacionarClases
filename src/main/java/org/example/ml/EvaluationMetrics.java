package org.example.ml;

import java.util.Objects;

/**
 * Holds evaluation metrics for the ML model.
 * Contains accuracy, precision, recall, and F1-score.
 */
public class EvaluationMetrics {
    
    private final Double accuracy;
    private final Double precision;
    private final Double recall;
    private final Double f1Score;
    
    /**
     * Creates evaluation metrics with all values.
     * 
     * @param accuracy the accuracy (correct predictions / total predictions)
     * @param precision the precision (true positives / predicted positives)
     * @param recall the recall (true positives / actual positives)
     * @param f1Score the F1 score (harmonic mean of precision and recall)
     */
    public EvaluationMetrics(Double accuracy, Double precision, Double recall, Double f1Score) {
        this.accuracy = accuracy;
        this.precision = precision;
        this.recall = recall;
        this.f1Score = f1Score;
    }
    
    public Double getAccuracy() {
        return accuracy;
    }
    
    public Double getPrecision() {
        return precision;
    }
    
    public Double getRecall() {
        return recall;
    }
    
    public Double getF1Score() {
        return f1Score;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvaluationMetrics that = (EvaluationMetrics) o;
        return Objects.equals(accuracy, that.accuracy) &&
               Objects.equals(precision, that.precision) &&
               Objects.equals(recall, that.recall) &&
               Objects.equals(f1Score, that.f1Score);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(accuracy, precision, recall, f1Score);
    }
    
    @Override
    public String toString() {
        return String.format("EvaluationMetrics{accuracy=%.4f, precision=%.4f, recall=%.4f, f1Score=%.4f}",
                accuracy != null ? accuracy : 0.0,
                precision != null ? precision : 0.0,
                recall != null ? recall : 0.0,
                f1Score != null ? f1Score : 0.0);
    }
}
