package org.example.service;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.example.config.ConfigManager;
import org.example.model.AnalysisSnapshot;
import org.example.model.SnapshotMetadata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Handles low-level snapshot file operations with individual files per snapshot.
 * Manages persistence of analysis snapshots to the file system.
 */
public class SnapshotManager {

    private static final Logger LOGGER = Logger.getLogger(SnapshotManager.class.getName());
    private static final String JSON_EXTENSION = ".json";
    private static final DateTimeFormatter ID_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern SNAPSHOT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,200}$");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-zA-Z0-9]");
    private static final Pattern MULTI_UNDERSCORE = Pattern.compile("_+");
    private static final Pattern EDGE_UNDERSCORE = Pattern.compile("^_|_$");
    
    private final Gson gson;
    private final File snapshotsDirectory;

    /**
     * Creates a new SnapshotManager using the configured snapshots directory.
     */
    public SnapshotManager() {
        this(ConfigManager.getInstance().getPersistenceConfig().snapshotsDirectory());
    }

    /**
     * Creates a new SnapshotManager with a custom snapshots directory.
     * 
     * @param snapshotsDir the directory path for storing snapshots
     */
    public SnapshotManager(String snapshotsDir) {
        this.snapshotsDirectory = new File(snapshotsDir);
        this.gson = createGsonInstance();
        initializeDirectory();
    }

    /**
     * Creates a Gson instance with custom serializers for LocalDateTime.
     */
    private Gson createGsonInstance() {
        return new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer())
            .create();
    }

    /**
     * Initializes the snapshots directory, creating it if it doesn't exist.
     */
    private void initializeDirectory() {
        if (!snapshotsDirectory.exists()) {
            boolean created = snapshotsDirectory.mkdirs();
            if (created) {
                LOGGER.info(() -> "Created snapshots directory: " + snapshotsDirectory.getAbsolutePath());
            } else {
                LOGGER.warning(() -> "Failed to create snapshots directory: " + snapshotsDirectory.getAbsolutePath());
            }
        }
    }


    /**
     * Generates a snapshot ID from the project name with timestamp.
     * Format: sanitizedProjectName_YYYYMMDD_HHmmss
     * 
     * @param projectName the project name to include in the ID
     * @return the generated snapshot ID
     */
    public String generateSnapshotId(String projectName) {
        String sanitized = sanitizeProjectName(projectName);
        String timestamp = LocalDateTime.now().format(ID_DATE_FORMAT);
        return sanitized + "_" + timestamp;
    }

    /**
     * Sanitizes a project name by replacing non-alphanumeric characters with underscores.
     * 
     * @param projectName the project name to sanitize
     * @return the sanitized project name
     */
    public String sanitizeProjectName(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return "unnamed";
        }
        // Replace any non-alphanumeric character with underscore
        String sanitized = NON_ALNUM.matcher(projectName).replaceAll("_");
        // Remove consecutive underscores
        sanitized = MULTI_UNDERSCORE.matcher(sanitized).replaceAll("_");
        // Remove leading/trailing underscores
        sanitized = EDGE_UNDERSCORE.matcher(sanitized).replaceAll("");
        // Ensure not empty after sanitization
        if (sanitized.isEmpty()) {
            return "unnamed";
        }
        return sanitized;
    }

    /**
     * Saves a snapshot to disk as a JSON file.
     * 
     * @param snapshot the snapshot to save
     * @return the snapshot ID
     * @throws IOException if saving fails
     */
    public String saveSnapshot(AnalysisSnapshot snapshot) throws IOException {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot cannot be null");
        }
        
        String snapshotId = snapshot.getId();
        if (snapshotId == null || snapshotId.isEmpty()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
        }
        
        File snapshotFile = getSnapshotFile(snapshotId);
        
        try (FileWriter writer = new FileWriter(snapshotFile)) {
            gson.toJson(snapshot, writer);
            LOGGER.info(() -> "Saved snapshot: " + snapshotId);
            return snapshotId;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Failed to save snapshot: " + snapshotId);
            throw e;
        }
    }

    /**
     * Loads a snapshot by ID.
     * 
     * @param snapshotId the ID of the snapshot to load
     * @return the loaded snapshot, or null if not found or corrupted
     */
    public AnalysisSnapshot loadSnapshot(String snapshotId) {
        if (snapshotId == null || snapshotId.isEmpty()) {
            return null;
        }
        if (!isValidSnapshotId(snapshotId)) {
            LOGGER.warning(() -> "Rejected invalid snapshot ID: " + snapshotId);
            return null;
        }
        
        File snapshotFile = getSnapshotFile(snapshotId);
        
        if (!snapshotFile.exists()) {
            LOGGER.warning(() -> "Snapshot file not found: " + snapshotId);
            return null;
        }
        
        try (FileReader reader = new FileReader(snapshotFile)) {
            AnalysisSnapshot snapshot = gson.fromJson(reader, AnalysisSnapshot.class);
            LOGGER.info(() -> "Loaded snapshot: " + snapshotId);
            return snapshot;
        } catch (IOException | JsonParseException e) {
            LOGGER.log(Level.WARNING, e, () -> "Failed to load snapshot: " + snapshotId);
            return null;
        }
    }

    /**
     * Deletes a snapshot by ID.
     * 
     * @param snapshotId the ID of the snapshot to delete
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteSnapshot(String snapshotId) {
        if (snapshotId == null || snapshotId.isEmpty()) {
            return false;
        }
        if (!isValidSnapshotId(snapshotId)) {
            LOGGER.warning(() -> "Rejected invalid snapshot ID: " + snapshotId);
            return false;
        }
        
        File snapshotFile = getSnapshotFile(snapshotId);
        
        if (!snapshotFile.exists()) {
            LOGGER.warning(() -> "Snapshot file not found for deletion: " + snapshotId);
            return false;
        }
        
        boolean deleted = snapshotFile.delete();
        if (deleted) {
            LOGGER.info(() -> "Deleted snapshot: " + snapshotId);
        } else {
            LOGGER.warning(() -> "Failed to delete snapshot: " + snapshotId);
        }
        return deleted;
    }

    /**
     * Deletes multiple snapshots.
     * 
     * @param snapshotIds the list of snapshot IDs to delete
     * @return the count of successfully deleted snapshots
     */
    public int deleteSnapshots(List<String> snapshotIds) {
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            return 0;
        }
        
        int deletedCount = 0;
        for (String snapshotId : snapshotIds) {
            if (deleteSnapshot(snapshotId)) {
                deletedCount++;
            }
        }
        final int finalDeletedCount = deletedCount;
        final int totalCount = snapshotIds.size();
        LOGGER.info(() -> "Bulk deleted " + finalDeletedCount + " of " + totalCount + " snapshots");
        return deletedCount;
    }


    /**
     * Lists all snapshot metadata without loading full relation data.
     * Returns snapshots sorted by timestamp descending (newest first).
     * 
     * @return list of snapshot metadata
     */
    public List<SnapshotMetadata> listSnapshots() {
        List<SnapshotMetadata> metadataList = new ArrayList<>();
        
        File[] files = snapshotsDirectory.listFiles((dir, name) -> name.endsWith(JSON_EXTENSION));
        
        if (files == null || files.length == 0) {
            return metadataList;
        }
        
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                AnalysisSnapshot snapshot = gson.fromJson(reader, AnalysisSnapshot.class);
                if (snapshot != null && snapshot.getMetadata() != null) {
                    metadataList.add(snapshot.getMetadata());
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.log(Level.WARNING, "Failed to read snapshot metadata from: " + file.getName(), e);
                // Skip corrupted files
            }
        }
        
        // Sort by timestamp descending (newest first)
        metadataList.sort(Comparator.comparing(
            SnapshotMetadata::getTimestamp,
            Comparator.nullsLast(Comparator.reverseOrder())
        ));
        
        return metadataList;
    }

    /**
     * Gets the file for a snapshot ID.
     * 
     * @param snapshotId the snapshot ID
     * @return the file object
     */
    private File getSnapshotFile(String snapshotId) {
        if (!isValidSnapshotId(snapshotId)) {
            throw new IllegalArgumentException("Invalid snapshot ID: " + snapshotId);
        }
        File file = new File(snapshotsDirectory, snapshotId + JSON_EXTENSION);
        Path dir = snapshotsDirectory.toPath().toAbsolutePath().normalize();
        Path resolved = file.toPath().toAbsolutePath().normalize();
        if (!dir.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Snapshot ID escapes the snapshots directory: " + snapshotId);
        }
        return file;
    }

    /**
     * Un ID válido solo tiene letras ASCII, dígitos, '_' y '-' (como los que genera
     * {@link #generateSnapshotId(String)} o un UUID). Así no puede contener separadores ni "..".
     *
     * @param snapshotId the ID to check
     * @return true if the ID can be used as a file name inside the snapshots directory
     */
    public static boolean isValidSnapshotId(String snapshotId) {
        return snapshotId != null && SNAPSHOT_ID_PATTERN.matcher(snapshotId).matches();
    }

    /**
     * Gets the snapshots directory.
     * 
     * @return the snapshots directory file
     */
    public File getSnapshotsDirectory() {
        return snapshotsDirectory;
    }

    /**
     * Custom serializer for LocalDateTime.
     */
    private static class LocalDateTimeSerializer implements JsonSerializer<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.format(FORMATTER));
        }
    }

    /**
     * Custom deserializer for LocalDateTime.
     */
    private static class LocalDateTimeDeserializer implements JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return LocalDateTime.parse(json.getAsString(), FORMATTER);
        }
    }
}
