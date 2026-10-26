package org.example.ml;

import java.util.logging.Logger;

import org.example.config.ConfigManager;
import org.example.config.MLConfig;

/**
 * Combines scores from different prediction sources (ML, Structural, Semantic).
 * Weights are configurable via config.properties.
 * 
 * Implements Requirements 4.1, 4.2: Combine ML predictions with structural and semantic analysis.
 */
public class ScoreCombiner {
    
    private static final Logger logger = Logger.getLogger(ScoreCombiner.class.getName());

    private final double mlWeight;
    private final double structuralWeight;
    private final double semanticWeight;
    
    /**
     * Creates a ScoreCombiner with weights from configuration.
     */
    public ScoreCombiner() {
        MLConfig config = ConfigManager.getInstance().getMLConfig();
        this.mlWeight = config.mlWeight();
        this.structuralWeight = config.structuralWeight();
        this.semanticWeight = config.semanticWeight();
        
        if (!config.areWeightsValid()) {
            logger.warning("Score weights do not sum to 1.0, results may be unexpected");
        }
    }
    
    /**
     * Creates a ScoreCombiner with explicit weights (for testing).
     */
    public ScoreCombiner(double mlWeight, double structuralWeight, double semanticWeight) {
        this.mlWeight = mlWeight;
        this.structuralWeight = structuralWeight;
        this.semanticWeight = semanticWeight;
    }
    
    // Legacy constants for backward compatibility
    /** @deprecated Use ConfigManager instead */
    @Deprecated
    public static final double ML_WEIGHT = 0.5;
    /** @deprecated Use ConfigManager instead */
    @Deprecated
    public static final double STRUCTURAL_WEIGHT = 0.3;
    /** @deprecated Use ConfigManager instead */
    @Deprecated
    public static final double SEMANTIC_WEIGHT = 0.2;

    /**
     * Combines scores from ML, structural, and semantic sources using weighted formula.
     * Formula: (mlScore * 0.5) + (structuralScore * 0.3) + (semanticScore * 0.2)
     * 
     * @param mlScore ML prediction score [0, 1]
     * @param structuralScore structural analysis score [0, 1]
     * @param semanticScore semantic analysis score [0, 1]
     * @return combined score [0, 1]
     */
    public double combine(double mlScore, double structuralScore, double semanticScore) {
        // Clamp input scores to valid range [0, 1]
        double clampedMl = clamp(mlScore);
        double clampedStructural = clamp(structuralScore);
        double clampedSemantic = clamp(semanticScore);
        
        // Apply weighted combination formula
        double result = (clampedMl * mlWeight) 
                      + (clampedStructural * structuralWeight) 
                      + (clampedSemantic * semanticWeight);
        
        logger.fine(String.format("Score combination: ML=%.3f, Structural=%.3f, Semantic=%.3f -> Combined=%.3f",
            clampedMl, clampedStructural, clampedSemantic, result));
        
        // Ensure result is in valid range (should be by construction, but be safe)
        return clamp(result);
    }

    /**
     * Determines which source contributed most to the final score.
     * Compares the weighted contributions of each source.
     * 
     * @param mlScore ML prediction score [0, 1]
     * @param structuralScore structural analysis score [0, 1]
     * @param semanticScore semantic analysis score [0, 1]
     * @return the PredictionSource that contributed most to the final score
     */
    public PredictionSource getPrimarySource(double mlScore, double structuralScore, double semanticScore) {
        // Calculate weighted contributions
        double mlContribution = clamp(mlScore) * mlWeight;
        double structuralContribution = clamp(structuralScore) * structuralWeight;
        double semanticContribution = clamp(semanticScore) * semanticWeight;
        
        // Find the maximum contribution
        double maxContribution = Math.max(mlContribution, 
                                 Math.max(structuralContribution, semanticContribution));
        
        // Handle edge case where all contributions are equal or very close
        double epsilon = 0.0001;
        int equalCount = 0;
        if (Math.abs(mlContribution - maxContribution) < epsilon) equalCount++;
        if (Math.abs(structuralContribution - maxContribution) < epsilon) equalCount++;
        if (Math.abs(semanticContribution - maxContribution) < epsilon) equalCount++;
        
        if (equalCount > 1) {
            return PredictionSource.COMBINED;
        }
        
        // Return the source with highest contribution
        if (Math.abs(mlContribution - maxContribution) < epsilon) {
            return PredictionSource.ML;
        } else if (Math.abs(structuralContribution - maxContribution) < epsilon) {
            return PredictionSource.STRUCTURAL;
        } else {
            return PredictionSource.SEMANTIC;
        }
    }

    /**
     * Clamps a value to the range [0, 1].
     * Handles NaN and infinite values.
     * 
     * @param value the value to clamp
     * @return clamped value in [0, 1]
     */
    private double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.5; // Default for NaN
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? 1.0 : 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
