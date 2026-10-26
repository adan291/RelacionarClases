package org.example.ui.bulk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.example.ml.PredictionSource;
import org.example.model.RelacionDetectada;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Property-based tests for SelectionManager.
 * Tests correctness properties defined in the design document.
 */
public class SelectionManagerProperties {

    private static final String[] RELATION_TYPES = {"OneToOne", "OneToMany", "ManyToOne", "ManyToMany"};
    private static final String[] CLASS_NAMES = {"User", "Order", "Product", "Customer", "Invoice", "Item", "Category", "Address"};

    /**
     * Creates a list of test relations with varying confidence levels.
     */
    private List<RelacionDetectada> createRelations(int count, List<Double> confidences) {
        List<RelacionDetectada> relations = new ArrayList<>();
        for (int i = 0; i < count && i < confidences.size(); i++) {
            String origen = CLASS_NAMES[i % CLASS_NAMES.length];
            String destino = CLASS_NAMES[(i + 1) % CLASS_NAMES.length];
            String tipo = RELATION_TYPES[i % RELATION_TYPES.length];
            double confianza = confidences.get(i);
            PredictionSource source = PredictionSource.values()[i % PredictionSource.values().length];
            relations.add(new RelacionDetectada(origen, destino, tipo, confianza, source));
        }
        return relations;
    }

    /**
     * **Feature: bulk-actions, Property 1: Selection State Consistency**
     * 
     * For any sequence of select/deselect operations, the count returned by 
     * getSelectedCount() should equal the size of the set returned by getSelectedRelations().
     * 
     * **Validates: Requirements 2.1, 2.2**
     */
    @Property(tries = 100)
    void selectionCountMatchesSetSize(
            @ForAll @Size(min = 1, max = 20) List<@DoubleRange(min = 0.0, max = 1.0) Double> confidences,
            @ForAll @Size(min = 1, max = 30) List<@IntRange(min = 0, max = 19) Integer> operationIndices) {
        
        List<RelacionDetectada> relations = createRelations(confidences.size(), confidences);
        ObservableList<RelacionDetectada> allRelations = FXCollections.observableArrayList(relations);
        FilteredList<RelacionDetectada> filteredRelations = new FilteredList<>(allRelations, r -> true);
        SelectionManager manager = new SelectionManager(allRelations, filteredRelations);
        
        // Perform random toggle operations
        for (int index : operationIndices) {
            if (index < relations.size()) {
                manager.toggleSelection(relations.get(index));
            }
        }
        
        // Verify consistency
        int count = manager.getSelectedCount();
        List<RelacionDetectada> selectedList = manager.getSelectedRelations();
        
        assert count == selectedList.size() : 
            "getSelectedCount() should equal getSelectedRelations().size(), got " + count + " vs " + selectedList.size();
        
        // Also verify state consistency
        SelectionState state = manager.getState();
        assert state.totalSelected() == count :
            "SelectionState.totalSelected() should equal getSelectedCount()";
        assert state.hasSelection() == (count > 0) :
            "SelectionState.hasSelection() should be true iff count > 0";
    }


    /**
     * **Feature: bulk-actions, Property 2: Select All Affects Only Visible**
     * 
     * For any filtered list state, calling selectAll() should only add visible relations 
     * to the selection, leaving the selection state of hidden relations unchanged.
     * 
     * **Validates: Requirements 1.3, 1.4**
     */
    @Property(tries = 100)
    void selectAllAffectsOnlyVisible(
            @ForAll @Size(min = 2, max = 20) List<@DoubleRange(min = 0.0, max = 1.0) Double> confidences,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double threshold) {
        
        List<RelacionDetectada> relations = createRelations(confidences.size(), confidences);
        ObservableList<RelacionDetectada> allRelations = FXCollections.observableArrayList(relations);
        
        // Filter by confidence threshold
        FilteredList<RelacionDetectada> filteredRelations = new FilteredList<>(allRelations, 
            r -> r.getConfianza() >= threshold);
        
        SelectionManager manager = new SelectionManager(allRelations, filteredRelations);
        
        // Pre-select some hidden relations
        Set<String> preSelectedHiddenKeys = new HashSet<>();
        for (RelacionDetectada r : allRelations) {
            if (r.getConfianza() < threshold) {
                manager.select(r);
                preSelectedHiddenKeys.add(r.getClaveMemoria());
            }
        }
        
        int hiddenSelectedBefore = manager.getHiddenSelectedCount();
        
        // Call selectAll
        manager.selectAll();
        
        // Verify all visible are selected
        for (RelacionDetectada r : filteredRelations) {
            assert manager.isSelected(r) : 
                "All visible relations should be selected after selectAll()";
        }
        
        // Verify hidden selections unchanged
        int hiddenSelectedAfter = manager.getHiddenSelectedCount();
        assert hiddenSelectedAfter == hiddenSelectedBefore :
            "Hidden selections should remain unchanged after selectAll(), was " + hiddenSelectedBefore + " now " + hiddenSelectedAfter;
        
        // Verify pre-selected hidden relations are still selected
        for (String key : preSelectedHiddenKeys) {
            RelacionDetectada r = allRelations.stream()
                .filter(rel -> rel.getClaveMemoria().equals(key))
                .findFirst().orElse(null);
            if (r != null) {
                assert manager.isSelected(r) :
                    "Pre-selected hidden relation should still be selected";
            }
        }
    }

