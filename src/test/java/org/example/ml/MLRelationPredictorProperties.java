package org.example.ml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import org.example.config.ConfigManager;
import org.example.service.MemoryService;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * Property-based tests for MLRelationPredictor.
 * Tests correctness properties defined in the design document.
 *
 * jqwik no soporta {@code @TempDir}, así que cada propiedad crea su propio directorio temporal
 * y lo borra al terminar. Ningún modelo se escribe en el directorio de trabajo.
 */
public class MLRelationPredictorProperties {

    private Path tempDir;

    @BeforeProperty
    void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("ml-predictor-props-");
    }

    @AfterProperty
    void deleteTempDir() throws IOException {
        if (tempDir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private String modelPath(String prefix) {
        return tempDir.resolve(prefix + System.nanoTime() + ".json").toString();
    }

    // Get threshold from configuration
    private static int getMinimumThreshold() {
        return ConfigManager.getInstance().getMLConfig().minDecisions();
    }

    private static MemoryService memoryWith(int decisionCount) {
        MemoryService memoryService = MemoryService.inMemory(Map.of());
        // Mix of accepted and rejected to ensure balanced training data
        for (int i = 0; i < decisionCount; i++) {
            String relation = "Class" + i + " -> Target" + i + " : OneToMany";
            memoryService.recordarDecision(relation, i % 2 == 0);
        }
        return memoryService;
    }

    /**
     * **Feature: ml-relation-inference, Property 7: Minimum data threshold fallback**
     * 
     * For any MemoryService containing fewer than the configured minimum decisions, 
     * the MLRelationPredictor.isMLAvailable() SHALL return false, 
     * causing the system to use heuristic-based inference.
     * 
     * **Validates: Requirements 2.4**
     */
    @Property(tries = 100)
    void minimumDataThresholdFallback(
            @ForAll @IntRange(min = 0, max = 9) int decisionCount) {
        
        int threshold = getMinimumThreshold();
        // Skip if decisionCount is at or above threshold
        if (decisionCount >= threshold) {
            return;
        }
        
        MemoryService memoryService = memoryWith(decisionCount);
        
        // Create predictor with the memory service
        MLRelationPredictor predictor = new MLRelationPredictor(
            memoryService, 
            new FeatureExtractor(), 
            new LogisticRegressionModel(),
            modelPath("test_model_")
        );
        
        // Verify isMLAvailable returns false when below threshold
        assert !predictor.isMLAvailable() : 
            "isMLAvailable() should return false when decision count (" + 
            decisionCount + ") is below threshold (" + threshold + ")";
        
        // Verify the decision count matches
        assert predictor.getDecisionCount() == decisionCount :
            "Decision count should be " + decisionCount + ", got " + predictor.getDecisionCount();
    }


    /**
     * Complementary property: When decision count is at or above threshold,
     * isMLAvailable() should return true and ensureTrained() should produce a trained model.
     */
    @Property(tries = 50)
    void mlAvailableWhenSufficientData(
            @ForAll @IntRange(min = 10, max = 25) int decisionCount) {
        
        int threshold = getMinimumThreshold();
        // Skip if decisionCount is below threshold
        if (decisionCount < threshold) {
            return;
        }
        
        MemoryService memoryService = memoryWith(decisionCount);
        String path = modelPath("test_model_sufficient_");
        
        MLRelationPredictor predictor = new MLRelationPredictor(
            memoryService, 
            new FeatureExtractor(), 
            new LogisticRegressionModel(),
            path
        );
        
        assert predictor.isMLAvailable() : 
            "isMLAvailable() should return true when decision count (" + 
            decisionCount + ") is at or above threshold (" + threshold + ")";
        
        assert predictor.ensureTrained() != null : "ensureTrained() should train the model";
        assert predictor.getModel().isTrained() :
            "Model should be trained when sufficient data is available";
        assert Files.exists(Path.of(path)) : "Trained model should be saved to the temp directory";
    }

    /**
     * isMLAvailable() no tiene efectos laterales: no entrena ni escribe el modelo.
     */
    @Property(tries = 20)
    void isMLAvailableHasNoSideEffects(
            @ForAll @IntRange(min = 10, max = 25) int decisionCount) {
        MemoryService memoryService = memoryWith(Math.max(decisionCount, getMinimumThreshold()));
        String path = modelPath("test_model_pure_");
        
        MLRelationPredictor predictor = new MLRelationPredictor(
            memoryService, new FeatureExtractor(), new LogisticRegressionModel(), path);
        
        assert predictor.isMLAvailable();
        assert !predictor.getModel().isTrained() : "isMLAvailable() must not train the model";
        assert !Files.exists(Path.of(path)) : "isMLAvailable() must not write the model file";
    }

    /**
     * Property: Null MemoryService should result in ML not being available.
     */
    @Property(tries = 10)
    void nullMemoryServiceReturnsNotAvailable() {
        MLRelationPredictor predictor = new MLRelationPredictor(
            null, 
            new FeatureExtractor(), 
            new LogisticRegressionModel(),
            modelPath("test_model_null_")
        );
        
        assert !predictor.isMLAvailable() : 
            "isMLAvailable() should return false when MemoryService is null";
        
        assert predictor.getDecisionCount() == 0 :
            "Decision count should be 0 when MemoryService is null";
    }
}
