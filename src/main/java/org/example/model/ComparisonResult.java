package org.example.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of comparing two analysis snapshots.
 * Contains categorized lists of changes: added, removed, type changes, and decision changes.
 */
public class ComparisonResult {
    
    private SnapshotMetadata snapshot1;
    private SnapshotMetadata snapshot2;
    private boolean sameProject;
    private List<RelationSnapshot> added;
    private List<RelationSnapshot> removed;
    private List<RelationChange> typeChanges;
    private List<RelationChange> decisionChanges;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public ComparisonResult() {
        this.added = new ArrayList<>();
        this.removed = new ArrayList<>();
        this.typeChanges = new ArrayList<>();
        this.decisionChanges = new ArrayList<>();
    }
    
    /**
     * Creates a new ComparisonResult with the specified snapshot metadata.
     * 
     * @param snapshot1 metadata of the first snapshot (older)
     * @param snapshot2 metadata of the second snapshot (newer)
     * @param sameProject whether both snapshots are from the same project
     */
    public ComparisonResult(SnapshotMetadata snapshot1, SnapshotMetadata snapshot2, boolean sameProject) {
        this();
        this.snapshot1 = snapshot1;
        this.snapshot2 = snapshot2;
        this.sameProject = sameProject;
    }
    
    /**
     * Gets the total number of changes across all categories.
     * 
     * @return the total change count
     */
    public int getTotalChanges() {
        return added.size() + removed.size() + typeChanges.size() + decisionChanges.size();
    }

    /**
     * Gets the count of added relations.
     * 
     * @return the added count
     */
    public int getAddedCount() {
        return added.size();
    }
    
    /**
     * Gets the count of removed relations.
     * 
     * @return the removed count
     */
    public int getRemovedCount() {
        return removed.size();
    }
    
    /**
     * Gets the count of type changes.
     * 
     * @return the type change count
     */
    public int getTypeChangeCount() {
        return typeChanges.size();
    }
    
    /**
     * Gets the count of decision changes.
     * 
     * @return the decision change count
     */
    public int getDecisionChangeCount() {
        return decisionChanges.size();
    }
    
    // Getters and Setters
    
    public SnapshotMetadata getSnapshot1() {
        return snapshot1;
    }
    
    public void setSnapshot1(SnapshotMetadata snapshot1) {
        this.snapshot1 = snapshot1;
    }
    
    public SnapshotMetadata getSnapshot2() {
        return snapshot2;
    }
    
    public void setSnapshot2(SnapshotMetadata snapshot2) {
        this.snapshot2 = snapshot2;
    }
    
    public boolean isSameProject() {
        return sameProject;
    }
    
    public void setSameProject(boolean sameProject) {
        this.sameProject = sameProject;
    }
    
    public List<RelationSnapshot> getAdded() {
        return added;
    }
    
    public void setAdded(List<RelationSnapshot> added) {
        this.added = added != null ? added : new ArrayList<>();
    }
    
    public List<RelationSnapshot> getRemoved() {
        return removed;
    }
    
    public void setRemoved(List<RelationSnapshot> removed) {
        this.removed = removed != null ? removed : new ArrayList<>();
    }
    
    public List<RelationChange> getTypeChanges() {
        return typeChanges;
    }
    
    public void setTypeChanges(List<RelationChange> typeChanges) {
        this.typeChanges = typeChanges != null ? typeChanges : new ArrayList<>();
    }
    
    public List<RelationChange> getDecisionChanges() {
        return decisionChanges;
    }
    
    public void setDecisionChanges(List<RelationChange> decisionChanges) {
        this.decisionChanges = decisionChanges != null ? decisionChanges : new ArrayList<>();
    }
    
    @Override
    public String toString() {
        return String.format("ComparisonResult{sameProject=%s, added=%d, removed=%d, typeChanges=%d, decisionChanges=%d}",
                sameProject, added.size(), removed.size(), typeChanges.size(), decisionChanges.size());
    }
}
