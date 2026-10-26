package org.example.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for MemoryService.
 * Tests save/load round trip, corrupted file handling, and concurrent access.
 * 
 * Requirements: 1.1, 6.1
 */
@Tag("integration")
class MemoryServiceIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Test save and load round trip.
     * Verifies that decisions saved to memory can be retrieved after reload.
     * 
     * Requirements: 1.1
     */
    @Test
    void saveAndLoadMemory_roundTrip() {
        // Arrange
        String memoryFile = tempDir.resolve("test-memory.json").toString();
        MemoryService service = new MemoryService(memoryFile);
        
        // Act - save some decisions
        service.recordarDecision("User -> Order : OneToMany", true);
        service.recordarDecision("Order -> User : ManyToOne", true);
        service.recordarDecision("Product -> Category : ManyToMany", false);
        
        // Create a new service instance to simulate restart
        MemoryService reloadedService = new MemoryService(memoryFile);
        
        // Assert - verify decisions are persisted
        Map<String, String> memoria = reloadedService.getMemoria();
        assertEquals(3, memoria.size());
        assertEquals("aceptada", memoria.get("User -> Order : OneToMany"));
        assertEquals("aceptada", memoria.get("Order -> User : ManyToOne"));
        assertEquals("rechazada", memoria.get("Product -> Category : ManyToMany"));
    }

    /**
     * Test handling of corrupted JSON file.
     * The current implementation throws JsonSyntaxException for malformed JSON.
     * This test verifies the actual behavior.
     * 
     * Requirements: 6.1
     */
    @Test
    void loadCorruptedFile_throwsException() throws IOException {
        // Arrange - create a corrupted JSON file
        File memoryFile = tempDir.resolve("corrupted-memory.json").toFile();
        try (FileWriter writer = new FileWriter(memoryFile)) {
            writer.write("{ invalid json content without closing brace");
        }
        
        // Act & Assert - current implementation throws exception for corrupted JSON
        assertThrows(com.google.gson.JsonSyntaxException.class, () -> {
            new MemoryService(memoryFile.getAbsolutePath());
        });
    }
    
    /**
     * Test handling of empty JSON file.
     * Verifies that an empty file results in empty memory.
     * 
     * Requirements: 6.1
     */
    @Test
    void loadEmptyFile_handlesGracefully() throws IOException {
        // Arrange - create an empty JSON file (valid empty object)
        File memoryFile = tempDir.resolve("empty-memory.json").toFile();
        try (FileWriter writer = new FileWriter(memoryFile)) {
            writer.write("{}");
        }
        
        // Act - create service with empty file
        MemoryService service = new MemoryService(memoryFile.getAbsolutePath());
        
        // Assert - should return empty memory
        Map<String, String> memoria = service.getMemoria();
        assertNotNull(memoria);
        assertTrue(memoria.isEmpty());
    }

    /**
     * Test concurrent access thread safety.
     * Verifies that multiple threads can safely read and write to memory.
     * 
     * Requirements: 1.1
     */
    @Test
    void concurrentAccess_threadSafe() throws InterruptedException {
        // Arrange
        String memoryFile = tempDir.resolve("concurrent-memory.json").toString();
        MemoryService service = new MemoryService(memoryFile);
        
        int threadCount = 10;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();
        
        // Act - run concurrent operations
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String relation = "Class" + threadId + " -> Target" + i + " : OneToMany";
                        service.recordarDecision(relation, i % 2 == 0);
                        
                        // Also read to test concurrent read/write
                        service.getMemoria();
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Assert
        assertTrue(completed, "All threads should complete within timeout");
        assertTrue(exceptions.isEmpty(), "No exceptions should occur: " + exceptions);
        assertEquals(threadCount, successCount.get(), "All threads should succeed");
        
        // Verify data integrity - reload and check
        MemoryService reloadedService = new MemoryService(memoryFile);
        Map<String, String> memoria = reloadedService.getMemoria();
        assertFalse(memoria.isEmpty(), "Memory should contain data");
    }

    /**
     * Test clear memory functionality.
     * Verifies that clearing memory removes all decisions and persists.
     * 
     * Requirements: 1.1
     */
    @Test
    void clearMemoria_removesAllDecisions() {
        // Arrange
        String memoryFile = tempDir.resolve("clear-memory.json").toString();
        MemoryService service = new MemoryService(memoryFile);
        service.recordarDecision("User -> Order : OneToMany", true);
        service.recordarDecision("Order -> Product : ManyToMany", false);
        
        // Act
        service.clearMemoria();
        
        // Assert - verify memory is cleared
        assertTrue(service.getMemoria().isEmpty());
        
        // Verify persistence - reload and check
        MemoryService reloadedService = new MemoryService(memoryFile);
        assertTrue(reloadedService.getMemoria().isEmpty());
    }

    /**
     * Test remove single decision.
     * Verifies that a single decision can be removed without affecting others.
     * 
     * Requirements: 1.1
     */
    @Test
    void removeDecision_removesSingleEntry() {
        // Arrange
        String memoryFile = tempDir.resolve("remove-memory.json").toString();
        MemoryService service = new MemoryService(memoryFile);
        service.recordarDecision("User -> Order : OneToMany", true);
        service.recordarDecision("Order -> Product : ManyToMany", false);
        
        // Act
        service.removeDecision("User -> Order : OneToMany");
        
        // Assert
        Map<String, String> memoria = service.getMemoria();
        assertEquals(1, memoria.size());
        assertNull(memoria.get("User -> Order : OneToMany"));
        assertEquals("rechazada", memoria.get("Order -> Product : ManyToMany"));
    }

    /**
     * Test handling of non-existent file.
     * Verifies that the service creates a new empty memory when file doesn't exist.
     * 
     * Requirements: 1.1
     */
    @Test
    void loadNonExistentFile_createsEmptyMemory() {
        // Arrange
        String memoryFile = tempDir.resolve("non-existent-memory.json").toString();
        
        // Act
        MemoryService service = new MemoryService(memoryFile);
        
        // Assert
        Map<String, String> memoria = service.getMemoria();
        assertNotNull(memoria);
        assertTrue(memoria.isEmpty());
    }

    /**
     * Test update existing decision.
     * Verifies that updating a decision overwrites the previous value.
     * 
     * Requirements: 1.1
     */
    @Test
    void recordarDecision_updatesExistingDecision() {
        // Arrange
        String memoryFile = tempDir.resolve("update-memory.json").toString();
        MemoryService service = new MemoryService(memoryFile);
        service.recordarDecision("User -> Order : OneToMany", true);
        
        // Act - update the decision
        service.recordarDecision("User -> Order : OneToMany", false);
        
        // Assert
        Map<String, String> memoria = service.getMemoria();
        assertEquals(1, memoria.size());
        assertEquals("rechazada", memoria.get("User -> Order : OneToMany"));
    }

    /**
     * Test handling of null and blank relations.
     * Verifies that null and blank relations are ignored.
     * 
     * Requirements: 1.1
     */
    @Test
    void recordarDecision_ignoresNullAndBlank() {
        // Arrange
        String memoryFile = tempDir.resolve("null-memory.json").toString();
        MemoryService service = new MemoryService(memoryFile);
        
        // Act
        service.recordarDecision(null, true);
        service.recordarDecision("", true);
        service.recordarDecision("   ", true);
        service.recordarDecision("Valid -> Relation : OneToMany", true);
        
        // Assert
        Map<String, String> memoria = service.getMemoria();
        assertEquals(1, memoria.size());
        assertEquals("aceptada", memoria.get("Valid -> Relation : OneToMany"));
    }
}
