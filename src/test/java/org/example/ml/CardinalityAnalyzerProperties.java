package org.example.ml;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for CardinalityAnalyzer.
 */
public class CardinalityAnalyzerProperties {
    
    private final CardinalityAnalyzer analyzer = new CardinalityAnalyzer();
    
    /**
     * **Feature: enhanced-ml-features, Property 3: Collection Detection Accuracy**
     * 
     * For any collection type, isCollectionType should return true.
     * 
     * **Validates: Requirements 3.1, 3.3, 3.4**
     */
    @Property(tries = 100)
    void collectionTypeDetection(@ForAll("collectionTypes") String type) {
        assert analyzer.isCollectionType(type) : 
            "Collection type should be detected: " + type;
    }
    
    /**
     * Non-collection types should return false.
     */
    @Property(tries = 100)
    void nonCollectionTypeDetection(@ForAll("nonCollectionTypes") String type) {
        assert !analyzer.isCollectionType(type) : 
            "Non-collection type should not be detected as collection: " + type;
    }
    
    /**
     * Set types should be detected correctly.
     */
    @Property(tries = 50)
    void setTypeDetection(@ForAll("setTypes") String type) {
        assert analyzer.isSetType(type) : "Set type should be detected: " + type;
        assert analyzer.isCollectionType(type) : "Set should also be a collection: " + type;
    }
    
    /**
     * List types should be detected correctly.
     */
    @Property(tries = 50)
    void listTypeDetection(@ForAll("listTypes") String type) {
        assert analyzer.isListType(type) : "List type should be detected: " + type;
        assert analyzer.isCollectionType(type) : "List should also be a collection: " + type;
    }
    
    /**
     * Generic type extraction should work correctly.
     */
    @Property(tries = 50)
    void genericTypeExtraction(@ForAll("genericTypes") String type,
                               @ForAll("classNames") String expected) {
        String fullType = "List<" + expected + ">";
        String extracted = analyzer.extractGenericType(fullType);
        assert expected.equals(extracted) : 
            "Generic type should be extracted: " + fullType + " -> " + extracted;
    }
    
    /**
     * Cardinality inference should be correct.
     */
    @Property(tries = 100)
    void cardinalityInference(@ForAll("collectionTypes") String type) {
        assert "many".equals(analyzer.inferCardinality(type)) : 
            "Collection should infer 'many' cardinality: " + type;
    }
    
    // === Providers ===
    
    @Provide
    Arbitrary<String> collectionTypes() {
        return Arbitraries.of(
            "List<Order>", "ArrayList<User>", "Set<Product>", 
            "HashSet<Item>", "Collection<Entity>", "Order[]",
            "LinkedList<Customer>", "TreeSet<Category>"
        );
    }
    
    @Provide
    Arbitrary<String> nonCollectionTypes() {
        return Arbitraries.of(
            "String", "Integer", "User", "Order", "Long", 
            "Boolean", "LocalDate", "BigDecimal"
        );
    }
    
    @Provide
    Arbitrary<String> setTypes() {
        return Arbitraries.of("Set<Order>", "HashSet<User>", "TreeSet<Item>", "LinkedHashSet<Product>");
    }
    
    @Provide
    Arbitrary<String> listTypes() {
        return Arbitraries.of("List<Order>", "ArrayList<User>", "LinkedList<Item>");
    }
    
    @Provide
    Arbitrary<String> genericTypes() {
        return Arbitraries.of("Order", "User", "Product", "Item", "Customer");
    }
    
    @Provide
    Arbitrary<String> classNames() {
        return Arbitraries.of("Order", "User", "Product", "Item", "Customer");
    }
}
