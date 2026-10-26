# Implementation Plan

- [x] 1. Create logging infrastructure




  - [x] 1.1 Create AppFormatter class

    - Extend java.util.logging.Formatter
    - Format: [YYYY-MM-DD HH:mm:ss.SSS] [LEVEL] [Class.method] - Message
    - Include exception stack traces when present
    - _Requirements: 3.1, 3.2, 3.3, 3.5_

  - [x] 1.2 Create LoggingConfig singleton class


    - Initialize root logger and handlers
    - Create logs/ directory if not exists
    - Configure ConsoleHandler and FileHandler
    - Apply AppFormatter to both handlers
    - _Requirements: 1.3, 1.4, 2.1_


  - [x] 1.3 Create logging.properties configuration file

    - Set default levels for root and packages
    - Configure file rotation (5MB, 10 files)
    - Set handler configurations
    - _Requirements: 2.2, 2.3, 2.4, 5.1_

  - [x] 1.4 Initialize logging in Main class


    - Call LoggingConfig.initialize() at startup
    - Add shutdown hook for clean handler closure
    - _Requirements: 1.3_

- [x] 2. Migrate service layer



  - [x] 2.1 Add Logger to MemoryService


    - Replace System.out with logger.info
    - Replace System.err with logger.warning/severe
    - Log memory load/save operations
    - _Requirements: 1.1, 4.1, 4.2_

  - [x] 2.2 Add Logger to AnalysisHistoryService


    - Log snapshot operations
    - Log comparison operations
    - _Requirements: 1.1, 4.1_

  - [x] 2.3 Add Logger to SnapshotManager


    - Log file operations
    - Log errors with stack traces
    - _Requirements: 1.1, 4.3_


  - [x] 2.4 Add Logger to CodeGenerationService

    - Log code generation events
    - Log file modifications
    - _Requirements: 1.1, 4.1_

- [x] 3. Migrate ML layer



  - [x] 3.1 Add Logger to MLRelationPredictor


    - Log prediction operations
    - Log model availability status
    - _Requirements: 1.1, 4.1_

  - [x] 3.2 Add Logger to LogisticRegressionModel

    - Log training progress at FINE level
    - Log model statistics
    - _Requirements: 1.1, 1.2_

  - [x] 3.3 Add Logger to TrainingDataGenerator

    - Log data generation events
    - Log dataset statistics
    - _Requirements: 1.1, 4.1_

  - [x] 3.4 Add Logger to ModelEvaluator

    - Log evaluation metrics
    - Log warnings for low accuracy
    - _Requirements: 1.1, 4.1_

- [x] 4. Migrate analyzer layer



  - [x] 4.1 Add Logger to IntelligentRelationAnalyzer

    - Log analysis start/complete
    - Log relation detection events at FINE level
    - _Requirements: 1.1, 4.1_

  - [x] 4.2 Add Logger to FeatureExtractor

    - Log feature extraction at FINEST level
    - _Requirements: 1.1, 1.2_


  - [x] 4.3 Add Logger to ScoreCombiner


    - Log score combination at FINE level


    - _Requirements: 1.1, 4.1_



- [x] 5. Migrate UI layer






  - [x] 5.1 Add Logger to RelationAnalyzerAppView


    - Log user actions
    - Log UI state changes


    - _Requirements: 1.1, 4.1_





  - [x] 5.2 Add Logger to HistoryPanelView


    - Log history operations
    - _Requirements: 1.1, 4.1_





  - [x] 5.3 Add Logger to GraphVisualizationView





    - Log graph rendering events


    - _Requirements: 1.1, 4.1_












  - [x] 5.4 Add Logger to BulkActionsToolbar and BulkActionExecutor






    - Log bulk action operations




    - _Requirements: 1.1, 4.1_




- [x] 6. Verification and cleanup




- [ ] 6. Verification and cleanup





  - [x] 6.1 Search and remove remaining System.out/err calls


    - Grep for System.out.println








    - Grep for System.err.println
    - Grep for printStackTrace()
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 6.2 Add setLevel method to LoggingConfig


    - Allow runtime level changes
    - _Requirements: 5.2_

  - [x] 6.3 Update README with logging documentation


    - Document log file location
    - Document configuration options
    - _Requirements: 5.3_

- [x] 7. Final Checkpoint - Ensure all tests pass






  - Run mvn test
  - Verify log files are created
  - Verify no System.out/err in production code
