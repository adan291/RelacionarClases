package org.example.ml;

import java.util.HashMap;
import java.util.Map;

import org.example.config.ConfigManager;
import org.example.service.MemoryService;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for MLRelationPredictor.
 * Tests correctness properties defined in the design document.
 */
public class MLRelationPredictorProperties {

    // Get threshold from configuration
    private static int getMinimumThreshold() {
        return ConfigManager.getInstance().getMLConfig().minDecisions();
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
        
        // Create a mock MemoryService with the specified number of decisions
        TestMemoryService memoryService = new TestMemoryService();
        
        // Add decisions (fewer than threshold)
        for (int i = 0; i < decisionCount; i++) {
            String relation = "Class" + i + " -> Target" + i + " : OneToMany";
            memoryService.recordarDecision(relation, i % 2 == 0);
        }
        
        // Create predictor with the memory service
        MLRelationPredictor predictor = new MLRelationPredictor(
            memoryService, 
            new FeatureExtractor(), 
            new LogisticRegressionModel(),
            "test_model_" + System.nanoTime() + ".json"
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
     * isMLAvailable() should return true (assuming training succeeds).
     */
    @Property(tries = 50)
    void mlAvailableWhenSufficientData(
            @ForAll @IntRange(min = 10, max = 25) int decisionCount) {
        
        int threshold = getMinimumThreshold();
        // Skip if decisionCount is below threshold
        if (decisionCount < threshold) {
            return;
        }
        
        // Create a mock MemoryService with sufficient decisions
        TestMemoryService memoryService = new TestMemoryService();
        
        // Add decisions (at or above threshold)
        // Mix of accepted and rejected to ensure balanced training data
        for (int i = 0; i < decisionCount; i++) {
            String relation = "Class" + i + " -> Target" + i + " : OneToMany";
            memoryService.recordarDecision(relation, i % 2 == 0);
        }
        
        // Create predictor with the memory service
        MLRelationPredictor predictor = new MLRelationPredictor(
            memoryService, 
            new FeatureExtractor(), 
            new LogisticRegressionModel(),
            "test_model_sufficient_" + System.nanoTime() + ".json"
        );
        
        // Verify isMLAvailable returns true when at or above threshold
        assert predictor.isMLAvailable() : 
            "isMLAvailable() should return true when decision count (" + 
            decisionCount + ") is at or above threshold (" + threshold + ")";
        
        // Verify the model is trained
        assert predictor.getModel().isTrained() :
            "Model should be trained when sufficient data is available";
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
            "test_model_null_" + System.nanoTime() + ".json"
        );
        
        assert !predictor.isMLAvailable() : 
            "isMLAvailable() should return false when MemoryService is null";
        
        assert predictor.getDecisionCount() == 0 :
            "Decision count should be 0 when MemoryService is null";
    }

    /**
     * Test helper: In-memory MemoryService for testing without file I/O.
     */
    private static class TestMemoryService extends MemoryService {
        private final Map<String, String> testMemoria = new HashMap<>();
        
        @Override
        public synchronized void cargar() {
            // Don't load from file in tests
        }
        
        @Override
        public synchronized void guardar() {
            // Don't save to file in tests
        }
        
        @Override
        public synchronized void recordarDecision(String relacion, boolean aceptada) {
            if (relacion != null && !relacion.isBlank()) {
                testMemoria.put(relacion, aceptada ? "aceptada" : "rechazada");
            }
        }
        
        @Override
        public synchronized Map<String, String> getMemoria() {
            return new HashMap<>(testMemoria);
        }
        
        @Override
        public synchronized void clearMemoria() {
            testMemoria.clear();
        }
    }
}
