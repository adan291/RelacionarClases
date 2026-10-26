package org.example.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.example.model.AnalysisSnapshot;
import org.example.model.DecisionState;
import org.example.model.RelationSnapshot;
import org.example.model.SnapshotMetadata;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for SnapshotManager.
 * Tests correctness properties defined in the design document.
 */
public class SnapshotManagerProperties {

    // Pattern for valid snapshot IDs: alphanumeric with underscores, ending with _YYYYMMDD_HHmmss
    private static final Pattern SNAPSHOT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+_\\d{8}_\\d{6}$");

    /**
     * **Feature: analysis-history, Property 6: Snapshot ID Format and Sanitization**
     * 
     * For any project name (including special characters), the generated snapshot ID 
     * should match the pattern [a-zA-Z0-9_]+_\d{8}_\d{6} and contain no special 
     * characters other than underscores.
     * 
     * **Validates: Requirements 6.1, 6.2**
     */
    @Property(tries = 100)
    void snapshotIdFormatAndSanitization(@ForAll("projectNames") String projectName) {
        SnapshotManager manager = new SnapshotManager(createTempDir());
        
        String snapshotId = manager.generateSnapshotId(projectName);
        
        // Verify the ID matches the expected pattern
        assert SNAPSHOT_ID_PATTERN.matcher(snapshotId).matches() :
            "Snapshot ID should match pattern [a-zA-Z0-9_]+_\\d{8}_\\d{6}, but was: " + snapshotId;
        
        // Verify no special characters other than underscores
        String withoutUnderscores = snapshotId.replace("_", "");
        assert withoutUnderscores.matches("[a-zA-Z0-9]+") :
            "Snapshot ID should only contain alphanumeric characters and underscores, but was: " + snapshotId;
    }

    /**
     * Additional property: Sanitized project name contains no special characters
     */
    @Property(tries = 100)
    void sanitizedProjectNameContainsNoSpecialChars(@ForAll("projectNames") String projectName) {
        SnapshotManager manager = new SnapshotManager(createTempDir());
        
        String sanitized = manager.sanitizeProjectName(projectName);
        
        // Verify sanitized name only contains alphanumeric and underscores
        assert sanitized.matches("[a-zA-Z0-9_]+") :
            "Sanitized name should only contain alphanumeric and underscores, but was: " + sanitized;
        
        // Verify no consecutive underscores
        assert !sanitized.contains("__") :
            "Sanitized name should not contain consecutive underscores, but was: " + sanitized;
        
        // Verify no leading or trailing underscores
        assert !sanitized.startsWith("_") && !sanitized.endsWith("_") :
            "Sanitized name should not start or end with underscore, but was: " + sanitized;
    }


