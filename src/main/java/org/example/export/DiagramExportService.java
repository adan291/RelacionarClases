package org.example.export;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.example.model.RelacionDetectada;

/**
 * Service for exporting relations to diagram formats.
 */
public class DiagramExportService {

    private static final java.util.regex.Pattern INVALID_FILENAME_CHARS =
        java.util.regex.Pattern.compile("[^a-zA-Z0-9._-]");
    private static final java.util.regex.Pattern MULTI_UNDERSCORE = java.util.regex.Pattern.compile("_+");
    
    private static final Logger logger = Logger.getLogger(DiagramExportService.class.getName());
    private static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    private final PlantUMLGenerator plantUMLGenerator;
    private final MermaidGenerator mermaidGenerator;
    
    public DiagramExportService() {
        this.plantUMLGenerator = new PlantUMLGenerator();
        this.mermaidGenerator = new MermaidGenerator();
    }
    
    /**
     * Exports relations to a diagram with default project name.
     * 
     * @param relations the relations to export
     * @param options export options
     * @return the export result
     */
    public ExportResult export(List<RelacionDetectada> relations, ExportOptions options) {
        return export(relations, options, "diagram");
    }
    
    /**
     * Exports relations to a diagram with specified project name.
     * 
     * @param relations the relations to export
     * @param options export options
     * @param projectName the project name for filename generation
     * @return the export result
     */
    public ExportResult export(List<RelacionDetectada> relations, ExportOptions options, String projectName) {
        // Apply filters
        List<RelacionDetectada> filtered = applyFilters(relations, options);
        
        // Generate content based on format
        String content = switch (options.format()) {
            case PLANTUML -> plantUMLGenerator.generate(filtered, options);
            case MERMAID -> mermaidGenerator.generate(filtered, options);
        };
        
        // Count entities
        Set<String> entities = new LinkedHashSet<>();
        for (RelacionDetectada rel : filtered) {
            entities.add(rel.getOrigen());
            entities.add(rel.getDestino());
        }
        
        String filename = generateFilename(projectName, options.format());
        
        return new ExportResult(content, entities.size(), filtered.size(), filename, options.format());
    }
    
    /**
     * Generates a filename based on project name and format.
     * Format: {projectName}_relations_{timestamp}.{extension}
     * 
     * @param projectName the project name
     * @param format the diagram format
     * @return the generated filename
     */
    public String generateFilename(String projectName, DiagramFormat format) {
        String sanitizedName = sanitizeFilename(projectName);
        String timestamp = LocalDateTime.now().format(FILENAME_DATE_FORMAT);
        return String.format("%s_relations_%s%s", sanitizedName, timestamp, format.getExtension());
    }
    
    /**
     * Sanitizes a project name for use in a filename.
     * Removes or replaces invalid characters.
     */
    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "diagram";
        }
        // Replace invalid filename characters with underscores
        return MULTI_UNDERSCORE.matcher(INVALID_FILENAME_CHARS.matcher(name).replaceAll("_")).replaceAll("_")
                   .toLowerCase(Locale.ROOT);
    }

    /**
     * Applies filter options to relations.
     */
    private List<RelacionDetectada> applyFilters(List<RelacionDetectada> relations, ExportOptions options) {
        if (relations == null) {
            return List.of();
        }
        
        return relations.stream()
            .filter(rel -> rel.getConfianza() >= options.minConfidence())
            .filter(rel -> {
                if (options.relationTypes().isEmpty()) {
                    return true;
                }
                return options.relationTypes().contains(rel.getTipo());
            })
            .toList();
    }
    
    /**
     * Saves export result to a file.
     * 
     * @param result the export result
     * @param path the file path
     * @throws IOException if saving fails
     */
    public void saveToFile(ExportResult result, Path path) throws IOException {
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writer.write(result.content());
            logger.info(() -> "Saved diagram to: " + path);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e, () -> "Failed to save diagram: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Saves export result to a file in the specified directory.
     * Uses the suggested filename from the result.
     * 
     * @param result the export result
     * @param directory the directory to save to
     * @return the path where the file was saved
     * @throws IOException if saving fails
     */
    public Path saveToDirectory(ExportResult result, Path directory) throws IOException {
        Path filePath = directory.resolve(result.suggestedFilename());
        saveToFile(result, filePath);
        return filePath;
    }
    
    /**
     * Copies export result to system clipboard.
     * 
     * @param result the export result
     */
    public void copyToClipboard(ExportResult result) {
        StringSelection selection = new StringSelection(result.content());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        logger.info("Copied diagram to clipboard");
    }
}
