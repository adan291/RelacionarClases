package org.example.model;

/**
 * Represents the acceptance status of a relation in the analysis history.
 * Used to track user decisions on detected relations.
 */
public enum DecisionState {
    /**
     * The relation has been accepted by the user.
     */
    ACCEPTED,
    
    /**
     * The relation has been rejected by the user.
     */
    REJECTED,
    
    /**
     * The relation is pending user decision.
     */
    PENDING
}
