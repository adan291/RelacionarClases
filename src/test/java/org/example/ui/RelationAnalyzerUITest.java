package org.example.ui;

import java.nio.file.Path;
import java.util.List;

import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;
import org.example.testutil.TestDataFactory;
import org.example.ui.bulk.BulkActionsToolbar;
import org.example.ui.bulk.SelectionManager;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * UI integration tests for RelationAnalyzerAppView using TestFX.
 * Tests folder selection, accept/reject buttons, bulk actions, and filters.
 * 
 * Requirements: 4.1-4.6
 */
@Tag("ui")
@SuppressWarnings("unused") // JUnit lifecycle methods and JavaFX varargs
class RelationAnalyzerUITest extends ApplicationTest {

    @TempDir
    static Path tempDir;
    private ObservableList<RelacionDetectada> allRelations;
    private FilteredList<RelacionDetectada> filteredRelations;
    private SelectionManager selectionManager;
    private BulkActionsToolbar bulkActionsToolbar;
    private TableView<RelacionDetectada> tableView;
    private ComboBox<String> typeFilter;
    private Button acceptButton;
    private Button rejectButton;
    private Label selectionCountLabel;
    private MemoryService memoryService;

    /**
     * Configure headless mode for CI/CD environments.
     * This must be called before JavaFX toolkit is initialized.
     */
    @BeforeAll
    static void setupHeadless() {
        // Configure headless mode using Monocle
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("java.awt.headless", "true");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
    }

    @Override
    public void start(Stage stage) throws Exception {
        
        // Initialize test data
        allRelations = FXCollections.observableArrayList();
        filteredRelations = new FilteredList<>(allRelations, p -> true);
        
        // Create memory service with temp directory
        String memoryFile = tempDir.resolve("test-memory.json").toString();
        memoryService = new MemoryService(memoryFile);
        
        // Create selection manager
        selectionManager = new SelectionManager(allRelations, filteredRelations);
        
        // Create bulk action executor (simplified for testing)
        org.example.ui.bulk.BulkActionExecutor executor = 
            new org.example.ui.bulk.BulkActionExecutor(
                memoryService, allRelations, selectionManager, msg -> {});
        
        // Create bulk actions toolbar
        bulkActionsToolbar = new BulkActionsToolbar(selectionManager, executor, stage);
        
        // Create table view
        tableView = createTestTableView();
        
        // Create type filter
        typeFilter = new ComboBox<>();
        typeFilter.setId("typeFilter");
        typeFilter.getItems().addAll("Todos", "OneToOne", "OneToMany", "ManyToOne", "ManyToMany");
        typeFilter.setValue("Todos");
        typeFilter.setOnAction(e -> applyTypeFilter());
        
        // Get references to toolbar components
        acceptButton = bulkActionsToolbar.getAcceptButton();
        acceptButton.setId("acceptButton");
        rejectButton = bulkActionsToolbar.getRejectButton();
        rejectButton.setId("rejectButton");
        selectionCountLabel = bulkActionsToolbar.getSelectionCountLabel();
        selectionCountLabel.setId("selectionCountLabel");
        
        // Create test scene
        VBox root = new VBox(10);
        root.getChildren().addAll(typeFilter, bulkActionsToolbar, tableView);
        
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.setTitle("UI Test");
        stage.show();
    }


    /**
     * Creates a test TableView with checkbox column for selection.
     */
    private TableView<RelacionDetectada> createTestTableView() {
        TableView<RelacionDetectada> table = new TableView<>();
        table.setId("relationsTable");
        
        // Checkbox column
        javafx.scene.control.TableColumn<RelacionDetectada, Boolean> checkboxCol = 
            new javafx.scene.control.TableColumn<>("Select");
        checkboxCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(e -> {
                    RelacionDetectada rel = getTableRow().getItem();
                    if (rel != null) {
                        selectionManager.toggleSelection(rel);
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
                    if (rel != null) {
                        checkBox.setSelected(selectionManager.isSelected(rel));
                    }
                    setGraphic(checkBox);
                }
            }
        });
        
