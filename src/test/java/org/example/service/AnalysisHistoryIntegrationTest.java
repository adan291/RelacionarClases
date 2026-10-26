package org.example.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.model.AnalysisSnapshot;
import org.example.model.ComparisonResult;
import org.example.model.RelacionDetectada;
import org.example.model.SnapshotMetadata;
import org.example.testutil.TestDataFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for AnalysisHistoryService.
 * Tests snapshot save/restore workflow, comparison, and bulk deletion.
 * 
 * Requirements: 1.2
 */
@Tag("integration")
class AnalysisHistoryIntegrationTest {

    @TempDir
    Path tempDir;

    private AnalysisHistoryService historyService;
    private SnapshotManager snapshotManager;

    @BeforeEach
    void setUp() {
        snapshotManager = new SnapshotManager(tempDir.toString());
        historyService = new AnalysisHistoryService(snapshotManager);
    }

    /**
     * Test snapshot save and restore workflow.
     * Verifies that a snapshot can be saved and restored with all data intact.
     * 
     * Requirements: 1.2
     */
    @Test
    void snapshotWorkflow_saveAndRestore() throws IOException {
        // Arrange
        String projectPath = "/test/project/myapp";
        List<RelacionDetectada> relations = TestDataFactory.createSampleRelations(5);
        Map<String, Boolean> decisions = new HashMap<>();
        decisions.put(relations.get(0).getClaveMemoria(), true);
        decisions.put(relations.get(1).getClaveMemoria(), false);
        String description = "Test snapshot";
        
        // Act - save snapshot
        String snapshotId = historyService.saveSnapshot(projectPath, relations, decisions, description);
        
        // Assert - snapshot ID is generated
        assertNotNull(snapshotId);
        assertFalse(snapshotId.isEmpty());
        
        // Act - restore snapshot
        AnalysisSnapshot restored = historyService.restoreSnapshot(snapshotId);
        
        // Assert - restored snapshot matches original
        assertNotNull(restored);
        assertEquals(snapshotId, restored.getId());
        assertEquals(5, restored.getRelations().size());
        
        // Verify decisions are preserved
        Map<String, Boolean> restoredDecisions = restored.getDecisions();
        assertEquals(2, restoredDecisions.size());
        assertTrue(restoredDecisions.get(relations.get(0).getClaveMemoria()));
        assertFalse(restoredDecisions.get(relations.get(1).getClaveMemoria()));
        
        // Verify metadata
        SnapshotMetadata metadata = restored.getMetadata();
        assertNotNull(metadata);
        assertEquals(projectPath, metadata.getProjectPath());
        assertEquals(description, metadata.getDescription());
    }

    /**
     * Test snapshot comparison.
     * Verifies that comparing two snapshots correctly identifies added, removed, and changed relations.
     * 
     * Requirements: 1.2
     */
    @Test
    void snapshotComparison_identifiesChanges() throws IOException, InterruptedException {
        // Arrange - create first snapshot
        String projectPath = "/test/project/myapp";
        List<RelacionDetectada> relations1 = new ArrayList<>();
        relations1.add(TestDataFactory.createRelation("User", "Order", "OneToMany", 0.9));
        relations1.add(TestDataFactory.createRelation("Order", "Product", "ManyToMany", 0.8));
        relations1.add(TestDataFactory.createRelation("Product", "Category", "ManyToOne", 0.85));
        
        Map<String, Boolean> decisions1 = new HashMap<>();
        decisions1.put(relations1.get(0).getClaveMemoria(), true);
        
        String snapshotId1 = historyService.saveSnapshot(projectPath, relations1, decisions1, "Snapshot 1");
        
        // Wait to ensure different timestamp for second snapshot
        Thread.sleep(1100);
        
        // Create second snapshot with changes
        List<RelacionDetectada> relations2 = new ArrayList<>();
        relations2.add(TestDataFactory.createRelation("User", "Order", "OneToMany", 0.9)); // Same
        relations2.add(TestDataFactory.createRelation("Order", "Product", "OneToMany", 0.8)); // Type changed
        relations2.add(TestDataFactory.createRelation("Customer", "Invoice", "OneToMany", 0.75)); // Added
        // Product -> Category removed
        
        Map<String, Boolean> decisions2 = new HashMap<>();
        decisions2.put(relations2.get(0).getClaveMemoria(), false); // Decision changed
        
        String snapshotId2 = historyService.saveSnapshot(projectPath, relations2, decisions2, "Snapshot 2");
        
        // Act
        ComparisonResult result = historyService.compare(snapshotId1, snapshotId2);
        
        // Assert
        assertNotNull(result);
        assertNotEquals(snapshotId1, snapshotId2, "Snapshot IDs should be different");
        
        // Check added relations
        assertEquals(1, result.getAdded().size());
        assertEquals("Customer", result.getAdded().get(0).getOrigen());
        
        // Check removed relations
        assertEquals(1, result.getRemoved().size());
        assertEquals("Product", result.getRemoved().get(0).getOrigen());
        
        // Check type changes
        assertEquals(1, result.getTypeChanges().size());
        assertEquals("Order", result.getTypeChanges().get(0).getBefore().getOrigen());
        
        // Check decision changes
        assertEquals(1, result.getDecisionChanges().size());
        assertEquals("User", result.getDecisionChanges().get(0).getBefore().getOrigen());
    }

