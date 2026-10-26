package org.example.config;

/**
 * Configuration record for ML-related settings.
 * 
 * @param minDecisions Minimum decisions required for ML availability
 * @param mlWeight Weight for ML score in combination (0-1)
 * @param structuralWeight Weight for structural score in combination (0-1)
 * @param semanticWeight Weight for semantic score in combination (0-1)
 * @param learningRate Learning rate for logistic regression
 * @param maxIterations Maximum iterations for model training
 */
public record MLConfig(
    int minDecisions,
    double mlWeight,
    double structuralWeight,
    double semanticWeight,
    double learningRate,
    int maxIterations
) {
    /** Default configuration values */
    public static final MLConfig DEFAULT = new MLConfig(
        10,     // minDecisions
        0.5,    // mlWeight
        0.3,    // structuralWeight
        0.2,    // semanticWeight
        0.01,   // learningRate
        1000    // maxIterations
    );
    
    /**
     * Validates that weights sum to 1.0 (within tolerance).
     * @return true if weights are valid
     */
    public boolean areWeightsValid() {
        double sum = mlWeight + structuralWeight + semanticWeight;
        return Math.abs(sum - 1.0) < 0.001;
    }
}
