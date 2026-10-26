package org.example.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Metadata information about an analysis snapshot.
 * Contains timestamp, project info, description, and statistics.
 */
public class SnapshotMetadata {
    
    private String id;
    private LocalDateTime timestamp;
    private String projectPath;
    private String projectName;
    private String description;
    private int totalRelations;
    private int acceptedCount;
    private int rejectedCount;
    private int pendingCount;
    private Map<String, Integer> typeBreakdown;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public SnapshotMetadata() {
        this.typeBreakdown = new HashMap<>();
    }
    
    /**
     * Creates a new SnapshotMetadata with the specified values.
     * 
     * @param id unique snapshot identifier
     * @param timestamp creation time
     * @param projectPath full path to the project
     * @param projectName project folder name
     * @param description user-provided description
     */
    public SnapshotMetadata(String id, LocalDateTime timestamp, String projectPath, 
                            String projectName, String description) {
        this.id = id;
        this.timestamp = timestamp;
        this.projectPath = projectPath;
        this.projectName = projectName;
        this.description = description;
        this.typeBreakdown = new HashMap<>();
    }

    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getProjectPath() {
        return projectPath;
    }
    
    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }
    
    public String getProjectName() {
        return projectName;
    }
    
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public int getTotalRelations() {
        return totalRelations;
    }
    
    public void setTotalRelations(int totalRelations) {
        this.totalRelations = totalRelations;
    }
    
    public int getAcceptedCount() {
        return acceptedCount;
    }
    
    public void setAcceptedCount(int acceptedCount) {
        this.acceptedCount = acceptedCount;
    }
    
    public int getRejectedCount() {
        return rejectedCount;
    }
    
    public void setRejectedCount(int rejectedCount) {
        this.rejectedCount = rejectedCount;
    }
    
    public int getPendingCount() {
        return pendingCount;
    }
    
    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }
    
    public Map<String, Integer> getTypeBreakdown() {
        return typeBreakdown;
    }
    
    public void setTypeBreakdown(Map<String, Integer> typeBreakdown) {
        this.typeBreakdown = typeBreakdown != null ? typeBreakdown : new HashMap<>();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SnapshotMetadata)) return false;
        SnapshotMetadata that = (SnapshotMetadata) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("SnapshotMetadata{id='%s', timestamp=%s, project='%s', relations=%d}",
                id, timestamp, projectName, totalRelations);
    }
}
