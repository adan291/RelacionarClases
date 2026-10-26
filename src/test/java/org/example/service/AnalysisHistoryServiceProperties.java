package org.example.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.model.AnalysisSnapshot;
import org.example.model.ComparisonResult;
import org.example.model.DecisionState;
import org.example.model.RelacionDetectada;
import org.example.model.RelationChange;
import org.example.model.RelationSnapshot;
import org.example.model.SnapshotMetadata;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

/**
 * Property-based tests for AnalysisHistoryService.
 * Tests correctness properties defined in the design document.
 */
public class AnalysisHistoryServiceProperties {

    /**
     * **Feature: analysis-history, Property 1: Snapshot Save-Restore Round Trip**
     * 
     * For any list of relations and decision map, saving as a snapshot and then 
     * restoring should produce an equivalent set of relations with the same decision states.
     * 
     * **Validates: Requirements 1.1, 3.1**
     */
    @Property(tries = 100)
    void snapshotSaveRestoreRoundTrip(
            @ForAll("relations") List<RelacionDetectada> relations,
            @ForAll("decisions") Map<String, Boolean> decisions) throws IOException {
        
        Path tempDir = Files.createTempDirectory("history_roundtrip_test_");
        try {
            SnapshotManager snapshotManager = new SnapshotManager(tempDir.toString());
            AnalysisHistoryService service = new AnalysisHistoryService(snapshotManager);
            
            String projectPath = "/test/project/path";
            String description = "Test snapshot";
            
            // Apply decisions to relations that exist
            Map<String, Boolean> applicableDecisions = new HashMap<>();
            for (RelacionDetectada rel : relations) {
                String key = rel.getClaveMemoria();
                if (decisions.containsKey(key)) {
                    applicableDecisions.put(key, decisions.get(key));
                }
            }
            
            // Save snapshot
            String snapshotId = service.saveSnapshot(projectPath, relations, applicableDecisions, description);
            
            // Restore snapshot
            AnalysisSnapshot restored = service.restoreSnapshot(snapshotId);
            
            // Verify restoration succeeded
            assert restored != null : "Restored snapshot should not be null";
            
            // Verify relation count matches
            assert restored.getRelations().size() == relations.size() :
                "Restored relations count should match. Expected " + relations.size() + 
                ", got " + restored.getRelations().size();
            
            // Verify each relation is preserved
            for (RelacionDetectada original : relations) {
                boolean found = restored.getRelations().stream()
                    .anyMatch(r -> r.getOrigen().equals(original.getOrigen()) &&
                                   r.getDestino().equals(original.getDestino()) &&
                                   r.getTipo().equals(original.getTipo()));
                assert found : "Original relation should be found in restored snapshot: " + original;
            }
            
            // Verify decisions are preserved
            for (RelationSnapshot restoredRel : restored.getRelations()) {
                String key = restoredRel.getKey();
                if (applicableDecisions.containsKey(key)) {
                    boolean expectedAccepted = applicableDecisions.get(key);
                    DecisionState expectedState = expectedAccepted ? 
                        DecisionState.ACCEPTED : DecisionState.REJECTED;
                    assert restoredRel.getDecisionState() == expectedState :
                        "Decision state should match for " + key + 
                        ". Expected " + expectedState + ", got " + restoredRel.getDecisionState();
                }
            }
            
        } finally {
            deleteDirectory(tempDir);
        }
    }


