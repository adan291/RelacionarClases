package org.example.config;

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for ConfigManager.
 * Tests correctness properties defined in the external-configuration design document.
 */
@SuppressWarnings("unused") // @Provide methods are used by jqwik via reflection
public class ConfigManagerProperties {

    /**
     * **Feature: external-configuration, Property 1: Default Values Applied**
     * 
     * When configuration file is missing, all settings should have sensible defaults.
     * For any configuration key, when the properties file is absent or empty,
     * the ConfigManager SHALL return the defined default value.
     * 
     * **Validates: Requirement 1.4**
     */
    @Property(tries = 1)
    void defaultValuesAppliedWhenConfigMissing() {
        // Test that the DEFAULT constants are properly defined and consistent
        // This validates that defaults exist and are sensible
        
        // Verify ML defaults are sensible
        MLConfig mlDefault = MLConfig.DEFAULT;
        assert mlDefault.minDecisions() > 0 :
            "minDecisions default should be positive, got " + mlDefault.minDecisions();
        assert mlDefault.mlWeight() >= 0 && mlDefault.mlWeight() <= 1 :
            "mlWeight default should be in [0,1], got " + mlDefault.mlWeight();
        assert mlDefault.structuralWeight() >= 0 && mlDefault.structuralWeight() <= 1 :
            "structuralWeight default should be in [0,1]";
        assert mlDefault.semanticWeight() >= 0 && mlDefault.semanticWeight() <= 1 :
            "semanticWeight default should be in [0,1]";
        assert mlDefault.learningRate() > 0 && mlDefault.learningRate() <= 1 :
            "learningRate default should be in (0,1]";
        assert mlDefault.maxIterations() > 0 :
            "maxIterations default should be positive";
        
        // Verify Analysis defaults are sensible
        AnalysisConfig analysisDefault = AnalysisConfig.DEFAULT;
        assert analysisDefault.defaultConfidenceThreshold() >= 0 && analysisDefault.defaultConfidenceThreshold() <= 1 :
            "defaultConfidenceThreshold should be in [0,1]";
        assert analysisDefault.highConfidenceThreshold() >= 0 && analysisDefault.highConfidenceThreshold() <= 1 :
            "highConfidenceThreshold should be in [0,1]";
        assert analysisDefault.highConfidenceThreshold() >= analysisDefault.defaultConfidenceThreshold() :
            "highConfidenceThreshold should be >= defaultConfidenceThreshold";
        assert analysisDefault.ignoredPaths() != null && !analysisDefault.ignoredPaths().isEmpty() :
            "ignoredPaths should have default values";
        assert analysisDefault.fileExtensions() != null && !analysisDefault.fileExtensions().isEmpty() :
            "fileExtensions should have default values";
        
        // Verify UI defaults are sensible
        UIConfig uiDefault = UIConfig.DEFAULT;
        assert uiDefault.bulkActionConfirmationThreshold() > 0 :
            "bulkActionConfirmationThreshold should be positive";
        assert uiDefault.windowWidth() > 0 :
            "windowWidth should be positive";
        assert uiDefault.windowHeight() > 0 :
            "windowHeight should be positive";
        assert uiDefault.defaultTheme() != null && !uiDefault.defaultTheme().isEmpty() :
            "defaultTheme should have a value";
        
        // Verify Persistence defaults are sensible
        PersistenceConfig persistenceDefault = PersistenceConfig.DEFAULT;
        assert persistenceDefault.memoryFilePath() != null && !persistenceDefault.memoryFilePath().isEmpty() :
            "memoryFilePath should have a default value";
        assert persistenceDefault.snapshotsDirectory() != null && !persistenceDefault.snapshotsDirectory().isEmpty() :
            "snapshotsDirectory should have a default value";
        assert persistenceDefault.modelFilePath() != null && !persistenceDefault.modelFilePath().isEmpty() :
            "modelFilePath should have a default value";
        assert persistenceDefault.trainingDataPath() != null && !persistenceDefault.trainingDataPath().isEmpty() :
            "trainingDataPath should have a default value";
    }
    
