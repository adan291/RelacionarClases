package org.example.ml;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

/**
 * Extracts numerical features from class pairs for the ML model.
 * Produces a 24-dimension feature vector containing:
 * 
 * Name Features (0-7):
 * 0. Jaro-Winkler similarity between class names
 * 1. Normalized Levenshtein similarity
 * 2. Field name similarity to target class
 * 3. Has ID suffix in field name (0/1)
 * 4. Has collection suffix in field name (0/1)
 * 5. Is plural naming (0/1)
 * 6. Has parent/child pattern (0/1)
 * 7. Naming consistency score
 * 
 * Structure Features (8-15):
 * 8. Normalized source field count
 * 9. Normalized target field count
 * 10. Has inheritance relationship (0/1)
 * 11. Shared interfaces count (normalized)
 * 12. Common prefix length (normalized)
 * 13. Common suffix length (normalized)
 * 14. Has existing JPA annotation (0/1)
 * 15. Source has @Id field (0/1)
 * 
 * Cardinality Features (16-23):
 * 16. Is collection type (0/1)
 * 17. Is Set type (0/1)
 * 18. Is List type (0/1)
 * 19. Is Map type (0/1)
 * 20. Is Optional type (0/1)
 * 21. Is array type (0/1)
 * 22. Is OneToOne/ManyToOne type (0/1)
 * 23. Is OneToMany/ManyToMany type (0/1)
 */
public class FeatureExtractor {

    /** Current feature vector size */
    public static final int FEATURE_COUNT = 24;
    
    /** Legacy feature count for backward compatibility */
    public static final int LEGACY_FEATURE_COUNT = 12;
    private static final double MAX_NAME_LENGTH = 50.0;
    private static final double MAX_FIELD_COUNT = 20.0;
    private static final JaroWinklerSimilarity JARO_WINKLER = new JaroWinklerSimilarity();
    
    private final NamingPatternAnalyzer namingAnalyzer = new NamingPatternAnalyzer();
    private final CardinalityAnalyzer cardinalityAnalyzer = new CardinalityAnalyzer();

    /**
     * Extracts a feature vector for a pair of classes.
     * 
     * @param origen name of the origin class
     * @param destino name of the destination class
     * @param tipoRelacion candidate relation type (OneToOne, OneToMany, ManyToOne, ManyToMany)
     * @param origenInfo structural information about the origin class (can be null)
     * @param destinoInfo structural information about the destination class (can be null)
     * @return normalized feature vector [0, 1] with 24 dimensions
     */
    public double[] extractFeatures(String origen, String destino, String tipoRelacion,
                                    ClassInfo origenInfo, ClassInfo destinoInfo) {
        return extractFeatures(origen, destino, tipoRelacion, origenInfo, destinoInfo, null, null);
    }
    
