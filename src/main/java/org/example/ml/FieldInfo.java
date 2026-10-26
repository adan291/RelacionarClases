package org.example.ml;

import java.util.List;

/**
 * Represents detailed information about a field in a Java class.
 * Used for enhanced feature extraction in ML model.
 * 
 * @param name the field name
 * @param type the field type (e.g., "String", "List<Order>")
 * @param isCollection whether the field is a collection type
 * @param genericType the generic type parameter if collection (e.g., "Order" from List<Order>)
 * @param annotations list of annotation names on the field
 */
public record FieldInfo(
    String name,
    String type,
    boolean isCollection,
    String genericType,
    List<String> annotations
) {
    /**
     * Creates a simple FieldInfo without annotations.
     */
    public static FieldInfo simple(String name, String type) {
        boolean isCollection = isCollectionType(type);
        String genericType = isCollection ? extractGenericType(type) : null;
        return new FieldInfo(name, type, isCollection, genericType, List.of());
    }
    
    /**
     * Creates a FieldInfo with annotations.
     */
    public static FieldInfo withAnnotations(String name, String type, List<String> annotations) {
        boolean isCollection = isCollectionType(type);
        String genericType = isCollection ? extractGenericType(type) : null;
        return new FieldInfo(name, type, isCollection, genericType, annotations);
    }
    
    /**
     * Checks if the field has a specific annotation.
     */
    public boolean hasAnnotation(String annotationName) {
        return annotations != null && annotations.stream()
            .anyMatch(a -> a.equals(annotationName) || a.endsWith("." + annotationName));
    }
    
    /**
     * Checks if this is an ID field (has @Id annotation).
     */
    public boolean isIdField() {
        return hasAnnotation("Id") || hasAnnotation("javax.persistence.Id") || 
               hasAnnotation("jakarta.persistence.Id");
    }
    
    /**
     * Checks if this field should be excluded (has @Transient).
     */
    public boolean isTransient() {
        return hasAnnotation("Transient") || hasAnnotation("javax.persistence.Transient") ||
               hasAnnotation("jakarta.persistence.Transient");
    }

    /**
     * Checks if a type is a collection type.
     */
    private static boolean isCollectionType(String type) {
        if (type == null) return false;
        return type.startsWith("List<") || type.startsWith("Set<") || 
               type.startsWith("Collection<") || type.startsWith("Iterable<") ||
               type.contains("[]") || type.startsWith("Map<");
    }
    
    /**
     * Extracts the generic type parameter from a collection type.
     */
    private static String extractGenericType(String type) {
        if (type == null) return null;
        
        // Handle arrays
        if (type.endsWith("[]")) {
            return type.substring(0, type.length() - 2);
        }
        
        // Handle generics like List<Order>
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');
        if (start > 0 && end > start) {
            String generic = type.substring(start + 1, end);
            // For Map<K,V>, return V (the value type)
            if (generic.contains(",")) {
                String[] parts = generic.split(",");
                return parts[parts.length - 1].trim();
            }
            return generic.trim();
        }
        
        return null;
    }
}
