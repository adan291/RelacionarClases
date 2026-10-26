# Implementation Plan

- [x] 1. Create naming pattern analyzer





  - [ ] 1.1 Create NamingPatternAnalyzer class
    - Implement hasIdSuffix(String name) - detects userId, orderId patterns
    - Implement hasCollectionSuffix(String name) - detects orderList, items patterns
    - Implement isPlural(String name) - detects orders, children patterns
    - Implement singularize/pluralize methods


    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 1.2 Write property test for ID suffix detection

    - **Property 4: Naming Pattern Detection**
    - Test: userId -> true, user -> false, ORDER_ID -> true
    - **Validates: Requirement 2.1**





  - [ ] 1.3 Implement parent/child pattern detection
    - Detect parent/child, master/detail, owner/owned patterns


    - Calculate naming consistency score
    - _Requirements: 2.4, 2.5_

- [x] 2. Enhance ClassInfo model






  - [x] 2.1 Create FieldInfo record

    - Include name, type, isCollection, genericType, annotations
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 2.2 Enhance ClassInfo with additional metadata


    - Add List<FieldInfo> fields

    - Add List<String> interfaces
    - Add String superClass
    - Add boolean hasIdField
    - Add List<String> constructorParams
    - _Requirements: 4.1, 4.2, 4.4, 5.2_

  - [x] 2.3 Update class parsing to populate new fields


    - Parse field annotations

    - Extract generic types from collections
    - Identify @Id annotated fields
    - _Requirements: 1.2, 1.3, 5.2_


- [x] 3. Implement cardinality features





  - [x] 3.1 Create collection type detection methods

    - isCollectionType(String type) - List, Set, Collection, Iterable


    - isSetType(String type)

    - isListType(String type)
    - isMapType(String type)
    - isArrayType(String type)

    - isOptionalType(String type)
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 3.2 Write property test for collection detection

    - **Property 3: Collection Detection Accuracy**
    - Test various collection types
    - **Validates: Requirements 3.1, 3.3, 3.4**

  - [x] 3.3 Implement generic type extraction

    - Extract T from List<T>, Set<T>, Optional<T>

    - Extract K,V from Map<K,V>
    - Handle nested generics
    - _Requirements: 1.3_


- [x] 4. Enhance FeatureExtractor





  - [x] 4.1 Update FEATURE_COUNT constant to 24

    - Define feature index constants for documentation
    - _Requirements: 6.1_



  - [x] 4.2 Implement name features (indices 0-7)

    - Add fieldNameSimilarity calculation
    - Integrate NamingPatternAnalyzer
    - _Requirements: 1.1, 2.1-2.5_


  - [x] 4.3 Implement structure features (indices 8-15)


    - Add field count normalization
    - Add inheritance detection
    - Add interface sharing detection

    - Add annotation detection
    - _Requirements: 4.1, 4.2, 5.1, 5.2_



  - [x] 4.4 Implement cardinality features (indices 16-23)

    - Add collection type indicators

    - Add relation type one-hot encoding
    - _Requirements: 3.1-3.4_

  - [x] 4.5 Write property test for feature vector length

    - **Property 1: Feature Vector Length Invariant**
    - **Validates: Requirement 6.1**




  - [x] 4.6 Write property test for feature normalization

    - **Property 2: Feature Normalization**
    - All values in [0, 1]

    - **Validates: Requirement 6.3**

- [x] 5. Implement backward compatibility







  - [x] 5.1 Add feature version to model metadata


    - Store FEATURE_COUNT in model JSON
    - _Requirements: 6.2_


  - [x] 5.2 Implement feature padding for old models

    - Detect old model by feature count
    - Pad missing features with 0.5 (neutral)
    - Log warning about model version mismatch
    - _Requirements: 6.2_

  - [x] 5.3 Write property test for backward compatibility


    - **Property 5: Backward Compatibility**
    - Old 12-feature model works with new extractor
    - **Validates: Requirement 6.2**

- [x] 6. Update training data generation





  - [x] 6.1 Update TrainingDataGenerator to use enhanced features


    - Pass ClassInfo to FeatureExtractor
    - Include field-level information
    - _Requirements: 7.2_

  - [x] 6.2 Implement training data migration


    - Detect old training data format
    - Regenerate with new features if needed
    - _Requirements: 7.2_


- [x] 7. Update model training





  - [x] 7.1 Update LogisticRegressionModel for 24 features


    - Initialize 24 weights
    - Handle variable feature count
    - _Requirements: 7.1_



  - [x] 7.2 Add feature importance logging
    - Log weight magnitudes after training
    - Identify most influential features
    - _Requirements: 7.3_

- [x] 8. Integration and testing






  - [x] 8.1 Update IntelligentRelationAnalyzer

    - Pass enhanced ClassInfo to FeatureExtractor
    - _Requirements: 1.1-1.4_


  - [x] 8.2 Run full test suite

    - Verify all existing tests pass
    - Verify new property tests pass
    - _Requirements: All_

- [x] 9. Final Checkpoint - Ensure all tests pass




  - Run mvn test
  - Verify feature extraction works correctly
  - Test with existing trained model (backward compatibility)
  - Test with newly trained model (enhanced features)