    /**
     * **Feature: analysis-history, Property 3: Snapshot Metadata Completeness**
     * 
     * For any saved snapshot, the metadata should contain non-null values for: 
     * id, timestamp, projectPath, projectName, totalRelations, and typeBreakdown.
     * 
     * **Validates: Requirements 2.2, 2.3**
     */
    @Property(tries = 100)
    void snapshotMetadataCompleteness(
            @ForAll("relations") List<RelacionDetectada> relations) throws IOException {
        
        Path tempDir = Files.createTempDirectory("history_metadata_test_");
        try {
            SnapshotManager snapshotManager = new SnapshotManager(tempDir.toString());
            AnalysisHistoryService service = new AnalysisHistoryService(snapshotManager);
            
            String projectPath = "/test/project/myproject";
            String description = "Test description";
            Map<String, Boolean> decisions = new HashMap<>();
            
            // Save snapshot
            String snapshotId = service.saveSnapshot(projectPath, relations, decisions, description);
            
            // Get summary (metadata)
            SnapshotMetadata metadata = service.getSummary(snapshotId);
            
            // Verify all required fields are non-null
            assert metadata != null : "Metadata should not be null";
            assert metadata.getId() != null : "Metadata ID should not be null";
            assert metadata.getTimestamp() != null : "Metadata timestamp should not be null";
            assert metadata.getProjectPath() != null : "Metadata projectPath should not be null";
            assert metadata.getProjectName() != null : "Metadata projectName should not be null";
            assert metadata.getTypeBreakdown() != null : "Metadata typeBreakdown should not be null";
            
            // Verify totalRelations matches
            assert metadata.getTotalRelations() == relations.size() :
                "Total relations should match. Expected " + relations.size() + 
                ", got " + metadata.getTotalRelations();
            
            // Verify type breakdown is consistent
            int typeBreakdownTotal = metadata.getTypeBreakdown().values().stream()
                .mapToInt(Integer::intValue).sum();
            assert typeBreakdownTotal == relations.size() :
                "Type breakdown total should match relation count. Expected " + relations.size() + 
                ", got " + typeBreakdownTotal;
            
            // Verify counts add up
            int countSum = metadata.getAcceptedCount() + metadata.getRejectedCount() + metadata.getPendingCount();
            assert countSum == relations.size() :
                "Decision counts should sum to total relations. Expected " + relations.size() + 
                ", got " + countSum;
            
        } finally {
            deleteDirectory(tempDir);
        }
    }


