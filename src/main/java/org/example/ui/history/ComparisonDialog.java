package org.example.ui.history;

import java.time.format.DateTimeFormatter;

import org.example.model.ComparisonResult;
import org.example.model.DecisionState;
import org.example.model.RelationChange;
import org.example.model.RelationSnapshot;
import org.example.model.SnapshotMetadata;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * 📊 ComparisonDialog
 * Dialog for displaying snapshot comparison results.
 * Shows categorized changes: added, removed, type changes, and decision changes.
 */
public class ComparisonDialog {

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    
    private Stage dialogStage;

    /**
     * Shows the comparison dialog with the specified results.
     * 
     * @param owner the owner stage
     * @param result the comparison result to display
     */
    public void show(Stage owner, ComparisonResult result) {
        dialogStage = new Stage();
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(owner);
        dialogStage.setTitle("📊 Comparación de Snapshots");
        
        VBox root = createMainLayout(result);
        Scene scene = new Scene(root, 750, 600);
        
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    /**
     * Creates the main layout for the comparison dialog.
     */
    private VBox createMainLayout(ComparisonResult result) {
        // Header with snapshot info
        VBox header = createHeader(result);
        
        // Warning banner if different projects
        Node warningBanner = null;
        if (!result.isSameProject()) {
            warningBanner = createWarningBanner();
        }
        
        // Summary stats
        HBox summaryBox = createSummaryBox(result);
        
        // Diff view
        ScrollPane diffScrollPane = new ScrollPane(buildDiffView(result));
        diffScrollPane.setFitToWidth(true);
        VBox.setVgrow(diffScrollPane, Priority.ALWAYS);
        
        // Close button
        Button closeButton = styledButton("✖ Cerrar", "#9E9E9E");
        closeButton.setOnAction(e -> dialogStage.close());
        
        HBox buttonBar = new HBox(closeButton);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new Insets(10));
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.getChildren().add(header);
        if (warningBanner != null) {
            root.getChildren().add(warningBanner);
        }
        root.getChildren().addAll(summaryBox, diffScrollPane, buttonBar);
        
        return root;
    }

    /**
     * Creates the header with snapshot information.
     */
    private VBox createHeader(ComparisonResult result) {
        Label titleLabel = new Label("📊 Comparación de Snapshots");
        titleLabel.setFont(Font.font("Segoe UI Semibold", 18));
        
        SnapshotMetadata snap1 = result.getSnapshot1();
        SnapshotMetadata snap2 = result.getSnapshot2();
        
        String snap1Info = formatSnapshotInfo(snap1, "Anterior");
        String snap2Info = formatSnapshotInfo(snap2, "Actual");
        
        Label snap1Label = new Label(snap1Info);
        snap1Label.setFont(Font.font("Segoe UI", 11));
        snap1Label.setStyle("-fx-text-fill: #666;");
        
        Label snap2Label = new Label(snap2Info);
        snap2Label.setFont(Font.font("Segoe UI", 11));
        snap2Label.setStyle("-fx-text-fill: #666;");
        
        VBox header = new VBox(5, titleLabel, snap1Label, snap2Label);
        header.setPadding(new Insets(0, 0, 10, 0));
        
        return header;
    }

    /**
     * Formats snapshot info for display.
     */
    private String formatSnapshotInfo(SnapshotMetadata metadata, String label) {
        if (metadata == null) {
            return label + ": N/A";
        }
        String timestamp = metadata.getTimestamp() != null 
            ? metadata.getTimestamp().format(DISPLAY_DATE_FORMAT) 
            : "N/A";
        return String.format("%s: %s (%s) - %d relaciones", 
            label, metadata.getProjectName(), timestamp, metadata.getTotalRelations());
    }

    /**
     * Creates a warning banner for different projects.
     */
    private Node createWarningBanner() {
        Label warningLabel = new Label("⚠️ Los snapshots son de proyectos diferentes. " +
            "La comparación puede no ser significativa.");
        warningLabel.setFont(Font.font("Segoe UI", 12));
        warningLabel.setStyle("-fx-background-color: #FFF3CD; -fx-text-fill: #856404; " +
            "-fx-padding: 10; -fx-background-radius: 5;");
        warningLabel.setMaxWidth(Double.MAX_VALUE);
        return warningLabel;
    }

    /**
     * Creates the summary statistics box.
     */
    private HBox createSummaryBox(ComparisonResult result) {
        Label addedLabel = createStatLabel("➕ Añadidas", result.getAddedCount(), "#4CAF50");
        Label removedLabel = createStatLabel("➖ Eliminadas", result.getRemovedCount(), "#f44336");
        Label typeLabel = createStatLabel("🔄 Tipo cambiado", result.getTypeChangeCount(), "#FF9800");
        Label decisionLabel = createStatLabel("📋 Decisión cambiada", result.getDecisionChangeCount(), "#2196F3");
        
        HBox summaryBox = new HBox(15, addedLabel, removedLabel, typeLabel, decisionLabel);
        summaryBox.setAlignment(Pos.CENTER);
        summaryBox.setPadding(new Insets(10));
        summaryBox.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-background-radius: 5;");
        
        return summaryBox;
    }

