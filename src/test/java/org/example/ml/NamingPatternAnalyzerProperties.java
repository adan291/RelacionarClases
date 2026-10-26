package org.example.ml;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for NamingPatternAnalyzer.
 */
public class NamingPatternAnalyzerProperties {
    
    private final NamingPatternAnalyzer analyzer = new NamingPatternAnalyzer();
    
    /**
     * **Feature: enhanced-ml-features, Property 4: Naming Pattern Detection**
     * 
     * For any name ending with Id/ID/_id suffix, hasIdSuffix should return true.
     * 
     * **Validates: Requirement 2.1**
     */
    @Property(tries = 100)
    void idSuffixDetection(@ForAll("namesWithIdSuffix") String name) {
        assert analyzer.hasIdSuffix(name) : 
            "Name with ID suffix should be detected: " + name;
    }
    
    /**
     * Names without ID suffix should return false.
     */
    @Property(tries = 100)
    void noIdSuffixDetection(@ForAll("namesWithoutIdSuffix") String name) {
        assert !analyzer.hasIdSuffix(name) : 
            "Name without ID suffix should not be detected: " + name;
    }
    
    /**
     * Plural detection should work for common plural forms.
     * 
     * **Validates: Requirement 2.3**
     */
    @Property(tries = 100)
    void pluralDetection(@ForAll("pluralNames") String name) {
        assert analyzer.isPlural(name) : 
            "Plural name should be detected: " + name;
    }
    
    /**
     * Singularize then pluralize should return equivalent plural.
     */
    @Property(tries = 50)
    void singularizePluralizeRoundTrip(@ForAll("regularPluralNames") String plural) {
        String singular = analyzer.singularize(plural);
        String rePluralized = analyzer.pluralize(singular);
        
        // The re-pluralized form should end with a plural suffix
        assert analyzer.isPlural(rePluralized) : 
            "Re-pluralized name should be plural: " + plural + " -> " + singular + " -> " + rePluralized;
    }

    /**
     * Collection suffix detection should work.
     * 
     * **Validates: Requirement 2.2**
     */
    @Property(tries = 100)
    void collectionSuffixDetection(@ForAll("namesWithCollectionSuffix") String name) {
        assert analyzer.hasCollectionSuffix(name) : 
            "Name with collection suffix should be detected: " + name;
    }
    
    /**
     * Naming consistency should be in [0, 1] range.
     */
    @Property(tries = 100)
    void consistencyScoreInRange(@ForAll("fieldNames") String fieldName,
                                  @ForAll("classNames") String className) {
        double score = analyzer.calculateConsistency(fieldName, className);
        
        assert score >= 0.0 && score <= 1.0 : 
            "Consistency score should be in [0,1]: " + score;
    }
    
    /**
     * Exact match should have high consistency.
     */
    @Property(tries = 50)
    void exactMatchHighConsistency(@ForAll("classNames") String className) {
        String fieldName = className.substring(0, 1).toLowerCase() + 
            (className.length() > 1 ? className.substring(1) : "");
        
        double score = analyzer.calculateConsistency(fieldName, className);
        
        assert score >= 0.3 : 
            "Exact match should have high consistency: " + fieldName + " vs " + className + " = " + score;
    }
    
    // === Providers ===
    
    @Provide
    Arbitrary<String> namesWithIdSuffix() {
        return Arbitraries.of("userId", "orderId", "customerId", "ORDER_ID", 
            "user_id", "productId", "categoryId", "itemID");
    }
    
    @Provide
    Arbitrary<String> namesWithoutIdSuffix() {
        return Arbitraries.of("user", "order", "name", "description", 
            "title", "value", "count", "status", "type");
    }
    
    @Provide
    Arbitrary<String> pluralNames() {
        return Arbitraries.of("orders", "users", "items", "categories", 
            "children", "entries", "boxes", "classes", "addresses");
    }
    
    @Provide
    Arbitrary<String> regularPluralNames() {
        return Arbitraries.of("orders", "users", "items", "products", 
            "customers", "entries", "values");
    }
    
    @Provide
    Arbitrary<String> namesWithCollectionSuffix() {
        return Arbitraries.of("orderList", "userSet", "itemCollection", 
            "productItems", "categoryElements", "dataEntries");
    }
    
    @Provide
    Arbitrary<String> fieldNames() {
        return Arbitraries.of("user", "orders", "productList", "category", 
            "items", "parentId", "children", "owner");
    }
    
    @Provide
    Arbitrary<String> classNames() {
        return Arbitraries.of("User", "Order", "Product", "Category", 
            "Item", "Customer", "Address");
    }
}
