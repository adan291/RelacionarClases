package org.example.ml;

import java.util.List;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Property-based tests for TrainingDataset serialization.
 * 
 * **Feature: ml-relation-inference, Property 8: Training data serialization round-trip**
 */
public class TrainingDatasetProperties {

    /**
     * **Feature: ml-relation-inference, Property 8: Training data serialization round-trip**
     * 
     * For any TrainingDataset, serializing to JSON and deserializing SHALL produce 
     * an equivalent dataset with identical features and labels for all examples.
     * 
     * **Validates: Requirements 2.5**
     */
    @Property(tries = 100)
    void trainingDataSerializationRoundTrip(
            @ForAll @Size(min = 0, max = 50) List<@DoubleRange(min = 0.0, max = 1.0) Double> featureList,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double label,
            @ForAll @IntRange(min = 0, max = 10) int additionalExamples) {
        
        // Create a dataset with the generated features
        int featureCount = featureList.size();
        TrainingDataset original = new TrainingDataset(featureCount);
        
        // Add the first example
        double[] features = featureList.stream().mapToDouble(Double::doubleValue).toArray();
        original.addExample(features, label);
        
        // Add additional examples with same feature count
        for (int i = 0; i < additionalExamples; i++) {
            double[] additionalFeatures = new double[featureCount];
            for (int j = 0; j < featureCount; j++) {
                additionalFeatures[j] = Math.random(); // Random features in [0, 1]
            }
            double additionalLabel = Math.random() > 0.5 ? 1.0 : 0.0;
            original.addExample(additionalFeatures, additionalLabel);
        }
        
        // Serialize to JSON
        String json = original.toJson();
        
        // Deserialize from JSON
        TrainingDataset deserialized = TrainingDataset.fromJson(json);
        
        // Verify round-trip produces equivalent dataset
        assert deserialized != null : "Deserialized dataset should not be null";
        assert deserialized.size() == original.size() : 
            "Dataset size should be preserved: expected " + original.size() + ", got " + deserialized.size();
        assert deserialized.getFeatureCount() == original.getFeatureCount() : 
            "Feature count should be preserved";
        
        // Verify each example is identical
        List<TrainingExample> originalExamples = original.getExamples();
        List<TrainingExample> deserializedExamples = deserialized.getExamples();
        
        for (int i = 0; i < originalExamples.size(); i++) {
            TrainingExample origEx = originalExamples.get(i);
            TrainingExample deserEx = deserializedExamples.get(i);
            
            assert origEx.getLabel() == deserEx.getLabel() : 
                "Label should be preserved for example " + i;
            
            double[] origFeatures = origEx.getFeatures();
            double[] deserFeatures = deserEx.getFeatures();
            
            assert origFeatures.length == deserFeatures.length : 
                "Feature array length should be preserved for example " + i;
            
            for (int j = 0; j < origFeatures.length; j++) {
                assert Double.compare(origFeatures[j], deserFeatures[j]) == 0 : 
                    "Feature " + j + " should be preserved for example " + i;
            }
        }
    }
    
    /**
     * Additional property: Empty dataset serialization round-trip
     */
    @Property(tries = 100)
    void emptyDatasetSerializationRoundTrip(@ForAll @IntRange(min = 0, max = 20) int featureCount) {
        TrainingDataset original = new TrainingDataset(featureCount);
        
        String json = original.toJson();
        TrainingDataset deserialized = TrainingDataset.fromJson(json);
        
        assert deserialized != null : "Deserialized dataset should not be null";
        assert deserialized.size() == 0 : "Empty dataset should remain empty";
        assert deserialized.getFeatureCount() == featureCount : "Feature count should be preserved";
    }
}
