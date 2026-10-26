package com.example.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Product entity with ManyToMany relationship to Category.
 * Demonstrates bidirectional ManyToMany relationship.
 */
@Entity
@Table(name = "products")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(name = "stock_quantity")
    private Integer stockQuantity;
    
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "product_categories",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();
    
    public Product() {
    }
    
    public Product(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public Integer getStockQuantity() {
        return stockQuantity;
    }
    
    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }
    
    public Set<Category> getCategories() {
        return categories;
    }
    
    public void setCategories(Set<Category> categories) {
        this.categories = categories;
    }
    
    public void addCategory(Category category) {
        categories.add(category);
        category.getProducts().add(this);
    }
    
    public void removeCategory(Category category) {
        categories.remove(category);
        category.getProducts().remove(this);
    }
}
