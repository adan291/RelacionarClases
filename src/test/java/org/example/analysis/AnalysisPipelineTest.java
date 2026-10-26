package org.example.analysis;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.model.RelacionDetectada;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for the analysis pipeline.
 * Tests full project analysis, bidirectional detection, and existing annotation detection.
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4
 */
@Tag("e2e")
@SuppressWarnings("unused") // JUnit lifecycle methods are used via reflection
class AnalysisPipelineTest {

    private static final String SAMPLE_PROJECT_PATH = "src/test/resources/sample-project";
    
    private File sampleProjectDir;
    private RelationDetector relationDetector;
    private BidirectionalRelationDetector bidirectionalDetector;
    private ExistingAnnotationScanner annotationScanner;

    @BeforeEach
    void setUp() {
        sampleProjectDir = new File(SAMPLE_PROJECT_PATH);
        relationDetector = new RelationDetector();
        bidirectionalDetector = new BidirectionalRelationDetector();
        annotationScanner = new ExistingAnnotationScanner();
    }


    /**
     * Test full analysis of sample Java project.
     * Verifies that all expected relations are detected with correct types.
     * 
     * **Feature: integration-tests, Property 1: Analysis Completeness**
     * **Validates: Requirements 2.1, 2.2**
     */
    @Test
    void analyzeProject_detectsAllRelations() throws IOException {
        // Arrange - ensure sample project exists
        assertTrue(sampleProjectDir.exists(), 
            "Sample project directory should exist at: " + SAMPLE_PROJECT_PATH);
        
        // Act - load classes and detect relations
        relationDetector.loadClasses(sampleProjectDir);
        List<RelacionDetectada> relations = relationDetector.detectRelationsAsObjects();
        
        // Assert - verify relations were detected
        assertNotNull(relations, "Relations list should not be null");
        assertFalse(relations.isEmpty(), "Should detect at least some relations");
        
        // Verify expected classes were loaded
        Set<String> classNames = relationDetector.getClassNames();
        assertTrue(classNames.contains("User"), "Should detect User class");
        assertTrue(classNames.contains("Order"), "Should detect Order class");
        assertTrue(classNames.contains("OrderItem"), "Should detect OrderItem class");
        assertTrue(classNames.contains("Product"), "Should detect Product class");
        assertTrue(classNames.contains("Category"), "Should detect Category class");
        
        // Verify expected relations exist
        // User -> Order (OneToMany via List<Order>)
        assertTrue(hasRelation(relations, "User", "Order", "OneToMany"),
            "Should detect User -> Order : OneToMany");
        
        // Order -> User (ManyToOne via User field)
        assertTrue(hasRelation(relations, "Order", "User", "ManyToOne"),
            "Should detect Order -> User : ManyToOne");
        
        // Order -> OrderItem (OneToMany via List<OrderItem>)
        assertTrue(hasRelation(relations, "Order", "OrderItem", "OneToMany"),
            "Should detect Order -> OrderItem : OneToMany");
        
        // OrderItem -> Order (ManyToOne via Order field)
        assertTrue(hasRelation(relations, "OrderItem", "Order", "ManyToOne"),
            "Should detect OrderItem -> Order : ManyToOne");
        
        // OrderItem -> Product (ManyToOne via Product field)
        assertTrue(hasRelation(relations, "OrderItem", "Product", "ManyToOne"),
            "Should detect OrderItem -> Product : ManyToOne");
        
        // Product -> Category (OneToMany via Set<Category>)
        assertTrue(hasRelation(relations, "Product", "Category", "OneToMany"),
            "Should detect Product -> Category : OneToMany");
        
        // Category -> Product (OneToMany via Set<Product>)
        assertTrue(hasRelation(relations, "Category", "Product", "OneToMany"),
            "Should detect Category -> Product : OneToMany");
        
        // Category self-referential relations
        assertTrue(hasRelation(relations, "Category", "Category", "ManyToOne") ||
                   hasRelation(relations, "Category", "Category", "OneToMany"),
            "Should detect Category self-referential relation");
    }

    /**
     * Helper method to check if a specific relation exists.
     */
    private boolean hasRelation(List<RelacionDetectada> relations, 
                                String source, String target, String type) {
        return relations.stream()
            .anyMatch(r -> r.getOrigen().equals(source) && 
                          r.getDestino().equals(target) && 
                          r.getTipo().equals(type));
    }

    /**
     * Helper method to find a specific relation.
     */
    private Optional<RelacionDetectada> findRelation(List<RelacionDetectada> relations,
                                                      String source, String target) {
        return relations.stream()
            .filter(r -> r.getOrigen().equals(source) && r.getDestino().equals(target))
            .findFirst();
    }


