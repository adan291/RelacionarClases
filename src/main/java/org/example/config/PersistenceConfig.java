package org.example.config;

/**
 * Configuration record for persistence-related settings.
 * 
 * @param memoryFilePath Path to the memory/decisions JSON file
 * @param snapshotsDirectory Directory for storing analysis snapshots
 * @param modelFilePath Path to the trained ML model file
 * @param trainingDataPath Path to training data directory
 */
public record PersistenceConfig(
    String memoryFilePath,
    String snapshotsDirectory,
    String modelFilePath,
    String trainingDataPath
) {
    /** Default configuration values */
    public static final PersistenceConfig DEFAULT = new PersistenceConfig(
        "memoria_relaciones.json",  // memoryFilePath
        "snapshots",                // snapshotsDirectory
        "model.json",               // modelFilePath
        "training_data"             // trainingDataPath
    );
}
