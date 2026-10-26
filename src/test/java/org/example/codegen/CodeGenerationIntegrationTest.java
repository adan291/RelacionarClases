package org.example.codegen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.example.model.RelacionDetectada;
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
 * Integration tests for JPACodeGenerator.
 * Tests code generation with real Java files, annotation validation, and backup creation.
 * 
 * Requirements: 1.4
 */
@Tag("integration")
class CodeGenerationIntegrationTest {

    @TempDir
    Path tempDir;

    private JPACodeGenerator codeGenerator;
    private File sourceDir;

    @BeforeEach
    void setUp() throws IOException {
        codeGenerator = new JPACodeGenerator();
        sourceDir = tempDir.toFile();
        
        // Create a simple Java entity file for testing
        createTestEntityFile();
    }

    /**
     * Creates a simple Java entity file without JPA annotations for testing.
     */
    private void createTestEntityFile() throws IOException {
        Path modelDir = tempDir.resolve("com/example/model");
        Files.createDirectories(modelDir);
        
        // Create Employee.java - a simple entity without JPA annotations
        String employeeContent = """
            package com.example.model;
            
            import java.util.List;
            
            public class Employee {
                private Long id;
                private String name;
                private Department department;
                private List<Project> projects;
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public Department getDepartment() { return department; }
                public void setDepartment(Department department) { this.department = department; }
                public List<Project> getProjects() { return projects; }
                public void setProjects(List<Project> projects) { this.projects = projects; }
            }
            """;
        Files.writeString(modelDir.resolve("Employee.java"), employeeContent);
        
        // Create Department.java
        String departmentContent = """
            package com.example.model;
            
            import java.util.List;
            
            public class Department {
                private Long id;
                private String name;
                private List<Employee> employees;
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public List<Employee> getEmployees() { return employees; }
                public void setEmployees(List<Employee> employees) { this.employees = employees; }
            }
            """;
        Files.writeString(modelDir.resolve("Department.java"), departmentContent);
        
        // Create Project.java
        String projectContent = """
            package com.example.model;
            
            import java.util.Set;
            
            public class Project {
                private Long id;
                private String name;
                private Set<Employee> employees;
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public Set<Employee> getEmployees() { return employees; }
                public void setEmployees(Set<Employee> employees) { this.employees = employees; }
            }
            """;
        Files.writeString(modelDir.resolve("Project.java"), projectContent);
    }

    /**
     * Test code generation with real Java files.
     * Verifies that annotations are correctly added to entity files.
     * 
     * Requirements: 1.4
     */
    @Test
    void applyAnnotations_addsJPAAnnotations() {
        // Arrange
        List<RelacionDetectada> relations = new ArrayList<>();
        relations.add(TestDataFactory.createRelation("Employee", "Department", "ManyToOne", 0.9));
        
        // Act
        List<JPACodeGenerator.GenerationResult> results = codeGenerator.applyAnnotations(sourceDir, relations);
        
        // Assert
        assertFalse(results.isEmpty());
        
        // Find the result for Employee -> Department
        JPACodeGenerator.GenerationResult employeeResult = results.stream()
            .filter(r -> r.className.equals("Employee") && r.fieldName != null && r.fieldName.equals("department"))
            .findFirst()
            .orElse(null);
        
        assertNotNull(employeeResult, "Should have result for Employee.department");
        assertTrue(employeeResult.success, "Annotation should be applied successfully: " + employeeResult.message);
    }

    /**
     * Test backup file creation.
     * Verifies that a backup file is created before modifying the original.
     * 
     * Requirements: 1.4
     */
    @Test
    void applyAnnotations_createsBackupFile() throws IOException {
        // Arrange
        List<RelacionDetectada> relations = new ArrayList<>();
        relations.add(TestDataFactory.createRelation("Employee", "Department", "ManyToOne", 0.9));
        
        Path employeeFile = tempDir.resolve("com/example/model/Employee.java");
        String originalContent = Files.readString(employeeFile);
        
        // Act
        codeGenerator.applyAnnotations(sourceDir, relations);
        
        // Assert - backup file should exist
        Path backupFile = tempDir.resolve("com/example/model/Employee.java.bak");
        assertTrue(Files.exists(backupFile), "Backup file should be created");
        
        // Backup should contain original content
        String backupContent = Files.readString(backupFile);
        assertEquals(originalContent, backupContent, "Backup should contain original content");
    }

    /**
     * Test generated annotations are valid.
     * Verifies that the generated code contains proper JPA annotations.
     * 
     * Requirements: 1.4
     */
    @Test
    void applyAnnotations_generatesValidAnnotations() throws IOException {
        // Arrange
        List<RelacionDetectada> relations = new ArrayList<>();
        relations.add(TestDataFactory.createRelation("Employee", "Department", "ManyToOne", 0.9));
        
        // Act
        codeGenerator.applyAnnotations(sourceDir, relations);
        
        // Assert - read modified file and check for annotations
        Path employeeFile = tempDir.resolve("com/example/model/Employee.java");
        String modifiedContent = Files.readString(employeeFile);
        
        // Should contain ManyToOne annotation
        assertTrue(modifiedContent.contains("@ManyToOne"), "Should contain @ManyToOne annotation");
        
        // Should contain JoinColumn annotation
        assertTrue(modifiedContent.contains("@JoinColumn"), "Should contain @JoinColumn annotation");
        
        // Should contain import for jakarta.persistence
        assertTrue(modifiedContent.contains("jakarta.persistence"), "Should import jakarta.persistence");
    }

