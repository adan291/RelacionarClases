package org.example.service;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.model.AnalysisSnapshot;
import org.example.model.ChangeType;
import org.example.model.ComparisonResult;
import org.example.model.RelacionDetectada;
import org.example.model.RelationChange;
import org.example.model.RelationSnapshot;
import org.example.model.SnapshotMetadata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * 📜 AnalysisHistoryService
 * Enhanced service for managing analysis snapshots with full state persistence.
 * Integrates with SnapshotManager for file operations.
 */
public class AnalysisHistoryService {

    private static final Logger LOGGER = Logger.getLogger(AnalysisHistoryService.class.getName());
    private static final String HISTORY_FILE = "analysis_history.json";
    private static final int MAX_HISTORY = 20;
    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final SnapshotManager snapshotManager;
    private List<AnalysisRecord> history = new ArrayList<>();

    /**
     * Creates a new AnalysisHistoryService with default SnapshotManager.
     */
    public AnalysisHistoryService() {
        this(new SnapshotManager());
    }

    /**
     * Creates a new AnalysisHistoryService with a custom SnapshotManager.
     * 
     * @param snapshotManager the snapshot manager to use for file operations
     */
    public AnalysisHistoryService(SnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
        load();
    }

    /**
     * Gets the SnapshotManager instance.
     * 
     * @return the snapshot manager
     */
    public SnapshotManager getSnapshotManager() {
        return snapshotManager;
    }


    // ==================== NEW SNAPSHOT METHODS ====================

    /**
     * Saves the current analysis state as a snapshot.
     * 
     * @param projectPath full path to the project
     * @param relations list of detected relations
     * @param decisions map of relation keys to acceptance decisions (true=accepted, false=rejected)
     * @param description optional user-provided description
     * @return the snapshot ID
     * @throws IOException if saving fails
     */
    public String saveSnapshot(String projectPath, 
                               List<RelacionDetectada> relations,
                               Map<String, Boolean> decisions,
                               String description) throws IOException {
        String projectName = new File(projectPath).getName();
        String snapshotId = snapshotManager.generateSnapshotId(projectName);
        
        AnalysisSnapshot snapshot = AnalysisSnapshot.fromRelaciones(
            snapshotId, projectPath, projectName, description, relations, decisions);
        
        snapshotManager.saveSnapshot(snapshot);
        LOGGER.info("Saved snapshot: " + snapshotId);
        return snapshotId;
    }

    /**
     * Restores a snapshot by ID.
     * 
     * @param snapshotId the ID of the snapshot to restore
     * @return the loaded AnalysisSnapshot, or null if not found
     * @throws IOException if loading fails due to file corruption
     */
    public AnalysisSnapshot restoreSnapshot(String snapshotId) throws IOException {
        AnalysisSnapshot snapshot = snapshotManager.loadSnapshot(snapshotId);
        if (snapshot == null) {
            LOGGER.warning("Snapshot not found: " + snapshotId);
            return null;
        }
        LOGGER.info("Restored snapshot: " + snapshotId);
        return snapshot;
    }

    /**
     * Gets a summary of a snapshot including statistics.
     * 
     * @param snapshotId the ID of the snapshot
     * @return the snapshot metadata with statistics, or null if not found
     */
    public SnapshotMetadata getSummary(String snapshotId) {
        AnalysisSnapshot snapshot = snapshotManager.loadSnapshot(snapshotId);
        if (snapshot == null) {
            return null;
        }
        return snapshot.getMetadata();
    }

    /**
     * Lists all snapshot metadata sorted by timestamp (newest first).
     * 
     * @return list of snapshot metadata
     */
    public List<SnapshotMetadata> listSnapshots() {
        return snapshotManager.listSnapshots();
    }

    /**
     * Deletes a snapshot by ID.
     * 
     * @param snapshotId the ID of the snapshot to delete
     * @return true if deleted successfully
     */
    public boolean deleteSnapshot(String snapshotId) {
        return snapshotManager.deleteSnapshot(snapshotId);
    }

    /**
     * Deletes multiple snapshots.
     * 
     * @param snapshotIds list of snapshot IDs to delete
     * @return count of successfully deleted snapshots
     */
    public int deleteSnapshots(List<String> snapshotIds) {
        return snapshotManager.deleteSnapshots(snapshotIds);
    }

