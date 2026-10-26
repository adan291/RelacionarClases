package org.example.ui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.example.analysis.BidirectionalRelationDetector;
import org.example.analysis.IntelligentRelationAnalyzer;
import org.example.codegen.JPACodeGenerator;
import org.example.config.AnalysisConfig;
import org.example.config.ConfigManager;
import org.example.config.UIConfig;
import org.example.controller.RelationAnalysisOrchestrator;
import org.example.ml.PredictionSource;
import org.example.model.AnalysisSnapshot;
import org.example.model.DecisionState;
import org.example.model.RelacionDetectada;
import org.example.model.RelationSnapshot;
import org.example.service.AnalysisHistoryService;
import org.example.service.ConfigService;
import org.example.service.GitRepoService;
import org.example.service.MemoryService;
import org.example.ui.bulk.BulkActionExecutor;
import org.example.ui.bulk.BulkActionsToolbar;
import org.example.ui.bulk.SelectionManager;
import org.example.ui.export.ExportDialog;
import org.example.ui.graph.EntityGraphView;
import org.example.ui.history.HistoryPanelView;
import org.example.ui.theme.ThemeManager;
import org.example.ui.theme.ThemeToggleSwitch;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * 🧠 RelationAnalyzerAppView
 * UI con filtros, tooltips, atajos de teclado y estadísticas.
 */
public class RelationAnalyzerAppView {
    
    private static final Logger logger = Logger.getLogger(RelationAnalyzerAppView.class.getName());

    private final Stage stage;
    private final RelationAnalysisOrchestrator orchestrator;
    private final MemoryService memoryService;
    private final ConfigService configService = new ConfigService();
    private final ThemeManager themeManager = new ThemeManager(configService);
    private final GitRepoService gitService = new GitRepoService();
    private final AnalysisHistoryService historyService = new AnalysisHistoryService();

    private final TableView<RelacionDetectada> table = new TableView<>();
    private final TextArea logArea = new TextArea();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label progressLabel = new Label("Esperando análisis...");
    private final Label mlStatusLabel = new Label();
    private final Label statsLabel = new Label();
    
    // Filtros
    private final ObservableList<RelacionDetectada> allRelations = FXCollections.observableArrayList();
    private FilteredList<RelacionDetectada> filteredRelations;
    private final ComboBox<String> tipoFilter = new ComboBox<>();
    private final ComboBox<String> sourceFilter = new ComboBox<>();
    private final Slider confidenceSlider;
    
    // Configuration values
    private final UIConfig uiConfig;
    private final AnalysisConfig analysisConfig;
    private final TextField searchField = new TextField();
    private final Label confidenceLabel;

    private File carpetaSeleccionada;
    private IntelligentRelationAnalyzer analyzer;
    
    // Unsaved changes tracking
    private boolean hasUnsavedChanges = false;
    
    // Bulk actions components
    private SelectionManager selectionManager;
    private BulkActionExecutor bulkActionExecutor;
    private BulkActionsToolbar bulkActionsToolbar;
    private CheckBox headerCheckBox;

