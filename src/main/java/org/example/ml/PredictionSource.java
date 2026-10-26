package org.example.ml;

/**
 * Enum representing the source of a relation prediction.
 * Used to indicate which analysis method contributed most to the final score.
 * 
 * Implements Requirements 4.3: Indicate the source of each prediction.
 */
public enum PredictionSource {
    
    /** Prediction primarily based on Machine Learning model */
    ML("Machine Learning"),
    
    /** Prediction primarily based on structural analysis */
    STRUCTURAL("Structural Analysis"),
    
    /** Prediction primarily based on semantic/lexical analysis */
    SEMANTIC("Semantic Analysis"),
    
    /** Prediction based on combined sources with no clear primary */
    COMBINED("Combined Analysis");

    private final String displayName;

    PredictionSource(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the human-readable display name for this source.
     * 
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
