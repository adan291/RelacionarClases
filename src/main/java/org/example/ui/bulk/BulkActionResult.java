package org.example.ui.bulk;

/**
 * Immutable record representing the result of a bulk action execution.
 * Contains information about the number of processed relations and timing.
 * 
 * Requirements: 3.5
 */
public record BulkActionResult(
    int processedCount,
    boolean accepted,
    long executionTimeMs
) {
    /**
     * Creates an empty result indicating no relations were processed.
     * 
     * @param accepted whether the action was accept (true) or reject (false)
     * @return an empty BulkActionResult
     */
    public static BulkActionResult empty(boolean accepted) {
        return new BulkActionResult(0, accepted, 0);
    }
}
