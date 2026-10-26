package com.example.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Category entity with bidirectional ManyToMany relationship to Product.
 * This is the inverse side of the relationship (mappedBy on Product).
 */
@Entity
@Table(name = "categories")
public class Category {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(length = 500)
    private String description;
    
    @ManyToMany(mappedBy = "categories")
    private Set<Product> products = new HashSet<>();
    
    // Self-referential relationship for category hierarchy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;
    
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private Set<Category> children = new HashSet<>();
    
    public Category() {
    }
    
    public Category(String name) {
        this.name = name;
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
    
    public Set<Product> getProducts() {
        return products;
    }
    
    public void setProducts(Set<Product> products) {
        this.products = products;
    }
    
    public Category getParent() {
        return parent;
    }
    
    public void setParent(Category parent) {
        this.parent = parent;
    }
    
    public Set<Category> getChildren() {
        return children;
    }
    
    public void setChildren(Set<Category> children) {
        this.children = children;
    }
    
    public void addChild(Category child) {
        children.add(child);
        child.setParent(this);
    }
    
    public void removeChild(Category child) {
        children.remove(child);
        child.setParent(null);
    }
}