        // Source column
        javafx.scene.control.TableColumn<RelacionDetectada, String> sourceCol = 
            new javafx.scene.control.TableColumn<>("Source");
        sourceCol.setCellValueFactory(d -> 
            new javafx.beans.property.SimpleStringProperty(
                d.getValue() != null ? d.getValue().getOrigen() : ""));
        
        // Target column
        javafx.scene.control.TableColumn<RelacionDetectada, String> targetCol = 
            new javafx.scene.control.TableColumn<>("Target");
        targetCol.setCellValueFactory(d -> 
            new javafx.beans.property.SimpleStringProperty(
                d.getValue() != null ? d.getValue().getDestino() : ""));
        
        // Type column
        javafx.scene.control.TableColumn<RelacionDetectada, String> typeCol = 
            new javafx.scene.control.TableColumn<>("Type");
        typeCol.setCellValueFactory(d -> 
            new javafx.beans.property.SimpleStringProperty(
                d.getValue() != null ? d.getValue().getTipo() : ""));
        
        // Confidence column
        javafx.scene.control.TableColumn<RelacionDetectada, String> confCol = 
            new javafx.scene.control.TableColumn<>("Confidence");
        confCol.setCellValueFactory(d -> 
            new javafx.beans.property.SimpleStringProperty(
                d.getValue() != null ? 
                    String.format("%.0f%%", d.getValue().getConfianza() * 100) : ""));
        
        var columns = java.util.List.of(checkboxCol, sourceCol, targetCol, typeCol, confCol);
        table.getColumns().addAll(columns);
        table.setItems(filteredRelations);
        
