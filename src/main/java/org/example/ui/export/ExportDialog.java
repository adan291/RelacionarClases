package org.example.ui.export;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.example.export.DiagramExportService;
import org.example.export.DiagramFormat;
import org.example.export.ExportOptions;
import org.example.export.ExportResult;
import org.example.model.RelacionDetectada;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Dialog for exporting relations to diagram formats (PlantUML/Mermaid).
 * Requirements: 5.2, 5.3, 5.4
 */
public class ExportDialog extends Dialog<ExportResult> {
    
    private static final Logger logger = Logger.getLogger(ExportDialog.class.getName());
    
    private final DiagramExportService exportService = new DiagramExportService();
    private final List<RelacionDetectada> relations;
    
    // Format selection
    private ComboBox<DiagramFormat> formatCombo;
    
    // Filter controls - Requirements: 3.1, 3.2, 3.3, 3.4
    private CheckBox acceptedOnlyCheckBox;
    private CheckBox includePendingCheckBox;
    private Slider confidenceSlider;
    private Label confidenceLabel;
    
    // Relation type filters - Requirements: 3.3
    private CheckBox oneToOneCheckBox;
    private CheckBox oneToManyCheckBox;
    private CheckBox manyToOneCheckBox;
    private CheckBox manyToManyCheckBox;
    
    // Styling options - Requirements: 3.5, 6.1
    private CheckBox showConfidenceCheckBox;
    private CheckBox colorByPackageCheckBox;
    
    // Preview - Requirements: 5.3
    private TextArea previewArea;
    private Label statsLabel;
    private ExportResult currentResult;
    
    public ExportDialog(List<RelacionDetectada> relations) {
        this.relations = relations;
        
        setTitle("📄 Exportar Diagrama");
        setHeaderText("Exportar relaciones a formato de diagrama");
        setResizable(true);
        
        setupContent();
        setupButtons();
        
        // Generate initial preview
        updatePreview();
    }


    private void setupContent() {
        VBox mainContent = new VBox(10);
        mainContent.setPadding(new Insets(15));
        
        // Format selection row
        HBox formatRow = createFormatRow();
        
        // Filters section
        VBox filtersSection = createFiltersSection();
        
        // Styling options section
        VBox stylingSection = createStylingSection();
        
        // Stats label
        statsLabel = new Label();
        statsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        
        // Preview area - Requirements: 5.3
        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefRowCount(18);
        previewArea.setPrefColumnCount(65);
        previewArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 11px;");
        
        VBox previewBox = new VBox(5, new Label("Vista previa:"), previewArea);
        VBox.setVgrow(previewArea, Priority.ALWAYS);
        
        mainContent.getChildren().addAll(
            formatRow,
            new Separator(),
            filtersSection,
            new Separator(),
            stylingSection,
            new Separator(),
            statsLabel,
            previewBox
        );
        
        VBox.setVgrow(previewBox, Priority.ALWAYS);
        getDialogPane().setContent(mainContent);
        getDialogPane().setPrefWidth(600);
        getDialogPane().setPrefHeight(650);
    }
    
