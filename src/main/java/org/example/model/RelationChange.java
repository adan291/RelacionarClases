package org.example.model;

import java.util.Objects;

/**
 * Represents a change detected between two relation snapshots.
 * Contains the before and after states along with the type of change.
 */
public class RelationChange {
    
    private RelationSnapshot before;
    private RelationSnapshot after;
    private ChangeType changeType;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public RelationChange() {
    }
    
    /**
     * Creates a new RelationChange with the specified values.
     * 
     * @param before the relation state before the change
     * @param after the relation state after the change
     * @param changeType the type of change detected
     */
    public RelationChange(RelationSnapshot before, RelationSnapshot after, ChangeType changeType) {
        this.before = before;
        this.after = after;
        this.changeType = changeType;
    }
    
    /**
     * Gets the relation key (based on origen, destino, tipo from the before snapshot,
     * or after snapshot if before is null).
     * 
     * @return the relation key
     */
    public String getKey() {
        if (before != null) {
            return before.getKey();
        } else if (after != null) {
            return after.getKey();
        }
        return null;
    }
    
    // Getters and Setters
    
    public RelationSnapshot getBefore() {
        return before;
    }
    
    public void setBefore(RelationSnapshot before) {
        this.before = before;
    }
    
    public RelationSnapshot getAfter() {
        return after;
    }
    
    public void setAfter(RelationSnapshot after) {
        this.after = after;
    }
    
    public ChangeType getChangeType() {
        return changeType;
    }
    
    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelationChange)) return false;
        RelationChange that = (RelationChange) o;
        return Objects.equals(before, that.before) &&
               Objects.equals(after, that.after) &&
               changeType == that.changeType;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(before, after, changeType);
    }
    
    @Override
    public String toString() {
        return String.format("RelationChange{type=%s, before=%s, after=%s}",
                changeType, before, after);
    }
}