    /**
     * Test preview changes without modifying files.
     * Verifies that preview returns expected changes without modifying files.
     * 
     * Requirements: 1.4
     */
    @Test
    void previewChanges_doesNotModifyFiles() throws IOException {
        // Arrange
        List<RelacionDetectada> relations = new ArrayList<>();
        relations.add(TestDataFactory.createRelation("Employee", "Department", "ManyToOne", 0.9));
        
        Path employeeFile = tempDir.resolve("com/example/model/Employee.java");
        String originalContent = Files.readString(employeeFile);
        
        // Act
        List<JPACodeGenerator.GenerationResult> results = codeGenerator.previewChanges(sourceDir, relations);
        
        // Assert - file should not be modified
        String afterContent = Files.readString(employeeFile);
        assertEquals(originalContent, afterContent, "File should not be modified during preview");
        
        // Preview should return results
        assertFalse(results.isEmpty());
    }

    /**
     * Test handling of non-existent class file.
     * Verifies that the generator handles missing files gracefully.
     * 
     * Requirements: 1.4
     */
    @Test
    void applyAnnotations_handlesNonExistentClass() {
        // Arrange
        List<RelacionDetectada> relations = new ArrayList<>();
        relations.add(TestDataFactory.createRelation("NonExistent", "Department", "ManyToOne", 0.9));
        
        // Act
        List<JPACodeGenerator.GenerationResult> results = codeGenerator.applyAnnotations(sourceDir, relations);
        
        // Assert
        assertFalse(results.isEmpty());
        JPACodeGenerator.GenerationResult result = results.get(0);
        assertFalse(result.success, "Should fail for non-existent class");
        assertTrue(result.message.contains("no encontrado") || result.message.contains("not found"), 
            "Message should indicate file not found");
    }

    /**
     * Test handling of already annotated relations.
     * Verifies that existing annotations are not duplicated.
     * 
     * Requirements: 1.4
     */
    @Test
    void applyAnnotations_skipsExistingAnnotations() {
        // Arrange
        List<RelacionDetectada> relations = new ArrayList<>();
        RelacionDetectada existingRelation = TestDataFactory.createExistingAnnotationRelation(
            "Employee", "Department", "ManyToOne", 0.9);
        relations.add(existingRelation);
        
        // Act
        List<JPACodeGenerator.GenerationResult> results = codeGenerator.applyAnnotations(sourceDir, relations);
        
        // Assert
        assertFalse(results.isEmpty());
        JPACodeGenerator.GenerationResult result = results.get(0);
        assertTrue(result.success, "Should succeed for existing annotation");
        assertTrue(result.message.contains("anotada") || result.message.contains("annotated"),
            "Message should indicate already annotated");
    }

    /**
     * Test OneToMany annotation generation.
     * Verifies that OneToMany annotations are correctly generated.
     * 
     * Requirements: 1.4
     */
    @Test
    void applyAnnotations_generatesOneToMany() throws IOException {
        // Arrange
        List<RelacionDetectada> relations = new ArrayList<>();
        relations.add(TestDataFactory.createRelation("Department", "Employee", "OneToMany", 0.9));
        
        // Act
        codeGenerator.applyAnnotations(sourceDir, relations);
        
        // Assert
        Path departmentFile = tempDir.resolve("com/example/model/Department.java");
        String content = Files.readString(departmentFile);
        
        assertTrue(content.contains("@OneToMany"), "Should contain @OneToMany annotation");
    }

    /**
     * Test ManyToMany annotation generation.
     * Verifies that ManyToMany annotations are correctly generated.
     * 
     * Requirements: 1.4
     */
    @Test
    void applyAnnotations_generatesManyToMany() throws IOException {
        // Arrange
        List<RelacionDetectada> relations = new ArrayList<>();
        relations.add(TestDataFactory.createRelation("Employee", "Project", "ManyToMany", 0.9));
        
        // Act
        codeGenerator.applyAnnotations(sourceDir, relations);
        
        // Assert
        Path employeeFile = tempDir.resolve("com/example/model/Employee.java");
        String content = Files.readString(employeeFile);
        
        assertTrue(content.contains("@ManyToMany"), "Should contain @ManyToMany annotation");
    }

    /**
     * Test bidirectional relation annotation generation.
     * Verifies that mappedBy is correctly set for inverse side.
     * 
     * Requirements: 1.4
     */
    @Test
    void applyAnnotations_handlesBidirectionalRelation() throws IOException {
        // Arrange
        List<RelacionDetectada> relations = new ArrayList<>();
        RelacionDetectada relation = TestDataFactory.createBidirectionalRelation(
            "Department", "Employee", "OneToMany", 0.9, false, "department");
        relations.add(relation);
        
        // Act
        codeGenerator.applyAnnotations(sourceDir, relations);
        
        // Assert
        Path departmentFile = tempDir.resolve("com/example/model/Department.java");
        String content = Files.readString(departmentFile);
        
        assertTrue(content.contains("mappedBy"), "Should contain mappedBy for inverse side");
    }

    /**
     * Test preview returns correct annotation code.
     * Verifies that preview shows the expected annotation code.
     * 
     * Requirements: 1.4
     */
    @Test
    void previewChanges_returnsAnnotationCode() {
        // Arrange
        List<RelacionDetectada> relations = new ArrayList<>();
        relations.add(TestDataFactory.createRelation("Employee", "Department", "ManyToOne", 0.9));
        
        // Act
        List<JPACodeGenerator.GenerationResult> results = codeGenerator.previewChanges(sourceDir, relations);
        
        // Assert
        JPACodeGenerator.GenerationResult result = results.stream()
            .filter(r -> r.className.equals("Employee") && r.preview != null)
            .findFirst()
            .orElse(null);
        
        assertNotNull(result, "Should have preview result");
        assertNotNull(result.preview, "Preview should not be null");
        assertTrue(result.preview.contains("@ManyToOne"), "Preview should contain @ManyToOne");
    }
}
