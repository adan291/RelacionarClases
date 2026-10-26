package org.example.ml;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Analyzes naming patterns in field and class names to detect relationship indicators.
 * Used to enhance ML feature extraction with naming-based signals.
 */
public class NamingPatternAnalyzer {
    
    // Common ID suffixes
    private static final Set<String> ID_SUFFIXES = Set.of(
        "id", "Id", "ID", "_id", "Identifier", "identifier"
    );
    
    // Common collection suffixes
    private static final Set<String> COLLECTION_SUFFIXES = Set.of(
        "List", "list", "Set", "set", "Collection", "collection",
        "Items", "items", "Elements", "elements", "Entries", "entries"
    );
    
    // Common plural endings
    private static final List<String> PLURAL_ENDINGS = List.of(
        "ies", "es", "s"
    );
    
    // Irregular plurals
    private static final Set<String> IRREGULAR_PLURALS = Set.of(
        "children", "people", "men", "women", "feet", "teeth", "mice", "geese"
    );
    
    // Parent/child pattern words
    private static final Set<String> PARENT_WORDS = Set.of(
        "parent", "owner", "master", "container", "holder", "root"
    );
    
    private static final Set<String> CHILD_WORDS = Set.of(
        "child", "children", "item", "items", "element", "elements",
        "detail", "details", "member", "members", "entry", "entries"
    );
    
    // Pattern for camelCase splitting
    private static final Pattern CAMEL_CASE = Pattern.compile("(?<=[a-z])(?=[A-Z])");

    /**
     * Detects if a name has an ID suffix pattern (userId, orderId, customer_id).
     * 
     * @param name the field or variable name
     * @return true if the name ends with an ID suffix
     */
    public boolean hasIdSuffix(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        String normalized = name.trim();
        
        // Check direct suffix matches
        for (String suffix : ID_SUFFIXES) {
            if (normalized.endsWith(suffix)) {
                return true;
            }
        }
        
        // Check uppercase version (ORDER_ID, USER_ID)
        String upper = normalized.toUpperCase(Locale.ROOT);
        return upper.endsWith("_ID") || upper.endsWith("ID") && normalized.length() > 2;
    }
    
