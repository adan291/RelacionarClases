package org.example.config;

import java.util.List;

/**
 * Configuration record for analysis-related settings.
 * 
 * @param defaultConfidenceThreshold Default confidence threshold for filtering
 * @param highConfidenceThreshold Threshold for high confidence smart selection
 * @param ignoredPaths List of paths to ignore during analysis
 * @param fileExtensions List of file extensions to analyze
 */
public record AnalysisConfig(
    double defaultConfidenceThreshold,
    double highConfidenceThreshold,
    List<String> ignoredPaths,
    List<String> fileExtensions
) {
    /** Default configuration values */
    public static final AnalysisConfig DEFAULT = new AnalysisConfig(
        0.0,    // defaultConfidenceThreshold
        0.8,    // highConfidenceThreshold
        List.of("target", "build", ".git", "node_modules"),  // ignoredPaths
        List.of(".java")  // fileExtensions
    );
}
