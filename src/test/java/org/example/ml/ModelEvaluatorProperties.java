package org.example.ml;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for ModelEvaluator.
 * Tests correctness properties defined in the design document.
 */
public class ModelEvaluatorProperties {

    private final ModelEvaluator evaluator = new ModelEvaluator();

    /**
     * **Feature: ml-relation-inference, Property 13: Evaluation metrics completeness**
     * 
     * For any model evaluation result, the EvaluationMetrics object SHALL contain 
     * non-null values for accuracy, precision, recall, and f1Score.
     * 
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 100)
    void evaluationMetricsCompleteness(
            @ForAll("validTrainingDataset") TrainingDataset dataset,
            @ForAll @IntRange(min = 2, max = 10) int folds) {
        
        // Ensure we have enough examples for the number of folds
        if (dataset.size() < folds) {
            return; // Skip this case - not enough data for requested folds
        }
        
        EvaluationMetrics metrics = evaluator.evaluate(dataset, folds);
        
        // All metrics should be non-null
        assert metrics.getAccuracy() != null : 
            "Accuracy should not be null for any evaluation result";
        assert metrics.getPrecision() != null : 
            "Precision should not be null for any evaluation result";
        assert metrics.getRecall() != null : 
            "Recall should not be null for any evaluation result";
        assert metrics.getF1Score() != null : 
            "F1Score should not be null for any evaluation result";
        
        // All metrics should be in valid range [0, 1]
        assert metrics.getAccuracy() >= 0.0 && metrics.getAccuracy() <= 1.0 :
            "Accuracy should be in range [0, 1], got " + metrics.getAccuracy();
        assert metrics.getPrecision() >= 0.0 && metrics.getPrecision() <= 1.0 :
            "Precision should be in range [0, 1], got " + metrics.getPrecision();
        assert metrics.getRecall() >= 0.0 && metrics.getRecall() <= 1.0 :
            "Recall should be in range [0, 1], got " + metrics.getRecall();
        assert metrics.getF1Score() >= 0.0 && metrics.getF1Score() <= 1.0 :
            "F1Score should be in range [0, 1], got " + metrics.getF1Score();
    }


    /**
     * Provides valid training datasets with balanced examples.
     * Generates datasets with at least 10 examples to ensure meaningful evaluation.
     */
    @Provide
    Arbitrary<TrainingDataset> validTrainingDataset() {
        // Generate feature vectors with 12 dimensions (matching FeatureExtractor)
        Arbitrary<double[]> featureVector = Arbitraries.doubles()
                .between(0.0, 1.0)
                .array(double[].class)
                .ofSize(12);
        
        // Generate labels (0.0 or 1.0)
        Arbitrary<Double> label = Arbitraries.of(0.0, 1.0);
        
        // Generate training examples
        Arbitrary<TrainingExample> example = Combinators.combine(featureVector, label)
                .as(TrainingExample::new);
        
        // Generate datasets with 10-50 examples for meaningful cross-validation
        return example.list().ofMinSize(10).ofMaxSize(50)
                .map(examples -> {
                    TrainingDataset dataset = new TrainingDataset(12);
                    for (TrainingExample ex : examples) {
                        dataset.addExample(ex);
                    }
                    return dataset;
                });
    }

    /**
     * Property: Evaluation handles empty dataset gracefully
     */
    @Property(tries = 1)
    void evaluationHandlesEmptyDataset() {
        TrainingDataset emptyDataset = new TrainingDataset(12);
        
        EvaluationMetrics metrics = evaluator.evaluate(emptyDataset, 5);
        
        // Should return metrics with zero values, not null
        assert metrics != null : "Metrics should not be null for empty dataset";
        assert metrics.getAccuracy() != null : "Accuracy should not be null";
        assert metrics.getPrecision() != null : "Precision should not be null";
        assert metrics.getRecall() != null : "Recall should not be null";
        assert metrics.getF1Score() != null : "F1Score should not be null";
    }

    /**
     * Property: Evaluation handles null dataset gracefully
     */
    @Property(tries = 1)
    void evaluationHandlesNullDataset() {
        EvaluationMetrics metrics = evaluator.evaluate(null, 5);
        
        // Should return metrics with zero values, not null
        assert metrics != null : "Metrics should not be null for null dataset";
        assert metrics.getAccuracy() != null : "Accuracy should not be null";
    }



    /**
     * Property: More folds doesn't change metric validity
     */
    @Property(tries = 50)
    void metricsValidWithDifferentFolds(
            @ForAll("validTrainingDataset") TrainingDataset dataset,
            @ForAll @IntRange(min = 2, max = 10) int folds) {
        
        if (dataset.size() < folds) {
            return;
        }
        
        EvaluationMetrics metrics = evaluator.evaluate(dataset, folds);
        
        // Metrics should always be valid regardless of fold count
        assert metrics.getAccuracy() != null && !Double.isNaN(metrics.getAccuracy()) :
            "Accuracy should be valid for " + folds + " folds";
        assert metrics.getPrecision() != null && !Double.isNaN(metrics.getPrecision()) :
            "Precision should be valid for " + folds + " folds";
        assert metrics.getRecall() != null && !Double.isNaN(metrics.getRecall()) :
            "Recall should be valid for " + folds + " folds";
        assert metrics.getF1Score() != null && !Double.isNaN(metrics.getF1Score()) :
            "F1Score should be valid for " + folds + " folds";
    }
}
