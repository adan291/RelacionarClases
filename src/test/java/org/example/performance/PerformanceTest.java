package org.example.performance;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.example.analysis.RelationDetector;
import org.example.model.RelacionDetectada;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Performance tests for the relation analyzer.
 * Tests analysis time and memory usage for large projects.
 * 
 * Requirements: 7.1-7.3
 */
@Tag("performance")
class PerformanceTest {

    @TempDir
    Path tempDir;
    
    private RelationDetector relationDetector;
    private MemoryMXBean memoryBean;
    
    // Performance thresholds
    private static final long MAX_ANALYSIS_TIME_MS = 10_000; // 10 seconds
    private static final long MAX_HEAP_USAGE_MB = 512; // 512 MB reasonable limit
    
    @BeforeEach
    void setUp() {
        relationDetector = new RelationDetector();
        memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    @AfterEach
    void tearDown() {
        // Force garbage collection to clean up after tests
        System.gc();
    }

    /**
     * Test analysis time for 100+ classes.
     * Generates a project with 100+ Java classes and measures analysis time.
     * 
     * Requirements: 7.1
     */
    @Test
    void analyzeProject_with100Classes_completesWithin10Seconds() throws IOException {
        // Arrange - Generate 100+ class project
        int classCount = 120;
        Path projectDir = generateLargeProject(classCount);
        
        // Act - Measure analysis time
        long startTime = System.currentTimeMillis();
        
        relationDetector.loadClasses(projectDir.toFile());
        List<RelacionDetectada> relations = relationDetector.detectRelationsAsObjects();
        
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;
        
        // Assert
        assertTrue(elapsedTime < MAX_ANALYSIS_TIME_MS,
            "Analysis of " + classCount + " classes should complete within " + 
            MAX_ANALYSIS_TIME_MS + "ms, but took " + elapsedTime + "ms");
        
        // Verify analysis actually processed the classes
        assertFalse(relations.isEmpty(), 
            "Should detect relations in generated project");
        assertTrue(relationDetector.getClassNames().size() >= classCount,
            "Should load at least " + classCount + " classes");
        
        System.out.println("Performance Test Results:");
        System.out.println("  Classes analyzed: " + relationDetector.getClassNames().size());
        System.out.println("  Relations detected: " + relations.size());
        System.out.println("  Time elapsed: " + elapsedTime + "ms");
    }

    /**
     * Test memory usage during large project analysis.
     * Monitors heap usage and asserts reasonable limits.
     * 
     * Requirements: 7.3
     */
    @Test
    void analyzeProject_withLargeProject_maintainsReasonableMemoryUsage() throws IOException {
        // Arrange - Generate large project
        int classCount = 150;
        Path projectDir = generateLargeProject(classCount);
        
        // Force GC before measuring
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Record initial memory
        MemoryUsage heapBefore = memoryBean.getHeapMemoryUsage();
        long usedBefore = heapBefore.getUsed();
        
        // Act - Analyze project
        relationDetector.loadClasses(projectDir.toFile());
        List<RelacionDetectada> relations = relationDetector.detectRelationsAsObjects();
        
        // Record memory after analysis
        MemoryUsage heapAfter = memoryBean.getHeapMemoryUsage();
        long usedAfter = heapAfter.getUsed();
        long memoryIncrease = usedAfter - usedBefore;
        long memoryIncreaseMB = memoryIncrease / (1024 * 1024);
        
        // Assert
        assertTrue(memoryIncreaseMB < MAX_HEAP_USAGE_MB,
            "Memory increase should be less than " + MAX_HEAP_USAGE_MB + 
            "MB, but was " + memoryIncreaseMB + "MB");
        
        // Verify analysis completed successfully
        assertFalse(relations.isEmpty(), 
            "Should detect relations in generated project");
        
        System.out.println("Memory Test Results:");
        System.out.println("  Classes analyzed: " + relationDetector.getClassNames().size());
        System.out.println("  Memory before: " + (usedBefore / (1024 * 1024)) + "MB");
        System.out.println("  Memory after: " + (usedAfter / (1024 * 1024)) + "MB");
        System.out.println("  Memory increase: " + memoryIncreaseMB + "MB");
    }

    /**
     * Test scalability - analysis time should scale reasonably with class count.
     * 
     * Requirements: 7.1
     */
    @Test
    void analyzeProject_scalability_timeScalesReasonably() throws IOException {
        // Arrange - Generate projects of different sizes
        int[] classCounts = {25, 50, 100};
        long[] times = new long[classCounts.length];
        
        for (int i = 0; i < classCounts.length; i++) {
            Path projectDir = generateLargeProject(classCounts[i]);
            RelationDetector detector = new RelationDetector();
            
            // Warm up JIT
            if (i == 0) {
                detector.loadClasses(projectDir.toFile());
                detector = new RelationDetector();
            }
            
            long startTime = System.currentTimeMillis();
            detector.loadClasses(projectDir.toFile());
            detector.detectRelationsAsObjects();
            times[i] = System.currentTimeMillis() - startTime;
        }
        
        // Assert - Time should not grow exponentially
        // Doubling classes should not more than triple the time
        double ratio1 = (double) times[1] / Math.max(times[0], 1);
        double ratio2 = (double) times[2] / Math.max(times[1], 1);
        
        System.out.println("Scalability Test Results:");
        for (int i = 0; i < classCounts.length; i++) {
            System.out.println("  " + classCounts[i] + " classes: " + times[i] + "ms");
        }
        System.out.println("  Ratio (50/25): " + String.format("%.2f", ratio1));
        System.out.println("  Ratio (100/50): " + String.format("%.2f", ratio2));
        
        // Allow for some variance but ensure it's not exponential
        assertTrue(ratio2 < 5.0,
            "Analysis time should scale sub-exponentially. " +
            "Ratio of 100/50 classes was " + String.format("%.2f", ratio2));
    }

    /**
     * Generates a large Java project with the specified number of classes.
     * Creates entity classes with relationships to test analysis performance.
     */
    private Path generateLargeProject(int classCount) throws IOException {
        Path projectDir = tempDir.resolve("project-" + classCount);
        Path packageDir = projectDir.resolve("com/example/model");
        Files.createDirectories(packageDir);
        
        // Generate entity classes with relationships
        for (int i = 0; i < classCount; i++) {
            String className = "Entity" + i;
            String javaContent = generateEntityClass(className, i, classCount);
            
            Path classFile = packageDir.resolve(className + ".java");
            Files.writeString(classFile, javaContent);
        }
        
        return projectDir;
    }

    /**
     * Generates a Java entity class with relationships to other entities.
     */
    private String generateEntityClass(String className, int index, int totalClasses) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("package com.example.model;\n\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.Set;\n\n");
        sb.append("public class ").append(className).append(" {\n\n");
        
        // Add ID field
        sb.append("    private Long id;\n");
        sb.append("    private String name;\n\n");
        
        // Add ManyToOne relationship to previous entity
        if (index > 0) {
            String targetClass = "Entity" + (index - 1);
            sb.append("    private ").append(targetClass).append(" parent;\n\n");
        }
        
        // Add OneToMany relationship to next entity
        if (index < totalClasses - 1) {
            String targetClass = "Entity" + (index + 1);
            sb.append("    private List<").append(targetClass).append("> children;\n\n");
        }
        
        // Add some additional relationships for variety
        if (index % 5 == 0 && index + 10 < totalClasses) {
            String targetClass = "Entity" + (index + 10);
            sb.append("    private Set<").append(targetClass).append("> related;\n\n");
        }
        
        // Add getters/setters (minimal)
        sb.append("    public Long getId() { return id; }\n");
        sb.append("    public void setId(Long id) { this.id = id; }\n");
        sb.append("    public String getName() { return name; }\n");
        sb.append("    public void setName(String name) { this.name = name; }\n");
        
        sb.append("}\n");
        
        return sb.toString();
    }
}
