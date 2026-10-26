package org.example.ml;

import java.util.List;

/**
 * Lógica de {@link FieldInfo}. Está aparte porque FieldInfo tiene dos versiones
 * ({@code src/main/java25}: record; {@code src/main/java28}: value record) y así lo único
 * duplicado es la declaración.
 */
final class FieldInfoSupport {

    private FieldInfoSupport() {
    }

    /** Copia inmutable de las anotaciones (null se conserva como null). */
    static List<String> immutable(List<String> annotations) {
        return annotations == null ? null : List.copyOf(annotations);
    }

    static FieldInfo create(String name, String type, List<String> annotations) {
        boolean isCollection = isCollectionType(type);
        String genericType = isCollection ? extractGenericType(type) : null;
        return new FieldInfo(name, type, isCollection, genericType, annotations);
    }

    static boolean hasAnnotation(List<String> annotations, String annotationName) {
        return annotations != null && annotations.stream()
            .anyMatch(a -> a.equals(annotationName) || a.endsWith("." + annotationName));
    }

    static boolean isIdField(List<String> annotations) {
        return hasAnnotation(annotations, "Id") || hasAnnotation(annotations, "javax.persistence.Id")
            || hasAnnotation(annotations, "jakarta.persistence.Id");
    }

    static boolean isTransient(List<String> annotations) {
        return hasAnnotation(annotations, "Transient") || hasAnnotation(annotations, "javax.persistence.Transient")
            || hasAnnotation(annotations, "jakarta.persistence.Transient");
    }

    /**
     * Checks if a type is a collection type.
     */
    static boolean isCollectionType(String type) {
        if (type == null) return false;
        return type.startsWith("List<") || type.startsWith("Set<") || 
               type.startsWith("Collection<") || type.startsWith("Iterable<") ||
               type.contains("[]") || type.startsWith("Map<");
    }
    
    /**
     * Extracts the generic type parameter from a collection type.
     */
    static String extractGenericType(String type) {
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
