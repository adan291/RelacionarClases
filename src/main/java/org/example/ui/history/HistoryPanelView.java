package org.example.ui.history;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.model.AnalysisSnapshot;
import org.example.model.ComparisonResult;
import org.example.model.SnapshotMetadata;
import org.example.service.AnalysisHistoryService;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * 📜 HistoryPanelView
 * JavaFX component for browsing and managing analysis history snapshots.
 * Provides functionality to view, restore, delete, and compare snapshots.
 */
public class HistoryPanelView {
    
    private static final Logger logger = Logger.getLogger(HistoryPanelView.class.getName());

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    
    private final AnalysisHistoryService historyService;
    private final ObservableList<SnapshotMetadata> snapshotList;
    private final ListView<SnapshotMetadata> listView;
    private final VBox detailsPane;
    private final Label detailsLabel;
    
    // Action buttons
    private Button restoreButton;
    private Button deleteButton;
    private Button compareButton;
    
    // Callbacks
    private Consumer<AnalysisSnapshot> onRestoreCallback;
    private boolean hasUnsavedChanges = false;
    
    private Stage dialogStage;

    /**
     * Creates a new HistoryPanelView with the specified history service.
     * 
     * @param historyService the service for managing snapshots
     */
    public HistoryPanelView(AnalysisHistoryService historyService) {
        this.historyService = historyService;
        this.snapshotList = FXCollections.observableArrayList();
        this.listView = new ListView<>(snapshotList);
        this.detailsPane = new VBox(10);
        this.detailsLabel = new Label();
        
        setupListView();
        setupDetailsPane();
    }


    /**
     * Sets up the ListView with custom cell factory and selection handling.
     */
    private void setupListView() {
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setCellFactory(lv -> new SnapshotListCell());
        listView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> onSnapshotSelected(newVal));
        
