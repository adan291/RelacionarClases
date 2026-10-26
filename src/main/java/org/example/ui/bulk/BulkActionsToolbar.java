package org.example.ui.bulk;

import java.util.function.Consumer;
import java.util.logging.Logger;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Toolbar component for bulk actions on selected relations.
 * Contains selection counter, accept/reject buttons, and smart selection menu.
 * 
 * Requirements: 2.1, 3.1, 3.2, 4.1, 4.2, 4.3, 4.4
 */
public class BulkActionsToolbar extends HBox {
    
    private static final Logger logger = Logger.getLogger(BulkActionsToolbar.class.getName());
    
    private final SelectionManager selectionManager;
    private final BulkActionExecutor executor;
    private final Stage owner;
    
    private final Label selectionCountLabel;
    private final Button acceptButton;
    private final Button rejectButton;
    private final MenuButton smartSelectMenu;
    
    // Callback for when bulk actions are executed - Requirements: 3.3
    private Consumer<BulkActionResult> onBulkActionCallback;
    
    /**
     * Creates a new BulkActionsToolbar.
     * 
     * @param selectionManager the selection manager
     * @param executor the bulk action executor
     * @param owner the owner stage for dialogs
     */
    public BulkActionsToolbar(SelectionManager selectionManager,
                              BulkActionExecutor executor,
                              Stage owner) {
        this.selectionManager = selectionManager;
        this.executor = executor;
        this.owner = owner;
        
        // Create components
        this.selectionCountLabel = createSelectionCountLabel();
        this.acceptButton = createAcceptButton();
        this.rejectButton = createRejectButton();
        this.smartSelectMenu = createSmartSelectMenu();
        
        // Layout
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(5, 10, 5, 10));
        
        getChildren().addAll(
            selectionCountLabel,
            acceptButton,
            rejectButton,
            smartSelectMenu
        );

        
        // Listen for selection changes
        selectionManager.addSelectionListener(this::updateButtonStates);
        
