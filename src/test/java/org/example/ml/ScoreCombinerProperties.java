package org.example.ml;

import org.example.config.MLConfig;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based tests for ScoreCombiner.
 * Tests correctness properties defined in the design document.
 */
public class ScoreCombinerProperties {

    private final ScoreCombiner combiner = new ScoreCombiner();

    /**
     * **Feature: ml-relation-inference, Property 11: Weighted combination formula**
     * 
     * For any three scores (mlScore, structuralScore, semanticScore), the 
     * ScoreCombiner.combine() method SHALL return exactly: 
     * (mlScore * 0.5) + (structuralScore * 0.3) + (semanticScore * 0.2).
     * 
     * **Validates: Requirements 4.1, 4.2**
     */
    @Property(tries = 100)
    void weightedCombinationFormula(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double mlScore,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double structuralScore,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double semanticScore) {
        
        double result = combiner.combine(mlScore, structuralScore, semanticScore);
        
        // Calculate expected value using the exact formula from requirements
        double expected = (mlScore * 0.5) + (structuralScore * 0.3) + (semanticScore * 0.2);
        
        // Allow small floating-point tolerance
        double epsilon = 0.0000001;
        assert Math.abs(result - expected) < epsilon :
            String.format("Combined score should be (%.4f * 0.5) + (%.4f * 0.3) + (%.4f * 0.2) = %.4f, but got %.4f",
                mlScore, structuralScore, semanticScore, expected, result);
        
        // Result should always be in valid range [0, 1]
        assert result >= 0.0 : "Combined score should be >= 0.0, got " + result;
        assert result <= 1.0 : "Combined score should be <= 1.0, got " + result;
    }

    /**
     * Additional property: Weights sum to 1.0
     * Verifies that the weights used in combination sum to exactly 1.0.
     */
    @Property(tries = 1)
    void weightsSumToOne() {
        MLConfig config = MLConfig.DEFAULT;
        double weightSum = config.mlWeight() + config.structuralWeight() + config.semanticWeight();
        double epsilon = 0.0000001;
        
        assert Math.abs(weightSum - 1.0) < epsilon :
            "Weights should sum to 1.0, but sum is " + weightSum;
    }

    /**
     * Property: Combine handles edge case scores (0 and 1)
     */
    @Property(tries = 100)
    void combineHandlesEdgeCases(
            @ForAll("edgeScores") double mlScore,
            @ForAll("edgeScores") double structuralScore,
            @ForAll("edgeScores") double semanticScore) {
        
        double result = combiner.combine(mlScore, structuralScore, semanticScore);
        
        // Result should always be in valid range
        assert result >= 0.0 : "Combined score should be >= 0.0";
        assert result <= 1.0 : "Combined score should be <= 1.0";
        assert !Double.isNaN(result) : "Combined score should not be NaN";
        assert !Double.isInfinite(result) : "Combined score should not be infinite";
    }

    @Provide
    @SuppressWarnings("unused")
    Arbitrary<Double> edgeScores() {
        return Arbitraries.of(0.0, 0.5, 1.0);
    }

    /**
     * **Feature: ml-relation-inference, Property 12: Prediction source attribution**
     * 
     * For any RelacionDetectada produced by the combined analysis, the result 
     * SHALL include a non-null PredictionSource indicating whether the primary 
     * contributor was ML, STRUCTURAL, or SEMANTIC.
     * 
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    void predictionSourceAttribution(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double mlScore,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double structuralScore,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double semanticScore) {
        
        PredictionSource source = combiner.getPrimarySource(mlScore, structuralScore, semanticScore);
        
        // Source should never be null
        assert source != null : 
            "PredictionSource should never be null for any input scores";
        
        // Source should be one of the valid enum values
        assert source == PredictionSource.ML 
            || source == PredictionSource.STRUCTURAL 
            || source == PredictionSource.SEMANTIC 
            || source == PredictionSource.COMBINED :
            "PredictionSource should be ML, STRUCTURAL, SEMANTIC, or COMBINED, got " + source;
    }

    /**
     * Property: Primary source reflects highest weighted contribution
     * When one source clearly dominates, it should be identified as primary.
     */
    @Property(tries = 100)
    void primarySourceReflectsHighestContribution(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double mlScore,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double structuralScore,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double semanticScore) {
        
        // Calculate weighted contributions using default config
        MLConfig config = MLConfig.DEFAULT;
        double mlContribution = mlScore * config.mlWeight();
        double structuralContribution = structuralScore * config.structuralWeight();
        double semanticContribution = semanticScore * config.semanticWeight();
        
        PredictionSource source = combiner.getPrimarySource(mlScore, structuralScore, semanticScore);
        
        double epsilon = 0.0001;
        double maxContribution = Math.max(mlContribution, 
                                 Math.max(structuralContribution, semanticContribution));
        
        // Count how many sources have the max contribution (within epsilon)
        int equalCount = 0;
        if (Math.abs(mlContribution - maxContribution) < epsilon) equalCount++;
        if (Math.abs(structuralContribution - maxContribution) < epsilon) equalCount++;
        if (Math.abs(semanticContribution - maxContribution) < epsilon) equalCount++;
        
        if (equalCount > 1) {
            // Multiple sources tied - should be COMBINED
            assert source == PredictionSource.COMBINED :
                String.format("When multiple sources are tied (count=%d), source should be COMBINED, got %s. " +
                    "ML=%.4f, Structural=%.4f, Semantic=%.4f", 
                    equalCount, source, mlContribution, structuralContribution, semanticContribution);
        } else {
            // Single dominant source
            if (Math.abs(mlContribution - maxContribution) < epsilon) {
                assert source == PredictionSource.ML :
                    String.format("ML has highest contribution (%.4f), source should be ML, got %s",
                        mlContribution, source);
            } else if (Math.abs(structuralContribution - maxContribution) < epsilon) {
                assert source == PredictionSource.STRUCTURAL :
                    String.format("Structural has highest contribution (%.4f), source should be STRUCTURAL, got %s",
                        structuralContribution, source);
            } else {
                assert source == PredictionSource.SEMANTIC :
                    String.format("Semantic has highest contribution (%.4f), source should be SEMANTIC, got %s",
                        semanticContribution, source);
            }
        }
    }

    /**
     * Property: ML dominates when ML score is significantly higher
     * Given ML weight is 50%, when ML score is 1.0 and others are 0.0, ML should be primary.
     */
    @Property(tries = 1)
    void mlDominatesWhenHighest() {
        PredictionSource source = combiner.getPrimarySource(1.0, 0.0, 0.0);
        assert source == PredictionSource.ML :
            "When ML=1.0 and others=0.0, source should be ML, got " + source;
    }

    /**
     * Property: Structural can be primary when it has highest weighted contribution
     */
    @Property(tries = 1)
    void structuralCanBePrimary() {
        // ML=0, Structural=1.0, Semantic=0 -> Structural contribution = 0.3
        PredictionSource source = combiner.getPrimarySource(0.0, 1.0, 0.0);
        assert source == PredictionSource.STRUCTURAL :
            "When Structural=1.0 and others=0.0, source should be STRUCTURAL, got " + source;
    }

    /**
     * Property: Semantic can be primary when it has highest weighted contribution
     */
    @Property(tries = 1)
    void semanticCanBePrimary() {
        // ML=0, Structural=0, Semantic=1.0 -> Semantic contribution = 0.2
        PredictionSource source = combiner.getPrimarySource(0.0, 0.0, 1.0);
        assert source == PredictionSource.SEMANTIC :
            "When Semantic=1.0 and others=0.0, source should be SEMANTIC, got " + source;
    }
}