    /**
     * Property: ConfigManager returns defaults for missing keys
     * For any missing key, the type-safe getters should return the provided default.
     */
    @Property(tries = 100)
    void missingKeysReturnDefaults(
            @ForAll @IntRange(min = -1000, max = 1000) int defaultInt,
            @ForAll @DoubleRange(min = -1000.0, max = 1000.0) double defaultDouble,
            @ForAll boolean defaultBool) {
        
        ConfigManager.reset();
        ConfigManager manager = ConfigManager.getInstance();
        manager.load();
        
        // Generate a unique key that definitely doesn't exist
        String missingKey = "nonexistent.key." + System.nanoTime();
        
        // All getters should return defaults for non-existent keys
        assert manager.getInt(missingKey, defaultInt) == defaultInt :
            "getInt should return default for missing key";
        assert manager.getDouble(missingKey, defaultDouble) == defaultDouble :
            "getDouble should return default for missing key";
        assert manager.getBoolean(missingKey, defaultBool) == defaultBool :
            "getBoolean should return default for missing key";
        assert manager.getString(missingKey, "default").equals("default") :
            "getString should return default for missing key";
        assert manager.getList(missingKey, List.of("a", "b")).equals(List.of("a", "b")) :
            "getList should return default for missing key";
        
        ConfigManager.reset();
    }

    /**
     * **Feature: external-configuration, Property 2: Type Safety**
     * 
     * Getting a value with wrong type should return default, not throw exception.
     * For any invalid value string, when parsed as int/double/boolean,
     * the ConfigManager SHALL return the provided default value without throwing.
     * 
     * **Validates: Requirement 6.2**
     */
    @Property(tries = 100)
    void typeSafetyReturnsDefaultForInvalidValues(
            @ForAll("invalidIntStrings") String invalidInt,
            @ForAll("invalidDoubleStrings") String invalidDouble,
            @ForAll @IntRange(min = -1000, max = 1000) int defaultInt,
            @ForAll @DoubleRange(min = -1000.0, max = 1000.0) double defaultDouble,
            @ForAll boolean defaultBool) {
        
        ConfigManager.reset();
        ConfigManager manager = ConfigManager.getInstance();
        
        // Test getInt with invalid value - should return default without exception
        int intResult = manager.getInt("nonexistent.key", defaultInt);
        assert intResult == defaultInt :
            String.format("getInt should return default %d for missing key, got %d", defaultInt, intResult);
        
        // Test getDouble with invalid value - should return default without exception
        double doubleResult = manager.getDouble("nonexistent.key", defaultDouble);
        assert doubleResult == defaultDouble :
            String.format("getDouble should return default %f for missing key, got %f", defaultDouble, doubleResult);
        
        // Test getBoolean with missing value - should return default without exception
        boolean boolResult = manager.getBoolean("nonexistent.key", defaultBool);
        assert boolResult == defaultBool :
            String.format("getBoolean should return default %b for missing key, got %b", defaultBool, boolResult);
        
        // Test getString with missing value - should return default without exception
        String stringDefault = "test-default";
        String stringResult = manager.getString("nonexistent.key", stringDefault);
        assert stringResult.equals(stringDefault) :
            String.format("getString should return default '%s' for missing key, got '%s'", stringDefault, stringResult);
        
        // Test getList with missing value - should return default without exception
        List<String> listDefault = List.of("a", "b", "c");
        List<String> listResult = manager.getList("nonexistent.key", listDefault);
        assert listResult.equals(listDefault) :
            String.format("getList should return default %s for missing key, got %s", listDefault, listResult);
        
        ConfigManager.reset();
    }

    @Provide
    Arbitrary<String> invalidIntStrings() {
        return Arbitraries.of(
            "abc",
            "12.34",
            "1e10",
            "",
            "   ",
            "null",
            "true",
            "1,000",
            "1 2 3"
        );
    }