    /**
     * **Feature: bulk-actions, Property 4: Selection Persistence Through Filters**
     * 
     * For any selected relation that becomes hidden by a filter change, the relation 
     * should remain in the selected set and be counted in hiddenSelected.
     * 
     * **Validates: Requirements 7.1, 7.2, 7.3**
     */
    @Property(tries = 100)
    void selectionPersistsThroughFilters(
            @ForAll @Size(min = 3, max = 20) List<@DoubleRange(min = 0.0, max = 1.0) Double> confidences) {
        
        List<RelacionDetectada> relations = createRelations(confidences.size(), confidences);
        ObservableList<RelacionDetectada> allRelations = FXCollections.observableArrayList(relations);
        
        // Start with no filter
        FilteredList<RelacionDetectada> filteredRelations = new FilteredList<>(allRelations, r -> true);
        
        SelectionManager manager = new SelectionManager(allRelations, filteredRelations);
        
        // Select all relations
        manager.selectAll();
        int totalSelectedBefore = manager.getSelectedCount();
        Set<String> selectedKeysBefore = new HashSet<>();
        for (RelacionDetectada r : manager.getSelectedRelations()) {
            selectedKeysBefore.add(r.getClaveMemoria());
        }
        
        // Apply a filter that hides some relations
        filteredRelations.setPredicate(r -> r.getConfianza() >= 0.5);
        
        // Verify total selection count unchanged
        int totalSelectedAfter = manager.getSelectedCount();
        assert totalSelectedAfter == totalSelectedBefore :
            "Total selection count should persist through filter changes, was " + totalSelectedBefore + " now " + totalSelectedAfter;
        
        // Verify all originally selected relations are still selected
        for (String key : selectedKeysBefore) {
            RelacionDetectada r = allRelations.stream()
                .filter(rel -> rel.getClaveMemoria().equals(key))
                .findFirst().orElse(null);
            if (r != null) {
                assert manager.isSelected(r) :
                    "Selection should persist through filter changes";
            }
        }
        
        // Verify hidden count is correct
        int visibleSelected = manager.getVisibleSelectedCount();
        int hiddenSelected = manager.getHiddenSelectedCount();
        assert visibleSelected + hiddenSelected == totalSelectedAfter :
            "visibleSelected + hiddenSelected should equal totalSelected";
    }


    /**
     * **Feature: bulk-actions, Property 5: Invert Selection Toggles All Visible**
     * 
     * For any visible relation set, calling invertSelection() should result in previously 
     * selected visible relations being deselected and previously unselected visible relations 
     * being selected, with hidden selections unchanged.
     * 
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    void invertSelectionTogglesAllVisible(
            @ForAll @Size(min = 2, max = 20) List<@DoubleRange(min = 0.0, max = 1.0) Double> confidences,
            @ForAll @Size(min = 0, max = 10) List<@IntRange(min = 0, max = 19) Integer> initialSelectionIndices) {
        
        List<RelacionDetectada> relations = createRelations(confidences.size(), confidences);
        ObservableList<RelacionDetectada> allRelations = FXCollections.observableArrayList(relations);
        
        // Filter to show only high confidence relations
        FilteredList<RelacionDetectada> filteredRelations = new FilteredList<>(allRelations, 
            r -> r.getConfianza() >= 0.5);
        
        SelectionManager manager = new SelectionManager(allRelations, filteredRelations);
        
        // Make initial selections
        for (int index : initialSelectionIndices) {
            if (index < relations.size()) {
                manager.select(relations.get(index));
            }
        }
        
        // Record state before invert
        Set<String> visibleSelectedBefore = new HashSet<>();
        Set<String> visibleUnselectedBefore = new HashSet<>();
        Set<String> hiddenSelectedBefore = new HashSet<>();
        
        for (RelacionDetectada r : filteredRelations) {
            if (manager.isSelected(r)) {
                visibleSelectedBefore.add(r.getClaveMemoria());
            } else {
                visibleUnselectedBefore.add(r.getClaveMemoria());
            }
        }
        
        for (RelacionDetectada r : allRelations) {
            if (!filteredRelations.contains(r) && manager.isSelected(r)) {
                hiddenSelectedBefore.add(r.getClaveMemoria());
            }
        }
        
        // Invert selection
        manager.invertSelection();
        
        // Verify previously selected visible are now unselected
        for (String key : visibleSelectedBefore) {
            RelacionDetectada r = allRelations.stream()
                .filter(rel -> rel.getClaveMemoria().equals(key))
                .findFirst().orElse(null);
            if (r != null && filteredRelations.contains(r)) {
                assert !manager.isSelected(r) :
                    "Previously selected visible relation should be unselected after invert";
            }
        }
        
        // Verify previously unselected visible are now selected
        for (String key : visibleUnselectedBefore) {
            RelacionDetectada r = allRelations.stream()
                .filter(rel -> rel.getClaveMemoria().equals(key))
                .findFirst().orElse(null);
            if (r != null && filteredRelations.contains(r)) {
                assert manager.isSelected(r) :
                    "Previously unselected visible relation should be selected after invert";
            }
        }
        
        // Verify hidden selections unchanged
        for (String key : hiddenSelectedBefore) {
            RelacionDetectada r = allRelations.stream()
                .filter(rel -> rel.getClaveMemoria().equals(key))
                .findFirst().orElse(null);
            if (r != null && !filteredRelations.contains(r)) {
                assert manager.isSelected(r) :
                    "Hidden selection should remain unchanged after invert";
            }
        }
    }
}
