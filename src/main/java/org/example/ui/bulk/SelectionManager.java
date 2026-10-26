package org.example.ui.bulk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.example.config.ConfigManager;
import org.example.ml.PredictionSource;
import org.example.model.RelacionDetectada;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

/**
 * Manages the selection state of relations in the bulk actions system.
 * Uses relation's getClaveMemoria() as unique key for selection tracking.
 * 
 * Requirements: 1.2, 2.1
 */
public class SelectionManager {
    
    private final Set<String> selectedKeys;
    private final ObservableList<RelacionDetectada> allRelations;
    private final FilteredList<RelacionDetectada> filteredRelations;
    private final List<Consumer<SelectionState>> listeners;
    
    /**
     * Creates a new SelectionManager.
     * 
     * @param allRelations all relations in the system
     * @param filteredRelations the filtered (visible) relations
     */
    public SelectionManager(ObservableList<RelacionDetectada> allRelations,
                           FilteredList<RelacionDetectada> filteredRelations) {
        this.selectedKeys = new HashSet<>();
        this.allRelations = allRelations;
        this.filteredRelations = filteredRelations;
        this.listeners = new ArrayList<>();
    }
    
    // === Basic Operations ===
    
    /**
     * Toggles the selection state of a relation.
     * 
     * @param relation the relation to toggle
     */
    public void toggleSelection(RelacionDetectada relation) {
        if (relation == null) return;
        
        String key = relation.getClaveMemoria();
        if (selectedKeys.contains(key)) {
            selectedKeys.remove(key);
        } else {
            selectedKeys.add(key);
        }
        notifyListeners();
    }

    /**
     * Selects a relation.
     * 
     * @param relation the relation to select
     */
    public void select(RelacionDetectada relation) {
        if (relation == null) return;
        
        String key = relation.getClaveMemoria();
        if (selectedKeys.add(key)) {
            notifyListeners();
        }
    }
    
    /**
     * Deselects a relation.
     * 
     * @param relation the relation to deselect
     */
    public void deselect(RelacionDetectada relation) {
        if (relation == null) return;
        
        String key = relation.getClaveMemoria();
        if (selectedKeys.remove(key)) {
            notifyListeners();
        }
    }
    
    /**
     * Checks if a relation is selected.
     * 
     * @param relation the relation to check
     * @return true if selected, false otherwise
     */
    public boolean isSelected(RelacionDetectada relation) {
        if (relation == null) return false;
        return selectedKeys.contains(relation.getClaveMemoria());
    }
    
    // === Bulk Operations ===
    
    /**
     * Selects all visible (filtered) relations.
     * Requirements: 1.3, 1.4
     */
    public void selectAll() {
        for (RelacionDetectada relation : filteredRelations) {
            selectedKeys.add(relation.getClaveMemoria());
        }
        notifyListeners();
    }
    
    /**
     * Deselects all relations (both visible and hidden).
     * Requirements: 4.4
     */
    public void deselectAll() {
        if (!selectedKeys.isEmpty()) {
            selectedKeys.clear();
            notifyListeners();
        }
    }
    
    /**
     * Inverts the selection of all visible relations.
     * Hidden selections remain unchanged.
     * Requirements: 4.3
     */
    public void invertSelection() {
        Set<String> visibleKeys = filteredRelations.stream()
            .map(RelacionDetectada::getClaveMemoria)
            .collect(Collectors.toSet());
        
        for (String key : visibleKeys) {
            if (selectedKeys.contains(key)) {
                selectedKeys.remove(key);
            } else {
                selectedKeys.add(key);
            }
        }
        notifyListeners();
    }
    
    // === Smart Selection Methods ===
    
    /**
     * Selects all visible relations with confidence above the threshold.
     * Requirements: 4.1
     * 
     * @param minConfidence minimum confidence threshold (0.0 to 1.0)
     */
    public void selectByConfidence(double minConfidence) {
        for (RelacionDetectada relation : filteredRelations) {
            if (relation.getConfianza() >= minConfidence) {
                selectedKeys.add(relation.getClaveMemoria());
            }
        }
        notifyListeners();
    }
    
    /**
     * Selects all visible relations with high confidence using the configured threshold.
     * Uses the highConfidenceThreshold from ConfigManager.
     * Requirements: 3.2
     */
    public void selectHighConfidence() {
        double threshold = ConfigManager.getInstance()
            .getAnalysisConfig()
            .highConfidenceThreshold();
        selectByConfidence(threshold);
    }
    