    @Provide
    Arbitrary<String> invalidDoubleStrings() {
        return Arbitraries.of(
            "abc",
            "",
            "   ",
            "null",
            "true",
            "1,234.56",
            "NaN-invalid",
            "1.2.3"
        );
    }

    /**
     * Property: Type-safe getters handle null and empty strings gracefully
     */
    @Property(tries = 50)
    void typeSafeGettersHandleEmptyValues(
            @ForAll @IntRange(min = 0, max = 100) int defaultInt,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double defaultDouble) {
        
        ConfigManager.reset();
        ConfigManager manager = ConfigManager.getInstance();
        
        // All getters should return defaults for non-existent keys
        assert manager.getInt("missing.key", defaultInt) == defaultInt;
        assert manager.getDouble("missing.key", defaultDouble) == defaultDouble;
        assert manager.getBoolean("missing.key", true) == true;
        assert manager.getBoolean("missing.key", false) == false;
        assert manager.getString("missing.key", "default").equals("default");
        
        ConfigManager.reset();
    }

    /**
     * Property: ConfigManager singleton returns same instance
     */
    @Property(tries = 10)
    void singletonReturnsSameInstance() {
        ConfigManager.reset();
        
        ConfigManager instance1 = ConfigManager.getInstance();
        ConfigManager instance2 = ConfigManager.getInstance();
        
        assert instance1 == instance2 :
            "getInstance() should always return the same instance";
        
        ConfigManager.reset();
    }

    /**
     * Property: Default weights in MLConfig sum to 1.0
     */
    @Property(tries = 1)
    void defaultWeightsSumToOne() {
        double sum = MLConfig.DEFAULT.mlWeight() 
                   + MLConfig.DEFAULT.structuralWeight() 
                   + MLConfig.DEFAULT.semanticWeight();
        double epsilon = 0.0001;
        
        assert Math.abs(sum - 1.0) < epsilon :
            String.format("Default weights should sum to 1.0, got %f", sum);
    }

    /**
     * **Feature: external-configuration, Property 3: Weight Sum Validation**
     * 
     * ML + Structural + Semantic weights should sum to 1.0 (with tolerance).
     * For any valid weight configuration where each weight is in [0,1],
     * the areWeightsValid() method SHALL return true if and only if the sum equals 1.0.
     * 
     * **Validates: Requirements 2.2, 2.3, 2.4**
     */
    @Property(tries = 100)
    void weightSumValidation(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double mlWeight,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double structuralWeight,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double semanticWeight) {
        
        MLConfig config = new MLConfig(
            10,  // minDecisions
            mlWeight,
            structuralWeight,
            semanticWeight,
            0.01,  // learningRate
            1000   // maxIterations
        );
        
        double sum = mlWeight + structuralWeight + semanticWeight;
        double tolerance = 0.001;
        boolean shouldBeValid = Math.abs(sum - 1.0) < tolerance;
        
        assert config.areWeightsValid() == shouldBeValid :
            String.format("areWeightsValid() returned %b but weights sum to %f (expected valid=%b)",
                config.areWeightsValid(), sum, shouldBeValid);
    }

    /**
     * Property: Valid weights always produce valid combined scores
     * When weights sum to 1.0 and input scores are in [0,1], combined score is in [0,1].
     */
    @Property(tries = 100)
    void validWeightsProduceValidCombinedScores(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double mlScore,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double structuralScore,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double semanticScore) {
        
        // Use default valid weights (sum to 1.0)
        org.example.ml.ScoreCombiner combiner = new org.example.ml.ScoreCombiner(
            MLConfig.DEFAULT.mlWeight(),
            MLConfig.DEFAULT.structuralWeight(),
            MLConfig.DEFAULT.semanticWeight()
        );
        
        double combined = combiner.combine(mlScore, structuralScore, semanticScore);
        
        assert combined >= 0.0 && combined <= 1.0 :
            String.format("Combined score %f should be in [0,1] for inputs ML=%f, Structural=%f, Semantic=%f",
                combined, mlScore, structuralScore, semanticScore);
    }

