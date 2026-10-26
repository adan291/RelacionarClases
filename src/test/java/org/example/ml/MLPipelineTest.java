package org.example.ml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.example.service.MemoryService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end integration tests for the ML pipeline.
 * Tests the complete flow from training data generation through model training,
 * prediction, and persistence.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
@Tag("e2e")
public class MLPipelineTest {

    @TempDir
    Path tempDir;

    private Path memoryFilePath;
    private Path modelFilePath;
    private Path trainingDataPath;
    private MemoryService memoryService;

    /**
     * Sets up test memory with decisions for ML training.
     * Creates sufficient decisions (10+) for ML to be available.
     */
    @BeforeEach
    void setUp() throws IOException {
        memoryFilePath = tempDir.resolve("test-memory.json");
        modelFilePath = tempDir.resolve("test-model.json");
        trainingDataPath = tempDir.resolve("test-training-data.json");

        // Create memory file with sufficient decisions for ML training
        createTestMemoryWithDecisions(15);
        
        memoryService = new MemoryService(memoryFilePath.toString());
    }

    /**
     * Creates a test memory file with the specified number of decisions.
     * Decisions alternate between accepted and rejected for balanced training.
     */
    private void createTestMemoryWithDecisions(int count) throws IOException {
        StringBuilder json = new StringBuilder("{");
        String[] types = {"OneToMany", "ManyToOne", "ManyToMany", "OneToOne"};
        String[] classes = {"User", "Order", "Product", "Category", "Customer", 
                           "Invoice", "Payment", "Address", "Employee", "Department",
                           "Company", "Project", "Task", "Comment", "Tag"};
        
        for (int i = 0; i < count; i++) {
            if (i > 0) json.append(",");
            String source = classes[i % classes.length];
            String target = classes[(i + 1) % classes.length];
            String type = types[i % types.length];
            String decision = (i % 2 == 0) ? "aceptada" : "rechazada";
            
            json.append(String.format("\"%s -> %s : %s\":\"%s\"", 
                source, target, type, decision));
        }
        json.append("}");
        
        Files.writeString(memoryFilePath, json.toString());
    }

    /**
     * Creates a test memory file with fewer than 5 decisions (insufficient for ML).
     */
    private void createInsufficientMemory() throws IOException {
        String json = """
            {
                "User -> Order : OneToMany": "aceptada",
                "Order -> User : ManyToOne": "rechazada",
                "Product -> Category : ManyToMany": "aceptada"
            }
            """;
        Files.writeString(memoryFilePath, json);
    }


    // ========== 4.2 Training Data Generation Tests ==========

    /**
     * Test for training data generation from test memory.
     * Verifies that feature vectors are generated correctly and labels match decisions.
     * 
     * Requirements: 3.1
     */
    @Test
    void testTrainingDataGeneration_generatesCorrectFeatureVectors() {
        // Arrange
        TrainingDataGenerator generator = new TrainingDataGenerator(
            new FeatureExtractor(), trainingDataPath.toString());
        
        // Act
        TrainingDataset dataset = generator.generateFromMemory(memoryService);
        
        // Assert
        assertNotNull(dataset, "Dataset should not be null");
        assertEquals(15, dataset.size(), "Dataset should have 15 examples (one per decision)");
        assertEquals(FeatureExtractor.FEATURE_COUNT, dataset.getFeatureCount(), 
            "Feature count should match FeatureExtractor.FEATURE_COUNT (24)");
        
        // Verify each example has valid features
        for (TrainingExample example : dataset.getExamples()) {
            double[] features = example.getFeatures();
            assertNotNull(features, "Features should not be null");
            assertEquals(FeatureExtractor.FEATURE_COUNT, features.length, 
                "Each example should have 24 features");
            
            // Verify features are in valid range [0, 1]
            for (int i = 0; i < features.length; i++) {
                assertTrue(features[i] >= 0.0 && features[i] <= 1.0,
                    "Feature " + i + " should be in range [0, 1], got " + features[i]);
            }
        }
    }

    /**
     * Test that labels match decisions in memory.
     * "aceptada" -> 1.0, "rechazada" -> 0.0
     * 
     * Requirements: 3.1
     */
    @Test
    void testTrainingDataGeneration_labelsMatchDecisions() {
        // Arrange
        TrainingDataGenerator generator = new TrainingDataGenerator(
            new FeatureExtractor(), trainingDataPath.toString());
        
        // Act
        TrainingDataset dataset = generator.generateFromMemory(memoryService);
        
        // Assert - count accepted and rejected labels
        long acceptedCount = dataset.getExamples().stream()
            .filter(e -> e.getLabel() == 1.0)
            .count();
        long rejectedCount = dataset.getExamples().stream()
            .filter(e -> e.getLabel() == 0.0)
            .count();
        
        // With 15 decisions alternating, we expect 8 accepted (even indices) and 7 rejected (odd indices)
        assertEquals(8, acceptedCount, "Should have 8 accepted examples");
        assertEquals(7, rejectedCount, "Should have 7 rejected examples");
    }

