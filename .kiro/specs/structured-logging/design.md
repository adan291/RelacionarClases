# Design: Structured Logging

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Classes                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ Service  │ │   UI     │ │   ML     │ │ Analyzer │       │
│  │ Classes  │ │ Classes  │ │ Classes  │ │ Classes  │       │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘       │
│       │            │            │            │              │
│       └────────────┴────────────┴────────────┘              │
│                         │                                    │
│                         ▼                                    │
│              ┌─────────────────────┐                        │
│              │   LoggingConfig     │                        │
│              │   (Singleton)       │                        │
│              └──────────┬──────────┘                        │
│                         │                                    │
│         ┌───────────────┼───────────────┐                   │
│         ▼               ▼               ▼                   │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │ConsoleHandler│ │ FileHandler │ │CustomFormatter│          │
│  └─────────────┘ └─────────────┘ └─────────────┘           │
└─────────────────────────────────────────────────────────────┘
```

## Components

### 1. LoggingConfig (Singleton)
Centralized logging configuration manager.

```java
public class LoggingConfig {
    private static final String CONFIG_FILE = "logging.properties";
    private static LoggingConfig instance;
    
    public static void initialize();
    public static void setLevel(String loggerName, Level level);
    public static void shutdown();
}
```

### 2. AppFormatter (Custom Formatter)
Consistent log format across all handlers.

```java
public class AppFormatter extends Formatter {
    // Format: [TIMESTAMP] [LEVEL] [CLASS.METHOD] [THREAD] - Message
    @Override
    public String format(LogRecord record);
}
```

### 3. logging.properties
Configuration file for logging settings.

```properties
# Root logger
.level=INFO

# Handlers
handlers=java.util.logging.ConsoleHandler,java.util.logging.FileHandler

# Console Handler
java.util.logging.ConsoleHandler.level=INFO
java.util.logging.ConsoleHandler.formatter=org.example.logging.AppFormatter

# File Handler
java.util.logging.FileHandler.pattern=logs/app_%g.log
java.util.logging.FileHandler.limit=5242880
java.util.logging.FileHandler.count=10
java.util.logging.FileHandler.formatter=org.example.logging.AppFormatter

# Package-specific levels
org.example.service.level=INFO
org.example.ml.level=INFO
org.example.ui.level=INFO
```

## Migration Strategy

### Phase 1: Infrastructure
1. Create LoggingConfig class
2. Create AppFormatter class
3. Create logging.properties file
4. Initialize logging in Main class

### Phase 2: Service Layer Migration
Replace System.out/err in:
- MemoryService
- AnalysisHistoryService
- SnapshotManager
- CodeGenerationService

### Phase 3: ML Layer Migration
Replace System.out/err in:
- MLRelationPredictor
- LogisticRegressionModel
- TrainingDataGenerator
- ModelEvaluator

### Phase 4: Analyzer Layer Migration
Replace System.out/err in:
- IntelligentRelationAnalyzer
- FeatureExtractor
- ScoreCombiner

### Phase 5: UI Layer Migration
Replace System.out/err in:
- RelationAnalyzerAppView
- HistoryPanelView
- GraphVisualizationView

## Log Level Guidelines

| Level   | Usage                                           |
|---------|------------------------------------------------|
| SEVERE  | Errors that prevent operation                   |
| WARNING | Recoverable errors, deprecated usage            |
| INFO    | Important business events (analysis complete)   |
| CONFIG  | Configuration information                       |
| FINE    | Detailed flow information                       |
| FINER   | Method entry/exit                              |
| FINEST  | Very detailed debugging                         |

## Correctness Properties

### Property 1: Logger Instance Per Class
Every class that logs should have exactly one static final Logger instance.
- **Validates**: Requirement 1.1

### Property 2: No System.out/err After Migration
After migration, no System.out.println or System.err.println should exist in production code.
- **Validates**: Requirements 4.1, 4.2

### Property 3: Log File Rotation
When log file exceeds 5MB, a new file should be created and old files rotated.
- **Validates**: Requirements 2.2, 2.3

### Property 4: Configuration Loading
LoggingConfig should successfully load and apply settings from logging.properties.
- **Validates**: Requirements 5.1, 5.2