    /**
     * **Feature: analysis-history, Property 5: Deletion Removes From History**
     * 
     * For any snapshot that is deleted (single or bulk), it should no longer 
     * appear in the history list after deletion.
     * 
     * **Validates: Requirements 5.2, 5.4**
     */
    @Property(tries = 100)
    void deletionRemovesFromHistory(@ForAll @IntRange(min = 1, max = 5) int snapshotCount) throws IOException {
        Path tempDir = Files.createTempDirectory("snapshot_test_");
        try {
            SnapshotManager manager = new SnapshotManager(tempDir.toString());
            
            // Create and save multiple snapshots
            List<String> savedIds = new ArrayList<>();
            for (int i = 0; i < snapshotCount; i++) {
                AnalysisSnapshot snapshot = createTestSnapshot("project" + i, "Test snapshot " + i);
                manager.saveSnapshot(snapshot);
                savedIds.add(snapshot.getId());
            }
            
            // Verify all snapshots are in the list
            List<SnapshotMetadata> beforeDelete = manager.listSnapshots();
            assert beforeDelete.size() == snapshotCount :
                "Should have " + snapshotCount + " snapshots before deletion, but had " + beforeDelete.size();
            
            // Delete the first snapshot
            String idToDelete = savedIds.get(0);
            boolean deleted = manager.deleteSnapshot(idToDelete);
            assert deleted : "Deletion should succeed";
            
            // Verify deleted snapshot is no longer in the list
            List<SnapshotMetadata> afterDelete = manager.listSnapshots();
            assert afterDelete.size() == snapshotCount - 1 :
                "Should have " + (snapshotCount - 1) + " snapshots after deletion, but had " + afterDelete.size();
            
            boolean foundDeleted = afterDelete.stream()
                .anyMatch(m -> m.getId().equals(idToDelete));
            assert !foundDeleted : "Deleted snapshot should not appear in history list";
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    /**
     * Additional property: Bulk deletion removes all specified snapshots
     */
    @Property(tries = 50)
    void bulkDeletionRemovesAllSpecified(@ForAll @IntRange(min = 2, max = 5) int snapshotCount) throws IOException {
        Path tempDir = Files.createTempDirectory("snapshot_bulk_test_");
        try {
            SnapshotManager manager = new SnapshotManager(tempDir.toString());
            
            // Create and save multiple snapshots
            List<String> savedIds = new ArrayList<>();
            for (int i = 0; i < snapshotCount; i++) {
                AnalysisSnapshot snapshot = createTestSnapshot("project" + i, "Test snapshot " + i);
                manager.saveSnapshot(snapshot);
                savedIds.add(snapshot.getId());
            }
            
            // Delete half of the snapshots
            int deleteCount = snapshotCount / 2;
            List<String> idsToDelete = savedIds.subList(0, deleteCount);
            int deletedCount = manager.deleteSnapshots(new ArrayList<>(idsToDelete));
            
            assert deletedCount == deleteCount :
                "Should have deleted " + deleteCount + " snapshots, but deleted " + deletedCount;
            
            // Verify deleted snapshots are no longer in the list
            List<SnapshotMetadata> afterDelete = manager.listSnapshots();
            for (String deletedId : idsToDelete) {
                boolean found = afterDelete.stream()
                    .anyMatch(m -> m.getId().equals(deletedId));
                assert !found : "Deleted snapshot " + deletedId + " should not appear in history list";
            }
            
        } finally {
            deleteDirectory(tempDir);
        }
    }


    /**
     * **Feature: analysis-history, Property 2: History List Chronological Order**
     * 
     * For any set of snapshots with different timestamps, the history list should 
     * return them sorted in descending chronological order (newest first).
     * 
     * **Validates: Requirements 2.1**
     */
    @Property(tries = 100)
    void historyListChronologicalOrder(@ForAll @IntRange(min = 2, max = 10) int snapshotCount) throws IOException {
        Path tempDir = Files.createTempDirectory("snapshot_order_test_");
        try {
            SnapshotManager manager = new SnapshotManager(tempDir.toString());
            
            // Create snapshots with different timestamps
            LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
            for (int i = 0; i < snapshotCount; i++) {
                LocalDateTime timestamp = baseTime.plusHours(i);
                AnalysisSnapshot snapshot = createTestSnapshotWithTimestamp(
                    "project", "Snapshot " + i, timestamp, "snap_" + i);
                manager.saveSnapshot(snapshot);
            }
            
            // Get the list and verify order
            List<SnapshotMetadata> snapshots = manager.listSnapshots();
            
            assert snapshots.size() == snapshotCount :
                "Should have " + snapshotCount + " snapshots, but had " + snapshots.size();
            
            // Verify descending order (newest first)
            for (int i = 0; i < snapshots.size() - 1; i++) {
                LocalDateTime current = snapshots.get(i).getTimestamp();
                LocalDateTime next = snapshots.get(i + 1).getTimestamp();
                
                assert current != null && next != null :
                    "Timestamps should not be null";
                
                assert !current.isBefore(next) :
                    "Snapshots should be in descending order. Found " + current + " before " + next;
            }
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<String> projectNames() {
        return Arbitraries.strings()
            .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_. @#$%&*()[]{}|\\:;<>,?/~`!^")
            .ofMinLength(1)
            .ofMaxLength(50);
    }

    // ==================== Helper Methods ====================

    private String createTempDir() {
        try {
            return Files.createTempDirectory("snapshot_test_").toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory", e);
        }
    }

    private AnalysisSnapshot createTestSnapshot(String projectName, String description) {
        String id = "test_" + projectName + "_" + System.nanoTime();
        LocalDateTime timestamp = LocalDateTime.now();
        SnapshotMetadata metadata = new SnapshotMetadata(id, timestamp, "/path/to/" + projectName, 
                                                          projectName, description);
        
        List<RelationSnapshot> relations = new ArrayList<>();
        RelationSnapshot relation = new RelationSnapshot();
        relation.setOrigen("ClassA");
        relation.setDestino("ClassB");
        relation.setTipo("OneToMany");
        relation.setConfianza(0.85);
        relation.setDecisionState(DecisionState.PENDING);
        relations.add(relation);
        
        Map<String, Boolean> decisions = new HashMap<>();
        
        AnalysisSnapshot snapshot = new AnalysisSnapshot(id, metadata, relations, decisions);
        snapshot.computeStatistics();
        return snapshot;
    }

    private AnalysisSnapshot createTestSnapshotWithTimestamp(String projectName, String description, 
                                                              LocalDateTime timestamp, String idSuffix) {
        String id = "test_" + projectName + "_" + idSuffix;
        SnapshotMetadata metadata = new SnapshotMetadata(id, timestamp, "/path/to/" + projectName, 
                                                          projectName, description);
        
        List<RelationSnapshot> relations = new ArrayList<>();
        Map<String, Boolean> decisions = new HashMap<>();
        
        AnalysisSnapshot snapshot = new AnalysisSnapshot(id, metadata, relations, decisions);
        snapshot.computeStatistics();
        return snapshot;
    }

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
