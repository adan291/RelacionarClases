package org.example.ml;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes field types to determine cardinality indicators.
 * Used for enhanced ML feature extraction.
 */
public class CardinalityAnalyzer {
    
    private static final Set<String> COLLECTION_TYPES = Set.of(
        "List", "ArrayList", "LinkedList",
        "Set", "HashSet", "TreeSet", "LinkedHashSet",
        "Collection", "Iterable",
        "Queue", "Deque", "ArrayDeque"
    );
    
    private static final Set<String> SET_TYPES = Set.of(
        "Set", "HashSet", "TreeSet", "LinkedHashSet", "SortedSet", "NavigableSet"
    );
    
    private static final Set<String> LIST_TYPES = Set.of(
        "List", "ArrayList", "LinkedList", "Vector", "Stack"
    );
    
    private static final Set<String> MAP_TYPES = Set.of(
        "Map", "HashMap", "TreeMap", "LinkedHashMap", "ConcurrentHashMap",
        "SortedMap", "NavigableMap", "Hashtable"
    );
    
    private static final Pattern GENERIC_PATTERN = Pattern.compile("([A-Za-z]+)<(.+)>");
    
    /**
     * Checks if a type is any collection type (List, Set, Collection, etc.).
     */
    public boolean isCollectionType(String type) {
        if (type == null) return false;
        String baseType = extractBaseType(type);
        return COLLECTION_TYPES.contains(baseType) || type.contains("[]");
    }
    
    /**
     * Checks if a type is a Set type (unique elements).
     */
    public boolean isSetType(String type) {
        if (type == null) return false;
        return SET_TYPES.contains(extractBaseType(type));
    }
    
    /**
     * Checks if a type is a List type (ordered elements).
     */
    public boolean isListType(String type) {
        if (type == null) return false;
        return LIST_TYPES.contains(extractBaseType(type));
    }

    /**
     * Checks if a type is a Map type.
     */
    public boolean isMapType(String type) {
        if (type == null) return false;
        return MAP_TYPES.contains(extractBaseType(type));
    }
    
    /**
     * Checks if a type is an array type.
     */
    public boolean isArrayType(String type) {
        return type != null && type.contains("[]");
    }
    
    /**
     * Checks if a type is Optional.
     */
    public boolean isOptionalType(String type) {
        if (type == null) return false;
        String baseType = extractBaseType(type);
        return "Optional".equals(baseType) || "OptionalInt".equals(baseType) ||
               "OptionalLong".equals(baseType) || "OptionalDouble".equals(baseType);
    }
    
    /**
     * Extracts the generic type parameter from a parameterized type.
     * For List<Order> returns "Order", for Map<String,Order> returns "Order".
     */
    public String extractGenericType(String type) {
        if (type == null) return null;
        
        // Handle arrays
        if (type.endsWith("[]")) {
            return type.substring(0, type.length() - 2);
        }
        
        Matcher matcher = GENERIC_PATTERN.matcher(type);
        if (matcher.find()) {
            String generic = matcher.group(2);
            // For Map<K,V>, return V (the value type)
            if (generic.contains(",")) {
                String[] parts = splitGenericParams(generic);
                return parts[parts.length - 1].trim();
            }
            return generic.trim();
        }
        
        return null;
    }
    
    /**
     * Extracts the key type from a Map type.
     * For Map<String, Order> returns "String".
     * Returns null if not a Map type or no generic parameters.
     */
    public String extractMapKeyType(String type) {
        if (type == null || !isMapType(type)) return null;
        
        Matcher matcher = GENERIC_PATTERN.matcher(type);
        if (matcher.find()) {
            String generic = matcher.group(2);
            if (generic.contains(",")) {
                String[] parts = splitGenericParams(generic);
                if (parts.length >= 1) {
                    return parts[0].trim();
                }
            }
        }
        return null;
    }
    
    /**
     * Extracts the value type from a Map type.
     * For Map<String, Order> returns "Order".
     * Returns null if not a Map type or no generic parameters.
     */
    public String extractMapValueType(String type) {
        if (type == null || !isMapType(type)) return null;
        
        Matcher matcher = GENERIC_PATTERN.matcher(type);
        if (matcher.find()) {
            String generic = matcher.group(2);
            if (generic.contains(",")) {
                String[] parts = splitGenericParams(generic);
                if (parts.length >= 2) {
                    return parts[1].trim();
                }
            }
        }
        return null;
    }
    
    /**
     * Extracts both key and value types from a Map type.
     * For Map<String, Order> returns ["String", "Order"].
     * Returns null if not a Map type or no generic parameters.
     */
    public String[] extractMapTypes(String type) {
        if (type == null || !isMapType(type)) return null;
        
        Matcher matcher = GENERIC_PATTERN.matcher(type);
        if (matcher.find()) {
            String generic = matcher.group(2);
            if (generic.contains(",")) {
                String[] parts = splitGenericParams(generic);
                if (parts.length >= 2) {
                    return new String[] { parts[0].trim(), parts[1].trim() };
                }
            }
        }
        return null;
    }
    
    /**
     * Splits generic parameters handling nested generics.
     * For "String, List<Order>" returns ["String", "List<Order>"].
     * For "Map<String, Integer>, Order" returns ["Map<String, Integer>", "Order"].
     */
    private String[] splitGenericParams(String params) {
        java.util.List<String> result = new java.util.ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        
        for (char c : params.toCharArray()) {
            if (c == '<') {
                depth++;
                current.append(c);
            } else if (c == '>') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        
        return result.toArray(String[]::new);
    }
    
    /**
     * Extracts the inner generic type from nested generics.
     * For List<Set<Order>> returns "Set<Order>".
     * For Optional<List<User>> returns "List<User>".
     */
    public String extractNestedGenericType(String type) {
        if (type == null) return null;
        
        Matcher matcher = GENERIC_PATTERN.matcher(type);
        if (matcher.find()) {
            return matcher.group(2).trim();
        }
        return null;
    }
    
    /**
     * Extracts the innermost type from nested generics.
     * For List<Set<Order>> returns "Order".
     * For Optional<List<User>> returns "User".
     */
    public String extractInnermostType(String type) {
        if (type == null) return null;
        
        String current = type;
        String innerType = extractGenericType(current);
        
        while (innerType != null && innerType.contains("<")) {
            current = innerType;
            innerType = extractGenericType(current);
        }
        
        return innerType != null ? innerType : extractGenericType(type);
    }
    
    /**
     * Extracts the base type name without generics.
     * For List<Order> returns "List".
     */
    private String extractBaseType(String type) {
        if (type == null) return null;
        int idx = type.indexOf('<');
        if (idx > 0) {
            return type.substring(0, idx);
        }
        // Handle fully qualified names
        int dotIdx = type.lastIndexOf('.');
        if (dotIdx > 0) {
            return type.substring(dotIdx + 1);
        }
        return type;
    }
    
    /**
     * Infers cardinality from field type.
     * @return "one" for single references, "many" for collections
     */
    public String inferCardinality(String type) {
        if (isCollectionType(type) || isArrayType(type)) {
            return "many";
        }
        return "one";
    }
}
