package org.example.ml;

import java.util.HashMap;
import java.util.Map;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for TrainingDataGenerator.
 */
public class TrainingDataGeneratorProperties {

    private static final String[] RELATION_TYPES = {
        "OneToOne", "OneToMany", "ManyToOne", "ManyToMany"
    };
    
    private static final String[] DECISIONS = {"aceptada", "rechazada"};

    /**
     * **Feature: ml-relation-inference, Property 5: Training data generation completeness**
     * 
     * For any MemoryService containing N decision entries, the generated TrainingDataset 
     * SHALL contain exactly N training examples.
     * 
     * This test validates that for any set of valid memory entries, the generator
     * produces exactly one training example per entry.
     * 
     * **Validates: Requirements 2.1, 7.2**
     */
    @Property(tries = 100)
    void trainingDataGenerationCompleteness(
            @ForAll("validMemoryEntries") Map<String, String> memoryEntries) {
        
        TrainingDataGenerator generator = new TrainingDataGenerator();
        int expectedFeatureCount = generator.getFeatureCount(); // Should be 24 for enhanced features
        
        // Count how many examples we should get
        int expectedCount = memoryEntries.size();
        
        // Generate examples directly from the entries
        TrainingDataset dataset = new TrainingDataset(expectedFeatureCount);
        for (Map.Entry<String, String> entry : memoryEntries.entrySet()) {
            TrainingExample example = generator.createExampleFromRelation(
                entry.getKey(), entry.getValue());
            if (example != null) {
                dataset.addExample(example);
            }
        }
        
        // Property: dataset size should equal number of valid memory entries
        assert dataset.size() == expectedCount :
            "Dataset size (" + dataset.size() + ") should equal memory entries count (" + 
            expectedCount + ")";
        
        // Additional check: feature count should be consistent (24 dimensions for enhanced features)
        if (dataset.size() > 0) {
            assert dataset.getFeatureCount() == FeatureExtractor.FEATURE_COUNT :
                "Feature count should be " + FeatureExtractor.FEATURE_COUNT + ", got " + dataset.getFeatureCount();
        }
    }


    /**
     * **Feature: ml-relation-inference, Property 6: Label assignment correctness**
     * 
     * For any training example generated from a memory entry, the label SHALL be 1.0 
     * if the decision was "aceptada" and 0.0 if the decision was "rechazada".
     * 
     * **Validates: Requirements 2.2, 2.3**
     */
    @Property(tries = 100)
    void labelAssignmentCorrectness(
            @ForAll("validClassName") String origen,
            @ForAll("validClassName") String destino,
            @ForAll("validRelationType") String tipo,
            @ForAll("validDecision") String decision) {
        
        String relationKey = origen + " -> " + destino + " : " + tipo;
        
        TrainingDataGenerator generator = new TrainingDataGenerator();
        TrainingExample example = generator.createExampleFromRelation(relationKey, decision);
        
        assert example != null : "Example should not be null for valid input";
        
        double expectedLabel = "aceptada".equalsIgnoreCase(decision) ? 1.0 : 0.0;
        
        assert example.getLabel() == expectedLabel :
            "Label should be " + expectedLabel + " for decision '" + decision + 
            "', but got " + example.getLabel();
    }
    
    /**
     * Additional property: Parsed relation components are correctly extracted
     */
    @Property(tries = 100)
    void relationParsingCorrectness(
            @ForAll("validClassName") String origen,
            @ForAll("validClassName") String destino,
            @ForAll("validRelationType") String tipo) {
        
        String relationKey = origen + " -> " + destino + " : " + tipo;
        
        TrainingDataGenerator generator = new TrainingDataGenerator();
        TrainingDataGenerator.ParsedRelation parsed = generator.parseRelationString(relationKey);
        
        assert parsed != null : "Parsed relation should not be null for valid input";
        assert parsed.origen.equals(origen) : 
            "Origen should be '" + origen + "', got '" + parsed.origen + "'";
        assert parsed.destino.equals(destino) : 
            "Destino should be '" + destino + "', got '" + parsed.destino + "'";
        assert parsed.tipo.equals(tipo) : 
            "Tipo should be '" + tipo + "', got '" + parsed.tipo + "'";
    }
    
