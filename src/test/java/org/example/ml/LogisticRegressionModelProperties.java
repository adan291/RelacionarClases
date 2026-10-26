package org.example.ml;

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Property-based tests for LogisticRegressionModel.
 * Tests correctness properties defined in the design document.
 */
public class LogisticRegressionModelProperties {

    /**
     * **Feature: ml-relation-inference, Property 10: Prediction range validity**
     * 
     * For any input feature vector, the LogisticRegressionModel.predict() method 
     * SHALL return a value in the range [0.0, 1.0] inclusive.
     * 
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    void predictionRangeValidity(
            @ForAll @Size(min = 1, max = 20) List<@DoubleRange(min = -1000.0, max = 1000.0) Double> featureList,
            @ForAll @Size(min = 2, max = 10) List<@DoubleRange(min = 0.0, max = 1.0) Double> labels) {
        
        int featureCount = featureList.size();
        
        // Create and train a model with random training data
        LogisticRegressionModel model = new LogisticRegressionModel(42L);
        TrainingDataset dataset = createTrainingDataset(featureCount, labels.size());
        model.train(dataset, 0.1, 100);
        
        // Convert feature list to array
        double[] features = featureList.stream().mapToDouble(Double::doubleValue).toArray();
        
        // Make prediction
        double prediction = model.predict(features);
        
        // Verify prediction is in valid range [0, 1]
        assert prediction >= 0.0 : 
            "Prediction should be >= 0.0, got " + prediction;
        assert prediction <= 1.0 : 
            "Prediction should be <= 1.0, got " + prediction;
        assert !Double.isNaN(prediction) : 
            "Prediction should not be NaN";
        assert !Double.isInfinite(prediction) : 
            "Prediction should not be infinite";
    }


    /**
     * **Feature: ml-relation-inference, Property 14: Output clamping for invalid values**
     * 
     * For any model output including NaN or infinite values, the final prediction 
     * SHALL be clamped to a valid value in [0.0, 1.0] (NaN → 0.5, +Inf → 1.0, -Inf → 0.0).
     * 
     * **Validates: Requirements 6.4**
     */
    @Property(tries = 100)
    void outputClampingForInvalidValues(
            @ForAll @IntRange(min = 1, max = 20) int featureCount,
            @ForAll("extremeFeatureValues") List<Double> extremeValues) {
        
        // Create and train a model
        LogisticRegressionModel model = new LogisticRegressionModel(42L);
        TrainingDataset dataset = createTrainingDataset(featureCount, 5);
        model.train(dataset, 0.1, 100);
        
        // Create feature array with extreme values that could cause NaN/Inf
        double[] features = new double[featureCount];
        for (int i = 0; i < featureCount; i++) {
            // Use extreme values from the generated list, cycling if needed
            features[i] = extremeValues.get(i % extremeValues.size());
        }
        
        // Make prediction - should handle extreme values gracefully
        double prediction = model.predict(features);
        
        // Verify output is clamped to valid range
        assert prediction >= 0.0 : 
            "Clamped prediction should be >= 0.0, got " + prediction;
        assert prediction <= 1.0 : 
            "Clamped prediction should be <= 1.0, got " + prediction;
        assert !Double.isNaN(prediction) : 
            "Clamped prediction should not be NaN";
        assert !Double.isInfinite(prediction) : 
            "Clamped prediction should not be infinite";
    }

    @Provide
    Arbitrary<List<Double>> extremeFeatureValues() {
        return Arbitraries.of(
            // Normal values
            0.0, 0.5, 1.0, -1.0,
            // Large values that could cause overflow
            1e10, -1e10, 1e100, -1e100,
            // Very small values
            1e-10, -1e-10,
            // Edge cases
            Double.MAX_VALUE, -Double.MAX_VALUE,
            Double.MIN_VALUE, -Double.MIN_VALUE
        ).list().ofMinSize(1).ofMaxSize(10);
    }

    /**
     * Additional property: Sigmoid function bounds
     * 
     * For any input value, sigmoid should return a value in [0, 1].
     * Note: Due to floating-point precision, sigmoid(x) can equal exactly 0.0 or 1.0
     * for extreme values of x (approximately |x| > 36).
     */
    @Property(tries = 100)
    void sigmoidFunctionBounds(@ForAll @DoubleRange(min = -1000.0, max = 1000.0) double input) {
        LogisticRegressionModel model = new LogisticRegressionModel();
        double result = model.sigmoid(input);
        
        // Sigmoid should always be in [0, 1] range
        assert result >= 0.0 : 
            "Sigmoid should be >= 0, got " + result;
        assert result <= 1.0 : 
            "Sigmoid should be <= 1, got " + result;
        assert !Double.isNaN(result) : 
            "Sigmoid should not return NaN";
        assert !Double.isInfinite(result) : 
            "Sigmoid should not return infinite";
        
        // For moderate inputs, sigmoid should be strictly between 0 and 1
        if (input > -30 && input < 30) {
            assert result > 0.0 : 
                "Sigmoid should be > 0 for moderate inputs, got " + result + " for input " + input;
            assert result < 1.0 : 
                "Sigmoid should be < 1 for moderate inputs, got " + result + " for input " + input;
        }
    }


    /**
     * Additional property: Model training produces valid weights
     * 
     * After training, the model should have weights that produce valid predictions.
     */
    @Property(tries = 100)
    void modelTrainingProducesValidWeights(
            @ForAll @IntRange(min = 1, max = 15) int featureCount,
            @ForAll @IntRange(min = 2, max = 20) int exampleCount,
            @ForAll @DoubleRange(min = 0.01, max = 1.0) double learningRate,
            @ForAll @IntRange(min = 10, max = 500) int iterations) {
        
        LogisticRegressionModel model = new LogisticRegressionModel(42L);
        TrainingDataset dataset = createTrainingDataset(featureCount, exampleCount);
        
        model.train(dataset, learningRate, iterations);
        
        // Verify model is trained
        assert model.isTrained() : "Model should be trained after training";
        
        // Verify weights are valid
        double[] weights = model.getWeights();
        assert weights != null : "Weights should not be null after training";
        assert weights.length == featureCount : 
            "Weights length should match feature count: expected " + featureCount + ", got " + weights.length;
        
        // Verify all weights are finite
        for (int i = 0; i < weights.length; i++) {
            assert !Double.isNaN(weights[i]) : "Weight " + i + " should not be NaN";
            assert !Double.isInfinite(weights[i]) : "Weight " + i + " should not be infinite";
        }
        
        // Verify bias is finite
        assert !Double.isNaN(model.getBias()) : "Bias should not be NaN";
        assert !Double.isInfinite(model.getBias()) : "Bias should not be infinite";
    }

    /**
     * Helper method to create a training dataset with random data.
     */
    private TrainingDataset createTrainingDataset(int featureCount, int exampleCount) {
        TrainingDataset dataset = new TrainingDataset(featureCount);
        java.util.Random random = new java.util.Random(42L);
        
        for (int i = 0; i < exampleCount; i++) {
            double[] features = new double[featureCount];
            for (int j = 0; j < featureCount; j++) {
                features[j] = random.nextDouble();
            }
            double label = random.nextBoolean() ? 1.0 : 0.0;
            dataset.addExample(features, label);
        }
        
        return dataset;
    }
}
