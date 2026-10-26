package org.example.ml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for ModelPersistence.
 * Tests correctness properties defined in the design document.
 */
public class ModelPersistenceProperties {

    /**
     * **Feature: ml-relation-inference, Property 9: Model persistence round-trip**
     * 
     * For any trained LogisticRegressionModel, saving to file and loading 
     * SHALL produce a model that generates identical predictions for the same input features.
     * 
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 100)
    void modelPersistenceRoundTrip(
            @ForAll @IntRange(min = 1, max = 20) int featureCount,
            @ForAll @IntRange(min = 2, max = 20) int exampleCount,
            @ForAll @DoubleRange(min = 0.01, max = 1.0) double learningRate,
            @ForAll @IntRange(min = 10, max = 200) int iterations) throws IOException {
        
        Path tempFile = null;
        try {
            // Create and train original model
            LogisticRegressionModel originalModel = new LogisticRegressionModel(42L);
            TrainingDataset dataset = createTrainingDataset(featureCount, exampleCount);
            originalModel.train(dataset, learningRate, iterations);
            
            // Save model to temp file
            tempFile = Files.createTempFile("model_test_", ".json");
            ModelPersistence persistence = new ModelPersistence();
            persistence.saveModel(originalModel, tempFile.toString());
            
            // Load model from file
            LogisticRegressionModel loadedModel = persistence.loadModel(tempFile.toString());
            
            // Verify loaded model is not null
            assert loadedModel != null : "Loaded model should not be null";
            
            // Verify loaded model is trained
            assert loadedModel.isTrained() : "Loaded model should be trained";
            
            // Verify weights are identical
            double[] originalWeights = originalModel.getWeights();
            double[] loadedWeights = loadedModel.getWeights();
            
            assert originalWeights.length == loadedWeights.length :
                "Weights length should match: original=" + originalWeights.length + 
                ", loaded=" + loadedWeights.length;
            
            for (int i = 0; i < originalWeights.length; i++) {
                assert originalWeights[i] == loadedWeights[i] :
                    "Weight " + i + " should match: original=" + originalWeights[i] + 
                    ", loaded=" + loadedWeights[i];
            }
            
            // Verify bias is identical
            assert originalModel.getBias() == loadedModel.getBias() :
                "Bias should match: original=" + originalModel.getBias() + 
                ", loaded=" + loadedModel.getBias();
            
            // Verify predictions are identical for random test inputs
            double[] testFeatures = createTestFeatures(featureCount);
            double originalPrediction = originalModel.predict(testFeatures);
            double loadedPrediction = loadedModel.predict(testFeatures);
            
            assert originalPrediction == loadedPrediction :
                "Predictions should be identical: original=" + originalPrediction + 
                ", loaded=" + loadedPrediction;
                
        } finally {
            // Clean up temp file
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }


    /**
     * Additional property: Invalid file returns null
     * 
     * Loading from a non-existent or corrupted file should return null.
     */
    @Property(tries = 50)
    void loadInvalidFileReturnsNull(@ForAll @IntRange(min = 1, max = 100) int randomSuffix) {
        ModelPersistence persistence = new ModelPersistence();
        
        // Non-existent file should return null
        String nonExistentPath = "non_existent_model_" + randomSuffix + ".json";
        LogisticRegressionModel model = persistence.loadModel(nonExistentPath);
        
        assert model == null : "Loading non-existent file should return null";
    }

    /**
     * Additional property: isModelValid returns false for invalid files
     */
    @Property(tries = 50)
    void isModelValidReturnsFalseForInvalidFiles(@ForAll @IntRange(min = 1, max = 100) int randomSuffix) {
        ModelPersistence persistence = new ModelPersistence();
        
        // Non-existent file should be invalid
        String nonExistentPath = "non_existent_model_" + randomSuffix + ".json";
        boolean isValid = persistence.isModelValid(nonExistentPath);
        
        assert !isValid : "Non-existent file should be invalid";
        
        // Null path should be invalid
        assert !persistence.isModelValid(null) : "Null path should be invalid";
        
        // Empty path should be invalid
        assert !persistence.isModelValid("") : "Empty path should be invalid";
    }