    /**
     * Additional property: Save and load dataset round-trip
     * Tests that saving and loading a dataset preserves all data.
     * 
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 50)
    void saveLoadDatasetRoundTrip(
            @ForAll("validMemoryEntries") Map<String, String> memoryEntries) throws Exception {
        
        TrainingDataGenerator generator = new TrainingDataGenerator();
        int featureCount = generator.getFeatureCount(); // Should be 24 for enhanced features
        
        // Generate dataset from entries
        TrainingDataset original = new TrainingDataset(featureCount);
        for (Map.Entry<String, String> entry : memoryEntries.entrySet()) {
            TrainingExample example = generator.createExampleFromRelation(
                entry.getKey(), entry.getValue());
            if (example != null) {
                original.addExample(example);
            }
        }
        
        // Use a temp file for testing
        String tempPath = "test_training_data_" + System.nanoTime() + ".json";
        
        try {
            // Save and load
            generator.saveDataset(original, tempPath);
            TrainingDataset loaded = generator.loadDataset(tempPath);
            
            // Verify round-trip
            assert loaded != null : "Loaded dataset should not be null";
            assert loaded.size() == original.size() : 
                "Dataset size should be preserved: expected " + original.size() + ", got " + loaded.size();
            assert loaded.getFeatureCount() == original.getFeatureCount() : 
                "Feature count should be preserved";
            
            // Verify each example
            for (int i = 0; i < original.size(); i++) {
                TrainingExample origEx = original.getExamples().get(i);
                TrainingExample loadedEx = loaded.getExamples().get(i);
                
                assert origEx.getLabel() == loadedEx.getLabel() : 
                    "Label should be preserved for example " + i;
                
                double[] origFeatures = origEx.getFeatures();
                double[] loadedFeatures = loadedEx.getFeatures();
                
                assert origFeatures.length == loadedFeatures.length : 
                    "Feature array length should be preserved";
                
                for (int j = 0; j < origFeatures.length; j++) {
                    assert Double.compare(origFeatures[j], loadedFeatures[j]) == 0 : 
                        "Feature " + j + " should be preserved";
                }
            }
        } finally {
            // Clean up temp file
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(tempPath));
        }
    }

    
    /**
     * **Feature: enhanced-ml-features, Property: Training data migration preserves labels**
     * 
     * For any old-format dataset (12 features), migration to new format (24 features)
     * SHALL preserve all labels and pad new features with 0.5.
     * 
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 50)
    void trainingDataMigrationPreservesLabels(
            @ForAll("validMemoryEntries") Map<String, String> memoryEntries) {
        
        TrainingDataGenerator generator = new TrainingDataGenerator();
        
        // Create old-format dataset (12 features)
        TrainingDataset oldDataset = new TrainingDataset(FeatureExtractor.LEGACY_FEATURE_COUNT);
        for (Map.Entry<String, String> entry : memoryEntries.entrySet()) {
            // Create example with legacy features
            double[] legacyFeatures = new double[FeatureExtractor.LEGACY_FEATURE_COUNT];
            for (int i = 0; i < legacyFeatures.length; i++) {
                legacyFeatures[i] = Math.random(); // Random values for testing
            }
            double label = "aceptada".equals(entry.getValue()) ? 1.0 : 0.0;
            oldDataset.addExample(new TrainingExample(legacyFeatures, label));
        }
        
        // Migrate to new format
        TrainingDataset migratedDataset = generator.migrateDataset(oldDataset);
        
        // Verify migration
        assert migratedDataset.size() == oldDataset.size() :
            "Migrated dataset should have same number of examples";
        
        if (migratedDataset.size() > 0) {
            assert migratedDataset.getFeatureCount() == FeatureExtractor.FEATURE_COUNT :
                "Migrated dataset should have " + FeatureExtractor.FEATURE_COUNT + " features";
            
            // Verify labels are preserved
            for (int i = 0; i < oldDataset.size(); i++) {
                TrainingExample oldEx = oldDataset.getExamples().get(i);
                TrainingExample newEx = migratedDataset.getExamples().get(i);
                
                assert oldEx.getLabel() == newEx.getLabel() :
                    "Label should be preserved for example " + i;
                
                // Verify new features are padded with 0.5
                double[] newFeatures = newEx.getFeatures();
                for (int j = FeatureExtractor.LEGACY_FEATURE_COUNT; j < newFeatures.length; j++) {
                    assert newFeatures[j] == 0.5 :
                        "New feature " + j + " should be padded with 0.5, got " + newFeatures[j];
                }
            }
        }
    }
    
    /**
     * **Feature: enhanced-ml-features, Property: Migration detection works correctly**
     * 
     * For any dataset, needsMigration SHALL return true if feature count < 24.
     * 
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 50)
    void migrationDetectionCorrectness(
            @ForAll("featureCount") int featureCount) {
        
        TrainingDataGenerator generator = new TrainingDataGenerator();
        
        // Create dataset with specified feature count
        TrainingDataset dataset = new TrainingDataset(featureCount);
        double[] features = new double[featureCount];
        dataset.addExample(new TrainingExample(features, 1.0));
        
        boolean needsMigration = generator.needsMigration(dataset);
        boolean expectedNeedsMigration = featureCount < FeatureExtractor.FEATURE_COUNT;
        
        assert needsMigration == expectedNeedsMigration :
            "needsMigration should be " + expectedNeedsMigration + " for " + featureCount + 
            " features, got " + needsMigration;
    }
    
    /**
     * Provides valid feature counts for testing migration detection.
     */
    @Provide
    Arbitrary<Integer> featureCount() {
        return Arbitraries.integers().between(1, 30);
    }

    /**
     * Provides valid memory entries with properly formatted relation strings.
     */
    @Provide
    Arbitrary<Map<String, String>> validMemoryEntries() {
        return Arbitraries.integers().between(0, 20).flatMap(count -> {
            if (count == 0) {
                return Arbitraries.just(new HashMap<>());
            }
            
            return Arbitraries.maps(
                validRelationKey(),
                Arbitraries.of(DECISIONS)
            ).ofSize(count);
        });
    }
    
    /**
     * Provides valid relation keys in format "Origen -> Destino : Tipo"
     */
    @Provide
    Arbitrary<String> validRelationKey() {
        return Combinators.combine(
            validClassName(),
            validClassName(),
            validRelationType()
        ).as((origen, destino, tipo) -> origen + " -> " + destino + " : " + tipo);
    }
    
    /**
     * Provides valid class names (non-empty, alphanumeric).
     */
    @Provide
    Arbitrary<String> validClassName() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(30)
            .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase());
    }
    
    /**
     * Provides valid relation types.
     */
    @Provide
    Arbitrary<String> validRelationType() {
        return Arbitraries.of(RELATION_TYPES);
    }
    
    /**
     * Provides valid decision strings.
     */
    @Provide
    Arbitrary<String> validDecision() {
        return Arbitraries.of(DECISIONS);
    }
}
