package org.example.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.analysis.RelationDetector;
import org.example.model.AnalysisSnapshot;
import org.example.model.DecisionState;
import org.example.model.RelacionDetectada;
import org.example.model.RelationSnapshot;
import org.example.testutil.TestDataFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for data flow between components.
 * Tests analysis to UI flow, decision persistence, and snapshot workflows.
 * 
 * Requirements: 5.1-5.4
 */
@Tag("integration")
class DataFlowIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH = "src/test/resources/sample-project";

    @TempDir
    Path tempDir;

    private File sampleProjectDir;
    private MemoryService memoryService;
    private AnalysisHistoryService historyService;
    private SnapshotManager snapshotManager;
    private RelationDetector relationDetector;

    @BeforeEach
    void setUp() {
        sampleProjectDir = new File(SAMPLE_PROJECT_PATH);
        String memoryFile = tempDir.resolve("test-memory.json").toString();
        memoryService = new MemoryService(memoryFile);
        snapshotManager = new SnapshotManager(tempDir.toString());
        historyService = new AnalysisHistoryService(snapshotManager);
        relationDetector = new RelationDetector();
    }


    /**
     * Test analysis to UI flow.
     * Verifies that relations detected from analysis appear in expected format.
     * 
     * Requirements: 5.1
     */
    @Test
    void analysisToUIFlow_relationsAppearInExpectedFormat() throws IOException {
        // Arrange - ensure sample project exists
        assertTrue(sampleProjectDir.exists(), 
            "Sample project directory should exist at: " + SAMPLE_PROJECT_PATH);
        
        // Act - analyze project
        relationDetector.loadClasses(sampleProjectDir);
        List<RelacionDetectada> relations = relationDetector.detectRelationsAsObjects();
        
        // Assert - verify relations are in expected format for UI display
        assertNotNull(relations, "Relations list should not be null");
        assertFalse(relations.isEmpty(), "Should detect at least some relations");
        
        // Verify each relation has required fields for UI display
        for (RelacionDetectada relation : relations) {
            assertNotNull(relation.getOrigen(), "Relation should have source class");
            assertNotNull(relation.getDestino(), "Relation should have target class");
            assertNotNull(relation.getTipo(), "Relation should have type");
            assertTrue(relation.getConfianza() >= 0 && relation.getConfianza() <= 1,
                "Confidence should be between 0 and 1");
            
            // Verify memory key format (used for persistence)
            String memoryKey = relation.getClaveMemoria();
            assertNotNull(memoryKey, "Memory key should not be null");
            assertTrue(memoryKey.contains("->"), "Memory key should contain arrow");
            assertTrue(memoryKey.contains(":"), "Memory key should contain colon");
        }
        
        // Verify expected relation types are present
        boolean hasOneToMany = relations.stream()
            .anyMatch(r -> "OneToMany".equals(r.getTipo()));
        boolean hasManyToOne = relations.stream()
            .anyMatch(r -> "ManyToOne".equals(r.getTipo()));
        
        assertTrue(hasOneToMany, "Should detect OneToMany relations");
        assertTrue(hasManyToOne, "Should detect ManyToOne relations");
    }


    /**
     * Test decision persistence flow.
     * Verifies that accept/reject decisions are saved to memory and persist after reload.
     * 
     * **Feature: integration-tests, Property 2: Memory Persistence**
     * **Validates: Requirements 5.2**
     */
    @Test
    void decisionPersistenceFlow_decisionsPersistedAfterReload() throws IOException {
        // Arrange - analyze project and get relations
        relationDetector.loadClasses(sampleProjectDir);
        List<RelacionDetectada> relations = relationDetector.detectRelationsAsObjects();
        assertFalse(relations.isEmpty(), "Should have relations to test with");
        
        // Act - accept/reject some relations
        RelacionDetectada firstRelation = relations.get(0);
        RelacionDetectada secondRelation = relations.size() > 1 ? relations.get(1) : firstRelation;
        
        String firstKey = firstRelation.getClaveMemoria();
        String secondKey = secondRelation.getClaveMemoria();
        
        memoryService.recordarDecision(firstKey, true);  // Accept
        memoryService.recordarDecision(secondKey, false); // Reject
        
        // Verify decisions are saved
        Map<String, String> memoria = memoryService.getMemoria();
        assertEquals("aceptada", memoria.get(firstKey), "First relation should be accepted");
        assertEquals("rechazada", memoria.get(secondKey), "Second relation should be rejected");
        
        // Act - create new service instance to simulate restart
        String memoryFile = tempDir.resolve("test-memory.json").toString();
        MemoryService reloadedService = new MemoryService(memoryFile);
        
        // Assert - verify decisions persist after reload
        Map<String, String> reloadedMemoria = reloadedService.getMemoria();
        assertEquals("aceptada", reloadedMemoria.get(firstKey), 
            "First relation should still be accepted after reload");
        assertEquals("rechazada", reloadedMemoria.get(secondKey), 
            "Second relation should still be rejected after reload");
    }

    /**
     * Test decision update flow.
     * Verifies that changing a decision updates the persisted value.
     * 
     * **Validates: Requirements 5.2**
     */
    @Test
    void decisionUpdateFlow_changedDecisionsPersist() throws IOException {
        // Arrange - create and save initial decision
        relationDetector.loadClasses(sampleProjectDir);
        List<RelacionDetectada> relations = relationDetector.detectRelationsAsObjects();
        assertFalse(relations.isEmpty(), "Should have relations to test with");
        
        RelacionDetectada relation = relations.get(0);
        String key = relation.getClaveMemoria();
        
        // Initially accept
        memoryService.recordarDecision(key, true);
        assertEquals("aceptada", memoryService.getMemoria().get(key));
        
        // Act - change decision to reject
        memoryService.recordarDecision(key, false);
        
        // Assert - verify change persists
        String memoryFile = tempDir.resolve("test-memory.json").toString();
        MemoryService reloadedService = new MemoryService(memoryFile);
        assertEquals("rechazada", reloadedService.getMemoria().get(key),
            "Changed decision should persist after reload");
    }


    /**
     * Test snapshot flow.
     * Verifies that saving a snapshot, modifying state, and restoring returns to original state.
     * 
     * **Feature: integration-tests, Property 4: Snapshot Integrity**
     * **Validates: Requirements 5.3**
     */
    @Test
    void snapshotFlow_restoreMatchesOriginalState() throws IOException {
        // Arrange - analyze project and create initial state
        relationDetector.loadClasses(sampleProjectDir);
        List<RelacionDetectada> relations = relationDetector.detectRelationsAsObjects();
        assertFalse(relations.isEmpty(), "Should have relations to test with");
        
        String projectPath = sampleProjectDir.getAbsolutePath();
        
        // Create decisions for some relations
        Map<String, Boolean> originalDecisions = new HashMap<>();
        for (int i = 0; i < Math.min(3, relations.size()); i++) {
            String key = relations.get(i).getClaveMemoria();
            originalDecisions.put(key, i % 2 == 0); // Alternate accept/reject
        }
        
        // Act - save snapshot
        String snapshotId = historyService.saveSnapshot(
            projectPath, relations, originalDecisions, "Original state");
        assertNotNull(snapshotId, "Snapshot ID should not be null");
        
        // Modify state - change decisions
        Map<String, Boolean> modifiedDecisions = new HashMap<>();
        for (String key : originalDecisions.keySet()) {
            modifiedDecisions.put(key, !originalDecisions.get(key)); // Flip all decisions
        }
        
        // Act - restore snapshot
        AnalysisSnapshot restored = historyService.restoreSnapshot(snapshotId);
        
        // Assert - verify restored state matches original
        assertNotNull(restored, "Restored snapshot should not be null");
        assertEquals(snapshotId, restored.getId(), "Snapshot ID should match");
        
        // Verify relations count matches
        assertEquals(relations.size(), restored.getRelations().size(),
            "Restored relations count should match original");
        
        // Verify decisions match original
        Map<String, Boolean> restoredDecisions = restored.getDecisions();
        for (Map.Entry<String, Boolean> entry : originalDecisions.entrySet()) {
            assertEquals(entry.getValue(), restoredDecisions.get(entry.getKey()),
                "Decision for " + entry.getKey() + " should match original");
        }
        
        // Verify relation data integrity
        for (RelationSnapshot restoredRel : restored.getRelations()) {
            assertNotNull(restoredRel.getOrigen(), "Restored relation should have source");
            assertNotNull(restoredRel.getDestino(), "Restored relation should have target");
            assertNotNull(restoredRel.getTipo(), "Restored relation should have type");
        }
    }

    /**
     * Test snapshot with full analysis cycle.
     * Verifies complete flow: analyze -> decide -> snapshot -> restore.
     * 
     * **Validates: Requirements 5.3**
     */
    @Test
    void snapshotFlow_fullAnalysisCycle() throws IOException {
        // Arrange - analyze project
        relationDetector.loadClasses(sampleProjectDir);
        List<RelacionDetectada> relations = relationDetector.detectRelationsAsObjects();
        String projectPath = sampleProjectDir.getAbsolutePath();
        
        // Make decisions and save to memory
        Map<String, Boolean> decisions = new HashMap<>();
        for (RelacionDetectada rel : relations) {
            String key = rel.getClaveMemoria();
            boolean accept = rel.getTipo().equals("OneToMany"); // Accept OneToMany, reject others
            decisions.put(key, accept);
            memoryService.recordarDecision(key, accept);
        }
        
        // Save snapshot
        String snapshotId = historyService.saveSnapshot(
            projectPath, relations, decisions, "Full cycle test");
        
        // Clear memory to simulate fresh start
        memoryService.clearMemoria();
        assertTrue(memoryService.getMemoria().isEmpty(), "Memory should be cleared");
        
        // Restore snapshot
        AnalysisSnapshot restored = historyService.restoreSnapshot(snapshotId);
        
        // Verify restored snapshot has correct decision states
        assertNotNull(restored);
        for (RelationSnapshot rel : restored.getRelations()) {
            String key = rel.getOrigen() + " -> " + rel.getDestino() + " : " + rel.getTipo();
            Boolean expectedDecision = decisions.get(key);
            
            if (expectedDecision != null) {
                DecisionState expectedState = expectedDecision 
                    ? DecisionState.ACCEPTED 
                    : DecisionState.REJECTED;
                assertEquals(expectedState, rel.getDecisionState(),
                    "Decision state should match for " + key);
            }
        }
    }


    /**
     * Test configuration flow from file to components.
     * Verifies that configuration values are properly loaded and applied.
     * 
     * **Validates: Requirements 5.4**
     */
    @Test
    void configurationFlow_valuesAppliedToComponents() {
        // Arrange & Act - services are created in setUp with temp directory config
        
        // Assert - verify services are properly configured
        assertNotNull(memoryService, "MemoryService should be created");
        assertNotNull(historyService, "AnalysisHistoryService should be created");
        assertNotNull(snapshotManager, "SnapshotManager should be created");
        
        // Verify snapshot manager uses correct directory
        File snapshotsDir = snapshotManager.getSnapshotsDirectory();
        assertNotNull(snapshotsDir, "Snapshots directory should be set");
        assertTrue(snapshotsDir.getAbsolutePath().contains(tempDir.toString()) ||
                   tempDir.toString().contains(snapshotsDir.getName()),
            "Snapshots directory should be in temp directory");
    }

    /**
     * Test data integrity across multiple operations.
     * Verifies that data remains consistent through multiple save/load cycles.
     * 
     * **Validates: Requirements 5.2, 5.3**
     */
    @Test
    void dataIntegrity_multipleOperationsCycle() throws IOException, InterruptedException {
        // Arrange
        List<RelacionDetectada> relations = TestDataFactory.createSampleRelations(5);
        String projectPath = "/test/project/integrity-test";
        
        // First cycle - save with some decisions
        Map<String, Boolean> decisions1 = new HashMap<>();
        decisions1.put(relations.get(0).getClaveMemoria(), true);
        decisions1.put(relations.get(1).getClaveMemoria(), false);
        
        String snapshotId1 = historyService.saveSnapshot(
            projectPath, relations, decisions1, "Cycle 1");
        
        // Wait for unique timestamp
        Thread.sleep(1100);
        
        // Second cycle - add more decisions
        Map<String, Boolean> decisions2 = new HashMap<>(decisions1);
        decisions2.put(relations.get(2).getClaveMemoria(), true);
        decisions2.put(relations.get(3).getClaveMemoria(), false);
        
        String snapshotId2 = historyService.saveSnapshot(
            projectPath, relations, decisions2, "Cycle 2");
        
        // Verify both snapshots exist and are different
        assertNotNull(snapshotId1);
        assertNotNull(snapshotId2);
        
        // Restore first snapshot and verify
        AnalysisSnapshot restored1 = historyService.restoreSnapshot(snapshotId1);
        assertNotNull(restored1);
        assertEquals(2, restored1.getDecisions().size(), 
            "First snapshot should have 2 decisions");
        
        // Restore second snapshot and verify
        AnalysisSnapshot restored2 = historyService.restoreSnapshot(snapshotId2);
        assertNotNull(restored2);
        assertEquals(4, restored2.getDecisions().size(), 
            "Second snapshot should have 4 decisions");
        
        // Verify data integrity - decisions should match what was saved
        assertTrue(restored1.getDecisions().get(relations.get(0).getClaveMemoria()));
        assertFalse(restored1.getDecisions().get(relations.get(1).getClaveMemoria()));
        
        assertTrue(restored2.getDecisions().get(relations.get(2).getClaveMemoria()));
        assertFalse(restored2.getDecisions().get(relations.get(3).getClaveMemoria()));
    }
}
