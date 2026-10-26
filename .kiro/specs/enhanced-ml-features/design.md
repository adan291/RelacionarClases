# Design: Enhanced ML Features

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                  Enhanced Feature Extraction                 │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              FeatureExtractor (Enhanced)              │   │
│  │                                                       │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐    │   │
│  │  │   Name      │ │  Structure  │ │  Cardinality│    │   │
│  │  │  Features   │ │  Features   │ │  Features   │    │   │
│  │  │  (8 dims)   │ │  (8 dims)   │ │  (8 dims)   │    │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘    │   │
│  │                         │                            │   │
│  │                         ▼                            │   │
│  │              ┌─────────────────────┐                │   │
│  │              │  24-dim Feature     │                │   │
│  │              │      Vector         │                │   │
│  │              └─────────────────────┘                │   │
│  └──────────────────────────────────────────────────────┘   │
│                            │                                 │
│                            ▼                                 │
│              ┌─────────────────────────┐                    │
│              │  LogisticRegressionModel │                    │
│              │  (24 weights + bias)     │                    │
│              └─────────────────────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

## Enhanced Feature Vector (24 dimensions)

### Name Features (0-7)
| Index | Feature | Description | Range |
|-------|---------|-------------|-------|
| 0 | classNameJaroWinkler | Jaro-Winkler similarity of class names | [0,1] |
| 1 | classNameLevenshtein | Normalized Levenshtein distance | [0,1] |
| 2 | fieldNameSimilarity | Similarity of field names | [0,1] |
| 3 | hasIdSuffix | Field ends with Id/ID | {0,1} |
| 4 | hasListSuffix | Field ends with List/s/es | {0,1} |
| 5 | isPluralNaming | Field name is plural form | {0,1} |
| 6 | hasParentChildPattern | Names suggest parent/child | {0,1} |
| 7 | namingConsistency | Overall naming convention score | [0,1] |

### Structure Features (8-15)
| Index | Feature | Description | Range |
|-------|---------|-------------|-------|
| 8 | sourceFieldCount | Normalized field count in source | [0,1] |
| 9 | targetFieldCount | Normalized field count in target | [0,1] |
| 10 | hasInheritance | Classes share inheritance | {0,1} |
| 11 | sharedInterfaces | Count of shared interfaces | [0,1] |
| 12 | methodReferences | Cross-class method calls | [0,1] |
| 13 | constructorInjection | Target injected in constructor | {0,1} |
| 14 | hasExistingAnnotation | JPA annotation already exists | {0,1} |
| 15 | hasIdField | Source has @Id field | {0,1} |

### Cardinality Features (16-23)
| Index | Feature | Description | Range |
|-------|---------|-------------|-------|
| 16 | isCollection | Field is List/Set/Collection | {0,1} |
| 17 | isSet | Field is Set (unique) | {0,1} |
| 18 | isList | Field is List (ordered) | {0,1} |
| 19 | isMap | Field is Map | {0,1} |
| 20 | isOptional | Field is Optional<T> | {0,1} |
| 21 | isArray | Field is array type | {0,1} |
| 22 | relationType_OneToOne | One-hot: OneToOne | {0,1} |
| 23 | relationType_OneToMany | One-hot: OneToMany | {0,1} |

## Component Changes

### FeatureExtractor (Enhanced)

```java
public class FeatureExtractor {
    public static final int FEATURE_COUNT = 24;
    
    public double[] extractFeatures(String sourceClass, String targetClass,
                                    String fieldName, String fieldType,
                                    String relationType, ClassInfo sourceInfo,
                                    ClassInfo targetInfo);
    
    // New helper methods
    private double calculateFieldNameSimilarity(String fieldName, String targetClass);
    private boolean hasIdSuffix(String fieldName);
    private boolean hasListSuffix(String fieldName);
    private boolean isPluralNaming(String fieldName);
    private boolean hasParentChildPattern(String source, String target);
    private double calculateNamingConsistency(String fieldName, String targetClass);
    private boolean isCollectionType(String fieldType);
    private String extractGenericType(String fieldType);
}
```

### ClassInfo (Enhanced)

```java
public class ClassInfo {
    private String className;
    private List<FieldInfo> fields;
    private List<String> interfaces;
    private String superClass;
    private boolean hasIdField;
    private List<String> constructorParams;
    
    public record FieldInfo(
        String name,
        String type,
        boolean isCollection,
        String genericType,
        List<String> annotations
    ) {}
}
```

### NamingPatternAnalyzer (New)

```java
public class NamingPatternAnalyzer {
    public boolean hasIdSuffix(String name);
    public boolean hasCollectionSuffix(String name);
    public boolean isPlural(String name);
    public String singularize(String name);
    public String pluralize(String name);
    public double calculateConsistency(String fieldName, String className);
}
```

## Migration Strategy

### Phase 1: Add New Features
1. Create NamingPatternAnalyzer
2. Enhance ClassInfo with additional metadata
3. Extend FeatureExtractor with new features

### Phase 2: Model Compatibility
1. Add feature version to model metadata
2. Implement feature padding for old models (fill new features with 0.5)
3. Log warning when using old model with new features

### Phase 3: Retraining
1. Regenerate training data with new features
2. Retrain model with expanded feature vector
3. Evaluate accuracy improvement

## Correctness Properties

### Property 1: Feature Vector Length Invariant
Feature vector should always have exactly 24 dimensions.
- **Validates**: Requirement 6.1

### Property 2: Feature Normalization
All features should be in [0, 1] range.
- **Validates**: Requirement 6.3

### Property 3: Collection Detection Accuracy
Collection types (List, Set, Map) should be correctly identified.
- **Validates**: Requirements 3.1, 3.3, 3.4

### Property 4: Naming Pattern Detection
ID suffix and plural patterns should be correctly detected.
- **Validates**: Requirements 2.1, 2.3

### Property 5: Backward Compatibility
Old models should work with new feature extractor (with padding).
- **Validates**: Requirement 6.2