    /**
     * Extracts an enhanced feature vector with field-level information.
     * 
     * @param origen name of the origin class
     * @param destino name of the destination class
     * @param tipoRelacion candidate relation type
     * @param origenInfo structural information about the origin class
     * @param destinoInfo structural information about the destination class
     * @param fieldName the field name that references the target (can be null)
     * @param fieldType the field type (can be null)
     * @return normalized feature vector [0, 1] with 24 dimensions
     */
    public double[] extractFeatures(String origen, String destino, String tipoRelacion,
                                    ClassInfo origenInfo, ClassInfo destinoInfo,
                                    String fieldName, String fieldType) {
        double[] features = new double[FEATURE_COUNT];
        
        // Handle null/empty inputs
        String safeOrigen = origen != null ? origen : "";
        String safeDestino = destino != null ? destino : "";
        String safeTipo = tipoRelacion != null ? tipoRelacion : "";
        String safeFieldName = fieldName != null ? fieldName : safeDestino.toLowerCase();
        String safeFieldType = fieldType != null ? fieldType : "";
        
        // === Name Features (0-7) ===
        features[0] = calculateJaroWinklerSimilarity(safeOrigen, safeDestino);
        features[1] = calculateLevenshteinSimilarity(safeOrigen, safeDestino);
        features[2] = namingAnalyzer.calculateConsistency(safeFieldName, safeDestino);
        features[3] = namingAnalyzer.hasIdSuffix(safeFieldName) ? 1.0 : 0.0;
        features[4] = namingAnalyzer.hasCollectionSuffix(safeFieldName) ? 1.0 : 0.0;
        features[5] = namingAnalyzer.isPlural(safeFieldName) ? 1.0 : 0.0;
        features[6] = namingAnalyzer.hasParentChildPattern(safeOrigen, safeDestino) ? 1.0 : 0.0;
        features[7] = namingAnalyzer.calculateConsistency(safeFieldName, safeDestino);
        
        // === Structure Features (8-15) ===
        features[8] = normalizeFieldCount(origenInfo);
        features[9] = normalizeFieldCount(destinoInfo);
        features[10] = hasInheritance(origenInfo, destinoInfo) ? 1.0 : 0.0;
        features[11] = normalizeSharedInterfaces(origenInfo, destinoInfo);
        features[12] = normalizeLength(commonPrefixLength(safeOrigen, safeDestino));
        features[13] = normalizeLength(commonSuffixLength(safeOrigen, safeDestino));
        features[14] = hasExistingAnnotation(origenInfo, safeFieldName) ? 1.0 : 0.0;
        features[15] = hasIdField(origenInfo) ? 1.0 : 0.0;
        
        // === Cardinality Features (16-23) ===
        features[16] = cardinalityAnalyzer.isCollectionType(safeFieldType) ? 1.0 : 0.0;
        features[17] = cardinalityAnalyzer.isSetType(safeFieldType) ? 1.0 : 0.0;
        features[18] = cardinalityAnalyzer.isListType(safeFieldType) ? 1.0 : 0.0;
        features[19] = cardinalityAnalyzer.isMapType(safeFieldType) ? 1.0 : 0.0;
        features[20] = cardinalityAnalyzer.isOptionalType(safeFieldType) ? 1.0 : 0.0;
        features[21] = cardinalityAnalyzer.isArrayType(safeFieldType) ? 1.0 : 0.0;
        features[22] = (safeTipo.equals("OneToOne") || safeTipo.equals("ManyToOne")) ? 1.0 : 0.0;
        features[23] = (safeTipo.equals("OneToMany") || safeTipo.equals("ManyToMany")) ? 1.0 : 0.0;
        
        return features;
    }
    
    /**
     * Extracts legacy 12-feature vector for backward compatibility.
     */
    public double[] extractLegacyFeatures(String origen, String destino, String tipoRelacion,
                                          ClassInfo origenInfo, ClassInfo destinoInfo) {
        double[] features = new double[LEGACY_FEATURE_COUNT];
        
        String safeOrigen = origen != null ? origen : "";
        String safeDestino = destino != null ? destino : "";
        String safeTipo = tipoRelacion != null ? tipoRelacion : "";
        
        features[0] = calculateJaroWinklerSimilarity(safeOrigen, safeDestino);
        features[1] = calculateLevenshteinSimilarity(safeOrigen, safeDestino);
        features[2] = normalizeLength(safeOrigen.length());
        features[3] = normalizeLength(safeDestino.length());
        features[4] = normalizeLength(commonPrefixLength(safeOrigen, safeDestino));
        features[5] = normalizeLength(commonSuffixLength(safeOrigen, safeDestino));
        features[6] = hasCollection(origenInfo) ? 1.0 : 0.0;
        features[7] = hasCollection(destinoInfo) ? 1.0 : 0.0;
        features[8] = safeTipo.equalsIgnoreCase("OneToOne") ? 1.0 : 0.0;
        features[9] = safeTipo.equalsIgnoreCase("OneToMany") ? 1.0 : 0.0;
        features[10] = safeTipo.equalsIgnoreCase("ManyToOne") ? 1.0 : 0.0;
        features[11] = safeTipo.equalsIgnoreCase("ManyToMany") ? 1.0 : 0.0;
        
        return features;
    }

    /**
     * @return the fixed number of features in the vector (12)
     */
    public int getFeatureCount() {
        return FEATURE_COUNT;
    }