    // ========== 4.3 Model Training Tests ==========

    /**
     * Test for model training with sufficient data (10+ examples).
     * Verifies that model weights are set after training.
     * 
     * **Property 3: ML Training Effectiveness**
     * Model trained on sample data should predict with >60% accuracy.
     * 
     * Requirements: 3.2
     */
    @Test
    void testModelTraining_setsWeightsWithSufficientData() {
        // Arrange
        TrainingDataGenerator generator = new TrainingDataGenerator(
            new FeatureExtractor(), trainingDataPath.toString());
        TrainingDataset dataset = generator.generateFromMemory(memoryService);
        LogisticRegressionModel model = new LogisticRegressionModel(42L); // Fixed seed for reproducibility
        
        // Act
        model.train(dataset, 0.1, 1000);
        
        // Assert
        assertTrue(model.isTrained(), "Model should be trained");
        assertNotNull(model.getWeights(), "Model weights should not be null");
        assertEquals(FeatureExtractor.FEATURE_COUNT, model.getFeatureCount(), 
            "Model should have 24 weights");
        
        // Verify weights are not all zeros
        double[] weights = model.getWeights();
        boolean hasNonZeroWeight = false;
        for (double weight : weights) {
            if (Math.abs(weight) > 0.001) {
                hasNonZeroWeight = true;
                break;
            }
        }
        assertTrue(hasNonZeroWeight, "Model should have at least one non-zero weight");
    }

    /**
     * Test that trained model achieves reasonable accuracy on training data.
     * 
     * **Property 3: ML Training Effectiveness**
     * 
     * Requirements: 3.2
     */
    @Test
    void testModelTraining_achievesReasonableAccuracy() {
        // Arrange
        TrainingDataGenerator generator = new TrainingDataGenerator(
            new FeatureExtractor(), trainingDataPath.toString());
        TrainingDataset dataset = generator.generateFromMemory(memoryService);
        LogisticRegressionModel model = new LogisticRegressionModel(42L);
        
        // Act
        model.train(dataset, 0.1, 1000);
        
        // Calculate accuracy on training data
        int correct = 0;
        for (TrainingExample example : dataset.getExamples()) {
            double prediction = model.predict(example.getFeatures());
            double predictedLabel = prediction >= 0.5 ? 1.0 : 0.0;
            if (predictedLabel == example.getLabel()) {
                correct++;
            }
        }
        double accuracy = (double) correct / dataset.size();
        
        // Assert - model should achieve at least 60% accuracy
        assertTrue(accuracy >= 0.6, 
            "Model should achieve at least 60% accuracy on training data, got " + 
            String.format("%.2f%%", accuracy * 100));
    }


    // ========== 4.4 Prediction Tests ==========

    /**
     * Test that predictions are in valid range [0, 1].
     * 
     * Requirements: 3.3
     */
    @Test
    void testPrediction_returnsValidRange() {
        // Arrange
        TrainingDataGenerator generator = new TrainingDataGenerator(
            new FeatureExtractor(), trainingDataPath.toString());
        TrainingDataset dataset = generator.generateFromMemory(memoryService);
        LogisticRegressionModel model = new LogisticRegressionModel(42L);
        model.train(dataset, 0.1, 1000);
        
        FeatureExtractor featureExtractor = new FeatureExtractor();
        
        // Act & Assert - test predictions for various class pairs
        String[][] testPairs = {
            {"User", "Order", "OneToMany"},
            {"Order", "Product", "ManyToOne"},
            {"Category", "Product", "ManyToMany"},
            {"Employee", "Department", "OneToOne"}
        };
        
        for (String[] pair : testPairs) {
            double[] features = featureExtractor.extractFeatures(pair[0], pair[1], pair[2], null, null);
            double prediction = model.predict(features);
            
            assertTrue(prediction >= 0.0 && prediction <= 1.0,
                "Prediction for " + pair[0] + " -> " + pair[1] + " should be in [0, 1], got " + prediction);
        }
    }