    /**
     * Gets the configured high confidence threshold.
     * Requirements: 3.2
     * 
     * @return the high confidence threshold from configuration
     */
    public double getHighConfidenceThreshold() {
        return ConfigManager.getInstance()
            .getAnalysisConfig()
            .highConfidenceThreshold();
    }
    
    /**
     * Selects all visible relations of a specific type.
     * Requirements: 4.2
     * 
     * @param tipo the relation type to select
     */
    public void selectByType(String tipo) {
        if (tipo == null) return;
        
        for (RelacionDetectada relation : filteredRelations) {
            if (tipo.equals(relation.getTipo())) {
                selectedKeys.add(relation.getClaveMemoria());
            }
        }
        notifyListeners();
    }
    
    /**
     * Selects all visible relations from a specific prediction source.
     * Requirements: 4.2
     * 
     * @param source the prediction source to select
     */
    public void selectBySource(PredictionSource source) {
        if (source == null) return;
        
        for (RelacionDetectada relation : filteredRelations) {
            if (source.equals(relation.getSource())) {
                selectedKeys.add(relation.getClaveMemoria());
            }
        }
        notifyListeners();
    }

    // === State Tracking ===
    
    /**
     * Gets the total count of selected relations.
     * Requirements: 7.1
     * 
     * @return total selected count
     */
    public int getSelectedCount() {
        return selectedKeys.size();
    }
    
    /**
     * Gets the count of selected relations that are currently visible.
     * Requirements: 7.2
     * 
     * @return visible selected count
     */
    public int getVisibleSelectedCount() {
        Set<String> visibleKeys = filteredRelations.stream()
            .map(RelacionDetectada::getClaveMemoria)
            .collect(Collectors.toSet());
        
        return (int) selectedKeys.stream()
            .filter(visibleKeys::contains)
            .count();
    }
    
    /**
     * Gets the count of selected relations that are currently hidden by filters.
     * Requirements: 7.3
     * 
     * @return hidden selected count
     */
    public int getHiddenSelectedCount() {
        return getSelectedCount() - getVisibleSelectedCount();
    }
    
    /**
     * Gets all selected relations.
     * Returns one relation per selected key (first match if duplicates exist).
     * 
     * @return list of selected relations
     */
    public List<RelacionDetectada> getSelectedRelations() {
        Set<String> foundKeys = new HashSet<>();
        List<RelacionDetectada> result = new ArrayList<>();
        
        for (RelacionDetectada r : allRelations) {
            String key = r.getClaveMemoria();
            if (selectedKeys.contains(key) && !foundKeys.contains(key)) {
                result.add(r);
                foundKeys.add(key);
            }
        }
        
        return result;
    }
    
    /**
     * Gets the current selection state.
     * Requirements: 7.1, 7.2, 7.3
     * 
     * @return current SelectionState
     */
    public SelectionState getState() {
        int total = getSelectedCount();
        int visible = getVisibleSelectedCount();
        int hidden = total - visible;
        boolean hasSelection = total > 0;
        boolean allVisibleSelected = !filteredRelations.isEmpty() && 
            filteredRelations.stream()
                .allMatch(r -> selectedKeys.contains(r.getClaveMemoria()));
        
        return new SelectionState(total, visible, hidden, hasSelection, allVisibleSelected);
    }
    
    // === Listener Support ===
    
    /**
     * Adds a listener for selection changes.
     * 
     * @param listener the listener to add
     */
    public void addSelectionListener(Consumer<SelectionState> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
    
    /**
     * Removes a selection listener.
     * 
     * @param listener the listener to remove
     */
    public void removeSelectionListener(Consumer<SelectionState> listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notifies all listeners of a selection change.
     */
    private void notifyListeners() {
        SelectionState state = getState();
        for (Consumer<SelectionState> listener : listeners) {
            listener.accept(state);
        }
    }
    
    // === Cleanup Methods ===
    
    /**
     * Clears all selections.
     */
    public void clearSelection() {
        deselectAll();
    }
    
    /**
     * Removes specific relations from the selection.
     * Used after bulk actions to clean up processed relations.
     * 
     * @param relations the relations to remove from selection
     */
    public void removeFromSelection(Collection<RelacionDetectada> relations) {
        if (relations == null || relations.isEmpty()) return;
        
        boolean changed = false;
        for (RelacionDetectada relation : relations) {
            if (selectedKeys.remove(relation.getClaveMemoria())) {
                changed = true;
            }
        }
        
        if (changed) {
            notifyListeners();
        }
    }
}