    /**
     * Calculates Jaro-Winkler similarity between two strings.
     * Returns 0.0 for empty strings.
     */
    private double calculateJaroWinklerSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.equals(b) ? 1.0 : 0.0;
        }
        double similarity = JARO_WINKLER.apply(a.toLowerCase(), b.toLowerCase());
        return clamp(similarity);
    }

    /**
     * Calculates normalized Levenshtein similarity.
     * Returns 1.0 - (distance / maxLength), clamped to [0, 1].
     */
    private double calculateLevenshteinSimilarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int distance = levenshteinDistance(a.toLowerCase(), b.toLowerCase());
        int maxLength = Math.max(a.length(), b.length());
        double similarity = 1.0 - ((double) distance / maxLength);
        return clamp(similarity);
    }

    /**
     * Computes Levenshtein distance between two strings.
     */
    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    /**
     * Normalizes a length value to [0, 1] range.
     */
    private double normalizeLength(int length) {
        return clamp(length / MAX_NAME_LENGTH);
    }

    /**
     * Calculates the length of the common prefix between two strings.
     */
    private int commonPrefixLength(String a, String b) {
        int minLength = Math.min(a.length(), b.length());
        int prefixLength = 0;
        String aLower = a.toLowerCase();
        String bLower = b.toLowerCase();
        
        for (int i = 0; i < minLength; i++) {
            if (aLower.charAt(i) == bLower.charAt(i)) {
                prefixLength++;
            } else {
                break;
            }
        }
        return prefixLength;
    }

    /**
     * Calculates the length of the common suffix between two strings.
     */
    private int commonSuffixLength(String a, String b) {
        int minLength = Math.min(a.length(), b.length());
        int suffixLength = 0;
        String aLower = a.toLowerCase();
        String bLower = b.toLowerCase();
        
        for (int i = 0; i < minLength; i++) {
            if (aLower.charAt(aLower.length() - 1 - i) == bLower.charAt(bLower.length() - 1 - i)) {
                suffixLength++;
            } else {
                break;
            }
        }
        return suffixLength;
    }

    /**
     * Checks if a ClassInfo indicates the class has collections.
     */
    private boolean hasCollection(ClassInfo classInfo) {
        return classInfo != null && classInfo.hasCollections();
    }
    
    /**
     * Normalizes field count to [0, 1] range.
     */
    private double normalizeFieldCount(ClassInfo classInfo) {
        if (classInfo == null) return 0.5;
        return clamp(classInfo.getFieldCount() / MAX_FIELD_COUNT);
    }
    
    /**
     * Checks if two classes share inheritance.
     */
    private boolean hasInheritance(ClassInfo source, ClassInfo target) {
        if (source == null || target == null) return false;
        String sourceSuperClass = source.getSuperClass();
        String targetSuperClass = target.getSuperClass();
        
        // Check if one extends the other or they share a superclass
        if (sourceSuperClass != null && sourceSuperClass.equals(target.getClassName())) return true;
        if (targetSuperClass != null && targetSuperClass.equals(source.getClassName())) return true;
        if (sourceSuperClass != null && sourceSuperClass.equals(targetSuperClass)) return true;
        
        return false;
    }
    
    /**
     * Calculates normalized count of shared interfaces.
     */
    private double normalizeSharedInterfaces(ClassInfo source, ClassInfo target) {
        if (source == null || target == null) return 0.0;
        
        long sharedCount = source.getInterfaces().stream()
            .filter(i -> target.getInterfaces().contains(i))
            .count();
        
        int totalInterfaces = source.getInterfaces().size() + target.getInterfaces().size();
        if (totalInterfaces == 0) return 0.0;
        
        return clamp((double) sharedCount * 2 / totalInterfaces);
    }
    
    /**
     * Checks if a field has existing JPA annotation.
     */
    private boolean hasExistingAnnotation(ClassInfo classInfo, String fieldName) {
        if (classInfo == null || fieldName == null) return false;
        
        return classInfo.getFields().stream()
            .filter(f -> f.name().equals(fieldName))
            .anyMatch(f -> f.hasAnnotation("OneToOne") || f.hasAnnotation("OneToMany") ||
                          f.hasAnnotation("ManyToOne") || f.hasAnnotation("ManyToMany"));
    }
    
    /**
     * Checks if a class has an @Id field.
     */
    private boolean hasIdField(ClassInfo classInfo) {
        return classInfo != null && classInfo.hasIdField();
    }

    /**
     * Clamps a value to the [0, 1] range.
     */
    private double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.5;
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? 1.0 : 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