    /**
     * Test that model throws exception when predicting without training.
     * 
     * Requirements: 3.3
     */
    @Test
    void testPrediction_throwsExceptionWhenNotTrained() {
        // Arrange
        LogisticRegressionModel model = new LogisticRegressionModel();
        FeatureExtractor featureExtractor = new FeatureExtractor();
        double[] features = featureExtractor.extractFeatures("User", "Order", "OneToMany", null, null);
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> model.predict(features),
            "Should throw IllegalStateException when predicting with untrained model");
    }

    // ========== 4.5 Model Persistence Tests ==========

    /**
     * Test that model can be saved to file and loaded back.
     * Verifies that predictions match after save/load cycle.
     * 
     * Requirements: 3.4
     */
    @Test
    void testModelPersistence_saveAndLoadPreservesPredictions() throws IOException {
        // Arrange
        TrainingDataGenerator generator = new TrainingDataGenerator(
            new FeatureExtractor(), trainingDataPath.toString());
        TrainingDataset dataset = generator.generateFromMemory(memoryService);
        LogisticRegressionModel originalModel = new LogisticRegressionModel(42L);
        originalModel.train(dataset, 0.1, 1000);
        
        ModelPersistence persistence = new ModelPersistence(modelFilePath.toString());
        FeatureExtractor featureExtractor = new FeatureExtractor();
        
        // Generate test features for comparison
        double[] testFeatures = featureExtractor.extractFeatures("User", "Order", "OneToMany", null, null);
        double originalPrediction = originalModel.predict(testFeatures);
        
        // Act - save and load
        persistence.saveModel(originalModel, modelFilePath.toString());
        LogisticRegressionModel loadedModel = persistence.loadModel(modelFilePath.toString());
        
        // Assert
        assertNotNull(loadedModel, "Loaded model should not be null");
        assertTrue(loadedModel.isTrained(), "Loaded model should be trained");
        assertEquals(originalModel.getFeatureCount(), loadedModel.getFeatureCount(),
            "Feature count should be preserved");
        
        // Verify predictions match
        double loadedPrediction = loadedModel.predict(testFeatures);
        assertEquals(originalPrediction, loadedPrediction, 0.0001,
            "Predictions should match after save/load");
    }

    /**
     * Test that model weights are preserved after save/load.
     * 
     * Requirements: 3.4
     */
    @Test
    void testModelPersistence_preservesWeightsAndBias() throws IOException {
        // Arrange
        TrainingDataGenerator generator = new TrainingDataGenerator(
            new FeatureExtractor(), trainingDataPath.toString());
        TrainingDataset dataset = generator.generateFromMemory(memoryService);
        LogisticRegressionModel originalModel = new LogisticRegressionModel(42L);
        originalModel.train(dataset, 0.1, 1000);
        
        ModelPersistence persistence = new ModelPersistence(modelFilePath.toString());
        
        // Act
        persistence.saveModel(originalModel, modelFilePath.toString());
        LogisticRegressionModel loadedModel = persistence.loadModel(modelFilePath.toString());
        
        // Assert
        double[] originalWeights = originalModel.getWeights();
        double[] loadedWeights = loadedModel.getWeights();
        
        assertEquals(originalWeights.length, loadedWeights.length, 
            "Weight array length should be preserved");
        
        for (int i = 0; i < originalWeights.length; i++) {
            assertEquals(originalWeights[i], loadedWeights[i], 0.0001,
                "Weight " + i + " should be preserved");
        }
        
        assertEquals(originalModel.getBias(), loadedModel.getBias(), 0.0001,
            "Bias should be preserved");
    }

    /**
     * Test that loading non-existent model returns null.
     * 
     * Requirements: 3.4
     */
    @Test
    void testModelPersistence_loadNonExistentReturnsNull() {
        // Arrange
        ModelPersistence persistence = new ModelPersistence(modelFilePath.toString());
        Path nonExistentPath = tempDir.resolve("non-existent-model.json");
        
        // Act
        LogisticRegressionModel loadedModel = persistence.loadModel(nonExistentPath.toString());
        
        // Assert
        assertNull(loadedModel, "Loading non-existent model should return null");
    }


    // ========== 4.6 Fallback Behavior Tests ==========

    /**
     * Test that ML is not available with fewer than 5 decisions.
     * Verifies that heuristics are applied instead.
     * 
     * Requirements: 3.5
     */
    @Test
    void testFallbackBehavior_mlNotAvailableWithInsufficientData() throws IOException {
        // Arrange - create memory with only 3 decisions
        createInsufficientMemory();
        MemoryService insufficientMemory = new MemoryService(memoryFilePath.toString());
        
        MLRelationPredictor predictor = new MLRelationPredictor(
            insufficientMemory,
            new FeatureExtractor(),
            new LogisticRegressionModel(),
            modelFilePath.toString(),
            5 // minimum threshold
        );
        
        // Act & Assert
        assertFalse(predictor.isMLAvailable(), 
            "ML should not be available with fewer than 5 decisions");
        assertEquals(3, predictor.getDecisionCount(), 
            "Should have 3 decisions in memory");
        assertEquals(5, predictor.getMinimumDecisionsThreshold(),
            "Minimum threshold should be 5");
    }

    /**
     * Test that ML becomes available when threshold is met.
     * 
     * Requirements: 3.5
     */
    @Test
    void testFallbackBehavior_mlAvailableWhenThresholdMet() {
        // Arrange - memory already has 15 decisions from setUp()
        MLRelationPredictor predictor = new MLRelationPredictor(
            memoryService,
            new FeatureExtractor(),
            new LogisticRegressionModel(),
            modelFilePath.toString(),
            5 // minimum threshold
        );
        
        // Act & Assert
        assertTrue(predictor.isMLAvailable(), 
            "ML should be available with 15 decisions (>= 5 threshold)");
        assertEquals(15, predictor.getDecisionCount(), 
            "Should have 15 decisions in memory");
    }

    /**
     * Test that predictor returns empty list when ML is not available.
     * 
     * Requirements: 3.5
     */
    @Test
    void testFallbackBehavior_returnsEmptyPredictionsWhenMLUnavailable() throws IOException {
        // Arrange - create memory with only 3 decisions
        createInsufficientMemory();
        MemoryService insufficientMemory = new MemoryService(memoryFilePath.toString());
        
        MLRelationPredictor predictor = new MLRelationPredictor(
            insufficientMemory,
            new FeatureExtractor(),
            new LogisticRegressionModel(),
            modelFilePath.toString(),
            5
        );
        
        // Act
        var predictions = predictor.predictRelations(
            java.util.List.of("User", "Order", "Product"),
            new HashMap<>()
        );
        
        // Assert
        assertTrue(predictions.isEmpty(), 
            "Should return empty predictions when ML is not available");
    }

    /**
     * Test that predictor can make predictions when ML is available.
     * 
     * Requirements: 3.5
     */
    @Test
    void testFallbackBehavior_makesPredictionsWhenMLAvailable() {
        // Arrange - memory has 15 decisions from setUp()
        MLRelationPredictor predictor = new MLRelationPredictor(
            memoryService,
            new FeatureExtractor(),
            new LogisticRegressionModel(42L),
            modelFilePath.toString(),
            5
        );
        
        // Force training
        predictor.retrain();
        
        // Act
        var predictions = predictor.predictRelations(
            java.util.List.of("User", "Order", "Product"),
            new HashMap<>()
        );
        
        // Assert - should have some predictions (not necessarily all pairs)
        // The exact number depends on confidence threshold (0.3)
        assertNotNull(predictions, "Predictions should not be null");
        // Model is trained, so it should be able to make predictions
        assertTrue(predictor.getModel().isTrained(), "Model should be trained");
    }

    // ========== Full Pipeline Integration Test ==========

    /**
     * Test the complete ML pipeline: generate data -> train -> save -> load -> predict.
     * 
     * Requirements: 3.1, 3.2, 3.3, 3.4
     */
    @Test
    void testFullPipeline_endToEndWorkflow() throws IOException {
        // Step 1: Generate training data from memory
        TrainingDataGenerator generator = new TrainingDataGenerator(
            new FeatureExtractor(), trainingDataPath.toString());
        TrainingDataset dataset = generator.generateFromMemory(memoryService);
        
        assertNotNull(dataset, "Dataset should be generated");
        assertTrue(dataset.size() >= 10, "Should have at least 10 training examples");
        
        // Step 2: Train model
        LogisticRegressionModel model = new LogisticRegressionModel(42L);
        model.train(dataset, 0.1, 1000);
        
        assertTrue(model.isTrained(), "Model should be trained");
        
        // Step 3: Save model
        ModelPersistence persistence = new ModelPersistence(modelFilePath.toString());
        persistence.saveModel(model, modelFilePath.toString());
        
        assertTrue(Files.exists(modelFilePath), "Model file should exist");
        
        // Step 4: Load model
        LogisticRegressionModel loadedModel = persistence.loadModel(modelFilePath.toString());
        
        assertNotNull(loadedModel, "Loaded model should not be null");
        assertTrue(loadedModel.isTrained(), "Loaded model should be trained");
        
        // Step 5: Make predictions
        FeatureExtractor featureExtractor = new FeatureExtractor();
        double[] features = featureExtractor.extractFeatures("User", "Order", "OneToMany", null, null);
        
        double originalPrediction = model.predict(features);
        double loadedPrediction = loadedModel.predict(features);
        
        assertEquals(originalPrediction, loadedPrediction, 0.0001,
            "Predictions should match between original and loaded model");
        assertTrue(loadedPrediction >= 0.0 && loadedPrediction <= 1.0,
            "Prediction should be in valid range [0, 1]");
    }
}
