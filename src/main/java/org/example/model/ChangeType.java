package org.example.model;

/**
 * Represents the type of change detected between two relation snapshots.
 */
public enum ChangeType {
    /**
     * The relation type has changed (e.g., OneToMany to ManyToMany).
     */
    TYPE_CHANGE,
    
    /**
     * The decision state has changed (e.g., PENDING to ACCEPTED).
     */
    DECISION_CHANGE,
    
    /**
     * The confidence score has changed significantly.
     */
    CONFIDENCE_CHANGE
}