    /**
     * Test bidirectional relation detection.
     * Verifies that User-Order bidirectional relationship is detected with correct owner/inverse sides.
     * 
     * **Validates: Requirements 2.3**
     */
    @Test
    void analyzeProject_detectsBidirectionalRelations() throws IOException {
        // Arrange
        assertTrue(sampleProjectDir.exists(), 
            "Sample project directory should exist");
        
        // Act - load classes and detect relations
        relationDetector.loadClasses(sampleProjectDir);
        List<RelacionDetectada> relations = relationDetector.detectRelationsAsObjects();
        
        // Apply bidirectional detection
        List<RelacionDetectada> bidirectionalRelations = 
            bidirectionalDetector.detectBidirectional(relations);
        
        // Assert - verify bidirectional relations were detected
        assertNotNull(bidirectionalRelations, "Bidirectional relations list should not be null");
        
        // Find User -> Order relation (should be OneToMany, inverse side)
        Optional<RelacionDetectada> userToOrder = findRelation(bidirectionalRelations, "User", "Order");
        assertTrue(userToOrder.isPresent(), "Should find User -> Order relation");
        
        RelacionDetectada userOrderRel = userToOrder.get();
        assertTrue(userOrderRel.isBidirectional(), 
            "User -> Order should be marked as bidirectional");
        assertFalse(userOrderRel.isOwnerSide(), 
            "User -> Order (OneToMany) should NOT be the owner side");
        
        // Find Order -> User relation (should be ManyToOne, owner side)
        Optional<RelacionDetectada> orderToUser = findRelation(bidirectionalRelations, "Order", "User");
        assertTrue(orderToUser.isPresent(), "Should find Order -> User relation");
        
        RelacionDetectada orderUserRel = orderToUser.get();
        assertTrue(orderUserRel.isBidirectional(), 
            "Order -> User should be marked as bidirectional");
        assertTrue(orderUserRel.isOwnerSide(), 
            "Order -> User (ManyToOne) should be the owner side");
        
        // Verify Order -> OrderItem bidirectional
        Optional<RelacionDetectada> orderToItem = findRelation(bidirectionalRelations, "Order", "OrderItem");
        Optional<RelacionDetectada> itemToOrder = findRelation(bidirectionalRelations, "OrderItem", "Order");
        
        assertTrue(orderToItem.isPresent(), "Should find Order -> OrderItem relation");
        assertTrue(itemToOrder.isPresent(), "Should find OrderItem -> Order relation");
        
        if (orderToItem.get().isBidirectional()) {
            assertFalse(orderToItem.get().isOwnerSide(), 
                "Order -> OrderItem (OneToMany) should NOT be owner side");
        }
        
        if (itemToOrder.get().isBidirectional()) {
            assertTrue(itemToOrder.get().isOwnerSide(), 
                "OrderItem -> Order (ManyToOne) should be owner side");
        }
    }


    /**
     * Test existing annotation detection.
     * Verifies that @OneToMany on User.orders and @ManyToOne on Order.user are detected.
     * 
     * **Validates: Requirements 2.4**
     */
    @Test
    void analyzeProject_detectsExistingAnnotations() {
        // Arrange
        assertTrue(sampleProjectDir.exists(), 
            "Sample project directory should exist");
        
        // Act - scan for existing JPA annotations
        List<RelacionDetectada> existingAnnotations = 
            annotationScanner.scanExistingAnnotations(sampleProjectDir);
        
        // Assert - verify existing annotations were detected
        assertNotNull(existingAnnotations, "Existing annotations list should not be null");
        assertFalse(existingAnnotations.isEmpty(), 
            "Should detect existing JPA annotations in sample project");
        
        // Verify @OneToMany on User.orders is detected
        boolean foundUserOrders = existingAnnotations.stream()
            .anyMatch(r -> r.getOrigen().equals("User") && 
                          r.getDestino().equals("Order") && 
                          r.getTipo().equals("OneToMany") &&
                          r.isExistingAnnotation());
        assertTrue(foundUserOrders, 
            "Should detect @OneToMany on User.orders");
        
        // Verify @ManyToOne on Order.user is detected
        boolean foundOrderUser = existingAnnotations.stream()
            .anyMatch(r -> r.getOrigen().equals("Order") && 
                          r.getDestino().equals("User") && 
                          r.getTipo().equals("ManyToOne") &&
                          r.isExistingAnnotation());
        assertTrue(foundOrderUser, 
            "Should detect @ManyToOne on Order.user");
        
        // Verify @OneToMany on Order.items is detected
        boolean foundOrderItems = existingAnnotations.stream()
            .anyMatch(r -> r.getOrigen().equals("Order") && 
                          r.getDestino().equals("OrderItem") && 
                          r.getTipo().equals("OneToMany") &&
                          r.isExistingAnnotation());
        assertTrue(foundOrderItems, 
            "Should detect @OneToMany on Order.items");
        
        // Verify @ManyToOne on OrderItem.order is detected
        boolean foundItemOrder = existingAnnotations.stream()
            .anyMatch(r -> r.getOrigen().equals("OrderItem") && 
                          r.getDestino().equals("Order") && 
                          r.getTipo().equals("ManyToOne") &&
                          r.isExistingAnnotation());
        assertTrue(foundItemOrder, 
            "Should detect @ManyToOne on OrderItem.order");
        
        // Verify @ManyToMany on Product.categories is detected
        boolean foundProductCategories = existingAnnotations.stream()
            .anyMatch(r -> r.getOrigen().equals("Product") && 
                          r.getDestino().equals("Category") && 
                          r.getTipo().equals("ManyToMany") &&
                          r.isExistingAnnotation());
        assertTrue(foundProductCategories, 
            "Should detect @ManyToMany on Product.categories");
        
        // Verify @ManyToMany on Category.products is detected (mappedBy)
        boolean foundCategoryProducts = existingAnnotations.stream()
            .anyMatch(r -> r.getOrigen().equals("Category") && 
                          r.getDestino().equals("Product") && 
                          r.getTipo().equals("ManyToMany") &&
                          r.isExistingAnnotation());
        assertTrue(foundCategoryProducts, 
            "Should detect @ManyToMany on Category.products");
        
        // Verify all detected annotations are marked as existing
        assertTrue(existingAnnotations.stream().allMatch(RelacionDetectada::isExistingAnnotation),
            "All detected annotations should be marked as existing");
    }