    /**
     * Creates the format selection row.
     */
    private HBox createFormatRow() {
        Label formatLabel = new Label("Formato:");
        formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll(DiagramFormat.values());
        formatCombo.setValue(DiagramFormat.PLANTUML);
        formatCombo.setOnAction(e -> updatePreview());
        
        HBox row = new HBox(10, formatLabel, formatCombo);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
    
    /**
     * Creates the filters section with all filter controls.
     * Requirements: 3.1, 3.2, 3.3, 3.4
     */
    private VBox createFiltersSection() {
        Label sectionLabel = new Label("Filtros:");
        sectionLabel.setStyle("-fx-font-weight: bold;");
        
        // Status filters - Requirements: 3.1, 3.4
        acceptedOnlyCheckBox = new CheckBox("Solo relaciones aceptadas");
        acceptedOnlyCheckBox.setSelected(false);
        acceptedOnlyCheckBox.setOnAction(e -> updatePreview());
        
        includePendingCheckBox = new CheckBox("Incluir pendientes");
        includePendingCheckBox.setSelected(true);
        includePendingCheckBox.setOnAction(e -> updatePreview());
        
        HBox statusFilters = new HBox(20, acceptedOnlyCheckBox, includePendingCheckBox);
        
        // Confidence filter - Requirements: 3.2
        Label confLabel = new Label("Confianza mínima:");
        confidenceSlider = new Slider(0, 100, 0);
        confidenceSlider.setShowTickLabels(true);
        confidenceSlider.setShowTickMarks(true);
        confidenceSlider.setMajorTickUnit(25);
        confidenceSlider.setPrefWidth(200);
        confidenceSlider.valueProperty().addListener((obs, old, val) -> {
            confidenceLabel.setText(String.format("%.0f%%", val.doubleValue()));
            updatePreview();
        });
        
        confidenceLabel = new Label("0%");
        confidenceLabel.setMinWidth(40);
        
        HBox confidenceRow = new HBox(10, confLabel, confidenceSlider, confidenceLabel);
        confidenceRow.setAlignment(Pos.CENTER_LEFT);
        
        // Relation type filters - Requirements: 3.3
        Label typesLabel = new Label("Tipos:");
        oneToOneCheckBox = new CheckBox("1:1");
        oneToOneCheckBox.setSelected(true);
        oneToOneCheckBox.setOnAction(e -> updatePreview());
        
        oneToManyCheckBox = new CheckBox("1:N");
        oneToManyCheckBox.setSelected(true);
        oneToManyCheckBox.setOnAction(e -> updatePreview());
        
        manyToOneCheckBox = new CheckBox("N:1");
        manyToOneCheckBox.setSelected(true);
        manyToOneCheckBox.setOnAction(e -> updatePreview());
        
        manyToManyCheckBox = new CheckBox("N:M");
        manyToManyCheckBox.setSelected(true);
        manyToManyCheckBox.setOnAction(e -> updatePreview());
        
        HBox typesRow = new HBox(15, typesLabel, oneToOneCheckBox, oneToManyCheckBox, 
                                  manyToOneCheckBox, manyToManyCheckBox);
        typesRow.setAlignment(Pos.CENTER_LEFT);
        
        VBox section = new VBox(8, sectionLabel, statusFilters, confidenceRow, typesRow);
        return section;
    }
    
    /**
     * Creates the styling options section.
     * Requirements: 3.5, 6.1
     */
    private VBox createStylingSection() {
        Label sectionLabel = new Label("Opciones:");
        sectionLabel.setStyle("-fx-font-weight: bold;");
        
        showConfidenceCheckBox = new CheckBox("Mostrar scores de confianza");
        showConfidenceCheckBox.setSelected(false);
        showConfidenceCheckBox.setOnAction(e -> updatePreview());
        
        colorByPackageCheckBox = new CheckBox("Colorear por paquete");
        colorByPackageCheckBox.setSelected(false);
        colorByPackageCheckBox.setOnAction(e -> updatePreview());
        
        HBox optionsRow = new HBox(20, showConfidenceCheckBox, colorByPackageCheckBox);
        
        VBox section = new VBox(8, sectionLabel, optionsRow);
        return section;
    }

    
    /**
     * Sets up the dialog buttons.
     * Requirements: 4.3, 5.4
     */
    private void setupButtons() {
        // Add buttons
        ButtonType copyButton = new ButtonType("📋 Copiar");
        ButtonType saveButton = new ButtonType("💾 Guardar...");
        ButtonType cancelButton = ButtonType.CANCEL;
        
        getDialogPane().getButtonTypes().addAll(copyButton, saveButton, cancelButton);
        
        // Handle copy button - Requirements: 4.3, 5.4
        Button copyBtn = (Button) getDialogPane().lookupButton(copyButton);
        copyBtn.setOnAction(e -> {
            if (currentResult != null) {
                exportService.copyToClipboard(currentResult);
                showInfo("Copiado", "Copiado al portapapeles");
            }
            e.consume();
        });
        
        // Handle save button - Requirements: 4.1, 4.2
        Button saveBtn = (Button) getDialogPane().lookupButton(saveButton);
        saveBtn.setOnAction(e -> {
            if (currentResult != null) {
                saveToFile();
            }
            e.consume();
        });
        
        // Set result converter
        setResultConverter(buttonType -> {
            if (buttonType == copyButton || buttonType == saveButton) {
                return currentResult;
            }
            return null;
        });
    }

    /**
     * Builds ExportOptions from current UI state.
     */
    private ExportOptions buildOptions() {
        DiagramFormat format = formatCombo.getValue();
        boolean acceptedOnly = acceptedOnlyCheckBox.isSelected();
        boolean includePending = includePendingCheckBox.isSelected();
        double minConfidence = confidenceSlider.getValue() / 100.0;
        
        // Build relation types set
        Set<String> relationTypes = new HashSet<>();
        if (oneToOneCheckBox.isSelected()) relationTypes.add("OneToOne");
        if (oneToManyCheckBox.isSelected()) relationTypes.add("OneToMany");
        if (manyToOneCheckBox.isSelected()) relationTypes.add("ManyToOne");
        if (manyToManyCheckBox.isSelected()) relationTypes.add("ManyToMany");
        
        boolean showConfidence = showConfidenceCheckBox.isSelected();
        boolean colorByPackage = colorByPackageCheckBox.isSelected();
        
        return new ExportOptions(
            format,
            acceptedOnly,
            includePending,
            minConfidence,
            relationTypes,
            showConfidence,
            colorByPackage
        );
    }

    /**
     * Updates the preview when options change.
     * Requirements: 5.3
     */
    private void updatePreview() {
        ExportOptions options = buildOptions();
        
        currentResult = exportService.export(relations, options);
        previewArea.setText(currentResult.content());
        statsLabel.setText(String.format("Entidades: %d | Relaciones: %d | Formato: %s", 
            currentResult.entityCount(), 
            currentResult.relationCount(),
            currentResult.format().getDisplayName()));
        
        logger.fine(() -> "Preview updated: " + currentResult.entityCount() + 
            " entities, " + currentResult.relationCount() + " relations");
    }
    
    /**
     * Opens file save dialog and saves the diagram.
     * Requirements: 4.1, 4.2, 4.4, 5.4
     */
    private void saveToFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar Diagrama");
        fileChooser.setInitialFileName(currentResult.suggestedFilename());
        
        DiagramFormat format = formatCombo.getValue();
        if (format == DiagramFormat.PLANTUML) {
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PlantUML", "*.puml"));
        } else {
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown", "*.md"));
        }
        
        File file = fileChooser.showSaveDialog(getOwner());
        if (file != null) {
            try {
                exportService.saveToFile(currentResult, file.toPath());
                showInfo("Guardado", "Diagrama guardado en:\n" + file.getAbsolutePath());
            } catch (Exception ex) {
                logger.warning(() -> "Failed to save file: " + ex.getMessage());
                showError("Error", "No se pudo guardar el archivo: " + ex.getMessage());
            }
        }
    }
    
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
