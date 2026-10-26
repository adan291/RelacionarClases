package com.example.service;

import java.util.List;
import java.util.Optional;

import com.example.model.Order;
import com.example.model.User;

/**
 * Service class for User operations.
 * This is NOT an entity and should be ignored during relation analysis.
 */
public class UserService {
    
    // This field should NOT be detected as a relation
    private List<User> cachedUsers;
    
    public UserService() {
    }
    
    public Optional<User> findById(Long id) {
        // Implementation would go here
        return Optional.empty();
    }
    
    public List<User> findAll() {
        // Implementation would go here
        return List.of();
    }
    
    public User save(User user) {
        // Implementation would go here
        return user;
    }
    
    public void delete(Long id) {
        // Implementation would go here
    }
    
    public List<Order> getOrdersForUser(Long userId) {
        // Implementation would go here
        return List.of();
    }
}
