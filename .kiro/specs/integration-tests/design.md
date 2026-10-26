# Design: Integration Tests (E2E)

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Test Infrastructure                       │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Test Resources                           │   │
│  │  src/test/resources/                                 │   │
│  │  ├── sample-project/     (Java files for analysis)   │   │
│  │  ├── test-memory.json    (Pre-populated memory)      │   │
│  │  └── test-config.properties                          │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Test Categories                          │   │
│  │                                                       │   │
│  │  @Tag("integration")  - Service integration tests    │   │
│  │  @Tag("e2e")          - End-to-end workflow tests    │   │
│  │  @Tag("ui")           - TestFX UI tests              │   │
│  │  @Tag("performance")  - Performance benchmarks       │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Test Utilities                           │   │
│  │                                                       │   │
│  │  TestDataFactory     - Create test entities          │   │
│  │  TempDirectoryHelper - Manage temp directories       │   │
│  │  TestMemoryService   - In-memory test implementation │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Test Structure

### 1. Sample Project for Testing

```
src/test/resources/sample-project/
├── com/example/model/
│   ├── User.java           (Entity with @Id)
│   ├── Order.java          (Entity with List<OrderItem>)
│   ├── OrderItem.java      (Entity with ManyToOne)
│   ├── Product.java        (Entity with ManyToMany)
│   └── Category.java       (Entity with bidirectional)
└── com/example/service/
    └── UserService.java    (Non-entity, should be ignored)
```

### 2. Test Classes Structure

```java
// Service Integration Tests
@Tag("integration")
class MemoryServiceIntegrationTest {
    @TempDir Path tempDir;
    
    @Test void saveAndLoadMemory_roundTrip();
    @Test void loadCorruptedFile_handlesGracefully();
    @Test void concurrentAccess_threadSafe();
}

@Tag("integration")
class AnalysisHistoryIntegrationTest {
    @Test void snapshotWorkflow_saveRestoreCompare();
    @Test void bulkDelete_removesAllSpecified();
}

// Analysis Pipeline Tests
@Tag("e2e")
class AnalysisPipelineTest {
    @Test void analyzeProject_detectsAllRelations();
    @Test void analyzeProject_detectsBidirectional();
    @Test void analyzeProject_detectsExistingAnnotations();
}

// ML Pipeline Tests
@Tag("e2e")
class MLPipelineTest {
    @Test void trainAndPredict_fullCycle();
    @Test void insufficientData_fallsBackToHeuristics();
    @Test void modelPersistence_survivesRestart();
}

// UI Tests (TestFX)
@Tag("ui")
class RelationAnalyzerUITest extends ApplicationTest {
    @Test void selectFolder_triggersAnalysis();
    @Test void acceptButton_recordsDecision();
    @Test void bulkSelect_enablesActions();
    @Test void filterByType_updatesTable();
}
```

### 3. TestDataFactory

```java
public class TestDataFactory {
    public static RelacionDetectada createRelation(
        String source, String target, String type, double confidence);
    
    public static List<RelacionDetectada> createSampleRelations(int count);
    
    public static AnalysisSnapshot createSnapshot(
        String projectName, List<RelacionDetectada> relations);
    
    public static TrainingDataset createTrainingData(int examples);
}
```

### 4. Test Configuration

```java
@TestConfiguration
public class IntegrationTestConfig {
    @Bean
    public MemoryService testMemoryService(@TempDir Path tempDir) {
        return new MemoryService(tempDir.resolve("test-memory.json").toString());
    }
    
    @Bean
    public ConfigManager testConfigManager() {
        ConfigManager config = ConfigManager.getInstance();
        config.load("src/test/resources/test-config.properties");
        return config;
    }
}
```

### 5. Sample Java Files for Testing

```java
// src/test/resources/sample-project/com/example/model/User.java
package com.example.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
public class User {
    @Id
    private Long id;
    private String name;
    
    @OneToMany(mappedBy = "user")
    private List<Order> orders;
}

// src/test/resources/sample-project/com/example/model/Order.java
package com.example.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
public class Order {
    @Id
    private Long id;
    
    @ManyToOne
    private User user;
    
    @OneToMany
    private List<OrderItem> items;
}
```

## Maven Configuration

```xml
<!-- pom.xml additions -->
<dependency>
    <groupId>org.testfx</groupId>
    <artifactId>testfx-junit5</artifactId>
    <version>4.0.18</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testfx</groupId>
    <artifactId>openjfx-monocle</artifactId>
    <version>jdk-12.0.1+2</version>
    <scope>test</scope>
</dependency>

<!-- Surefire configuration for test tags -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <groups>integration,e2e</groups>
        <excludedGroups>ui,performance</excludedGroups>
    </configuration>
</plugin>
```

## Correctness Properties (Integration Level)

### Property 1: Analysis Completeness
Analysis of sample project should detect all expected relations.
- **Validates**: Requirement 2.1

### Property 2: Memory Persistence
Saved decisions should be retrievable after service restart.
- **Validates**: Requirement 1.1

### Property 3: ML Training Effectiveness
Model trained on sample data should predict with >60% accuracy.
- **Validates**: Requirement 3.3

### Property 4: Snapshot Integrity
Restored snapshot should match original state exactly.
- **Validates**: Requirement 1.2
