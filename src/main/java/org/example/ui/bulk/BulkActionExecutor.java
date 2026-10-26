package org.example.ui.bulk;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.example.config.ConfigManager;
import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;

import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/**
 * Executes bulk actions (accept/reject) on selected relations.
 * Handles confirmation dialogs for large selections.
 * 
 * Requirements: 3.1, 3.2, 5.1
 */
public class BulkActionExecutor {
    
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(BulkActionExecutor.class.getName());
    
    /** @deprecated Use ConfigManager instead */
    public static final int CONFIRMATION_THRESHOLD = 5;
    
    private final int confirmationThreshold;
    private final MemoryService memoryService;
    private final ObservableList<RelacionDetectada> allRelations;
    private final SelectionManager selectionManager;
    private final Consumer<String> logger;
    
    /**
     * Creates a new BulkActionExecutor.
     * 
     * @param memoryService service for recording decisions
     * @param allRelations the list of all relations (will be modified)
     * @param selectionManager the selection manager
     * @param logger consumer for logging messages
     */
    public BulkActionExecutor(MemoryService memoryService,
                              ObservableList<RelacionDetectada> allRelations,
                              SelectionManager selectionManager,
                              Consumer<String> logger) {
        this.memoryService = memoryService;
        this.allRelations = allRelations;
        this.selectionManager = selectionManager;
        this.logger = logger != null ? logger : msg -> {};
        this.confirmationThreshold = ConfigManager.getInstance().getUIConfig().bulkActionConfirmationThreshold();
    }

    // === Core Action Methods ===
    
    /**
     * Accepts all selected relations.
     * Records acceptance in MemoryService, removes from allRelations, and clears selection.
     * 
     * Requirements: 3.1, 3.3, 3.4, 3.5
     * 
     * @return result containing count and timing information
     */
    public BulkActionResult acceptSelected() {
        return executeAction(true);
    }
    
    /**
     * Rejects all selected relations.
     * Records rejection in MemoryService, removes from allRelations, and clears selection.
     * 
     * Requirements: 3.2, 3.3, 3.4, 3.5
     * 
     * @return result containing count and timing information
     */
    public BulkActionResult rejectSelected() {
        return executeAction(false);
    }
    
    /**
     * Executes the bulk action (accept or reject) on all selected relations.
     * 
     * @param accept true for accept, false for reject
     * @return result containing count and timing information
     */
    private BulkActionResult executeAction(boolean accept) {
        long startTime = System.currentTimeMillis();
        
        List<RelacionDetectada> selected = selectionManager.getSelectedRelations();
        
        if (selected.isEmpty()) {
            LOGGER.fine("No relations selected for bulk action");
            return BulkActionResult.empty(accept);
        }
        
        int count = selected.size();
        final String actionType = accept ? "accept" : "reject";
        LOGGER.info(() -> "Executing bulk " + actionType + " on " + count + " relations");
        
        // Record decisions in MemoryService
        for (RelacionDetectada relation : selected) {
            memoryService.recordarDecision(relation.getClaveMemoria(), accept);
        }
        
        // Remove processed relations from allRelations
        allRelations.removeAll(selected);
        
        // Clear selection
        selectionManager.clearSelection();
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        // Log summary
        String action = accept ? "aceptadas" : "rechazadas";
        logger.accept(String.format("✓ %d relaciones %s en %dms", count, action, executionTime));
        LOGGER.info(() -> "Bulk action completed: " + count + " relations " + action + " in " + executionTime + "ms");
        
        return new BulkActionResult(count, accept, executionTime);
    }
    
    // === Confirmation Methods ===
    
    /**
     * Checks if the current selection requires confirmation before action.
     * 
     * Requirements: 5.1
     * 
     * @return true if selected count exceeds CONFIRMATION_THRESHOLD
     */
    public boolean requiresConfirmation() {
        return selectionManager.getSelectedCount() > confirmationThreshold;
    }
    
    /**
     * Accepts selected relations with confirmation dialog if needed.
     * 
     * Requirements: 5.1, 5.2, 5.3, 5.4
     * 
     * @param owner the owner stage for the dialog
     * @return optional result, empty if cancelled
     */
    public Optional<BulkActionResult> acceptSelectedWithConfirmation(Stage owner) {
        return executeWithConfirmation(owner, true);
    }
    
    /**
     * Rejects selected relations with confirmation dialog if needed.
     * 
     * Requirements: 5.1, 5.2, 5.3, 5.4
     * 
     * @param owner the owner stage for the dialog
     * @return optional result, empty if cancelled
     */
    public Optional<BulkActionResult> rejectSelectedWithConfirmation(Stage owner) {
        return executeWithConfirmation(owner, false);
    }
    
    /**
     * Executes action with confirmation dialog if threshold is exceeded.
     * 
     * @param owner the owner stage for the dialog
     * @param accept true for accept, false for reject
     * @return optional result, empty if cancelled
     */
    private Optional<BulkActionResult> executeWithConfirmation(Stage owner, boolean accept) {
        int count = selectionManager.getSelectedCount();
        String actionType = accept ? "accept" : "reject";
        
        if (count == 0) {
            LOGGER.fine(() -> "No relations selected for bulk " + actionType);
            return Optional.of(BulkActionResult.empty(accept));
        }
        
        if (requiresConfirmation()) {
            LOGGER.info(() -> "Showing confirmation dialog for bulk " + actionType + " of " + count + " relations");
            if (!showConfirmationDialog(owner, count, accept)) {
                // User cancelled - Requirements: 5.3
                LOGGER.info(() -> "User cancelled bulk " + actionType + " action");
                return Optional.empty();
            }
            LOGGER.info(() -> "User confirmed bulk " + actionType + " action");
        }
        
        // User confirmed or no confirmation needed - Requirements: 5.4
        return Optional.of(accept ? acceptSelected() : rejectSelected());
    }
    
    /**
     * Shows a confirmation dialog for bulk actions.
     * 
     * Requirements: 5.2
     * 
     * @param owner the owner stage
     * @param count number of relations to process
     * @param accept true for accept action, false for reject
     * @return true if user confirmed, false if cancelled
     */
    private boolean showConfirmationDialog(Stage owner, int count, boolean accept) {
        String action = accept ? "aceptar" : "rechazar";
        String title = "Confirmar acción masiva";
        String header = String.format("¿%s %d relaciones?", 
            accept ? "Aceptar" : "Rechazar", count);
        String content = String.format(
            "Está a punto de %s %d relaciones seleccionadas.\n" +
            "Esta acción no se puede deshacer fácilmente.",
            action, count);
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        if (owner != null) {
            alert.initOwner(owner);
        }
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    // === Getters for testing ===
    
    /**
     * Gets the memory service (for testing purposes).
     * 
     * @return the memory service
     */
    public MemoryService getMemoryService() {
        return memoryService;
    }
    
    /**
     * Gets the selection manager (for testing purposes).
     * 
     * @return the selection manager
     */
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }
}