    /**
     * Checks if two snapshots are from the same project.
     * 
     * @param snapshotId1 first snapshot ID
     * @param snapshotId2 second snapshot ID
     * @return true if both snapshots are from the same project
     */
    public boolean areFromSameProject(String snapshotId1, String snapshotId2) {
        AnalysisSnapshot snap1 = snapshotManager.loadSnapshot(snapshotId1);
        AnalysisSnapshot snap2 = snapshotManager.loadSnapshot(snapshotId2);
        
        if (snap1 == null || snap2 == null) {
            return false;
        }
        
        SnapshotMetadata meta1 = snap1.getMetadata();
        SnapshotMetadata meta2 = snap2.getMetadata();
        
        if (meta1 == null || meta2 == null) {
            return false;
        }
        
        return Objects.equals(meta1.getProjectPath(), meta2.getProjectPath());
    }


    /**
     * Compares two snapshots and returns categorized changes.
     * Relations are categorized as: added, removed, type changed, or decision changed.
     * Each relation appears in at most one category.
     * 
     * @param snapshotId1 first snapshot ID (older)
     * @param snapshotId2 second snapshot ID (newer)
     * @return comparison result with categorized changes, or null if snapshots not found
     */
    public ComparisonResult compare(String snapshotId1, String snapshotId2) {
        AnalysisSnapshot snap1 = snapshotManager.loadSnapshot(snapshotId1);
        AnalysisSnapshot snap2 = snapshotManager.loadSnapshot(snapshotId2);
        
        if (snap1 == null || snap2 == null) {
            return null;
        }
        
        boolean sameProject = areFromSameProject(snapshotId1, snapshotId2);
        ComparisonResult result = new ComparisonResult(
            snap1.getMetadata(), snap2.getMetadata(), sameProject);
        
        // Build maps for efficient lookup using origen->destino as key (without tipo)
        Map<String, RelationSnapshot> map1 = buildRelationMap(snap1.getRelations());
        Map<String, RelationSnapshot> map2 = buildRelationMap(snap2.getRelations());
        
        // Track processed relations to ensure no duplicates
        Set<String> processedKeys = new HashSet<>();
        
        // Find removed and changed relations (in snap1 but not in snap2, or changed)
        for (RelationSnapshot rel1 : snap1.getRelations()) {
            String key = getRelationKey(rel1);
            RelationSnapshot rel2 = map2.get(key);
            
            if (rel2 == null) {
                // Relation was removed
                result.getRemoved().add(rel1);
            } else {
                // Check for type change first (higher priority)
                if (!Objects.equals(rel1.getTipo(), rel2.getTipo())) {
                    result.getTypeChanges().add(new RelationChange(rel1, rel2, ChangeType.TYPE_CHANGE));
                    processedKeys.add(key);
                } else if (rel1.getDecisionState() != rel2.getDecisionState()) {
                    // Decision changed (only if type didn't change)
                    result.getDecisionChanges().add(new RelationChange(rel1, rel2, ChangeType.DECISION_CHANGE));
                    processedKeys.add(key);
                }
            }
            processedKeys.add(key);
        }
        
        // Find added relations (in snap2 but not in snap1)
        for (RelationSnapshot rel2 : snap2.getRelations()) {
            String key = getRelationKey(rel2);
            if (!map1.containsKey(key)) {
                result.getAdded().add(rel2);
            }
        }
        
        return result;
    }

    /**
     * Builds a map of relations keyed by origen->destino for efficient lookup.
     */
    private Map<String, RelationSnapshot> buildRelationMap(List<RelationSnapshot> relations) {
        Map<String, RelationSnapshot> map = new HashMap<>();
        if (relations != null) {
            for (RelationSnapshot rel : relations) {
                map.put(getRelationKey(rel), rel);
            }
        }
        return map;
    }

    /**
     * Gets a unique key for a relation based on origen and destino only.
     * This allows detecting type changes for the same relation pair.
     */
    private String getRelationKey(RelationSnapshot rel) {
        return rel.getOrigen() + "->" + rel.getDestino();
    }


    // ==================== LEGACY METHODS (kept for backward compatibility) ====================

    /**
     * Registro de un análisis (legacy format).
     */
    public static class AnalysisRecord {
        public String id;
        public String timestamp;
        public String folderPath;
        public String folderName;
        public int totalRelations;
        public int acceptedCount;
        public int rejectedCount;
        public List<RelationSummary> relations;