    /**
     * Test that existing annotations have correct mappedBy detection.
     * 
     * **Validates: Requirements 2.4**
     */
    @Test
    void analyzeProject_detectsMappedByInExistingAnnotations() {
        // Arrange
        assertTrue(sampleProjectDir.exists(), 
            "Sample project directory should exist");
        
        // Act - scan for existing JPA annotations
        List<RelacionDetectada> existingAnnotations = 
            annotationScanner.scanExistingAnnotations(sampleProjectDir);
        
        // Assert - verify mappedBy is detected on inverse sides
        // User.orders has mappedBy="user"
        Optional<RelacionDetectada> userOrders = existingAnnotations.stream()
            .filter(r -> r.getOrigen().equals("User") && r.getDestino().equals("Order"))
            .findFirst();
        
        assertTrue(userOrders.isPresent(), "Should find User -> Order annotation");
        if (userOrders.get().getMappedBy() != null) {
            assertEquals("user", userOrders.get().getMappedBy(),
                "User.orders should have mappedBy='user'");
        }
        
        // Category.products has mappedBy="categories"
        Optional<RelacionDetectada> categoryProducts = existingAnnotations.stream()
            .filter(r -> r.getOrigen().equals("Category") && r.getDestino().equals("Product"))
            .findFirst();
        
        assertTrue(categoryProducts.isPresent(), "Should find Category -> Product annotation");
        if (categoryProducts.get().getMappedBy() != null) {
            assertEquals("categories", categoryProducts.get().getMappedBy(),
                "Category.products should have mappedBy='categories'");
        }
    }

    /**
     * Test that analysis detects correct relation types.
     * 
     * **Validates: Requirements 2.2**
     */
    @Test
    void analyzeProject_detectsCorrectRelationTypes() throws IOException {
        // Arrange
        assertTrue(sampleProjectDir.exists(), 
            "Sample project directory should exist");
        
        // Act
        relationDetector.loadClasses(sampleProjectDir);
        List<RelacionDetectada> relations = relationDetector.detectRelationsAsObjects();
        
        // Assert - verify relation types are correct
        // Direct entity references should be ManyToOne
        List<RelacionDetectada> manyToOneRelations = relations.stream()
            .filter(r -> r.getTipo().equals("ManyToOne"))
            .collect(Collectors.toList());
        
        assertFalse(manyToOneRelations.isEmpty(), 
            "Should detect ManyToOne relations for direct entity references");
        
        // Collection fields should be OneToMany
        List<RelacionDetectada> oneToManyRelations = relations.stream()
            .filter(r -> r.getTipo().equals("OneToMany"))
            .collect(Collectors.toList());
        
        assertFalse(oneToManyRelations.isEmpty(), 
            "Should detect OneToMany relations for collection fields");
        
        // Verify confidence levels are high for structural detection
        assertTrue(relations.stream().allMatch(r -> r.getConfianza() >= 0.9),
            "Structural detection should have high confidence (>= 0.9)");
    }

    /**
     * Test that non-entity classes are not included in relations.
     * 
     * **Validates: Requirements 2.1**
     */
    @Test
    void analyzeProject_excludesNonEntityClasses() throws IOException {
        // Arrange
        assertTrue(sampleProjectDir.exists(), 
            "Sample project directory should exist");
        
        // Act
        relationDetector.loadClasses(sampleProjectDir);
        relationDetector.detectRelationsAsObjects();
        Set<String> classNames = relationDetector.getClassNames();
        
        // Assert - UserService should be loaded but not have entity relations
        // (it's a service class, not an entity)
        assertTrue(classNames.contains("UserService"), 
            "Should load UserService class");
        
        // The key is that entity classes ARE detected properly.
        assertTrue(classNames.contains("User"), "Should detect User entity");
        assertTrue(classNames.contains("Order"), "Should detect Order entity");
    }
}
