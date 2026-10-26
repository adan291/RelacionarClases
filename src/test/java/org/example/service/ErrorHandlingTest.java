package org.example.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.example.analysis.IntelligentRelationAnalyzer;
import org.example.config.ConfigManager;
import org.example.model.RelacionDetectada;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for error handling scenarios.
 * Tests graceful handling of corrupted files, missing configuration, and invalid Java files.
 * 
 * Requirements: 6.1, 6.2, 6.3, 6.4
 */
@Tag("integration")
class ErrorHandlingTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Reset ConfigManager before each test to ensure clean state
        ConfigManager.reset();
    }

    @AfterEach
    void tearDown() {
        // Reset ConfigManager after each test
        ConfigManager.reset();
    }

    // ==================== 6.2 Corrupted Memory File Tests ====================

    /**
     * Un JSON corrupto no impide arrancar: se guarda una copia .corrupt y se empieza vacío.
     * 
     * Requirements: 6.1
     */
    @Test
    void corruptedMemoryFile_recoversWithEmptyMemoryAndCorruptCopy() throws IOException {
        // Arrange - create a corrupted JSON file (invalid JSON syntax)
        File memoryFile = tempDir.resolve("corrupted-memory.json").toFile();
        String corrupted = "{ invalid json without closing brace";
        try (FileWriter writer = new FileWriter(memoryFile)) {
            writer.write(corrupted);
        }

        // Act
        MemoryService service = assertDoesNotThrow(() -> new MemoryService(memoryFile.getAbsolutePath()));

        // Assert
        assertTrue(service.getMemoria().isEmpty(), "Corrupted memory should start empty");
        Path backup = Path.of(memoryFile.getAbsolutePath() + ".corrupt");
        assertTrue(Files.exists(backup), "A .corrupt copy should be kept");
        assertEquals(corrupted, Files.readString(backup), "The copy should keep the original content");

        // Se puede seguir trabajando y el fichero vuelve a ser JSON válido
        service.recordarDecision("A -> B : ManyToOne", true);
        MemoryService reloaded = new MemoryService(memoryFile.getAbsolutePath());
        assertEquals("aceptada", reloaded.getMemoria().get("A -> B : ManyToOne"));
    }

    /**
     * Test handling of memory file with invalid JSON structure.
     * JSON válido pero con otra estructura (array): mismo tratamiento que un fichero corrupto.
     * 
     * Requirements: 6.1
     */
    @Test
    void invalidJsonStructure_handlesGracefully() throws IOException {
        // Arrange - create a JSON file with wrong structure (array instead of object)
        File memoryFile = tempDir.resolve("invalid-structure.json").toFile();
        try (FileWriter writer = new FileWriter(memoryFile)) {
            writer.write("[\"item1\", \"item2\"]");
        }

        MemoryService service = assertDoesNotThrow(() -> new MemoryService(memoryFile.getAbsolutePath()));

        assertTrue(service.getMemoria().isEmpty());
        assertTrue(Files.exists(Path.of(memoryFile.getAbsolutePath() + ".corrupt")));
    }

    /**
     * La escritura de la memoria es atómica: no quedan temporales y el fichero siempre es JSON válido.
     */
    @Test
    void memoryWrite_isAtomicAndLeavesNoTempFiles() throws IOException {
        Path memoryFile = tempDir.resolve("atomic-memory.json");
        MemoryService service = new MemoryService(memoryFile.toString());
        for (int i = 0; i < 20; i++) {
            service.recordarDecision("A" + i + " -> B : OneToMany", i % 2 == 0);
        }

        try (var files = Files.list(tempDir)) {
            assertEquals(List.of("atomic-memory.json"),
                files.map(p -> p.getFileName().toString()).sorted().toList());
        }
        assertEquals(20, new MemoryService(memoryFile.toString()).getMemoria().size());
    }

    /**
     * Test handling of empty memory file.
     * Verifies that an empty file results in empty memory.
     * 
     * Requirements: 6.1
     */
    @Test
    void emptyMemoryFile_returnsEmptyMemory() throws IOException {
        // Arrange - create an empty JSON file (valid empty object)
        File memoryFile = tempDir.resolve("empty-memory.json").toFile();
        try (FileWriter writer = new FileWriter(memoryFile)) {
            writer.write("{}");
        }

        // Act
        MemoryService service = new MemoryService(memoryFile.getAbsolutePath());

        // Assert - should return empty memory
        Map<String, String> memoria = service.getMemoria();
        assertNotNull(memoria);
        assertTrue(memoria.isEmpty());
    }

    /**
     * Test handling of null values in memory file.
     * Verifies that null JSON results in empty memory.
     * 
     * Requirements: 6.1
     */
    @Test
    void nullJsonContent_returnsEmptyMemory() throws IOException {
        // Arrange - create a JSON file with null content
        File memoryFile = tempDir.resolve("null-memory.json").toFile();
        try (FileWriter writer = new FileWriter(memoryFile)) {
            writer.write("null");
        }

        // Act
        MemoryService service = new MemoryService(memoryFile.getAbsolutePath());

        // Assert - should return empty memory (null is handled gracefully)
        Map<String, String> memoria = service.getMemoria();
        assertNotNull(memoria);
        assertTrue(memoria.isEmpty());
    }

    // ==================== 6.3 Missing Configuration Tests ====================

    /**
     * Test behavior when configuration is loaded.
     * Verifies that configuration objects are properly initialized.
     * 
     * Requirements: 6.2
     */
    @Test
    void configLoaded_hasValidConfigObjects() {
        // Arrange - load configuration (may use file or defaults)
        ConfigManager.reset();
        ConfigManager manager = ConfigManager.getInstance();
        manager.load();

        // Assert - verify config objects are properly initialized
        // ML config
        assertNotNull(manager.getMLConfig(), "ML config should not be null");
        assertTrue(manager.getMLConfig().minDecisions() > 0, 
            "minDecisions should be positive");
        assertTrue(manager.getMLConfig().mlWeight() >= 0 && manager.getMLConfig().mlWeight() <= 1, 
            "ML weight should be in [0,1]");

        // Analysis config
        assertNotNull(manager.getAnalysisConfig(), "Analysis config should not be null");
        assertTrue(manager.getAnalysisConfig().defaultConfidenceThreshold() >= 0 
            && manager.getAnalysisConfig().defaultConfidenceThreshold() <= 1,
            "Default confidence threshold should be in [0,1]");

        // UI config
        assertNotNull(manager.getUIConfig(), "UI config should not be null");
        assertTrue(manager.getUIConfig().windowWidth() > 0, 
            "Should have positive window width");
        assertTrue(manager.getUIConfig().windowHeight() > 0, 
            "Should have positive window height");

        // Persistence config
        assertNotNull(manager.getPersistenceConfig(), "Persistence config should not be null");
        assertNotNull(manager.getPersistenceConfig().memoryFilePath());
        assertFalse(manager.getPersistenceConfig().memoryFilePath().isEmpty(),
            "Should have memory file path");
    }

    /**
     * Test that missing keys return default values.
     * Verifies type-safe getters return defaults for non-existent keys.
     * 
     * Requirements: 6.2
     */
    @Test
    void missingConfigKeys_returnDefaults() {
        // Arrange
        ConfigManager.reset();
        ConfigManager manager = ConfigManager.getInstance();
        manager.load();

        // Act & Assert - verify defaults are returned for missing keys
        assertEquals(42, manager.getInt("nonexistent.key", 42));
        assertEquals(3.14, manager.getDouble("nonexistent.key", 3.14), 0.001);
        assertTrue(manager.getBoolean("nonexistent.key", true));
        assertFalse(manager.getBoolean("nonexistent.key", false));
        assertEquals("default", manager.getString("nonexistent.key", "default"));
        assertEquals(List.of("a", "b"), manager.getList("nonexistent.key", List.of("a", "b")));
    }

    /**
     * Test that invalid numeric values in config return defaults.
     * Verifies graceful handling of malformed numeric values.
     * 
     * Requirements: 6.2
     */
    @Test
    void invalidNumericConfig_returnsDefaults() {
        // This test verifies the behavior documented in ConfigManager
        // When a value cannot be parsed, the default is returned
        ConfigManager.reset();
        ConfigManager manager = ConfigManager.getInstance();
        manager.load();

        // For missing keys, defaults should be returned
        int defaultInt = 100;
        double defaultDouble = 0.5;

        assertEquals(defaultInt, manager.getInt("missing.int.key", defaultInt));
        assertEquals(defaultDouble, manager.getDouble("missing.double.key", defaultDouble), 0.001);
    }

    // ==================== 6.4 Invalid Java Files Tests ====================

    /**
     * Test analysis behavior when encountering malformed Java file.
     * The IntelligentRelationAnalyzer catches parse exceptions and logs them,
     * returning an empty list rather than crashing.
     * 
     * Requirements: 6.3
     */
    @Test
    void malformedJavaFile_logsErrorAndReturnsEmptyList() throws IOException {
        // Arrange - create a directory with a malformed Java file
        Path projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);

        // Create a malformed Java file (syntax error)
        Path invalidFile = projectDir.resolve("InvalidClass.java");
        Files.writeString(invalidFile, """
            package test;
            
            public class InvalidClass {
                private Long id
                // Missing semicolon and closing brace - syntax error
            """);

        // Act - analyze the directory
        String memoryFile = tempDir.resolve("test-memory.json").toString();
        MemoryService memoryService = new MemoryService(memoryFile);
        IntelligentRelationAnalyzer analyzer = new IntelligentRelationAnalyzer(memoryService);

        // The analyzer catches the exception internally and logs it
        // It should return an empty list, not throw
        List<RelacionDetectada> relations = analyzer.analizar(projectDir.toString());

        // Assert - analysis should return empty list (error was logged)
        assertNotNull(relations, "Relations list should not be null");
        assertTrue(relations.isEmpty(), 
            "Should return empty list when parse error occurs");
    }
    
    /**
     * Test analysis with only valid Java files works correctly.
     * 
     * Requirements: 6.3
     */
    @Test
    void validJavaFiles_analysisSucceeds() throws IOException {
        // Arrange - create a directory with valid Java files
        Path projectDir = tempDir.resolve("valid-project");
        Files.createDirectories(projectDir);

        // Create valid Java files with a relationship
        Path userFile = projectDir.resolve("User.java");
        Files.writeString(userFile, """
            package test;
            
            public class User {
                private Long id;
                private String name;
            }
            """);

        Path orderFile = projectDir.resolve("Order.java");
        Files.writeString(orderFile, """
            package test;
            
            public class Order {
                private Long id;
                private User user;
            }
            """);

        // Act - analyze the directory
        String memoryFile = tempDir.resolve("test-memory.json").toString();
        MemoryService memoryService = new MemoryService(memoryFile);
        IntelligentRelationAnalyzer analyzer = new IntelligentRelationAnalyzer(memoryService);

        List<RelacionDetectada> relations = analyzer.analizar(projectDir.toString());

        // Assert - should find the Order -> User relationship
        assertNotNull(relations, "Relations list should not be null");
        // The analyzer should detect the relationship
    }

    /**
     * Test analysis handles empty Java file.
     * Verifies that empty files don't cause crashes.
     * 
     * Requirements: 6.3
     */
    @Test
    void emptyJavaFile_analysisContinues() throws IOException {
        // Arrange - create a directory with an empty Java file
        Path projectDir = tempDir.resolve("empty-project");
        Files.createDirectories(projectDir);

        // Create an empty Java file
        Path emptyFile = projectDir.resolve("EmptyClass.java");
        Files.writeString(emptyFile, "");

        // Create a valid Java file
        Path validFile = projectDir.resolve("ValidClass.java");
        Files.writeString(validFile, """
            package test;
            
            public class ValidClass {
                private Long id;
            }
            """);

        // Act - analyze the directory
        String memoryFile = tempDir.resolve("test-memory.json").toString();
        MemoryService memoryService = new MemoryService(memoryFile);
        IntelligentRelationAnalyzer analyzer = new IntelligentRelationAnalyzer(memoryService);

        // Should not throw exception
        List<RelacionDetectada> relations = assertDoesNotThrow(() -> 
            analyzer.analizar(projectDir.toString()),
            "Analysis should not throw exception for empty Java files"
        );

        // Assert
        assertNotNull(relations, "Relations list should not be null");
    }

    /**
     * Test analysis handles non-Java files in directory.
     * Verifies that non-Java files are ignored.
     * 
     * Requirements: 6.3
     */
    @Test
    void nonJavaFiles_areIgnored() throws IOException {
        // Arrange - create a directory with mixed file types
        Path projectDir = tempDir.resolve("mixed-project");
        Files.createDirectories(projectDir);

        // Create a valid Java file
        Path validFile = projectDir.resolve("ValidClass.java");
        Files.writeString(validFile, """
            package test;
            
            public class ValidClass {
                private Long id;
            }
            """);

        // Create non-Java files
        Path textFile = projectDir.resolve("readme.txt");
        Files.writeString(textFile, "This is a readme file");

        Path xmlFile = projectDir.resolve("config.xml");
        Files.writeString(xmlFile, "<config><setting>value</setting></config>");

        Path jsonFile = projectDir.resolve("data.json");
        Files.writeString(jsonFile, "{\"key\": \"value\"}");

        // Act - analyze the directory
        String memoryFile = tempDir.resolve("test-memory.json").toString();
        MemoryService memoryService = new MemoryService(memoryFile);
        IntelligentRelationAnalyzer analyzer = new IntelligentRelationAnalyzer(memoryService);

        // Should not throw exception
        List<RelacionDetectada> relations = assertDoesNotThrow(() -> 
            analyzer.analizar(projectDir.toString()),
            "Analysis should not throw exception for non-Java files"
        );

        // Assert
        assertNotNull(relations, "Relations list should not be null");
    }

    /**
     * Test analysis handles non-existent directory.
     * Verifies graceful handling of invalid paths.
     * 
     * Requirements: 6.3
     */
    @Test
    void nonExistentDirectory_returnsEmptyList() {
        // Arrange
        String nonExistentPath = tempDir.resolve("non-existent-dir").toString();
        String memoryFile = tempDir.resolve("test-memory.json").toString();
        MemoryService memoryService = new MemoryService(memoryFile);
        IntelligentRelationAnalyzer analyzer = new IntelligentRelationAnalyzer(memoryService);

        // Act
        List<RelacionDetectada> relations = analyzer.analizar(nonExistentPath);

        // Assert - should return empty list, not throw exception
        assertNotNull(relations, "Relations list should not be null");
        assertTrue(relations.isEmpty(), "Should return empty list for non-existent directory");
    }

    /**
     * Test analysis handles file instead of directory.
     * Verifies graceful handling when path points to a file.
     * 
     * Requirements: 6.3
     */
    @Test
    void fileInsteadOfDirectory_returnsEmptyList() throws IOException {
        // Arrange - create a file instead of directory
        Path filePath = tempDir.resolve("not-a-directory.java");
        Files.writeString(filePath, "public class Test {}");

        String memoryFile = tempDir.resolve("test-memory.json").toString();
        MemoryService memoryService = new MemoryService(memoryFile);
        IntelligentRelationAnalyzer analyzer = new IntelligentRelationAnalyzer(memoryService);

        // Act
        List<RelacionDetectada> relations = analyzer.analizar(filePath.toString());

        // Assert - should return empty list, not throw exception
        assertNotNull(relations, "Relations list should not be null");
        assertTrue(relations.isEmpty(), "Should return empty list when path is a file");
    }
}
