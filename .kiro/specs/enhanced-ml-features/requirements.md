# Requirements: Enhanced ML Features

## Overview
Improve ML model accuracy by adding additional features for relation prediction, including field-level analysis, naming patterns, and cardinality inference.

## Actors
- ML Model: Uses enhanced features for prediction
- FeatureExtractor: Extracts additional features from code
- Developer: Benefits from improved prediction accuracy

## Functional Requirements

### 1. Field-Level Features
- 1.1 Extract field name similarity (not just class name)
- 1.2 Detect field type patterns (List<X>, Set<X>, Map<K,V>)
- 1.3 Identify generic type parameters
- 1.4 Count fields per class as complexity indicator

### 2. Naming Pattern Features
- 2.1 Detect ID suffix patterns (userId, orderId, customerId)
- 2.2 Detect List/Collection suffix patterns (orderList, items, children)
- 2.3 Detect plural naming (orders vs order)
- 2.4 Detect parent/child naming patterns
- 2.5 Calculate naming convention consistency score

### 3. Cardinality Inference Features
- 3.1 Infer cardinality from collection type (List = many, single field = one)
- 3.2 Detect Optional<T> as nullable single reference
- 3.3 Identify Set vs List (uniqueness indicator)
- 3.4 Detect array types as collection indicator

### 4. Structural Pattern Features
- 4.1 Detect inheritance relationships (extends, implements)
- 4.2 Identify interface implementations
- 4.3 Count method references between classes
- 4.4 Detect constructor injection patterns

### 5. Annotation-Based Features
- 5.1 Detect existing JPA annotations weight
- 5.2 Identify @Id fields for entity detection
- 5.3 Detect @Transient fields to exclude
- 5.4 Identify cascade annotations for relationship strength

### 6. Feature Vector Enhancement
- 6.1 Expand feature vector from 12 to 24 dimensions
- 6.2 Maintain backward compatibility with existing models
- 6.3 Normalize all new features to [0, 1] range
- 6.4 Document each feature's meaning and calculation

### 7. Model Retraining
- 7.1 Automatically retrain when feature vector changes
- 7.2 Preserve existing training data during migration
- 7.3 Log feature importance after training

## Non-Functional Requirements
- Performance: Feature extraction should not increase analysis time by >50%
- Accuracy: Target 85% prediction accuracy (up from ~70%)
- Compatibility: Existing trained models should gracefully handle new features