        // Update button states when selection changes
        listView.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<SnapshotMetadata>) change -> updateButtonStates());
    }

    /**
     * Sets up the details pane for displaying snapshot information.
     */
    private void setupDetailsPane() {
        detailsPane.setPadding(new Insets(10));
        detailsPane.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-background-radius: 5;");
        detailsPane.setMinWidth(300);
        
        Label titleLabel = new Label("📋 Detalles del Snapshot");
        titleLabel.setFont(Font.font("Segoe UI Semibold", 14));
        
        detailsLabel.setWrapText(true);
        detailsLabel.setFont(Font.font("Segoe UI", 12));
        
        detailsPane.getChildren().addAll(titleLabel, detailsLabel);
    }

    /**
     * Shows the history panel as a modal dialog.
     * 
     * @param owner the owner stage
     */
    public void show(Stage owner) {
        dialogStage = new Stage();
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(owner);
        dialogStage.setTitle("📜 Historial de Análisis");
        
        VBox root = createMainLayout();
        Scene scene = new Scene(root, 850, 550);
        
        dialogStage.setScene(scene);
        refresh();
        dialogStage.showAndWait();
    }

    /**
     * Creates the main layout for the history panel.
     */
    private VBox createMainLayout() {
        // Header
        Label titleLabel = new Label("📜 Historial de Análisis");
        titleLabel.setFont(Font.font("Segoe UI Semibold", 20));
        
        HBox header = new HBox(titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 15, 10, 15));
        
        // Content: ListView + Details
        VBox listContainer = new VBox(5);
        listContainer.getChildren().add(listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
        HBox.setHgrow(listContainer, Priority.ALWAYS);
        
        HBox contentBox = new HBox(15, listContainer, detailsPane);
        contentBox.setPadding(new Insets(10));
        VBox.setVgrow(contentBox, Priority.ALWAYS);
        
        // Action buttons
        HBox buttonBar = createButtonBar();
        
        VBox root = new VBox(10, header, contentBox, buttonBar);
        root.setPadding(new Insets(10));
        
        return root;
    }

    /**
     * Creates the button bar with action buttons.
     */
    private HBox createButtonBar() {
        restoreButton = styledButton("🔄 Restaurar", "#4CAF50");
        deleteButton = styledButton("🗑️ Eliminar", "#f44336");
        compareButton = styledButton("📊 Comparar", "#2196F3");
        Button refreshButton = styledButton("🔃 Actualizar", "#FF9800");
        Button closeButton = styledButton("✖ Cerrar", "#9E9E9E");
        
        restoreButton.setOnAction(e -> onRestore());
        deleteButton.setOnAction(e -> onDelete());
        compareButton.setOnAction(e -> onCompare());
        refreshButton.setOnAction(e -> refresh());
        closeButton.setOnAction(e -> dialogStage.close());
        
        // Initially disable action buttons
        restoreButton.setDisable(true);
        deleteButton.setDisable(true);
        compareButton.setDisable(true);
        
        HBox buttonBar = new HBox(10, restoreButton, deleteButton, compareButton, 
                                   refreshButton, closeButton);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new Insets(10));
        
        return buttonBar;
    }

    /**
     * Updates the enabled state of action buttons based on selection.
     */
    private void updateButtonStates() {
        int selectedCount = listView.getSelectionModel().getSelectedItems().size();
        
        restoreButton.setDisable(selectedCount != 1);
        deleteButton.setDisable(selectedCount == 0);
        compareButton.setDisable(selectedCount != 2);
    }

    /**
     * Refreshes the snapshot list from the history service.
     */
    public void refresh() {
        List<SnapshotMetadata> snapshots = historyService.listSnapshots();
        snapshotList.setAll(snapshots);
        final int snapshotCount = snapshots.size();
        logger.info(() -> "Loaded " + snapshotCount + " snapshots from history");
        
        if (snapshots.isEmpty()) {
            detailsLabel.setText("ℹ️ No hay snapshots guardados.\n\n" +
                "Usa el botón '💾 Guardar' en la ventana principal\n" +
                "para crear un snapshot del análisis actual.");
        } else {
            detailsLabel.setText("Selecciona un snapshot para ver sus detalles.");
        }
        
        updateButtonStates();
    }

    /**
     * Handles snapshot selection to display details.
     */
    private void onSnapshotSelected(SnapshotMetadata metadata) {
        if (metadata == null) {
            detailsLabel.setText("Selecciona un snapshot para ver sus detalles.");
            return;
        }
        
        StringBuilder details = new StringBuilder();
        details.append("📌 ID: ").append(metadata.getId()).append("\n\n");
        
        if (metadata.getTimestamp() != null) {
            details.append("📅 Fecha: ").append(metadata.getTimestamp().format(DISPLAY_DATE_FORMAT)).append("\n\n");
        }
        
        details.append("📁 Proyecto: ").append(metadata.getProjectName()).append("\n");
        details.append("📂 Ruta: ").append(metadata.getProjectPath()).append("\n\n");
        
        if (metadata.getDescription() != null && !metadata.getDescription().isEmpty()) {
            details.append("📝 Descripción: ").append(metadata.getDescription()).append("\n\n");
        }
        
        // Statistics
        details.append("📊 Estadísticas:\n");
        details.append("   • Total relaciones: ").append(metadata.getTotalRelations()).append("\n");
        details.append("   • ✅ Aceptadas: ").append(metadata.getAcceptedCount()).append("\n");
        details.append("   • ❌ Rechazadas: ").append(metadata.getRejectedCount()).append("\n");
        details.append("   • ⏳ Pendientes: ").append(metadata.getPendingCount()).append("\n\n");
        
        // Type breakdown
        if (metadata.getTypeBreakdown() != null && !metadata.getTypeBreakdown().isEmpty()) {
            details.append("📈 Por tipo:\n");
            metadata.getTypeBreakdown().forEach((type, count) -> 
                details.append("   • ").append(type).append(": ").append(count).append("\n"));
        }
        
        detailsLabel.setText(details.toString());
    }


    /**
     * Handles the restore action.
     */
    private void onRestore() {
        SnapshotMetadata selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        
        // Check for unsaved changes
        if (hasUnsavedChanges) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Cambios sin guardar");
            confirmAlert.setHeaderText("Hay cambios sin guardar en el análisis actual");
            confirmAlert.setContentText("¿Deseas continuar y perder los cambios actuales?");
            
            if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        
        // Confirm restore
        Alert confirmRestore = new Alert(Alert.AlertType.CONFIRMATION);
        confirmRestore.setTitle("Confirmar restauración");
        confirmRestore.setHeaderText("¿Restaurar snapshot?");
        confirmRestore.setContentText("Se restaurará el estado del análisis desde:\n" + 
            selected.getId() + "\n\nFecha: " + 
            (selected.getTimestamp() != null ? selected.getTimestamp().format(DISPLAY_DATE_FORMAT) : "N/A"));
        
        if (confirmRestore.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        
        try {
            AnalysisSnapshot snapshot = historyService.restoreSnapshot(selected.getId());
            if (snapshot != null && onRestoreCallback != null) {
                logger.info(() -> "Restored snapshot: " + selected.getId());
                onRestoreCallback.accept(snapshot);
                dialogStage.close();
            } else {
                logger.warning(() -> "Failed to restore snapshot: " + selected.getId());
                showError("Error al restaurar", "No se pudo cargar el snapshot.");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e, () -> "Error restoring snapshot: " + e.getMessage());
            showError("Error al restaurar", "Error: " + e.getMessage());
        }
    }

    /**
     * Handles the delete action.
     */
    private void onDelete() {
        List<SnapshotMetadata> selected = new ArrayList<>(
            listView.getSelectionModel().getSelectedItems());
        
        if (selected.isEmpty()) {
            return;
        }
        
        String message = selected.size() == 1 
            ? "¿Eliminar el snapshot seleccionado?\n\n" + selected.get(0).getId()
            : "¿Eliminar " + selected.size() + " snapshots seleccionados?";
        
        Alert confirmDelete = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDelete.setTitle("Confirmar eliminación");
        confirmDelete.setHeaderText("Eliminar snapshots");
        confirmDelete.setContentText(message);
        
        if (confirmDelete.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        
        List<String> ids = selected.stream()
            .map(SnapshotMetadata::getId)
            .toList();
        
        int deleted = historyService.deleteSnapshots(ids);
        
        if (deleted > 0) {
            final int deletedCount = deleted;
            final int totalCount = ids.size();
            logger.info(() -> "Deleted " + deletedCount + " of " + totalCount + " snapshots");
            showInfo("Eliminación completada", 
                "Se eliminaron " + deleted + " de " + ids.size() + " snapshots.");
            refresh();
        } else {
            logger.warning("Failed to delete any snapshots");
            showError("Error al eliminar", "No se pudo eliminar ningún snapshot.");
        }
    }

    /**
     * Handles the compare action.
     */
    private void onCompare() {
        List<SnapshotMetadata> selected = new ArrayList<>(
            listView.getSelectionModel().getSelectedItems());
        
        if (selected.size() != 2) {
            showError("Selección inválida", "Selecciona exactamente 2 snapshots para comparar.");
            return;
        }
        
        SnapshotMetadata snap1 = selected.get(0);
        SnapshotMetadata snap2 = selected.get(1);
        
        // Order by timestamp (older first)
        if (snap1.getTimestamp() != null && snap2.getTimestamp() != null 
            && snap1.getTimestamp().isAfter(snap2.getTimestamp())) {
            SnapshotMetadata temp = snap1;
            snap1 = snap2;
            snap2 = temp;
        }
        
        ComparisonResult result = historyService.compare(snap1.getId(), snap2.getId());
        
        if (result == null) {
            showError("Error al comparar", "No se pudieron cargar los snapshots para comparar.");
            return;
        }
        
        // Show comparison dialog
        ComparisonDialog comparisonDialog = new ComparisonDialog();
        comparisonDialog.show(dialogStage, result);
    }

    /**
     * Sets the callback for restore actions.
     * 
     * @param callback the callback to invoke when a snapshot is restored
     */
    public void setOnRestoreCallback(Consumer<AnalysisSnapshot> callback) {
        this.onRestoreCallback = callback;
    }

    /**
     * Sets whether there are unsaved changes in the current analysis.
     * 
     * @param hasUnsavedChanges true if there are unsaved changes
     */
    public void setHasUnsavedChanges(boolean hasUnsavedChanges) {
        this.hasUnsavedChanges = hasUnsavedChanges;
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

    /**
     * Shows an error alert.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an info alert.
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Custom ListCell for displaying snapshot metadata.
     */
    private class SnapshotListCell extends javafx.scene.control.ListCell<SnapshotMetadata> {
        @Override
        protected void updateItem(SnapshotMetadata item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox cellContent = new VBox(3);
                cellContent.setPadding(new Insets(5));
                
                // Title line: project name + timestamp
                String timestamp = item.getTimestamp() != null 
                    ? item.getTimestamp().format(DISPLAY_DATE_FORMAT) 
                    : "N/A";
                Label titleLine = new Label("📁 " + item.getProjectName() + " - " + timestamp);
                titleLine.setFont(Font.font("Segoe UI Semibold", 12));
                
                // Info line: relation count + description
                String info = "📊 " + item.getTotalRelations() + " relaciones";
                if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                    info += " | 📝 " + item.getDescription();
                }
                Label infoLine = new Label(info);
                infoLine.setFont(Font.font("Segoe UI", 11));
                infoLine.setStyle("-fx-text-fill: #666;");
                
                // Stats line
                String stats = String.format("✅ %d | ❌ %d | ⏳ %d",
                    item.getAcceptedCount(), item.getRejectedCount(), item.getPendingCount());
                Label statsLine = new Label(stats);
                statsLine.setFont(Font.font("Segoe UI", 10));
                statsLine.setStyle("-fx-text-fill: #888;");
                
                cellContent.getChildren().addAll(titleLine, infoLine, statsLine);
                setGraphic(cellContent);
            }
        }
    }
}
