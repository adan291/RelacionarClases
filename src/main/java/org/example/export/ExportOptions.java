package org.example.export;

import java.util.Set;

/**
 * Configuration options for diagram export.
 * 
 * @param format the diagram format (PlantUML or Mermaid)
 * @param acceptedOnly if true, only export accepted relations
 * @param includePending if true, include pending relations
 * @param minConfidence minimum confidence threshold (0.0-1.0)
 * @param relationTypes set of relation types to include (empty = all)
 * @param showConfidence if true, show confidence values in diagram
 * @param colorByPackage if true, group/color entities by package
 */
public record ExportOptions(
    DiagramFormat format,
    boolean acceptedOnly,
    boolean includePending,
    double minConfidence,
    Set<String> relationTypes,
    boolean showConfidence,
    boolean colorByPackage
) {
    /**
     * Default export options.
     */
    public static final ExportOptions DEFAULT = new ExportOptions(
        DiagramFormat.PLANTUML,
        false,      // acceptedOnly
        true,       // includePending
        0.0,        // minConfidence
        Set.of(),   // relationTypes (empty = all)
        false,      // showConfidence
        false       // colorByPackage
    );
    
    /**
     * Creates default export options.
     * @return default options
     */
    public static ExportOptions defaults() {
        return DEFAULT;
    }
    
    /**
     * Creates a copy with a different format.
     */
    public ExportOptions withFormat(DiagramFormat format) {
        return new ExportOptions(format, acceptedOnly, includePending, 
            minConfidence, relationTypes, showConfidence, colorByPackage);
    }
    
    /**
     * Creates a copy with acceptedOnly filter.
     */
    public ExportOptions withAcceptedOnly(boolean acceptedOnly) {
        return new ExportOptions(format, acceptedOnly, includePending,
            minConfidence, relationTypes, showConfidence, colorByPackage);
    }
    
    /**
     * Creates a copy with minimum confidence threshold.
     */
    public ExportOptions withMinConfidence(double minConfidence) {
        return new ExportOptions(format, acceptedOnly, includePending,
            minConfidence, relationTypes, showConfidence, colorByPackage);
    }
}
