package org.example.export;

/**
 * Result of a diagram export operation.
 * 
 * @param content the generated diagram content
 * @param entityCount number of entities in the diagram
 * @param relationCount number of relations in the diagram
 * @param suggestedFilename suggested filename for saving
 * @param format the format used for export
 */
public record ExportResult(
    String content,
    int entityCount,
    int relationCount,
    String suggestedFilename,
    DiagramFormat format
) {
    /**
     * Creates an empty result.
     */
    public static ExportResult empty(DiagramFormat format) {
        return new ExportResult("", 0, 0, "diagram" + format.getExtension(), format);
    }
}