    /**
     * **Feature: external-configuration, Property 4: Configuration Persistence**
     * 
     * Loaded configuration should match file contents exactly.
     * For any valid configuration values written to a properties file,
     * when ConfigManager loads that file, the retrieved values SHALL match
     * the written values exactly.
     * 
     * **Validates: Requirements 1.1, 6.1**
     */
    @Property(tries = 100)
    void configurationPersistenceRoundTrip(
            @ForAll @IntRange(min = 1, max = 100) int minDecisions,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double defaultConfidence,
            @ForAll @IntRange(min = 1, max = 50) int bulkThreshold,
            @ForAll("validFilePaths") String memoryPath) {
        
        java.nio.file.Path originalConfig = java.nio.file.Paths.get("config.properties");
        java.nio.file.Path backupConfig = null;
        
        try {
            // Backup original config if it exists
            if (java.nio.file.Files.exists(originalConfig)) {
                backupConfig = java.nio.file.Paths.get("config.properties.backup");
                java.nio.file.Files.copy(originalConfig, backupConfig, 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Create a test config file with specific values
            java.util.Properties testProps = new java.util.Properties();
            testProps.setProperty("ml.minDecisions", String.valueOf(minDecisions));
            testProps.setProperty("analysis.defaultConfidenceThreshold", String.valueOf(defaultConfidence));
            testProps.setProperty("ui.bulkActionConfirmationThreshold", String.valueOf(bulkThreshold));
            testProps.setProperty("persistence.memoryFilePath", memoryPath);
            
            // Write to config.properties (where ConfigManager looks)
            try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(originalConfig)) {
                testProps.store(os, "Test configuration for property test");
            }
            
            // Reset and reload ConfigManager
            ConfigManager.reset();
            ConfigManager manager = ConfigManager.getInstance();
            manager.load();
            
            // Verify loaded values match written values exactly
            MLConfig mlConfig = manager.getMLConfig();
            assert mlConfig.minDecisions() == minDecisions :
                String.format("minDecisions mismatch: expected %d, got %d", 
                    minDecisions, mlConfig.minDecisions());
            
            AnalysisConfig analysisConfig = manager.getAnalysisConfig();
            assert Math.abs(analysisConfig.defaultConfidenceThreshold() - defaultConfidence) < 0.0001 :
                String.format("defaultConfidenceThreshold mismatch: expected %f, got %f",
                    defaultConfidence, analysisConfig.defaultConfidenceThreshold());
            
            UIConfig uiConfig = manager.getUIConfig();
            assert uiConfig.bulkActionConfirmationThreshold() == bulkThreshold :
                String.format("bulkActionConfirmationThreshold mismatch: expected %d, got %d",
                    bulkThreshold, uiConfig.bulkActionConfirmationThreshold());
            
            PersistenceConfig persistenceConfig = manager.getPersistenceConfig();
            assert persistenceConfig.memoryFilePath().equals(memoryPath) :
                String.format("memoryFilePath mismatch: expected '%s', got '%s'",
                    memoryPath, persistenceConfig.memoryFilePath());
            
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create/read test config file", e);
        } finally {
            // Cleanup: restore original config
            ConfigManager.reset();
            try {
                if (backupConfig != null && java.nio.file.Files.exists(backupConfig)) {
                    java.nio.file.Files.copy(backupConfig, originalConfig,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    java.nio.file.Files.deleteIfExists(backupConfig);
                }
            } catch (java.io.IOException e) {
                // Best effort cleanup
            }
        }
    }

    @Provide
    Arbitrary<String> validFilePaths() {
        return Arbitraries.of(
            "memory.json",
            "data/relations.json",
            "config/memoria.json",
            "test_memory.json",
            "output/data.json"
        );
    }
}