    public RelationAnalyzerAppView(Stage stage) {
        this.stage = stage;
        this.orchestrator = new RelationAnalysisOrchestrator();
        this.memoryService = orchestrator.getMemoryService();
        
        // Load configuration - Requirements: 4.2, 3.1
        this.uiConfig = ConfigManager.getInstance().getUIConfig();
        this.analysisConfig = ConfigManager.getInstance().getAnalysisConfig();
        
        // Initialize confidence slider with configured default threshold
        // The slider uses 0-100 scale, config uses 0.0-1.0 scale
        double defaultConfidencePercent = analysisConfig.defaultConfidenceThreshold() * 100;
        this.confidenceSlider = new Slider(0, 100, defaultConfidencePercent);
        this.confidenceLabel = new Label(String.format("%.0f%%", defaultConfidencePercent));
        
        // Cargar última carpeta usada
        String lastPath = configService.getLastPath();
        if (lastPath != null && !lastPath.isEmpty()) {
            File lastDir = new File(lastPath);
            if (lastDir.exists() && lastDir.isDirectory()) {
                carpetaSeleccionada = lastDir;
            }
        }
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // Use configured window dimensions - Requirements: 4.2
        Scene scene = new Scene(root, uiConfig.windowWidth(), uiConfig.windowHeight());
        themeManager.applySavedTheme(scene);
        
        // Initialize bulk actions components
        setupBulkActions();
        
        // Atajos de teclado globales - Requirements: 6.1, 6.2, 6.3, 6.4, 6.5
        setupKeyboardShortcuts(scene);

        // Header
        Label title = new Label("🧩 Intelligent Relation Analyzer");
        title.setFont(Font.font("Segoe UI Semibold", 24));

        ThemeToggleSwitch themeSwitch = new ThemeToggleSwitch(themeManager);
        HBox header = new HBox();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, themeSwitch.getToggle());
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5, 15, 10, 15));

        root.setTop(header);

        setupFilters();
        setupTable();
        updateMLStatus();

        // Botones principales
        Button selectFolderButton = styledButton("📂 Carpeta", "#8E44AD");
        Button gitButton = styledButton("🌍 Git", "#333333");
        Button analyzeButton = styledButton("🔍 Analizar", "#4CAF50");
        Button graphButton = styledButton("📊 Grafo", "#00BCD4");
        Button applyCodeButton = styledButton("⚡ Aplicar", "#E91E63");
        Button exportButton = styledButton("📤 Exportar", "#FF9800");
        Button diagramButton = styledButton("📄 Exportar Diagrama", "#795548");
        Button saveSnapshotButton = styledButton("💾 Guardar", "#009688");
        Button historyButton = styledButton("📜 Historial", "#673AB7");
        Button viewMemoryButton = styledButton("🧠 Memoria", "#2196F3");
        Button exitButton = styledButton("🚪 Salir", "#9E9E9E");
        
        // Tooltips para botones
        selectFolderButton.setTooltip(createTooltip("Seleccionar carpeta con clases Java"));
        gitButton.setTooltip(createTooltip("Clonar repositorio Git"));
        analyzeButton.setTooltip(createTooltip("Analizar relaciones (F5)"));
        graphButton.setTooltip(createTooltip("Ver grafo de entidades"));
        applyCodeButton.setTooltip(createTooltip("Aplicar anotaciones JPA"));
        exportButton.setTooltip(createTooltip("Exportar a archivo JPA"));
        diagramButton.setTooltip(createTooltip("Exportar diagrama PlantUML/Mermaid (Ctrl+D)"));
        saveSnapshotButton.setTooltip(createTooltip("Guardar snapshot del análisis actual"));
        historyButton.setTooltip(createTooltip("Ver historial de análisis"));
        viewMemoryButton.setTooltip(createTooltip("Ver decisiones guardadas"));

        HBox controls = new HBox(6, selectFolderButton, gitButton, analyzeButton, graphButton,
                                  applyCodeButton, exportButton, diagramButton, saveSnapshotButton, historyButton, 
                                  viewMemoryButton, exitButton);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(8));

        // Status bar con ML y estadísticas
        mlStatusLabel.setFont(Font.font("Segoe UI", 11));
        mlStatusLabel.setPadding(new Insets(4, 10, 4, 10));
        statsLabel.setFont(Font.font("Segoe UI", 11));
        statsLabel.setPadding(new Insets(4, 10, 4, 10));
        
        HBox statusBox = new HBox(15, mlStatusLabel, statsLabel);
        statusBox.setAlignment(Pos.CENTER);

        // Filtros UI
        HBox filterBox = createFilterBar();

        logArea.setEditable(false);
        logArea.setPrefHeight(100);
        logArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");

        VBox progressBox = new VBox(3, progressLabel, progressBar);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(3));

        // Mostrar carpeta seleccionada
        Label folderLabel = new Label(carpetaSeleccionada != null ? 
            "📁 " + carpetaSeleccionada.getAbsolutePath() : "📁 Ninguna carpeta seleccionada");
        folderLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        VBox centerBox = new VBox(8, statusBox, filterBox, table, progressBox, 
                                   controls, folderLabel, logArea);
        centerBox.setAlignment(Pos.CENTER);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(centerBox);

        // Acciones
        selectFolderButton.setOnAction(e -> seleccionarCarpeta(folderLabel));
        gitButton.setOnAction(e -> clonarRepositorio(folderLabel));
        analyzeButton.setOnAction(e -> ejecutarAnalisis());
        graphButton.setOnAction(e -> mostrarGrafo());
        applyCodeButton.setOnAction(e -> mostrarDialogoAplicarCodigo());
        exportButton.setOnAction(e -> exportarJPA());
        diagramButton.setOnAction(e -> mostrarExportDialog());
        saveSnapshotButton.setOnAction(e -> guardarSnapshot());
        historyButton.setOnAction(e -> mostrarHistorial());
        viewMemoryButton.setOnAction(e -> mostrarMemoria());
        exitButton.setOnAction(e -> Platform.exit());

        stage.setScene(scene);
        stage.setTitle("🧠 Intelligent Relation Analyzer");
        stage.show();
        
        if (carpetaSeleccionada != null) {
            log("📁 Última carpeta: " + carpetaSeleccionada.getName());
        }
    }

    private void setupFilters() {
        filteredRelations = new FilteredList<>(allRelations, p -> true);
        
        // Tipo filter
        tipoFilter.getItems().addAll("Todos", "OneToOne", "OneToMany", "ManyToOne", "ManyToMany");
        tipoFilter.setValue("Todos");
        tipoFilter.setOnAction(e -> applyFilters());
        
        // Source filter
        sourceFilter.getItems().addAll("Todas", "ML", "STRUCTURAL", "SEMANTIC", "COMBINED");
        sourceFilter.setValue("Todas");
        sourceFilter.setOnAction(e -> applyFilters());
        
        // Confidence slider
        confidenceSlider.setShowTickLabels(true);
        confidenceSlider.setShowTickMarks(true);
        confidenceSlider.setMajorTickUnit(25);
        confidenceSlider.setPrefWidth(150);
        confidenceSlider.valueProperty().addListener((obs, old, val) -> {
            confidenceLabel.setText(String.format("%.0f%%", val.doubleValue()));
            applyFilters();
        });
        
        // Search field
        searchField.setPromptText("🔍 Buscar clase...");
        searchField.setPrefWidth(150);
        searchField.textProperty().addListener((obs, old, val) -> applyFilters());
    }
    
    private HBox createFilterBar() {
        Label tipoLabel = new Label("Tipo:");
        Label sourceLabel = new Label("Fuente:");
        Label confLabel = new Label("≥");
        
        // Use BulkActionsToolbar instead of old buttons - Requirements: 2.1, 3.1, 3.2
        HBox filterBox = new HBox(8, 
            searchField,
            tipoLabel, tipoFilter,
            sourceLabel, sourceFilter,
            confLabel, confidenceSlider, confidenceLabel
        );
        
        // Add BulkActionsToolbar if initialized
        if (bulkActionsToolbar != null) {
            filterBox.getChildren().add(bulkActionsToolbar);
        }
        
        filterBox.setAlignment(Pos.CENTER);
        filterBox.setPadding(new Insets(5, 10, 5, 10));
        filterBox.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-background-radius: 5;");
        return filterBox;
    }
    
    /**
     * Sets up the bulk actions components.
     * Requirements: 1.1, 1.2, 1.3, 2.1, 3.1, 3.2
     */
    private void setupBulkActions() {
        // Create SelectionManager with allRelations and filteredRelations
        selectionManager = new SelectionManager(allRelations, filteredRelations);
        
        // Create BulkActionExecutor
        bulkActionExecutor = new BulkActionExecutor(
            memoryService,
            allRelations,
            selectionManager,
            this::log
        );
        
        // Create BulkActionsToolbar
        bulkActionsToolbar = new BulkActionsToolbar(
            selectionManager,
            bulkActionExecutor,
            stage
        );
        
        // Set callback for bulk action completion - Requirements: 3.3, 6.5
        bulkActionsToolbar.setOnBulkActionCallback(result -> {
            hasUnsavedChanges = true;
            updateMLStatus();
            updateStats();
        });
        
        // Listen for selection changes to update UI - Requirements: 1.5, 3.3
        selectionManager.addSelectionListener(state -> {
            Platform.runLater(() -> {
                // Update stats when selection changes
                updateStats();
                // Refresh table to update row highlighting
                table.refresh();
                // Update header checkbox state
                if (headerCheckBox != null) {
                    headerCheckBox.setSelected(state.allVisibleSelected() && state.hasSelection());
                    headerCheckBox.setIndeterminate(state.hasSelection() && !state.allVisibleSelected());
                }
            });
        });
    }
    
    /**
     * Sets up keyboard shortcuts for bulk actions.
     * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5
     */
    private void setupKeyboardShortcuts(Scene scene) {
        scene.setOnKeyPressed(e -> {
            // Ctrl+A: selectAll - Requirements: 6.1
            if (e.isControlDown() && !e.isShiftDown() && e.getCode() == KeyCode.A) {
                if (selectionManager != null) {
                    selectionManager.selectAll();
                    e.consume();
                }
            }
            // Ctrl+Shift+A: deselectAll - Requirements: 6.2
            else if (e.isControlDown() && e.isShiftDown() && e.getCode() == KeyCode.A) {
                if (selectionManager != null) {
                    selectionManager.deselectAll();
                    e.consume();
                }
            }
            // Ctrl+Enter: acceptSelected - Requirements: 6.3
            else if (e.isControlDown() && e.getCode() == KeyCode.ENTER) {
                if (bulkActionExecutor != null && selectionManager != null && 
                    selectionManager.getSelectedCount() > 0) {
                    bulkActionExecutor.acceptSelectedWithConfirmation(stage).ifPresent(result -> {
                        hasUnsavedChanges = true;
                        updateMLStatus();
                        updateStats();
                    });
                    e.consume();
                }
            }
            // Ctrl+Delete: rejectSelected - Requirements: 6.4
            else if (e.isControlDown() && e.getCode() == KeyCode.DELETE) {
                if (bulkActionExecutor != null && selectionManager != null && 
                    selectionManager.getSelectedCount() > 0) {
                    bulkActionExecutor.rejectSelectedWithConfirmation(stage).ifPresent(result -> {
                        hasUnsavedChanges = true;
                        updateMLStatus();
                        updateStats();
                    });
                    e.consume();
                }
            }
            // Space on focused row: toggleSelection - Requirements: 6.5
            else if (e.getCode() == KeyCode.SPACE && table.isFocused()) {
                RelacionDetectada selected = table.getSelectionModel().getSelectedItem();
                if (selected != null && selectionManager != null) {
                    selectionManager.toggleSelection(selected);
                    e.consume();
                }
            }
            // Enter: accept single relation (original behavior)
            else if (e.getCode() == KeyCode.ENTER && !e.isControlDown() && 
                     table.getSelectionModel().getSelectedItem() != null) {
                handleFeedback(table.getSelectionModel().getSelectedItem(), true);
            }
            // Delete: reject single relation (original behavior)
            else if (e.getCode() == KeyCode.DELETE && !e.isControlDown() && 
                     table.getSelectionModel().getSelectedItem() != null) {
                handleFeedback(table.getSelectionModel().getSelectedItem(), false);
            }
            // F5: run analysis
            else if (e.getCode() == KeyCode.F5) {
                ejecutarAnalisis();
            }
        });
    }
    
    private void applyFilters() {
        String tipo = tipoFilter.getValue();
        String source = sourceFilter.getValue();
        double minConf = confidenceSlider.getValue() / 100.0;
        String search = searchField.getText().toLowerCase();
        
        filteredRelations.setPredicate(rel -> {
            if (rel == null) return false;
            
            // Filtro tipo
            if (!"Todos".equals(tipo) && !rel.getTipo().equals(tipo)) return false;
            
            // Filtro source
            if (!"Todas".equals(source)) {
                if (rel.getSource() == null || !rel.getSource().name().equals(source)) return false;
            }
            
            // Filtro confianza
            if (rel.getConfianza() < minConf) return false;
            
            // Filtro búsqueda
            if (!search.isEmpty()) {
                return rel.getOrigen().toLowerCase().contains(search) ||
                       rel.getDestino().toLowerCase().contains(search);
            }
            
            return true;
        });
        
        updateStats();
    }

    private void setupTable() {
        // Checkbox column for selection - Requirements: 1.1, 1.2, 1.3
        TableColumn<RelacionDetectada, Boolean> checkboxCol = createCheckboxColumn();
        
        TableColumn<RelacionDetectada, String> origenCol = new TableColumn<>("Origen");
        origenCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue() != null ? d.getValue().getOrigen() : ""));
        origenCol.setPrefWidth(120);

        TableColumn<RelacionDetectada, String> destinoCol = new TableColumn<>("Destino");
        destinoCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue() != null ? d.getValue().getDestino() : ""));
        destinoCol.setPrefWidth(120);

        TableColumn<RelacionDetectada, String> tipoCol = new TableColumn<>("Tipo");
        tipoCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue() != null ? d.getValue().getTipo() : ""));
        tipoCol.setPrefWidth(100);

        TableColumn<RelacionDetectada, String> confCol = new TableColumn<>("Confianza");
        confCol.setCellValueFactory(d -> {
            var item = d.getValue();
            return new ReadOnlyStringWrapper(item == null ? "" : 
                String.format(Locale.ROOT, "%.0f%%", item.getConfianza() * 100));
        });
        confCol.setPrefWidth(80);
        confCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    RelacionDetectada rel = getTableRow().getItem();
                    if (rel != null) {
                        String color = rel.getConfianza() > 0.8 ? "#4CAF50" : 
                                       rel.getConfianza() > 0.5 ? "#FF9800" : "#f44336";
                        setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<RelacionDetectada, String> sourceCol = new TableColumn<>("Fuente");
        sourceCol.setCellValueFactory(d -> {
            var item = d.getValue();
            if (item == null || item.getSource() == null) return new SimpleStringProperty("-");
            return new SimpleStringProperty(item.getSource().name());
        });
        sourceCol.setPrefWidth(90);
        sourceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || "-".equals(item)) {
                    setText(item);
                    setStyle("");
                } else {
                    setText(item);
                    String color = getSourceColor(item);
                    setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                             "-fx-font-weight: bold; -fx-alignment: CENTER;");
                }
            }
        });

        // Columna de acciones
        TableColumn<RelacionDetectada, Void> accionesCol = new TableColumn<>("Acciones");
        accionesCol.setPrefWidth(100);
        accionesCol.setCellFactory(col -> new TableCell<>() {
            private final Button acceptBtn = new Button("✓");
            private final Button rejectBtn = new Button("✗");
            private final HBox box = new HBox(5, acceptBtn, rejectBtn);
            
            {
                acceptBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                                   "-fx-font-weight: bold; -fx-min-width: 30; -fx-padding: 2 8;");
                rejectBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; " +
                                   "-fx-font-weight: bold; -fx-min-width: 30; -fx-padding: 2 8;");
                box.setAlignment(Pos.CENTER);
                
                acceptBtn.setTooltip(createTooltip("Aceptar (Enter)"));
                rejectBtn.setTooltip(createTooltip("Rechazar (Delete)"));
                
                acceptBtn.setOnAction(e -> {
                    RelacionDetectada rel = getTableRow().getItem();
                    if (rel != null) handleFeedback(rel, true);
                });
                rejectBtn.setOnAction(e -> {
                    RelacionDetectada rel = getTableRow().getItem();
                    if (rel != null) handleFeedback(rel, false);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // Columna bidireccional
        TableColumn<RelacionDetectada, String> bidirCol = new TableColumn<>("Bidir");
        bidirCol.setCellValueFactory(d -> {
            var item = d.getValue();
            if (item == null) return new SimpleStringProperty("-");
            if (item.isBidirectional()) {
                return new SimpleStringProperty(item.isOwnerSide() ? "🔑" : "🔗");
            }
            return new SimpleStringProperty("→");
        });
        bidirCol.setPrefWidth(50);
        bidirCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    RelacionDetectada rel = getTableRow().getItem();
                    if (rel != null && rel.isBidirectional()) {
                        String tip = rel.isOwnerSide() ? "Owner (tiene FK)" : "Inverse (mappedBy)";
                        setTooltip(createTooltip(tip));
                    }
                }
            }
        });

        // Add checkbox column first - Requirements: 1.1
        table.getColumns().add(checkboxCol);
        table.getColumns().add(origenCol);
        table.getColumns().add(destinoCol);
        table.getColumns().add(tipoCol);
        table.getColumns().add(bidirCol);
        table.getColumns().add(confCol);
        table.getColumns().add(sourceCol);
        table.getColumns().add(accionesCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setItems(filteredRelations);
        
        // Row con tooltip detallado, estilo para existentes y highlighting para seleccionadas
        // Requirements: 1.5
        table.setRowFactory(tv -> {
            TableRow<RelacionDetectada> row = new TableRow<>();
            row.itemProperty().addListener((obs, old, rel) -> {
                updateRowStyle(row, rel);
            });
            return row;
        });
    }
    
    /**
     * Creates the checkbox column for row selection.
     * Requirements: 1.1, 1.2, 1.3
     */
    private TableColumn<RelacionDetectada, Boolean> createCheckboxColumn() {
        TableColumn<RelacionDetectada, Boolean> checkboxCol = new TableColumn<>();
        checkboxCol.setPrefWidth(40);
        checkboxCol.setMinWidth(40);
        checkboxCol.setMaxWidth(40);
        checkboxCol.setSortable(false);
        checkboxCol.setResizable(false);
        
        // Header checkbox for select all - Requirements: 1.3
        headerCheckBox = new CheckBox();
        headerCheckBox.setTooltip(createTooltip("Seleccionar/deseleccionar todas las visibles"));
        headerCheckBox.setOnAction(e -> {
            if (selectionManager != null) {
                if (headerCheckBox.isSelected()) {
                    selectionManager.selectAll();
                } else {
                    selectionManager.deselectAll();
                }
            }
        });
        checkboxCol.setGraphic(headerCheckBox);
        
        // Update header checkbox state when selection changes
        if (selectionManager != null) {
            selectionManager.addSelectionListener(state -> {
                Platform.runLater(() -> {
                    headerCheckBox.setSelected(state.allVisibleSelected() && state.hasSelection());
                    headerCheckBox.setIndeterminate(state.hasSelection() && !state.allVisibleSelected());
                });
            });
        }
        
        // Cell factory for row checkboxes - Requirements: 1.1, 1.2
        checkboxCol.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            
            {
                checkBox.setOnAction(e -> {
                    RelacionDetectada rel = getTableRow().getItem();
                    if (rel != null && selectionManager != null) {
                        selectionManager.toggleSelection(rel);
                        // Update row style after selection change
                        updateRowStyle(getTableRow(), rel);
                    }
                });
            }
            
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    RelacionDetectada rel = getTableRow().getItem();
                    if (rel != null && selectionManager != null) {
                        checkBox.setSelected(selectionManager.isSelected(rel));
                    }
                    setGraphic(checkBox);
                    setAlignment(Pos.CENTER);
                }
            }
        });
        
        return checkboxCol;
    }
    
    /**
     * Updates the row style based on selection and annotation state.
     * Requirements: 1.5
     */
    private void updateRowStyle(TableRow<RelacionDetectada> row, RelacionDetectada rel) {
        if (rel != null) {
            boolean isSelected = selectionManager != null && selectionManager.isSelected(rel);
            
            // Apply distinct background color for selected rows - Requirements: 1.5
            if (isSelected) {
                row.setStyle("-fx-background-color: rgba(33, 150, 243, 0.25);");
            } else if (rel.isExistingAnnotation()) {
                // Estilo diferente para anotaciones existentes
                row.setStyle("-fx-background-color: rgba(76, 175, 80, 0.15);");
            } else {
                row.setStyle("");
            }
            
            String bidirInfo = rel.isBidirectional() ? 
                "\n🔄 Bidireccional: " + (rel.isOwnerSide() ? "Owner" : "Inverse") : "";
            String existingInfo = rel.isExistingAnnotation() ? "\n📖 Ya anotada en código" : "";
            String selectedInfo = isSelected ? "\n✓ Seleccionada" : "";
            String tooltipText = String.format(
                "%s → %s\nTipo: %s\nConfianza: %.1f%%\nFuente: %s%s%s%s",
                rel.getOrigen(), rel.getDestino(), rel.getTipo(),
                rel.getConfianza() * 100,
                rel.getSource() != null ? rel.getSource().name() : "N/A",
                bidirInfo, existingInfo, selectedInfo
            );
            row.setTooltip(createTooltip(tooltipText));
        } else {
            row.setStyle("");
            row.setTooltip(null);
        }
    }
    
    private Tooltip createTooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.millis(300));
        tooltip.setStyle("-fx-font-size: 11px;");
        return tooltip;
    }

    private void handleFeedback(RelacionDetectada rel, boolean accepted) {
        String clave = rel.getClaveMemoria();
        memoryService.recordarDecision(clave, accepted);
        
        String emoji = accepted ? "✅" : "❌";
        String action = accepted ? "aceptada" : "rechazada";
        log(emoji + " " + rel.getOrigen() + " → " + rel.getDestino() + " (" + rel.getTipo() + ") " + action);
        
        allRelations.remove(rel);
        hasUnsavedChanges = true;
        updateMLStatus();
        updateStats();
        
        // Re-entrenar si hay suficientes datos
        if (analyzer != null && analyzer.getMLPredictor() != null) {
            int count = memoryService.getMemoria().size();
            if (count >= 5 && count % 3 == 0) {
                log("🧠 Re-entrenando modelo ML...");
                new Thread(() -> {
                    analyzer.getMLPredictor().retrain();
                    Platform.runLater(() -> log("✅ Modelo ML actualizado"));
                }).start();
            }
        }
    }
    
    private void updateStats() {
        if (allRelations.isEmpty()) {
            statsLabel.setText("");
            statsLabel.setStyle("");
            return;
        }
        
        long oneToOne = allRelations.stream().filter(r -> "OneToOne".equals(r.getTipo())).count();
        long oneToMany = allRelations.stream().filter(r -> "OneToMany".equals(r.getTipo())).count();
        long manyToOne = allRelations.stream().filter(r -> "ManyToOne".equals(r.getTipo())).count();
        long manyToMany = allRelations.stream().filter(r -> "ManyToMany".equals(r.getTipo())).count();
        
        String stats = String.format("📊 Total: %d | 1:1=%d | 1:N=%d | N:1=%d | N:M=%d | Filtradas: %d",
            allRelations.size(), oneToOne, oneToMany, manyToOne, manyToMany, filteredRelations.size());
        
        statsLabel.setText(stats);
        statsLabel.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; " +
                           "-fx-background-radius: 10; -fx-padding: 4 12;");
    }

    private void updateMLStatus() {
        int decisions = memoryService.getMemoria().size();
        int threshold = 5;
        
        if (decisions >= threshold) {
            mlStatusLabel.setText("🤖 ML Activo (" + decisions + " decisiones)");
            mlStatusLabel.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                                   "-fx-background-radius: 10; -fx-padding: 4 12;");
        } else {
            int needed = threshold - decisions;
            mlStatusLabel.setText("⏳ ML Inactivo (faltan " + needed + ")");
            mlStatusLabel.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; " +
                                   "-fx-background-radius: 10; -fx-padding: 4 12;");
        }
    }

    private String getSourceColor(String source) {
        return switch (source) {
            case "ML" -> "#9C27B0";
            case "STRUCTURAL" -> "#2196F3";
            case "SEMANTIC" -> "#4CAF50";
            case "COMBINED" -> "#FF9800";
            default -> "#9E9E9E";
        };
    }

    private void seleccionarCarpeta(Label folderLabel) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Seleccionar carpeta de clases Java");
        if (carpetaSeleccionada != null) {
            chooser.setInitialDirectory(carpetaSeleccionada.getParentFile());
        }
        File dir = chooser.showDialog(stage);
        if (dir != null && dir.isDirectory()) {
            carpetaSeleccionada = dir;
            folderLabel.setText("📁 " + dir.getAbsolutePath());
            log("📁 Carpeta: " + dir.getAbsolutePath());
            configService.setLastPath(dir.getAbsolutePath());
        }
    }

    private void clonarRepositorio(Label folderLabel) {
        if (!gitService.isGitAvailable()) {
            log("❌ Git no está instalado. Instala Git para usar esta función.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("🌍 Clonar Repositorio Git");
        dialog.setHeaderText("Introduce la URL del repositorio");
        dialog.setContentText("URL:");
        dialog.getEditor().setPrefWidth(400);
        dialog.getEditor().setPromptText("https://github.com/usuario/repo.git");

        dialog.showAndWait().ifPresent(url -> {
            if (url.isBlank()) return;
            
            log("🌍 Clonando repositorio: " + url);
            progressBar.setProgress(-1); // Indeterminate
            
            new Thread(() -> {
                File repoDir = gitService.cloneRepository(url, msg -> Platform.runLater(() -> log(msg)));
                
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    
                    if (repoDir != null) {
                        File srcDir = gitService.findMainSourceFolder(repoDir);
                        if (srcDir != null) {
                            carpetaSeleccionada = srcDir;
                            int javaFiles = gitService.countJavaFiles(srcDir);
                            folderLabel.setText("🌍 " + srcDir.getAbsolutePath());
                            log("✅ Encontrados " + javaFiles + " archivos Java en: " + srcDir.getName());
                            log("💡 Pulsa 'Analizar' para detectar relaciones");
                        } else {
                            log("⚠️ No se encontraron archivos Java en el repositorio");
                        }
                    }
                });
            }).start();
        });
    }

    private void ejecutarAnalisis() {
        if (carpetaSeleccionada == null) {
            log("⚠️ Selecciona una carpeta primero.");
            return;
        }

        log("🚀 Analizando: " + carpetaSeleccionada.getName());
        progressBar.setProgress(0);

        new Thread(() -> {
            try {
                analyzer = new IntelligentRelationAnalyzer(memoryService);
                List<RelacionDetectada> relaciones = orchestrator.ejecutarAnalisis(
                    carpetaSeleccionada.getAbsolutePath());

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    allRelations.setAll(relaciones);
                    updateMLStatus();
                    updateStats();
                    
                    // Contar anotaciones existentes
                    long existingCount = relaciones.stream()
                        .filter(RelacionDetectada::isExistingAnnotation).count();
                    long newCount = relaciones.size() - existingCount;
                    
                    log("✅ " + relaciones.size() + " relaciones detectadas");
                    if (existingCount > 0) {
                        log("📖 " + existingCount + " ya anotadas, " + newCount + " nuevas sugerencias");
                    }
                    
                    // Mostrar inconsistencias si las hay
                    if (analyzer != null) {
                        var inconsistencies = analyzer.getLastInconsistencies();
                        if (inconsistencies != null && !inconsistencies.isEmpty()) {
                            log("⚠️ " + inconsistencies.size() + " inconsistencias encontradas:");
                            inconsistencies.forEach(i -> log("   " + i.toString()));
                        }
                    }
                });

            } catch (RuntimeException ex) {
                Platform.runLater(() -> log("❌ Error: " + ex.getMessage()));
            }
        }).start();
    }

    /**
     * Shows the export dialog for diagram generation.
     * Requirements: 5.1, 5.2, 5.3, 5.4
     */
    private void mostrarExportDialog() {
        List<RelacionDetectada> toExport = filteredRelations.stream().collect(Collectors.toList());
        if (toExport.isEmpty()) {
            log("⚠️ No hay relaciones para exportar a diagrama.");
            return;
        }
        
        ExportDialog dialog = new ExportDialog(toExport);
        dialog.initOwner(stage);
        dialog.showAndWait().ifPresent(result -> {
            // Log success feedback - Requirements: 5.4
            log("📄 Diagrama " + result.format().getDisplayName() + " generado: " + 
                result.entityCount() + " entidades, " + result.relationCount() + " relaciones");
        });
    }

    private void exportarJPA() {
        List<RelacionDetectada> toExport = filteredRelations.stream().collect(Collectors.toList());
        if (toExport.isEmpty()) {
            log("⚠️ No hay relaciones para exportar.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar anotaciones JPA");
        chooser.setInitialFileName("relaciones_jpa.txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text", "*.txt"));
        File file = chooser.showSaveDialog(stage);
        
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("// Anotaciones JPA generadas por Intelligent Relation Analyzer\n");
                writer.write("// Fecha: " + java.time.LocalDateTime.now() + "\n");
                writer.write("// Total: " + toExport.size() + " relaciones\n\n");
                
                for (RelacionDetectada rel : toExport) {
                    writer.write(generateJPAAnnotation(rel));
                    writer.write("\n");
                }
                
                log("📤 Exportado " + toExport.size() + " relaciones a: " + file.getName());
            } catch (IOException e) {
                log("❌ Error exportando: " + e.getMessage());
            }
        }
    }

    private final BidirectionalRelationDetector bidirDetector = new BidirectionalRelationDetector();

    private String generateJPAAnnotation(RelacionDetectada rel) {
        // Usar el detector bidireccional para generar mejor código
        if (rel.isBidirectional()) {
            return bidirDetector.generateBidirectionalJPA(rel);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("// ").append(rel.getOrigen()).append(" -> ").append(rel.getDestino());
        sb.append(" (Confianza: ").append(String.format("%.0f%%", rel.getConfianza() * 100));
        sb.append(", Fuente: ").append(rel.getSource()).append(")\n");
        
        String annotation = switch (rel.getTipo()) {
            case "OneToOne" -> "@OneToOne";
            case "OneToMany" -> "@OneToMany(mappedBy = \"" + rel.getOrigen().toLowerCase() + "\")";
            case "ManyToOne" -> "@ManyToOne\n@JoinColumn(name = \"" + 
                                rel.getDestino().toLowerCase() + "_id\")";
            case "ManyToMany" -> "@ManyToMany";
            default -> "// Tipo desconocido: " + rel.getTipo();
        };
        
        sb.append("// En clase ").append(rel.getOrigen()).append(":\n");
        sb.append(annotation).append("\n");
        sb.append("private ").append(rel.getTipo().contains("Many") ? "List<" + rel.getDestino() + ">" : rel.getDestino());
        sb.append(" ").append(rel.getDestino().toLowerCase());
        sb.append(rel.getTipo().contains("Many") && rel.getTipo().startsWith("One") ? "s" : "");
        sb.append(";\n");
        
        return sb.toString();
    }

    private void mostrarGrafo() {
        if (allRelations.isEmpty()) {
            log("⚠️ No hay relaciones para mostrar. Ejecuta un análisis primero.");
            return;
        }
        
        EntityGraphView graphView = new EntityGraphView();
        graphView.show(new ArrayList<>(allRelations));
        log("📊 Grafo de entidades abierto");
    }

    private void mostrarDialogoAplicarCodigo() {
        if (carpetaSeleccionada == null) {
            log("⚠️ Selecciona una carpeta primero.");
            return;
        }
        
        List<RelacionDetectada> toApply = filteredRelations.stream()
            .filter(r -> !r.isExistingAnnotation())
            .collect(Collectors.toList());
        
        if (toApply.isEmpty()) {
            log("ℹ️ No hay relaciones nuevas para aplicar.");
            return;
        }

        Stage dialogStage = new Stage();
        dialogStage.setTitle("⚡ Aplicar Anotaciones JPA");

        JPACodeGenerator generator = new JPACodeGenerator();
        List<JPACodeGenerator.GenerationResult> preview = generator.previewChanges(carpetaSeleccionada, toApply);

        // Lista de preview
        ListView<String> previewList = new ListView<>();
        for (JPACodeGenerator.GenerationResult result : preview) {
            String icon = result.success ? "✅" : "❌";
            String text = icon + " " + result.className + "." + result.fieldName + " - " + result.message;
            previewList.getItems().add(text);
            if (result.preview != null) {
                previewList.getItems().add("   " + result.preview.replace("\n", "\n   "));
            }
        }

        Label warningLabel = new Label("⚠️ Se crearán backups (.bak) de los archivos modificados");
        warningLabel.setStyle("-fx-text-fill: #FF9800;");

        Button applyBtn = styledButton("⚡ Aplicar Cambios", "#E91E63");
        Button cancelBtn = new Button("Cancelar");

        applyBtn.setOnAction(e -> {
            log("⚡ Aplicando anotaciones JPA...");
            List<JPACodeGenerator.GenerationResult> results = generator.applyAnnotations(carpetaSeleccionada, toApply);
            
            int success = 0, failed = 0;
            for (JPACodeGenerator.GenerationResult r : results) {
                if (r.success) {
                    success++;
                    if (r.preview != null) {
                        log("✅ " + r.className + "." + r.fieldName + ": " + r.message);
                    }
                } else {
                    failed++;
                    log("❌ " + r.className + ": " + r.message);
                }
            }
            
            log("📊 Resultado: " + success + " aplicadas, " + failed + " fallidas");
            dialogStage.close();
            
            // Re-analizar para actualizar la tabla
            ejecutarAnalisis();
        });

        cancelBtn.setOnAction(e -> dialogStage.close());

        HBox buttons = new HBox(10, applyBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox vbox = new VBox(10, 
            new Label("📋 Preview de cambios (" + toApply.size() + " relaciones):"),
            previewList, warningLabel, buttons);
        vbox.setPadding(new Insets(15));
        vbox.setAlignment(Pos.CENTER);

        dialogStage.setScene(new Scene(vbox, 650, 500));
        dialogStage.showAndWait();
    }

    private void mostrarMemoria() {
        Stage memStage = new Stage();
        memStage.setTitle("🧠 Memoria Aprendida");

        ListView<String> list = new ListView<>();
        if (memoryService.getMemoria().isEmpty()) {
            list.getItems().add("ℹ️ Aún no hay relaciones aprendidas.");
        } else {
            memoryService.getMemoria().forEach((r, d) -> {
                String emoji = "aceptada".equals(d) ? "✅" : "❌";
                list.getItems().add(emoji + " " + r);
            });
        }

        Button close = new Button("Cerrar");
        close.setOnAction(e -> memStage.close());

        VBox vbox = new VBox(10, list, close);
        vbox.setPadding(new Insets(15));
        vbox.setAlignment(Pos.CENTER);

        memStage.setScene(new Scene(vbox, 550, 400));
        memStage.showAndWait();
    }

    /**
     * Saves the current analysis state as a snapshot.
     * Requirements: 1.1, 1.2, 1.3
     */
    private void guardarSnapshot() {
        if (carpetaSeleccionada == null) {
            log("⚠️ Selecciona una carpeta primero.");
            return;
        }
        
        if (allRelations.isEmpty()) {
            log("⚠️ No hay relaciones para guardar. Ejecuta un análisis primero.");
            return;
        }
        
        // Show description input dialog (Requirement 1.2)
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("💾 Guardar Snapshot");
        dialog.setHeaderText("Guardar estado del análisis");
        dialog.setContentText("Descripción (opcional):");
        dialog.getEditor().setPromptText("Ej: Análisis inicial del proyecto");
        
        dialog.showAndWait().ifPresent(description -> {
            try {
                // Get current decisions from memory service
                Map<String, Boolean> decisions = new java.util.HashMap<>();
                memoryService.getMemoria().forEach((key, value) -> {
                    decisions.put(key, "aceptada".equals(value));
                });
                
                // Save snapshot (Requirement 1.1)
                String snapshotId = historyService.saveSnapshot(
                    carpetaSeleccionada.getAbsolutePath(),
                    new ArrayList<>(allRelations),
                    decisions,
                    description.isEmpty() ? null : description
                );
                
                // Clear unsaved changes flag
                hasUnsavedChanges = false;
                
                // Display confirmation with snapshot ID (Requirement 1.3)
                log("💾 Snapshot guardado: " + snapshotId);
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Snapshot guardado");
                alert.setHeaderText("El análisis ha sido guardado correctamente");
                alert.setContentText("ID: " + snapshotId);
                alert.showAndWait();
                
            } catch (java.io.IOException e) {
                // Requirement 1.4: Display error message on disk error
                log("❌ Error guardando snapshot: " + e.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error al guardar");
                alert.setHeaderText("No se pudo guardar el snapshot");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
            }
        });
    }
    
    /**
     * Shows the history panel for browsing and managing snapshots.
     * Requirements: 2.1
     */
    private void mostrarHistorial() {
        HistoryPanelView historyPanel = new HistoryPanelView(historyService);
        historyPanel.setHasUnsavedChanges(hasUnsavedChanges);
        historyPanel.setOnRestoreCallback(this::restaurarSnapshot);
        historyPanel.show(stage);
    }
    
    /**
     * Restores the application state from a snapshot.
     * Requirements: 3.1, 3.3
     * 
     * @param snapshot the snapshot to restore
     */
    private void restaurarSnapshot(AnalysisSnapshot snapshot) {
        if (snapshot == null || snapshot.getRelations() == null) {
            log("❌ Error: Snapshot inválido");
            return;
        }
        
        // Convert RelationSnapshots back to RelacionDetectada
        List<RelacionDetectada> restoredRelations = new ArrayList<>();
        for (RelationSnapshot rs : snapshot.getRelations()) {
            RelacionDetectada rel = new RelacionDetectada(
                rs.getOrigen(),
                rs.getDestino(),
                rs.getTipo(),
                rs.getConfianza(),
                rs.getSource() != null ? PredictionSource.valueOf(rs.getSource()) : null
            );
            rel.setBidirectional(rs.isBidirectional());
            rel.setOwnerSide(rs.isOwnerSide());
            rel.setMappedBy(rs.getMappedBy());
            restoredRelations.add(rel);
        }
        
        // Update allRelations list with restored relations (Requirement 3.1)
        allRelations.setAll(restoredRelations);
        
        // Apply decision states to memory service (Requirement 3.1)
        Map<String, Boolean> decisions = snapshot.getDecisions();
        if (decisions != null) {
            for (Map.Entry<String, Boolean> entry : decisions.entrySet()) {
                memoryService.recordarDecision(entry.getKey(), entry.getValue());
            }
        }
        
        // Also apply decisions from relation snapshots
        for (RelationSnapshot rs : snapshot.getRelations()) {
            if (rs.getDecisionState() != DecisionState.PENDING) {
                String key = String.format("%s -> %s : %s", rs.getOrigen(), rs.getDestino(), rs.getTipo());
                memoryService.recordarDecision(key, rs.getDecisionState() == DecisionState.ACCEPTED);
            }
        }
        
        // Clear unsaved changes flag since we just restored
        hasUnsavedChanges = false;
        
        // Refresh UI (Requirement 3.3)
        updateMLStatus();
        updateStats();
        
        // Log restoration event (Requirement 3.3)
        String snapshotId = snapshot.getId() != null ? snapshot.getId() : "desconocido";
        log("🔄 Snapshot restaurado: " + snapshotId);
        log("📊 " + restoredRelations.size() + " relaciones cargadas");
    }

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

    private void log(String msg) {
        logger.info(msg);
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }
}
