package org.example.export;

/**
 * Supported diagram export formats.
 */
public enum DiagramFormat {
    PLANTUML(".puml", "PlantUML"),
    MERMAID(".md", "Mermaid");
    
    private final String extension;
    private final String displayName;
    
    DiagramFormat(String extension, String displayName) {
        this.extension = extension;
        this.displayName = displayName;
    }
    
    public String getExtension() {
        return extension;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
