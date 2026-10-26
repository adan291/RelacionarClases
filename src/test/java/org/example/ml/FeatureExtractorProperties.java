package org.example.ml;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for FeatureExtractor.
 */
public class FeatureExtractorProperties {
    
    private final FeatureExtractor extractor = new FeatureExtractor();
    
    /**
     * **Feature: enhanced-ml-features, Property 1: Feature Vector Length Invariant**
     * 
     * For any input, the feature vector should always have exactly 24 dimensions.
     * 
     * **Validates: Requirement 6.1**
     */
    @Property(tries = 100)
    void featureVectorLengthInvariant(@ForAll("classNames") String origen,
                                       @ForAll("classNames") String destino,
                                       @ForAll("relationTypes") String tipo) {
        double[] features = extractor.extractFeatures(origen, destino, tipo, null, null);
        
        assert features.length == FeatureExtractor.FEATURE_COUNT : 
            "Feature vector should have " + FeatureExtractor.FEATURE_COUNT + " dimensions, got " + features.length;
    }
    
    /**
     * **Feature: enhanced-ml-features, Property 2: Feature Normalization**
     * 
     * For any input, all feature values should be in [0, 1] range.
     * 
     * **Validates: Requirement 6.3**
     */
    @Property(tries = 100)
    void featureNormalization(@ForAll("classNames") String origen,
                              @ForAll("classNames") String destino,
                              @ForAll("relationTypes") String tipo) {
        double[] features = extractor.extractFeatures(origen, destino, tipo, null, null);
        
        for (int i = 0; i < features.length; i++) {
            assert features[i] >= 0.0 && features[i] <= 1.0 : 
                "Feature " + i + " should be in [0,1], got " + features[i];
        }
    }
    
    /**
     * Legacy feature extraction should produce 12 features.
     */
    @Property(tries = 50)
    void legacyFeatureVectorLength(@ForAll("classNames") String origen,
                                    @ForAll("classNames") String destino,
                                    @ForAll("relationTypes") String tipo) {
        double[] features = extractor.extractLegacyFeatures(origen, destino, tipo, null, null);
        
        assert features.length == FeatureExtractor.LEGACY_FEATURE_COUNT : 
            "Legacy feature vector should have 12 dimensions";
    }
    
    /**
     * Feature extraction with ClassInfo should still produce valid features.
     */
    @Property(tries = 50)
    void featureExtractionWithClassInfo(@ForAll("classNames") String origen,
                                         @ForAll("classNames") String destino,
                                         @ForAll("relationTypes") String tipo) {
        ClassInfo origenInfo = new ClassInfo(origen);
        ClassInfo destinoInfo = new ClassInfo(destino);
        
        double[] features = extractor.extractFeatures(origen, destino, tipo, origenInfo, destinoInfo);
        
        assert features.length == FeatureExtractor.FEATURE_COUNT : 
            "Feature vector with ClassInfo should have 24 dimensions";
        
        for (int i = 0; i < features.length; i++) {
            assert features[i] >= 0.0 && features[i] <= 1.0 : 
                "Feature " + i + " with ClassInfo should be in [0,1]";
        }
    }
    
    // === Providers ===
    
    @Provide
    Arbitrary<String> classNames() {
        return Arbitraries.of("User", "Order", "Product", "Category", 
            "Item", "Customer", "Address", "Payment", "Invoice");
    }
    
    @Provide
    Arbitrary<String> relationTypes() {
        return Arbitraries.of("OneToOne", "OneToMany", "ManyToOne", "ManyToMany");
    }
}
