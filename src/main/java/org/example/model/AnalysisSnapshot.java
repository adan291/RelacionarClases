package org.example.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a complete point-in-time capture of an analysis session.
 * Combines metadata with the full list of relation snapshots and decision states.
 */
public class AnalysisSnapshot {
    
    private String id;
    private SnapshotMetadata metadata;
    private List<RelationSnapshot> relations;
    private Map<String, Boolean> decisions;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public AnalysisSnapshot() {
        this.relations = new ArrayList<>();
        this.decisions = new HashMap<>();
    }
    
    /**
     * Creates a new AnalysisSnapshot with the specified components.
     * 
     * @param id unique snapshot identifier
     * @param metadata snapshot metadata
     * @param relations list of relation snapshots
     * @param decisions map of relation keys to acceptance decisions
     */
    public AnalysisSnapshot(String id, SnapshotMetadata metadata, 
                            List<RelationSnapshot> relations, 
                            Map<String, Boolean> decisions) {
        this.id = id;
        this.metadata = metadata;
        this.relations = relations != null ? new ArrayList<>(relations) : new ArrayList<>();
        this.decisions = decisions != null ? new HashMap<>(decisions) : new HashMap<>();
    }
    
    /**
     * Computes and updates the statistics in the metadata based on the relations list.
     * Updates totalRelations, acceptedCount, rejectedCount, pendingCount, and typeBreakdown.
     */
    public void computeStatistics() {
        if (metadata == null) {
            return;
        }
        
        int total = relations.size();
        int accepted = 0;
        int rejected = 0;
        int pending = 0;
        Map<String, Integer> typeBreakdown = new HashMap<>();

        for (RelationSnapshot relation : relations) {
            // Count by decision state
            DecisionState state = relation.getDecisionState();
            if (state == DecisionState.ACCEPTED) {
                accepted++;
            } else if (state == DecisionState.REJECTED) {
                rejected++;
            } else {
                pending++;
            }
            
            // Count by relation type
            String tipo = relation.getTipo();
            if (tipo != null) {
                typeBreakdown.merge(tipo, 1, Integer::sum);
            }
        }
        
        metadata.setTotalRelations(total);
        metadata.setAcceptedCount(accepted);
        metadata.setRejectedCount(rejected);
        metadata.setPendingCount(pending);
        metadata.setTypeBreakdown(typeBreakdown);
    }
    
    /**
     * Creates an AnalysisSnapshot from a list of RelacionDetectada and a decisions map.
     * 
     * @param id unique snapshot identifier
     * @param projectPath full path to the project
     * @param projectName project folder name
     * @param description user-provided description
     * @param relaciones list of detected relations
     * @param decisions map of relation keys to acceptance decisions (true=accepted, false=rejected)
     * @return a new AnalysisSnapshot with computed statistics
     */
    public static AnalysisSnapshot fromRelaciones(String id, String projectPath, 
                                                   String projectName, String description,
                                                   List<RelacionDetectada> relaciones,
                                                   Map<String, Boolean> decisions) {
        LocalDateTime timestamp = LocalDateTime.now();
        SnapshotMetadata metadata = new SnapshotMetadata(id, timestamp, projectPath, 
                                                          projectName, description);
        
        List<RelationSnapshot> snapshots = new ArrayList<>();
        Map<String, Boolean> decisionsCopy = decisions != null ? new HashMap<>(decisions) : new HashMap<>();
        
        if (relaciones != null) {
            for (RelacionDetectada relacion : relaciones) {
                String key = relacion.getClaveMemoria();
                DecisionState state = DecisionState.PENDING;
                
                if (decisionsCopy.containsKey(key)) {
                    state = decisionsCopy.get(key) ? DecisionState.ACCEPTED : DecisionState.REJECTED;
                }
                
                snapshots.add(new RelationSnapshot(relacion, state));
            }
        }
        
        AnalysisSnapshot snapshot = new AnalysisSnapshot(id, metadata, snapshots, decisionsCopy);
        snapshot.computeStatistics();
        return snapshot;
    }

    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public SnapshotMetadata getMetadata() {
        return metadata;
    }
    
    public void setMetadata(SnapshotMetadata metadata) {
        this.metadata = metadata;
    }
    
    public List<RelationSnapshot> getRelations() {
        return relations;
    }
    
    public void setRelations(List<RelationSnapshot> relations) {
        this.relations = relations != null ? relations : new ArrayList<>();
    }
    
    public Map<String, Boolean> getDecisions() {
        return decisions;
    }
    
    public void setDecisions(Map<String, Boolean> decisions) {
        this.decisions = decisions != null ? decisions : new HashMap<>();
    }
    
    @Override
    public String toString() {
        return String.format("AnalysisSnapshot{id='%s', relations=%d, metadata=%s}",
                id, relations.size(), metadata);
    }
}
