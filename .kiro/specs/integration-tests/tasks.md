# Implementation Plan

- [x] 1. Set up test infrastructure






  - [x] 1.1 Add TestFX dependencies to pom.xml


    - Add testfx-junit5 dependency
    - Add openjfx-monocle for headless testing
    - Configure surefire plugin for test tags
    - _Requirements: Testing infrastructure_

  - [x] 1.2 Create TestDataFactory utility class


    - Implement createRelation() method
    - Implement createSampleRelations(count) method
    - Implement createSnapshot() method
    - Implement createTrainingData() method
    - _Requirements: Test utilities_


  - [x] 1.3 Create sample Java project for testing

    - Create User.java with @Id and @OneToMany
    - Create Order.java with @ManyToOne and @OneToMany
    - Create OrderItem.java with @ManyToOne
    - Create Product.java with @ManyToMany
    - Create Category.java with bidirectional relation
    - _Requirements: 2.1, 2.2_

  - [x] 1.4 Create test configuration files


    - Create test-config.properties
    - Create test-memory.json with sample data
    - _Requirements: Test setup_
-

- [x] 2. Implement service integration tests




  - [x] 2.1 Create MemoryServiceIntegrationTest

    - Test save and load round trip
    - Test handling of corrupted file
    - Test concurrent access thread safety
    - Use @TempDir for isolation
    - _Requirements: 1.1, 6.1_


  - [x] 2.2 Create AnalysisHistoryIntegrationTest

    - Test snapshot save/restore workflow
    - Test snapshot comparison
    - Test bulk deletion
    - _Requirements: 1.2_


  - [x] 2.3 Create CodeGenerationIntegrationTest

    - Test code generation with real Java files
    - Verify generated annotations are valid
    - Test backup file creation
    - _Requirements: 1.4_

- [x] 3. Implement analysis pipeline tests






  - [x] 3.1 Create AnalysisPipelineTest class


    - Tag with @Tag("e2e")
    - Set up sample project path
    - _Requirements: 2.1_


  - [x] 3.2 Write test for full project analysis

    - Analyze sample-project directory
    - Verify expected relations are detected
    - Verify relation types are correct
    - **Property 1: Analysis Completeness**
    - _Requirements: 2.1, 2.2_

  - [x] 3.3 Write test for bidirectional detection


    - Verify User-Order bidirectional is detected
    - Verify owner/inverse sides are correct
    - _Requirements: 2.3_


  - [x] 3.4 Write test for existing annotation detection

    - Verify @OneToMany on User.orders is detected
    - Verify @ManyToOne on Order.user is detected
    - _Requirements: 2.4_

- [x] 4. Implement ML pipeline tests






  - [x] 4.1 Create MLPipelineTest class


    - Tag with @Tag("e2e")
    - Set up test memory with decisions
    - _Requirements: 3.1_

  - [x] 4.2 Write test for training data generation

    - Generate training data from test memory
    - Verify feature vectors are correct
    - Verify labels match decisions
    - _Requirements: 3.1_

  - [x] 4.3 Write test for model training

    - Train model with sufficient data (10+ examples)
    - Verify model weights are set
    - **Property 3: ML Training Effectiveness**
    - _Requirements: 3.2_

  - [x] 4.4 Write test for prediction

    - Use trained model to predict
    - Verify predictions are in valid range
    - _Requirements: 3.3_

  - [x] 4.5 Write test for model persistence

    - Save model to file
    - Load model from file
    - Verify predictions match
    - _Requirements: 3.4_

  - [x] 4.6 Write test for fallback behavior

    - Test with <5 decisions in memory
    - Verify ML is not used
    - Verify heuristics are applied
    - _Requirements: 3.5_

- [x] 5. Implement data flow tests






  - [x] 5.1 Create DataFlowIntegrationTest class


    - Tag with @Tag("integration")
    - _Requirements: 5.1-5.4_

  - [x] 5.2 Write test for analysis to UI flow

    - Analyze project
    - Verify relations appear in expected format
    - _Requirements: 5.1_

  - [x] 5.3 Write test for decision persistence flow

    - Accept/reject relations
    - Verify decisions saved to memory
    - Reload and verify persistence
    - **Property 2: Memory Persistence**
    - _Requirements: 5.2_

  - [x] 5.4 Write test for snapshot flow

    - Save snapshot
    - Modify state
    - Restore snapshot
    - Verify state matches original
    - **Property 4: Snapshot Integrity**
    - _Requirements: 5.3_

- [x] 6. Implement error handling tests





  - [x] 6.1 Create ErrorHandlingTest class


    - Tag with @Tag("integration")
    - _Requirements: 6.1-6.4_

  - [x] 6.2 Write test for corrupted memory file


    - Create invalid JSON file
    - Verify graceful handling
    - Verify empty memory returned
    - _Requirements: 6.1_


  - [x] 6.3 Write test for missing configuration

    - Remove config file
    - Verify defaults are applied
    - _Requirements: 6.2_



  - [x] 6.4 Write test for invalid Java files
    - Create malformed Java file
    - Verify analysis continues
    - Verify error is logged
    - _Requirements: 6.3_

- [x] 7. Implement UI integration tests (optional)





  - [x] 7.1 Create RelationAnalyzerUITest class


    - Extend ApplicationTest (TestFX)
    - Tag with @Tag("ui")
    - Configure headless mode
    - _Requirements: 4.1-4.6_

  - [x] 7.2 Write test for folder selection

    - Simulate folder selection
    - Verify analysis is triggered
    - _Requirements: 4.1_

  - [x] 7.3 Write test for accept/reject buttons

    - Click accept button
    - Verify relation is marked accepted
    - _Requirements: 4.2_

  - [x] 7.4 Write test for bulk actions

    - Select multiple relations
    - Click bulk accept
    - Verify all are accepted
    - _Requirements: 4.3_

  - [x] 7.5 Write test for filters

    - Apply type filter
    - Verify table shows only matching
    - _Requirements: 4.4_

- [x] 8. Implement performance tests






  - [x] 8.1 Create PerformanceTest class


    - Tag with @Tag("performance")
    - _Requirements: 7.1-7.3_

  - [x] 8.2 Write test for analysis time


    - Generate 100+ class project
    - Measure analysis time
    - Assert <10 seconds
    - _Requirements: 7.1_

  - [x] 8.3 Write test for memory usage


    - Analyze large project
    - Monitor heap usage
    - Assert reasonable limits
    - _Requirements: 7.3_
-

- [x] 9. Final Checkpoint - Ensure all tests pass




  - Run mvn test -Dgroups=integration,e2e
  - Verify all integration tests pass
  - Check code coverage report