    /**
     * Test bulk deletion of snapshots.
     * Verifies that multiple snapshots can be deleted at once.
     * 
     * Requirements: 1.2
     */
    @Test
    void bulkDeletion_removesAllSpecified() throws IOException, InterruptedException {
        // Arrange - create multiple snapshots with delays to ensure unique IDs
        String projectPath = "/test/project/myapp";
        List<RelacionDetectada> relations = TestDataFactory.createSampleRelations(3);
        
        String snapshotId1 = historyService.saveSnapshot(projectPath, relations, new HashMap<>(), "Snapshot 1");
        Thread.sleep(1100); // Wait for different timestamp
        String snapshotId2 = historyService.saveSnapshot(projectPath, relations, new HashMap<>(), "Snapshot 2");
        Thread.sleep(1100);
        String snapshotId3 = historyService.saveSnapshot(projectPath, relations, new HashMap<>(), "Snapshot 3");
        
        // Verify all snapshots exist and have unique IDs
        assertNotEquals(snapshotId1, snapshotId2);
        assertNotEquals(snapshotId2, snapshotId3);
        assertEquals(3, historyService.listSnapshots().size());
        
        // Act - delete two snapshots
        List<String> toDelete = List.of(snapshotId1, snapshotId3);
        int deletedCount = historyService.deleteSnapshots(toDelete);
        
        // Assert
        assertEquals(2, deletedCount);
        
        // Verify only one snapshot remains
        List<SnapshotMetadata> remaining = historyService.listSnapshots();
        assertEquals(1, remaining.size());
        assertEquals(snapshotId2, remaining.get(0).getId());
    }

    /**
     * Test listing snapshots returns sorted by timestamp.
     * Verifies that snapshots are listed with newest first.
     * 
     * Requirements: 1.2
     */
    @Test
    void listSnapshots_sortedByTimestamp() throws IOException, InterruptedException {
        // Arrange - create snapshots with sufficient delay for unique timestamps
        String projectPath = "/test/project/myapp";
        List<RelacionDetectada> relations = TestDataFactory.createSampleRelations(2);
        
        String snapshotId1 = historyService.saveSnapshot(projectPath, relations, new HashMap<>(), "First");
        Thread.sleep(1100); // Wait for different timestamp (ID uses seconds)
        String snapshotId2 = historyService.saveSnapshot(projectPath, relations, new HashMap<>(), "Second");
        Thread.sleep(1100);
        String snapshotId3 = historyService.saveSnapshot(projectPath, relations, new HashMap<>(), "Third");
        
        // Verify unique IDs
        assertNotEquals(snapshotId1, snapshotId2);
        assertNotEquals(snapshotId2, snapshotId3);
        
        // Act
        List<SnapshotMetadata> snapshots = historyService.listSnapshots();
        
        // Assert - newest first
        assertEquals(3, snapshots.size());
        assertEquals(snapshotId3, snapshots.get(0).getId());
        assertEquals(snapshotId2, snapshots.get(1).getId());
        assertEquals(snapshotId1, snapshots.get(2).getId());
    }