    /**
     * **Feature: analysis-history, Property 4: Comparison Correctness**
     * 
     * For any two snapshots, the comparison result should correctly categorize all 
     * relations as: added (in snap2 only), removed (in snap1 only), type changed, 
     * or decision changed, with no relation appearing in multiple categories.
     * 
     * **Validates: Requirements 4.1, 4.2, 4.3**
     */
    @Property(tries = 100)
    void comparisonCorrectness(
            @ForAll("relations") @Size(min = 1, max = 10) List<RelacionDetectada> relations1,
            @ForAll("relations") @Size(min = 1, max = 10) List<RelacionDetectada> relations2) throws IOException {
        
        Path tempDir = Files.createTempDirectory("history_compare_test_");
        try {
            SnapshotManager snapshotManager = new SnapshotManager(tempDir.toString());
            AnalysisHistoryService service = new AnalysisHistoryService(snapshotManager);
            
            String projectPath = "/test/project/path";
            Map<String, Boolean> decisions1 = generateDecisions(relations1);
            Map<String, Boolean> decisions2 = generateDecisions(relations2);
            
            // Save two snapshots
            String snapshotId1 = service.saveSnapshot(projectPath, relations1, decisions1, "Snapshot 1");
            
            // Small delay to ensure different timestamps
            try { Thread.sleep(10); } catch (InterruptedException e) { }
            
            String snapshotId2 = service.saveSnapshot(projectPath, relations2, decisions2, "Snapshot 2");
            
            // Compare snapshots
            ComparisonResult result = service.compare(snapshotId1, snapshotId2);
            
            assert result != null : "Comparison result should not be null";
            
            // Collect all relation keys that appear in any category
            Set<String> addedKeys = new HashSet<>();
            Set<String> removedKeys = new HashSet<>();
            Set<String> typeChangedKeys = new HashSet<>();
            Set<String> decisionChangedKeys = new HashSet<>();
            
            for (RelationSnapshot rel : result.getAdded()) {
                addedKeys.add(rel.getOrigen() + "->" + rel.getDestino());
            }
            for (RelationSnapshot rel : result.getRemoved()) {
                removedKeys.add(rel.getOrigen() + "->" + rel.getDestino());
            }
            for (RelationChange change : result.getTypeChanges()) {
                typeChangedKeys.add(change.getBefore().getOrigen() + "->" + change.getBefore().getDestino());
            }
            for (RelationChange change : result.getDecisionChanges()) {
                decisionChangedKeys.add(change.getBefore().getOrigen() + "->" + change.getBefore().getDestino());
            }
            
            // Verify no relation appears in multiple categories
            // Added relations should not be in removed, type changed, or decision changed
            for (String key : addedKeys) {
                assert !removedKeys.contains(key) : 
                    "Relation " + key + " should not be both added and removed";
                assert !typeChangedKeys.contains(key) : 
                    "Relation " + key + " should not be both added and type changed";
                assert !decisionChangedKeys.contains(key) : 
                    "Relation " + key + " should not be both added and decision changed";
            }
            
            // Removed relations should not be in type changed or decision changed
            for (String key : removedKeys) {
                assert !typeChangedKeys.contains(key) : 
                    "Relation " + key + " should not be both removed and type changed";
                assert !decisionChangedKeys.contains(key) : 
                    "Relation " + key + " should not be both removed and decision changed";
            }
            
            // Type changed relations should not be in decision changed
            for (String key : typeChangedKeys) {
                assert !decisionChangedKeys.contains(key) : 
                    "Relation " + key + " should not be both type changed and decision changed";
            }
            
            // Verify added relations are only in snap2
            for (RelationSnapshot added : result.getAdded()) {
                String key = added.getOrigen() + "->" + added.getDestino();
                boolean inSnap1 = relations1.stream()
                    .anyMatch(r -> (r.getOrigen() + "->" + r.getDestino()).equals(key));
                assert !inSnap1 : "Added relation " + key + " should not exist in snapshot 1";
            }
            
            // Verify removed relations are only in snap1
            for (RelationSnapshot removed : result.getRemoved()) {
                String key = removed.getOrigen() + "->" + removed.getDestino();
                boolean inSnap2 = relations2.stream()
                    .anyMatch(r -> (r.getOrigen() + "->" + r.getDestino()).equals(key));
                assert !inSnap2 : "Removed relation " + key + " should not exist in snapshot 2";
            }
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    /**
     * Helper method to generate random decisions for relations.
     */
    private Map<String, Boolean> generateDecisions(List<RelacionDetectada> relations) {
        Map<String, Boolean> decisions = new HashMap<>();
        int i = 0;
        for (RelacionDetectada rel : relations) {
            // Alternate between accepted, rejected, and pending
            if (i % 3 == 0) {
                decisions.put(rel.getClaveMemoria(), true);
            } else if (i % 3 == 1) {
                decisions.put(rel.getClaveMemoria(), false);
            }
            // i % 3 == 2 means pending (no entry in map)
            i++;
        }
        return decisions;
    }


    // ==================== Providers ====================

    @Provide
    Arbitrary<List<RelacionDetectada>> relations() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
            .flatMap(origen -> 
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                    .filter(destino -> !destino.equals(origen))
                    .flatMap(destino ->
                        Arbitraries.of("OneToOne", "OneToMany", "ManyToOne", "ManyToMany")
                            .flatMap(tipo ->
                                Arbitraries.doubles().between(0.1, 1.0)
                                    .map(conf -> new RelacionDetectada(origen, destino, tipo, conf))
                            )
                    )
            ).list().ofMinSize(0).ofMaxSize(20)
            .map(this::removeDuplicateRelations);
    }

    @Provide
    Arbitrary<Map<String, Boolean>> decisions() {
        return Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30),
            Arbitraries.of(true, false)
        ).ofMinSize(0).ofMaxSize(10);
    }

    /**
     * Remove duplicate relations (same origen->destino pair).
     */
    private List<RelacionDetectada> removeDuplicateRelations(List<RelacionDetectada> relations) {
        Set<String> seen = new HashSet<>();
        List<RelacionDetectada> unique = new ArrayList<>();
        for (RelacionDetectada rel : relations) {
            String key = rel.getOrigen() + "->" + rel.getDestino();
            if (!seen.contains(key)) {
                seen.add(key);
                unique.add(rel);
            }
        }
        return unique;
    }

    // ==================== Helper Methods ====================

    private void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }
}