    /**
     * Additional property: Saved model file is valid
     */
    @Property(tries = 50)
    void savedModelFileIsValid(
            @ForAll @IntRange(min = 1, max = 15) int featureCount,
            @ForAll @IntRange(min = 2, max = 10) int exampleCount) throws IOException {
        
        Path tempFile = null;
        try {
            // Create and train model
            LogisticRegressionModel model = new LogisticRegressionModel(42L);
            TrainingDataset dataset = createTrainingDataset(featureCount, exampleCount);
            model.train(dataset, 0.1, 100);
            
            // Save model
            tempFile = Files.createTempFile("model_valid_test_", ".json");
            ModelPersistence persistence = new ModelPersistence();
            persistence.saveModel(model, tempFile.toString());
            
            // Verify file is valid
            assert persistence.isModelValid(tempFile.toString()) : 
                "Saved model file should be valid";
                
        } finally {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    /**
     * **Feature: enhanced-ml-features, Property 5: Backward Compatibility**
     * 
     * For any legacy 12-feature model, when used with the new 24-feature extractor,
     * the model SHALL produce valid predictions by padding missing features with 0.5 (neutral).
     * Old 12-feature model works with new extractor.
     * 
     * **Validates: Requirement 6.2**
     */
    @Property(tries = 100)
    void backwardCompatibilityLegacyModelWithNewFeatures(
            @ForAll @IntRange(min = 2, max = 20) int exampleCount,
            @ForAll @DoubleRange(min = 0.01, max = 1.0) double learningRate,
            @ForAll @IntRange(min = 10, max = 200) int iterations) throws IOException {
        
        Path tempFile = null;
        try {
            // Create and train a LEGACY 12-feature model
            LogisticRegressionModel legacyModel = new LogisticRegressionModel(42L);
            TrainingDataset legacyDataset = createTrainingDataset(
                FeatureExtractor.LEGACY_FEATURE_COUNT, exampleCount);
            legacyModel.train(legacyDataset, learningRate, iterations);
            
            // Verify it's a legacy model
            assert legacyModel.getFeatureCount() == FeatureExtractor.LEGACY_FEATURE_COUNT :
                "Legacy model should have 12 features, got " + legacyModel.getFeatureCount();
            
            // Save the legacy model
            tempFile = Files.createTempFile("legacy_model_test_", ".json");
            ModelPersistence persistence = new ModelPersistence();
            persistence.saveModel(legacyModel, tempFile.toString());
            
            // Load the legacy model
            LogisticRegressionModel loadedModel = persistence.loadModel(tempFile.toString());
            assert loadedModel != null : "Loaded model should not be null";
            assert loadedModel.getFeatureCount() == FeatureExtractor.LEGACY_FEATURE_COUNT :
                "Loaded model should have 12 features";
            
            // Create a NEW 24-feature vector (as would be produced by new FeatureExtractor)
            double[] newFeatures = createTestFeatures(FeatureExtractor.FEATURE_COUNT);
            
            // The legacy model should still be able to make predictions with 24 features
            // due to feature vector adjustment (padding/truncation)
            double prediction = loadedModel.predict(newFeatures);
            
            // Verify prediction is valid
            assert prediction >= 0.0 && prediction <= 1.0 :
                "Prediction should be in [0, 1], got " + prediction;
            assert !Double.isNaN(prediction) :
                "Prediction should not be NaN";
            assert !Double.isInfinite(prediction) :
                "Prediction should not be infinite";
            
            // Also verify that legacy features still work correctly
            double[] legacyFeatures = createTestFeatures(FeatureExtractor.LEGACY_FEATURE_COUNT);
            double legacyPrediction = loadedModel.predict(legacyFeatures);
            
            assert legacyPrediction >= 0.0 && legacyPrediction <= 1.0 :
                "Legacy prediction should be in [0, 1], got " + legacyPrediction;
                
        } finally {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }
    
    /**
     * **Feature: enhanced-ml-features, Property 5: Backward Compatibility (Feature Padding)**
     * 
     * For any model with fewer features than the input vector, the model SHALL
     * truncate the input to match its expected feature count and produce valid predictions.
     * 
     * **Validates: Requirement 6.2**
     */
    @Property(tries = 100)
    void featurePaddingProducesValidPredictions(
            @ForAll @IntRange(min = 1, max = 12) int modelFeatureCount,
            @ForAll @IntRange(min = 13, max = 30) int inputFeatureCount,
            @ForAll @IntRange(min = 2, max = 10) int exampleCount) {
        
        // Create and train a model with fewer features
        LogisticRegressionModel model = new LogisticRegressionModel(42L);
        TrainingDataset dataset = createTrainingDataset(modelFeatureCount, exampleCount);
        model.train(dataset, 0.1, 100);
        
        // Create input with more features than the model expects
        double[] inputFeatures = createTestFeatures(inputFeatureCount);
        
        // Model should handle the mismatch gracefully
        double prediction = model.predict(inputFeatures);
        
        // Verify prediction is valid
        assert prediction >= 0.0 && prediction <= 1.0 :
            "Prediction should be in [0, 1], got " + prediction;
        assert !Double.isNaN(prediction) :
            "Prediction should not be NaN";
        assert !Double.isInfinite(prediction) :
            "Prediction should not be infinite";
    }
    
    /**
     * **Feature: enhanced-ml-features, Property 5: Backward Compatibility (Feature Schema Version)**
     * 
     * For any saved model, the feature schema version SHALL be correctly stored and retrievable.
     * 
     * **Validates: Requirement 6.2**
     */
    @Property(tries = 50)
    void featureSchemaVersionIsPersisted(
            @ForAll @IntRange(min = 2, max = 10) int exampleCount) throws IOException {
        
        Path tempFile = null;
        try {
            // Create and train a model with current feature count
            LogisticRegressionModel model = new LogisticRegressionModel(42L);
            TrainingDataset dataset = createTrainingDataset(
                FeatureExtractor.FEATURE_COUNT, exampleCount);
            model.train(dataset, 0.1, 100);
            
            // Save the model
            tempFile = Files.createTempFile("schema_version_test_", ".json");
            ModelPersistence persistence = new ModelPersistence();
            persistence.saveModel(model, tempFile.toString());
            
            // Verify schema version is correctly stored
            int schemaVersion = persistence.getFeatureSchemaVersion(tempFile.toString());
            assert schemaVersion == FeatureExtractor.FEATURE_COUNT :
                "Schema version should be " + FeatureExtractor.FEATURE_COUNT + 
                ", got " + schemaVersion;
            
            // Verify isCurrentSchemaModel returns true
            assert persistence.isCurrentSchemaModel(tempFile.toString()) :
                "Model should be identified as current schema";
            
            // Verify isLegacyModel returns false
            assert !persistence.isLegacyModel(tempFile.toString()) :
                "Model should not be identified as legacy";
                
        } finally {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
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

    /**
     * Helper method to create test features.
     */
    private double[] createTestFeatures(int featureCount) {
        double[] features = new double[featureCount];
        java.util.Random random = new java.util.Random(123L);
        for (int i = 0; i < featureCount; i++) {
            features[i] = random.nextDouble();
        }
        return features;
    }
}