    /**
     * Test restore non-existent snapshot.
     * Verifies that restoring a non-existent snapshot returns null.
     * 
     * Requirements: 1.2
     */
    @Test
    void restoreNonExistent_returnsNull() throws IOException {
        // Act
        AnalysisSnapshot result = historyService.restoreSnapshot("non-existent-id");
        
        // Assert
        assertNull(result);
    }

    /**
     * Test delete non-existent snapshot.
     * Verifies that deleting a non-existent snapshot returns false.
     * 
     * Requirements: 1.2
     */
    @Test
    void deleteNonExistent_returnsFalse() {
        // Act
        boolean result = historyService.deleteSnapshot("non-existent-id");
        
        // Assert
        assertFalse(result);
    }

    /**
     * Test snapshot summary retrieval.
     * Verifies that snapshot summary contains correct statistics.
     * 
     * Requirements: 1.2
     */
    @Test
    void getSummary_containsCorrectStatistics() throws IOException {
        // Arrange
        String projectPath = "/test/project/myapp";
        List<RelacionDetectada> relations = new ArrayList<>();
        relations.add(TestDataFactory.createRelation("User", "Order", "OneToMany", 0.9));
        relations.add(TestDataFactory.createRelation("Order", "Product", "ManyToMany", 0.8));
        relations.add(TestDataFactory.createRelation("Product", "Category", "ManyToOne", 0.85));
        
        Map<String, Boolean> decisions = new HashMap<>();
        decisions.put(relations.get(0).getClaveMemoria(), true);  // Accepted
        decisions.put(relations.get(1).getClaveMemoria(), false); // Rejected
        // Third relation is pending
        
        String snapshotId = historyService.saveSnapshot(projectPath, relations, decisions, "Test");
        
        // Act
        SnapshotMetadata summary = historyService.getSummary(snapshotId);
        
        // Assert
        assertNotNull(summary);
        assertEquals(3, summary.getTotalRelations());
        assertEquals(1, summary.getAcceptedCount());
        assertEquals(1, summary.getRejectedCount());
        assertEquals(1, summary.getPendingCount());
    }

    /**
     * Test comparing snapshots from different projects.
     * Verifies that comparison works but indicates different projects.
     * 
     * Requirements: 1.2
     */
    @Test
    void compareSnapshots_differentProjects() throws IOException {
        // Arrange
        List<RelacionDetectada> relations = TestDataFactory.createSampleRelations(2);
        
        String snapshotId1 = historyService.saveSnapshot("/project/app1", relations, new HashMap<>(), "App1");
        String snapshotId2 = historyService.saveSnapshot("/project/app2", relations, new HashMap<>(), "App2");
        
        // Act
        ComparisonResult result = historyService.compare(snapshotId1, snapshotId2);
        
        // Assert
        assertNotNull(result);
        assertFalse(result.isSameProject());
    }

    /**
     * Test comparing snapshots from same project.
     * Verifies that comparison indicates same project.
     * 
     * Requirements: 1.2
     */
    @Test
    void compareSnapshots_sameProject() throws IOException {
        // Arrange
        String projectPath = "/project/myapp";
        List<RelacionDetectada> relations = TestDataFactory.createSampleRelations(2);
        
        String snapshotId1 = historyService.saveSnapshot(projectPath, relations, new HashMap<>(), "V1");
        String snapshotId2 = historyService.saveSnapshot(projectPath, relations, new HashMap<>(), "V2");
        
        // Act
        ComparisonResult result = historyService.compare(snapshotId1, snapshotId2);
        
        // Assert
        assertNotNull(result);
        assertTrue(result.isSameProject());
    }
}
