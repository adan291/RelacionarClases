# Requirements: Integration Tests (E2E)

## Overview
Implement comprehensive integration tests covering end-to-end workflows, ensuring all components work together correctly.

## Actors
- Developer: Writes and runs tests
- CI/CD: Executes tests automatically
- Test Framework: JUnit 5 + TestFX for UI testing

## Functional Requirements

### 1. Service Integration Tests
- 1.1 Test MemoryService save/load cycle with real files
- 1.2 Test AnalysisHistoryService snapshot workflow
- 1.3 Test ML training and prediction pipeline
- 1.4 Test code generation with real Java files

### 2. Analysis Pipeline Tests
- 2.1 Test full analysis of sample Java project
- 2.2 Verify relation detection accuracy on known project
- 2.3 Test bidirectional relation detection
- 2.4 Test existing annotation detection

### 3. ML Pipeline Tests
- 3.1 Test training data generation from memory
- 3.2 Test model training with sufficient data
- 3.3 Test prediction with trained model
- 3.4 Test model persistence and reload
- 3.5 Test fallback when insufficient training data

### 4. UI Integration Tests (TestFX)
- 4.1 Test folder selection and analysis trigger
- 4.2 Test accept/reject button functionality
- 4.3 Test bulk selection and actions
- 4.4 Test filter application
- 4.5 Test history panel operations
- 4.6 Test export dialog workflow

### 5. Data Flow Tests
- 5.1 Test relation flow from analysis to UI display
- 5.2 Test decision flow from UI to memory persistence
- 5.3 Test snapshot flow from save to restore
- 5.4 Test configuration flow from file to components

### 6. Error Handling Tests
- 6.1 Test behavior with corrupted memory file
- 6.2 Test behavior with missing configuration
- 6.3 Test behavior with invalid Java files
- 6.4 Test behavior with network errors (Git clone)

### 7. Performance Tests
- 7.1 Test analysis time for 100+ classes
- 7.2 Test UI responsiveness during analysis
- 7.3 Test memory usage during large analysis

## Non-Functional Requirements
- Isolation: Tests should not affect each other
- Repeatability: Tests should produce same results
- Speed: Full test suite should complete in <5 minutes
- Coverage: Target 80% code coverage
