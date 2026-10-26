package org.example.ui.bulk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.example.ml.PredictionSource;
import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Property-based tests for BulkActionExecutor.
 * Tests correctness properties defined in the design document.
 */
public class BulkActionExecutorProperties {

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
     * Creates a mock MemoryService that doesn't persist to disk.
     */
    private MemoryService createMockMemoryService() {
        return new MemoryService() {
            private final java.util.Map<String, String> memoria = new java.util.HashMap<>();
            
            @Override
            public synchronized void recordarDecision(String relacion, boolean aceptada) {
                if (relacion == null || relacion.isBlank()) return;
                memoria.put(relacion, aceptada ? "aceptada" : "rechazada");
            }
            
            @Override
            public synchronized java.util.Map<String, String> getMemoria() {
                return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(memoria));
            }
            
            @Override
            public synchronized void guardar() {
                // No-op for testing
            }
            
            @Override
            public synchronized void cargar() {
                // No-op for testing
            }
        };
    }


    /**
     * **Feature: bulk-actions, Property 3: Bulk Action Removes All Selected**
     * 
     * For any set of selected relations, executing a bulk action (accept or reject) 
     * should result in all those relations being removed from allRelations and the 
     * selection being cleared.
     * 
     * **Validates: Requirements 3.3, 3.4**
     */
    @Property(tries = 100)
    void bulkActionRemovesAllSelected(
            @ForAll @Size(min = 1, max = 20) List<@DoubleRange(min = 0.0, max = 1.0) Double> confidences,
            @ForAll @Size(min = 1, max = 10) List<@IntRange(min = 0, max = 19) Integer> selectionIndices,
            @ForAll boolean acceptAction) {
        
        List<RelacionDetectada> relations = createRelations(confidences.size(), confidences);
        ObservableList<RelacionDetectada> allRelations = FXCollections.observableArrayList(relations);
        FilteredList<RelacionDetectada> filteredRelations = new FilteredList<>(allRelations, r -> true);
        
        SelectionManager selectionManager = new SelectionManager(allRelations, filteredRelations);
        MemoryService memoryService = createMockMemoryService();
        List<String> logMessages = new ArrayList<>();
        
        BulkActionExecutor executor = new BulkActionExecutor(
            memoryService, 
            allRelations, 
            selectionManager, 
            logMessages::add
        );
        
        // Select some relations - track unique keys selected
        Set<String> selectedKeys = new HashSet<>();
        for (int index : selectionIndices) {
            if (index < relations.size()) {
                RelacionDetectada r = relations.get(index);
                selectionManager.select(r);
                selectedKeys.add(r.getClaveMemoria());
            }
        }
        
        // Get actual selected count from manager (accounts for unique keys)
        int selectedCountBefore = selectionManager.getSelectedCount();
        int allRelationsCountBefore = allRelations.size();
        
        // Count how many relations in allRelations have keys that are selected
        // This accounts for potential duplicate keys in the relations list
        int relationsToRemove = (int) allRelations.stream()
            .filter(r -> selectedKeys.contains(r.getClaveMemoria()))
            .count();
        
        // Execute bulk action
        BulkActionResult result;
        if (acceptAction) {
            result = executor.acceptSelected();
        } else {
            result = executor.rejectSelected();
        }
        
        // Verify all selected relations were removed from allRelations
        for (String key : selectedKeys) {
            boolean stillExists = allRelations.stream()
                .anyMatch(r -> r.getClaveMemoria().equals(key));
            assert !stillExists : 
                "Selected relation with key '" + key + "' should be removed from allRelations after bulk action";
        }
        
        // Verify selection is cleared (Requirements 3.4)
        assert selectionManager.getSelectedCount() == 0 :
            "Selection should be cleared after bulk action, but got " + selectionManager.getSelectedCount();
        
        // Verify the correct number of relations were removed
        int expectedRemainingCount = allRelationsCountBefore - relationsToRemove;
        assert allRelations.size() == expectedRemainingCount :
            "allRelations size should be " + expectedRemainingCount + " but was " + allRelations.size();
        
        // Verify result contains correct count (based on unique selected relations)
        assert result.processedCount() == selectedCountBefore :
            "Result processedCount should be " + selectedCountBefore + " but was " + result.processedCount();
        
        // Verify result has correct action type
        assert result.accepted() == acceptAction :
            "Result accepted flag should be " + acceptAction;
        
        // Verify decisions were recorded in MemoryService
        java.util.Map<String, String> memoria = memoryService.getMemoria();
        String expectedDecision = acceptAction ? "aceptada" : "rechazada";
        for (String key : selectedKeys) {
            assert memoria.containsKey(key) :
                "Decision for key '" + key + "' should be recorded in MemoryService";
            assert expectedDecision.equals(memoria.get(key)) :
                "Decision for key '" + key + "' should be '" + expectedDecision + "' but was '" + memoria.get(key) + "'";
        }
    }

    /**
     * **Feature: bulk-actions, Property 3 (Edge Case): Empty Selection**
     * 
     * When no relations are selected, bulk action should return empty result
     * and not modify allRelations.
     * 
     * **Validates: Requirements 3.3, 3.4**
     */
    @Property(tries = 100)
    void bulkActionWithEmptySelectionDoesNothing(
            @ForAll @Size(min = 1, max = 20) List<@DoubleRange(min = 0.0, max = 1.0) Double> confidences,
            @ForAll boolean acceptAction) {
        
        List<RelacionDetectada> relations = createRelations(confidences.size(), confidences);
        ObservableList<RelacionDetectada> allRelations = FXCollections.observableArrayList(relations);
        FilteredList<RelacionDetectada> filteredRelations = new FilteredList<>(allRelations, r -> true);
        
        SelectionManager selectionManager = new SelectionManager(allRelations, filteredRelations);
        MemoryService memoryService = createMockMemoryService();
        
        BulkActionExecutor executor = new BulkActionExecutor(
            memoryService, 
            allRelations, 
            selectionManager, 
            msg -> {}
        );
        
        int allRelationsCountBefore = allRelations.size();
        
        // Execute bulk action with no selection
        BulkActionResult result;
        if (acceptAction) {
            result = executor.acceptSelected();
        } else {
            result = executor.rejectSelected();
        }
        
        // Verify nothing was removed
        assert allRelations.size() == allRelationsCountBefore :
            "allRelations should not change when no selection exists";
        
        // Verify result indicates no processing
        assert result.processedCount() == 0 :
            "processedCount should be 0 for empty selection";
        
        // Verify no decisions were recorded
        assert memoryService.getMemoria().isEmpty() :
            "No decisions should be recorded for empty selection";
    }
}