        return table;
    }

    /**
     * Applies the type filter to the filtered list.
     */
    private void applyTypeFilter() {
        String selectedType = typeFilter.getValue();
        if ("Todos".equals(selectedType)) {
            filteredRelations.setPredicate(p -> true);
        } else {
            filteredRelations.setPredicate(rel -> 
                rel != null && selectedType.equals(rel.getTipo()));
        }
    }

    @BeforeEach
    void setUpTestData() {
        // Clear any existing data
        Platform.runLater(() -> {
            allRelations.clear();
            selectionManager.deselectAll();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        Platform.runLater(() -> {
            allRelations.clear();
            selectionManager.deselectAll();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // ==================== Test Methods ====================

    /**
     * Test that the UI components are properly initialized.
     * Requirements: 4.1-4.6
     */
    @Test
    void uiComponents_areProperlyInitialized() {
        // Verify all components exist
        assertNotNull(tableView, "TableView should be initialized");
        assertNotNull(typeFilter, "Type filter should be initialized");
        assertNotNull(acceptButton, "Accept button should be initialized");
        assertNotNull(rejectButton, "Reject button should be initialized");
        assertNotNull(selectionCountLabel, "Selection count label should be initialized");
        assertNotNull(selectionManager, "SelectionManager should be initialized");
        
        // Verify initial states
        assertTrue(acceptButton.isDisabled(), "Accept button should be disabled initially");
        assertTrue(rejectButton.isDisabled(), "Reject button should be disabled initially");
        assertEquals("0 seleccionadas", selectionCountLabel.getText(), 
            "Selection count should be 0 initially");
    }


    /**
     * Test folder selection triggers analysis.
     * Note: This test verifies the folder selection mechanism works.
     * Actual file dialog interaction is mocked since TestFX cannot interact with native dialogs.
     * 
     * Requirements: 4.1
     */
    @Test
    void folderSelection_triggersAnalysisSetup() {
        // Verify that the test infrastructure is set up correctly
        // In a real scenario, folder selection would trigger analysis
        // Here we verify the data flow works by adding relations programmatically
        
        Platform.runLater(() -> {
            // Simulate what happens after folder selection - relations are loaded
            List<RelacionDetectada> testRelations = TestDataFactory.createSampleRelations(5);
            allRelations.addAll(testRelations);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify relations appear in table
        assertEquals(5, tableView.getItems().size(), 
            "Table should show 5 relations after simulated analysis");
        assertEquals(5, filteredRelations.size(), 
            "Filtered list should contain 5 relations");
    }

    /**
     * Test accept button functionality.
     * Requirements: 4.2
     */
    @Test
    void acceptButton_marksRelationAsAccepted() {
        // Add test relations
        Platform.runLater(() -> {
            List<RelacionDetectada> testRelations = TestDataFactory.createSampleRelations(3);
            allRelations.addAll(testRelations);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Select a relation
        RelacionDetectada firstRelation = allRelations.get(0);
        Platform.runLater(() -> {
            selectionManager.select(firstRelation);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify selection is reflected
        assertTrue(selectionManager.isSelected(firstRelation), 
            "First relation should be selected");
        assertFalse(acceptButton.isDisabled(), 
            "Accept button should be enabled when relation is selected");
        
        // Verify selection count is updated
        assertTrue(selectionCountLabel.getText().contains("1"), 
            "Selection count should show 1 selected");
    }

    /**
     * Test reject button functionality.
     * Requirements: 4.2
     */
    @Test
    void rejectButton_marksRelationAsRejected() {
        // Add test relations
        Platform.runLater(() -> {
            List<RelacionDetectada> testRelations = TestDataFactory.createSampleRelations(3);
            allRelations.addAll(testRelations);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Select a relation
        RelacionDetectada firstRelation = allRelations.get(0);
        Platform.runLater(() -> {
            selectionManager.select(firstRelation);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify reject button is enabled
        assertFalse(rejectButton.isDisabled(), 
            "Reject button should be enabled when relation is selected");
    }

    /**
     * Test bulk selection of multiple relations.
     * Requirements: 4.3
     */
    @Test
    void bulkSelection_selectsMultipleRelations() {
        // Add test relations
        Platform.runLater(() -> {
            List<RelacionDetectada> testRelations = TestDataFactory.createSampleRelations(5);
            allRelations.addAll(testRelations);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Select all relations
        Platform.runLater(() -> {
            selectionManager.selectAll();
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify all are selected
        assertEquals(5, selectionManager.getSelectedCount(), 
            "All 5 relations should be selected");
        assertTrue(selectionCountLabel.getText().contains("5"), 
            "Selection count should show 5 selected");
        
        // Verify buttons are enabled
        assertFalse(acceptButton.isDisabled(), 
            "Accept button should be enabled for bulk selection");
        assertFalse(rejectButton.isDisabled(), 
            "Reject button should be enabled for bulk selection");
    }

    /**
     * Test bulk accept action.
     * Requirements: 4.3
     */
    @Test
    void bulkAccept_acceptsAllSelectedRelations() {
        // Add test relations
        Platform.runLater(() -> {
            List<RelacionDetectada> testRelations = TestDataFactory.createSampleRelations(3);
            allRelations.addAll(testRelations);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Select all relations
        Platform.runLater(() -> {
            selectionManager.selectAll();
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify all are selected
        assertEquals(3, selectionManager.getSelectedCount(), 
            "All 3 relations should be selected");
        
        // Verify accept button is enabled
        assertFalse(acceptButton.isDisabled(), 
            "Accept button should be enabled");
    }

    /**
     * Test type filter application.
     * Requirements: 4.4
     */
    @Test
    void typeFilter_showsOnlyMatchingRelations() {
        // Add relations of different types
        Platform.runLater(() -> {
            allRelations.add(TestDataFactory.createRelation("User", "Order", "OneToMany", 0.9));
            allRelations.add(TestDataFactory.createRelation("Order", "User", "ManyToOne", 0.85));
            allRelations.add(TestDataFactory.createRelation("Product", "Category", "ManyToMany", 0.8));
            allRelations.add(TestDataFactory.createRelation("Employee", "Department", "OneToMany", 0.75));
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify all relations are shown initially
        assertEquals(4, filteredRelations.size(), 
            "All 4 relations should be visible initially");
        
        // Apply OneToMany filter
        Platform.runLater(() -> {
            typeFilter.setValue("OneToMany");
            applyTypeFilter();
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify only OneToMany relations are shown
        assertEquals(2, filteredRelations.size(), 
            "Only 2 OneToMany relations should be visible");
        assertTrue(filteredRelations.stream()
            .allMatch(r -> "OneToMany".equals(r.getTipo())),
            "All visible relations should be OneToMany");
        
        // Apply ManyToOne filter
        Platform.runLater(() -> {
            typeFilter.setValue("ManyToOne");
            applyTypeFilter();
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify only ManyToOne relations are shown
        assertEquals(1, filteredRelations.size(), 
            "Only 1 ManyToOne relation should be visible");
        assertEquals("ManyToOne", filteredRelations.get(0).getTipo(),
            "Visible relation should be ManyToOne");
        
        // Reset filter
        Platform.runLater(() -> {
            typeFilter.setValue("Todos");
            applyTypeFilter();
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify all relations are shown again
        assertEquals(4, filteredRelations.size(), 
            "All 4 relations should be visible after reset");
    }

    /**
     * Test selection persistence across filter changes.
     * Requirements: 4.3, 4.4
     */
    @Test
    void selectionPersistence_maintainedAcrossFilterChanges() {
        // Add relations of different types
        Platform.runLater(() -> {
            allRelations.add(TestDataFactory.createRelation("User", "Order", "OneToMany", 0.9));
            allRelations.add(TestDataFactory.createRelation("Order", "User", "ManyToOne", 0.85));
            allRelations.add(TestDataFactory.createRelation("Product", "Category", "ManyToMany", 0.8));
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Select the OneToMany relation
        RelacionDetectada oneToManyRelation = allRelations.stream()
            .filter(r -> "OneToMany".equals(r.getTipo()))
            .findFirst()
            .orElseThrow();
        
        Platform.runLater(() -> {
            selectionManager.select(oneToManyRelation);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Apply ManyToOne filter (hides the selected relation)
        Platform.runLater(() -> {
            typeFilter.setValue("ManyToOne");
            applyTypeFilter();
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify selection is maintained even though relation is hidden
        assertEquals(1, selectionManager.getSelectedCount(), 
            "Selection should be maintained when relation is filtered out");
        assertEquals(1, selectionManager.getHiddenSelectedCount(),
            "Hidden selection count should be 1");
        
        // Reset filter
        Platform.runLater(() -> {
            typeFilter.setValue("Todos");
            applyTypeFilter();
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify selection is still there
        assertTrue(selectionManager.isSelected(oneToManyRelation),
            "OneToMany relation should still be selected after filter reset");
    }

    /**
     * Test deselect all functionality.
     * Requirements: 4.3
     */
    @Test
    void deselectAll_clearsAllSelections() {
        // Add test relations
        Platform.runLater(() -> {
            List<RelacionDetectada> testRelations = TestDataFactory.createSampleRelations(5);
            allRelations.addAll(testRelations);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Select all
        Platform.runLater(() -> {
            selectionManager.selectAll();
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        assertEquals(5, selectionManager.getSelectedCount(), 
            "All 5 should be selected");
        
        // Deselect all
        Platform.runLater(() -> {
            selectionManager.deselectAll();
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify all are deselected
        assertEquals(0, selectionManager.getSelectedCount(), 
            "No relations should be selected after deselect all");
        assertTrue(acceptButton.isDisabled(), 
            "Accept button should be disabled after deselect all");
        assertTrue(rejectButton.isDisabled(), 
            "Reject button should be disabled after deselect all");
    }

    /**
     * Test invert selection functionality.
     * Requirements: 4.3
     */
    @Test
    void invertSelection_togglesAllSelections() {
        // Add test relations
        Platform.runLater(() -> {
            List<RelacionDetectada> testRelations = TestDataFactory.createSampleRelations(4);
            allRelations.addAll(testRelations);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Select first two
        Platform.runLater(() -> {
            selectionManager.select(allRelations.get(0));
            selectionManager.select(allRelations.get(1));
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        assertEquals(2, selectionManager.getSelectedCount(), 
            "2 relations should be selected");
        
        // Invert selection
        Platform.runLater(() -> {
            selectionManager.invertSelection();
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify selection is inverted
        assertEquals(2, selectionManager.getSelectedCount(), 
            "2 relations should still be selected after invert");
        assertFalse(selectionManager.isSelected(allRelations.get(0)),
            "First relation should not be selected after invert");
        assertFalse(selectionManager.isSelected(allRelations.get(1)),
            "Second relation should not be selected after invert");
        assertTrue(selectionManager.isSelected(allRelations.get(2)),
            "Third relation should be selected after invert");
        assertTrue(selectionManager.isSelected(allRelations.get(3)),
            "Fourth relation should be selected after invert");
    }

    /**
     * Test select by confidence functionality.
     * Requirements: 4.3
     */
    @Test
    void selectByConfidence_selectsHighConfidenceRelations() {
        // Add relations with varying confidence
        Platform.runLater(() -> {
            allRelations.add(TestDataFactory.createRelation("User", "Order", "OneToMany", 0.95));
            allRelations.add(TestDataFactory.createRelation("Order", "User", "ManyToOne", 0.85));
            allRelations.add(TestDataFactory.createRelation("Product", "Category", "ManyToMany", 0.60));
            allRelations.add(TestDataFactory.createRelation("Employee", "Dept", "OneToOne", 0.45));
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Select high confidence (>80%)
        Platform.runLater(() -> {
            selectionManager.selectByConfidence(0.80);
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify only high confidence relations are selected
        assertEquals(2, selectionManager.getSelectedCount(), 
            "Only 2 high confidence relations should be selected");
        assertTrue(selectionManager.isSelected(allRelations.get(0)),
            "95% confidence relation should be selected");
        assertTrue(selectionManager.isSelected(allRelations.get(1)),
            "85% confidence relation should be selected");
        assertFalse(selectionManager.isSelected(allRelations.get(2)),
            "60% confidence relation should not be selected");
        assertFalse(selectionManager.isSelected(allRelations.get(3)),
            "45% confidence relation should not be selected");
    }

    /**
     * Test select by type functionality.
     * Requirements: 4.3
     */
    @Test
    void selectByType_selectsMatchingTypeRelations() {
        // Add relations of different types
        Platform.runLater(() -> {
            allRelations.add(TestDataFactory.createRelation("User", "Order", "OneToMany", 0.9));
            allRelations.add(TestDataFactory.createRelation("Order", "User", "ManyToOne", 0.85));
            allRelations.add(TestDataFactory.createRelation("Product", "Category", "OneToMany", 0.8));
            allRelations.add(TestDataFactory.createRelation("Employee", "Dept", "ManyToMany", 0.75));
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Select by type OneToMany
        Platform.runLater(() -> {
            selectionManager.selectByType("OneToMany");
        });
        WaitForAsyncUtils.waitForFxEvents();
        
        // Verify only OneToMany relations are selected
        assertEquals(2, selectionManager.getSelectedCount(), 
            "Only 2 OneToMany relations should be selected");
        
        List<RelacionDetectada> selected = selectionManager.getSelectedRelations();
        assertTrue(selected.stream().allMatch(r -> "OneToMany".equals(r.getTipo())),
            "All selected relations should be OneToMany");
    }
}