    /**
     * Detects if a name has a collection suffix pattern (orderList, items).
     * 
     * @param name the field name
     * @return true if the name suggests a collection
     */
    public boolean hasCollectionSuffix(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        String normalized = name.trim();
        
        // Check direct suffix matches
        for (String suffix : COLLECTION_SUFFIXES) {
            if (normalized.endsWith(suffix)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Detects if a name is in plural form (orders, children, categories).
     * 
     * @param name the field name
     * @return true if the name appears to be plural
     */
    public boolean isPlural(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        String lower = name.toLowerCase(Locale.ROOT);
        
        // Check irregular plurals
        if (IRREGULAR_PLURALS.contains(lower)) {
            return true;
        }
        
        // Check common plural endings
        for (String ending : PLURAL_ENDINGS) {
            if (lower.endsWith(ending) && lower.length() > ending.length() + 1) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Attempts to singularize a plural name.
     * 
     * @param name the plural name
     * @return the singular form, or the original if not plural
     */
    public String singularize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        String lower = name.toLowerCase(Locale.ROOT);
        
        // Handle irregular plurals
        if (lower.equals("children")) return preserveCase(name, "child");
        if (lower.equals("people")) return preserveCase(name, "person");
        if (lower.equals("men")) return preserveCase(name, "man");
        if (lower.equals("women")) return preserveCase(name, "woman");
        
        // Handle -ies -> -y (categories -> category)
        if (lower.endsWith("ies") && name.length() > 3) {
            return name.substring(0, name.length() - 3) + "y";
        }
        
        // Handle -es -> (boxes -> box, classes -> class)
        if (lower.endsWith("es") && name.length() > 2) {
            String stem = name.substring(0, name.length() - 2);
            String stemLower = stem.toLowerCase(Locale.ROOT);
            if (stemLower.endsWith("ss") || stemLower.endsWith("x") || 
                stemLower.endsWith("ch") || stemLower.endsWith("sh")) {
                return stem;
            }
        }
        
        // Handle -s (orders -> order)
        if (lower.endsWith("s") && name.length() > 1) {
            return name.substring(0, name.length() - 1);
        }
        
        return name;
    }
    
    /**
     * Attempts to pluralize a singular name.
     * 
     * @param name the singular name
     * @return the plural form
     */
    public String pluralize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        String lower = name.toLowerCase(Locale.ROOT);
        
        // Handle irregular plurals
        if (lower.equals("child")) return preserveCase(name, "children");
        if (lower.equals("person")) return preserveCase(name, "people");
        if (lower.equals("man")) return preserveCase(name, "men");
        if (lower.equals("woman")) return preserveCase(name, "women");
        
        // Handle -y -> -ies (category -> categories)
        if (lower.endsWith("y") && name.length() > 1) {
            char beforeY = lower.charAt(lower.length() - 2);
            if (!isVowel(beforeY)) {
                return name.substring(0, name.length() - 1) + "ies";
            }
        }
        
        // Handle -s, -x, -ch, -sh -> -es
        if (lower.endsWith("s") || lower.endsWith("x") || 
            lower.endsWith("ch") || lower.endsWith("sh")) {
            return name + "es";
        }
        
        // Default: add -s
        return name + "s";
    }
    
    private boolean isVowel(char c) {
        return "aeiouAEIOU".indexOf(c) >= 0;
    }
    
    private String preserveCase(String original, String replacement) {
        if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }
        return replacement;
    }

    /**
     * Detects if two class names suggest a parent/child relationship.
     * 
     * @param sourceName the source class name
     * @param targetName the target class name
     * @return true if names suggest parent/child pattern
     */
    public boolean hasParentChildPattern(String sourceName, String targetName) {
        if (sourceName == null || targetName == null) {
            return false;
        }
        
        String sourceLower = sourceName.toLowerCase(Locale.ROOT);
        String targetLower = targetName.toLowerCase(Locale.ROOT);
        
        // Check if source contains parent words and target contains child words
        boolean sourceIsParent = PARENT_WORDS.stream().anyMatch(sourceLower::contains);
        boolean targetIsChild = CHILD_WORDS.stream().anyMatch(targetLower::contains);
        
        if (sourceIsParent && targetIsChild) {
            return true;
        }
        
        // Check if target is plural of source (Order -> Orders suggests parent has many)
        String singularTarget = singularize(targetName);
        if (sourceLower.equals(singularTarget.toLowerCase(Locale.ROOT))) {
            return true;
        }
        
        // Check common patterns: OrderItem belongs to Order
        if (targetLower.startsWith(sourceLower) || sourceLower.startsWith(targetLower)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Calculates a naming consistency score between a field name and target class.
     * Higher score means the field name is more consistent with the target class name.
     * 
     * @param fieldName the field name
     * @param targetClassName the target class name
     * @return consistency score in [0, 1]
     */
    public double calculateConsistency(String fieldName, String targetClassName) {
        if (fieldName == null || targetClassName == null) {
            return 0.0;
        }
        
        String fieldLower = fieldName.toLowerCase(Locale.ROOT);
        String targetLower = targetClassName.toLowerCase(Locale.ROOT);
        
        double score = 0.0;
        
        // Exact match (field "user" for class "User")
        if (fieldLower.equals(targetLower)) {
            score += 0.4;
        }
        
        // Field contains class name (field "userList" contains "user")
        if (fieldLower.contains(targetLower)) {
            score += 0.3;
        }
        
        // Singular/plural match
        String singularField = singularize(fieldName).toLowerCase(Locale.ROOT);
        if (singularField.equals(targetLower)) {
            score += 0.2;
        }
        
        // Camel case parts match
        String[] fieldParts = CAMEL_CASE.split(fieldName);
        String[] targetParts = CAMEL_CASE.split(targetClassName);
        
        for (String fp : fieldParts) {
            for (String tp : targetParts) {
                if (fp.equalsIgnoreCase(tp)) {
                    score += 0.1;
                    break;
                }
            }
        }
        
        return Math.min(1.0, score);
    }
}