        public AnalysisRecord() {
            this.relations = new ArrayList<>();
        }
    }

    /**
     * Resumen de una relación (sin todos los detalles).
     */
    public static class RelationSummary {
        public String origen;
        public String destino;
        public String tipo;
        public double confianza;
        public String source;
        public boolean bidirectional;

        public RelationSummary(RelacionDetectada rel) {
            this.origen = rel.getOrigen();
            this.destino = rel.getDestino();
            this.tipo = rel.getTipo();
            this.confianza = rel.getConfianza();
            this.source = rel.getSource() != null ? rel.getSource().name() : "UNKNOWN";
            this.bidirectional = rel.isBidirectional();
        }
    }

    /**
     * Guarda un nuevo análisis en el historial (legacy method).
     */
    public void saveAnalysis(String folderPath, List<RelacionDetectada> relations) {
        AnalysisRecord record = new AnalysisRecord();
        record.id = generateId();
        record.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        record.folderPath = folderPath;
        record.folderName = new File(folderPath).getName();
        record.totalRelations = relations.size();
        
        for (RelacionDetectada rel : relations) {
            record.relations.add(new RelationSummary(rel));
        }

        // Agregar al inicio
        history.add(0, record);

        // Limitar tamaño
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }

        save();
    }

    /**
     * Obtiene el historial completo (legacy method).
     */
    public List<AnalysisRecord> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * Obtiene un análisis específico por ID (legacy method).
     */
    public AnalysisRecord getAnalysis(String id) {
        return history.stream()
            .filter(r -> r.id.equals(id))
            .findFirst()
            .orElse(null);
    }

    /**
     * Elimina un análisis del historial (legacy method).
     */
    public void deleteAnalysis(String id) {
        history.removeIf(r -> r.id.equals(id));
        save();
    }

    /**
     * Limpia todo el historial (legacy method).
     */
    public void clearHistory() {
        history.clear();
        save();
    }

    /**
     * Compara dos análisis y devuelve las diferencias (legacy method).
     */
    public LegacyComparisonResult compareLegacy(String id1, String id2) {
        AnalysisRecord r1 = getAnalysis(id1);
        AnalysisRecord r2 = getAnalysis(id2);
        
        if (r1 == null || r2 == null) {
            return null;
        }

        LegacyComparisonResult result = new LegacyComparisonResult();
        result.analysis1 = r1;
        result.analysis2 = r2;

        // Encontrar relaciones nuevas, eliminadas y modificadas
        for (RelationSummary rel1 : r1.relations) {
            String key = rel1.origen + "->" + rel1.destino;
            RelationSummary match = r2.relations.stream()
                .filter(r -> (r.origen + "->" + r.destino).equals(key))
                .findFirst()
                .orElse(null);

            if (match == null) {
                result.removed.add(rel1);
            } else if (!rel1.tipo.equals(match.tipo)) {
                result.modified.add(new ModifiedRelation(rel1, match));
            }
        }

        for (RelationSummary rel2 : r2.relations) {
            String key = rel2.origen + "->" + rel2.destino;
            boolean exists = r1.relations.stream()
                .anyMatch(r -> (r.origen + "->" + r.destino).equals(key));
            if (!exists) {
                result.added.add(rel2);
            }
        }

        return result;
    }

    private void load() {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<AnalysisRecord>>() {}.getType();
            List<AnalysisRecord> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                history = loaded;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error cargando historial", e);
        }
    }

    private void save() {
        try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
            gson.toJson(history, writer);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error guardando historial", e);
        }
    }

    private String generateId() {
        return "analysis_" + System.currentTimeMillis();
    }

    /**
     * Resultado de comparación entre dos análisis (legacy).
     */
    public static class LegacyComparisonResult {
        public AnalysisRecord analysis1;
        public AnalysisRecord analysis2;
        public List<RelationSummary> added = new ArrayList<>();
        public List<RelationSummary> removed = new ArrayList<>();
        public List<ModifiedRelation> modified = new ArrayList<>();
    }

    /**
     * Relación que cambió de tipo.
     */
    public static class ModifiedRelation {
        public RelationSummary before;
        public RelationSummary after;

        public ModifiedRelation(RelationSummary before, RelationSummary after) {
            this.before = before;
            this.after = after;
        }
    }
}