        // Initialize button states
        updateButtonStates(selectionManager.getState());
    }
    
    // === Component Creation ===
    
    /**
     * Creates the selection count label.
     * Requirements: 2.1
     */
    private Label createSelectionCountLabel() {
        Label label = new Label("0 seleccionadas");
        label.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        label.setMinWidth(120);
        return label;
    }
    
    /**
     * Creates the accept button.
     * Requirements: 3.1
     */
    private Button createAcceptButton() {
        Button btn = new Button("✓ Aceptar");
        btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                     "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 4;");
        btn.setTooltip(createTooltip("Aceptar relaciones seleccionadas (Ctrl+Enter)"));
        btn.setDisable(true);
        
        btn.setOnAction(e -> {
            logger.info("Bulk accept action triggered");
            executor.acceptSelectedWithConfirmation(owner).ifPresent(result -> {
                logger.info(() -> "Bulk accept completed: " + result.processedCount() + " relations");
                if (onBulkActionCallback != null) {
                    onBulkActionCallback.accept(result);
                }
            });
        });
        
        // Hover effect
        btn.setOnMouseEntered(e -> {
            if (!btn.isDisabled()) {
                btn.setStyle("-fx-background-color: #45a049; -fx-text-fill: white; " +
                             "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 4;");
            }
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                         "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 4;");
        });
        
        return btn;
    }
    
    /**
     * Creates the reject button.
     * Requirements: 3.2
     */
    private Button createRejectButton() {
        Button btn = new Button("✗ Rechazar");
        btn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; " +
                     "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 4;");
        btn.setTooltip(createTooltip("Rechazar relaciones seleccionadas (Ctrl+Delete)"));
        btn.setDisable(true);
        
        btn.setOnAction(e -> {
            logger.info("Bulk reject action triggered");
            executor.rejectSelectedWithConfirmation(owner).ifPresent(result -> {
                logger.info(() -> "Bulk reject completed: " + result.processedCount() + " relations");
                if (onBulkActionCallback != null) {
                    onBulkActionCallback.accept(result);
                }
            });
        });
        
        // Hover effect
        btn.setOnMouseEntered(e -> {
            if (!btn.isDisabled()) {
                btn.setStyle("-fx-background-color: #da190b; -fx-text-fill: white; " +
                             "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 4;");
            }
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; " +
                         "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 4;");
        });
        
        return btn;
    }
    
    /**
     * Creates the smart selection menu.
     * Requirements: 4.1, 4.2, 4.3, 4.4
     */
    private MenuButton createSmartSelectMenu() {
        MenuButton menu = new MenuButton("⚡ Selección");
        menu.setStyle("-fx-font-size: 11px;");
        menu.setTooltip(createTooltip("Opciones de selección inteligente"));
        
        // High confidence selection - Requirements: 4.1
        MenuItem highConfidence = new MenuItem("Seleccionar alta confianza (>80%)");
        highConfidence.setOnAction(e -> {
            logger.info("Smart selection: selecting high confidence relations (>80%)");
            selectionManager.selectByConfidence(0.80);
        });
        
        // Select by type submenu - Requirements: 4.2
        Menu typeMenu = new Menu("Seleccionar por tipo");
        
        MenuItem oneToOne = new MenuItem("OneToOne");
        oneToOne.setOnAction(e -> {
            logger.info("Smart selection: selecting relations by type OneToOne");
            selectionManager.selectByType("OneToOne");
        });
        
        MenuItem oneToMany = new MenuItem("OneToMany");
        oneToMany.setOnAction(e -> {
            logger.info("Smart selection: selecting relations by type OneToMany");
            selectionManager.selectByType("OneToMany");
        });
        
        MenuItem manyToOne = new MenuItem("ManyToOne");
        manyToOne.setOnAction(e -> {
            logger.info("Smart selection: selecting relations by type ManyToOne");
            selectionManager.selectByType("ManyToOne");
        });
        
        MenuItem manyToMany = new MenuItem("ManyToMany");
        manyToMany.setOnAction(e -> {
            logger.info("Smart selection: selecting relations by type ManyToMany");
            selectionManager.selectByType("ManyToMany");
        });
        
        typeMenu.getItems().addAll(oneToOne, oneToMany, manyToOne, manyToMany);
        
        // Invert selection - Requirements: 4.3
        MenuItem invertSelection = new MenuItem("Invertir selección");
        invertSelection.setOnAction(e -> {
            logger.info("Smart selection: inverting selection");
            selectionManager.invertSelection();
        });
        
        // Deselect all - Requirements: 4.4
        MenuItem deselectAll = new MenuItem("Deseleccionar todas");
        deselectAll.setOnAction(e -> {
            logger.info("Smart selection: deselecting all relations");
            selectionManager.deselectAll();
        });
        
        menu.getItems().addAll(
            highConfidence,
            typeMenu,
            invertSelection,
            deselectAll
        );
        
        return menu;
    }
    
    // === State Management ===
    
    /**
     * Updates button states based on selection state.
     * Requirements: 2.1, 2.2, 2.3, 7.3
     * 
     * @param state the current selection state
     */
    private void updateButtonStates(SelectionState state) {
        // Update counter label - Requirements: 2.1, 2.2
        if (state.hasSelection()) {
            String text = state.totalSelected() + " seleccionadas";
            
            // Show hidden count if any - Requirements: 7.3
            if (state.hiddenSelected() > 0) {
                text += " (" + state.hiddenSelected() + " ocultas)";
            }
            
            selectionCountLabel.setText(text);
            selectionCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #333; -fx-font-weight: bold;");
        } else {
            selectionCountLabel.setText("0 seleccionadas");
            selectionCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        }
        
        // Enable/disable buttons - Requirements: 2.3
        boolean hasSelection = state.hasSelection();
        acceptButton.setDisable(!hasSelection);
        rejectButton.setDisable(!hasSelection);
    }
    
    // === Utility Methods ===
    
    /**
     * Creates a tooltip with consistent styling.
     */
    private Tooltip createTooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.millis(300));
        tooltip.setStyle("-fx-font-size: 11px;");
        return tooltip;
    }
    
    // === Getters for testing ===
    
    /**
     * Gets the selection count label.
     * @return the selection count label
     */
    public Label getSelectionCountLabel() {
        return selectionCountLabel;
    }
    
    /**
     * Gets the accept button.
     * @return the accept button
     */
    public Button getAcceptButton() {
        return acceptButton;
    }
    
    /**
     * Gets the reject button.
     * @return the reject button
     */
    public Button getRejectButton() {
        return rejectButton;
    }
    
    /**
     * Gets the smart select menu.
     * @return the smart select menu
     */
    public MenuButton getSmartSelectMenu() {
        return smartSelectMenu;
    }
    
    /**
     * Sets the callback to be invoked when a bulk action is executed.
     * Requirements: 3.3
     * 
     * @param callback the callback to invoke with the action result
     */
    public void setOnBulkActionCallback(Consumer<BulkActionResult> callback) {
        this.onBulkActionCallback = callback;
    }
}