    /**
     * Creates a stat label with count.
     */
    private Label createStatLabel(String text, int count, String color) {
        Label label = new Label(text + ": " + count);
        label.setFont(Font.font("Segoe UI Semibold", 12));
        label.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
            "-fx-padding: 5 10; -fx-background-radius: 5;");
        return label;
    }


    /**
     * Builds the diff view with categorized changes.
     */
    private Node buildDiffView(ComparisonResult result) {
        VBox diffView = new VBox(10);
        diffView.setPadding(new Insets(10));
        
        // Added relations section
        if (!result.getAdded().isEmpty()) {
            TitledPane addedPane = createSection("➕ Relaciones Añadidas (" + result.getAddedCount() + ")",
                "#4CAF50", result.getAdded().stream()
                    .map(this::formatAddedRelation)
                    .toList());
            diffView.getChildren().add(addedPane);
        }
        
        // Removed relations section
        if (!result.getRemoved().isEmpty()) {
            TitledPane removedPane = createSection("➖ Relaciones Eliminadas (" + result.getRemovedCount() + ")",
                "#f44336", result.getRemoved().stream()
                    .map(this::formatRemovedRelation)
                    .toList());
            diffView.getChildren().add(removedPane);
        }
        
        // Type changes section
        if (!result.getTypeChanges().isEmpty()) {
            TitledPane typePane = createSection("🔄 Cambios de Tipo (" + result.getTypeChangeCount() + ")",
                "#FF9800", result.getTypeChanges().stream()
                    .map(this::formatTypeChange)
                    .toList());
            diffView.getChildren().add(typePane);
        }
        
        // Decision changes section
        if (!result.getDecisionChanges().isEmpty()) {
            TitledPane decisionPane = createSection("📋 Cambios de Decisión (" + result.getDecisionChangeCount() + ")",
                "#2196F3", result.getDecisionChanges().stream()
                    .map(this::formatDecisionChange)
                    .toList());
            diffView.getChildren().add(decisionPane);
        }
        
        // No changes message
        if (result.getTotalChanges() == 0) {
            Label noChangesLabel = new Label("✅ No se detectaron cambios entre los snapshots.");
            noChangesLabel.setFont(Font.font("Segoe UI", 14));
            noChangesLabel.setStyle("-fx-text-fill: #4CAF50;");
            diffView.getChildren().add(noChangesLabel);
        }
        
        return diffView;
    }

    /**
     * Creates a collapsible section with items.
     */
    private TitledPane createSection(String title, String headerColor, java.util.List<Node> items) {
        VBox content = new VBox(5);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(items);
        
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(true);
        pane.setStyle("-fx-font-weight: bold; -fx-color: " + headerColor + ";");
        
        return pane;
    }

    /**
     * Formats an added relation for display.
     */
    private Node formatAddedRelation(RelationSnapshot rel) {
        String text = String.format("+ %s → %s : %s (%.0f%%)",
            rel.getOrigen(), rel.getDestino(), rel.getTipo(), rel.getConfianza() * 100);
        
        Label label = new Label(text);
        label.setFont(Font.font("Consolas", 11));
        label.setStyle("-fx-text-fill: #2E7D32; -fx-background-color: #E8F5E9; " +
            "-fx-padding: 5; -fx-background-radius: 3;");
        label.setMaxWidth(Double.MAX_VALUE);
        
        return label;
    }

    /**
     * Formats a removed relation for display.
     */
    private Node formatRemovedRelation(RelationSnapshot rel) {
        String text = String.format("- %s → %s : %s (%.0f%%)",
            rel.getOrigen(), rel.getDestino(), rel.getTipo(), rel.getConfianza() * 100);
        
        Label label = new Label(text);
        label.setFont(Font.font("Consolas", 11));
        label.setStyle("-fx-text-fill: #C62828; -fx-background-color: #FFEBEE; " +
            "-fx-padding: 5; -fx-background-radius: 3;");
        label.setMaxWidth(Double.MAX_VALUE);
        
        return label;
    }

    /**
     * Formats a type change for display.
     */
    private Node formatTypeChange(RelationChange change) {
        RelationSnapshot before = change.getBefore();
        RelationSnapshot after = change.getAfter();
        
        String text = String.format("~ %s → %s : %s ➜ %s",
            before.getOrigen(), before.getDestino(), 
            before.getTipo(), after.getTipo());
        
        Label label = new Label(text);
        label.setFont(Font.font("Consolas", 11));
        label.setStyle("-fx-text-fill: #E65100; -fx-background-color: #FFF3E0; " +
            "-fx-padding: 5; -fx-background-radius: 3;");
        label.setMaxWidth(Double.MAX_VALUE);
        
        return label;
    }

    /**
     * Formats a decision change for display.
     */
    private Node formatDecisionChange(RelationChange change) {
        RelationSnapshot before = change.getBefore();
        RelationSnapshot after = change.getAfter();
        
        String beforeState = formatDecisionState(before.getDecisionState());
        String afterState = formatDecisionState(after.getDecisionState());
        
        String text = String.format("📋 %s → %s : %s | %s ➜ %s",
            before.getOrigen(), before.getDestino(), before.getTipo(),
            beforeState, afterState);
        
        Label label = new Label(text);
        label.setFont(Font.font("Consolas", 11));
        label.setStyle("-fx-text-fill: #1565C0; -fx-background-color: #E3F2FD; " +
            "-fx-padding: 5; -fx-background-radius: 3;");
        label.setMaxWidth(Double.MAX_VALUE);
        
        return label;
    }

    /**
     * Formats a decision state for display.
     */
    private String formatDecisionState(DecisionState state) {
        if (state == null) {
            return "⏳ Pendiente";
        }
        return switch (state) {
            case ACCEPTED -> "✅ Aceptada";
            case REJECTED -> "❌ Rechazada";
            case PENDING -> "⏳ Pendiente";
        };
    }

    /**
     * Creates a styled button.
     */
    private Button styledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Segoe UI", 12));
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                     "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 6 12;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: derive(" + color + 
                             ", 20%); -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 6 12;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + color + 
                            "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 6 12;"));
        return btn;
    }
}
