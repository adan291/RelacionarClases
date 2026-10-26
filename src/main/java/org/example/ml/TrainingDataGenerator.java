package org.example.ml;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.config.ConfigManager;
import org.example.service.MemoryService;

/**
 * Converts user's historical decisions into training data for the ML model.
 * Reads from MemoryService and generates labeled training examples.
 * 
 * Enhanced to support 24-feature vectors with field-level information.
 * Implements Requirements 1.5: Enhanced training pipeline.
 */
public class TrainingDataGenerator {

    private static final Logger LOGGER = Logger.getLogger(TrainingDataGenerator.class.getName());
    
    // Pattern to parse relation strings: "Origen -> Destino : Tipo"
    private static final Pattern RELATION_PATTERN = 
        Pattern.compile("^(.+?)\\s*->\\s*(.+?)\\s*:\\s*(.+)$");
    
    // Pattern to parse relation strings with field info: "Origen.fieldName -> Destino : Tipo"
    private static final Pattern RELATION_WITH_FIELD_PATTERN = 
        Pattern.compile("^(.+?)\\.(.+?)\\s*->\\s*(.+?)\\s*:\\s*(.+)$");
    
    private final FeatureExtractor featureExtractor;
    private final ClassInfoParser classInfoParser;
    private final String trainingDataPath;
    private Map<String, ClassInfo> classInfoCache;
    
    /**
     * Creates a new TrainingDataGenerator with a default FeatureExtractor
     * and the configured training data path.
     */
    public TrainingDataGenerator() {
        this.featureExtractor = new FeatureExtractor();
        this.classInfoParser = new ClassInfoParser();
        this.trainingDataPath = ConfigManager.getInstance().getPersistenceConfig().trainingDataPath();
        this.classInfoCache = Collections.emptyMap();
    }
    
    /**
     * Creates a new TrainingDataGenerator with the specified FeatureExtractor
     * and the configured training data path.
     * @param featureExtractor the feature extractor to use
     */
    public TrainingDataGenerator(FeatureExtractor featureExtractor) {
        this.featureExtractor = featureExtractor != null ? featureExtractor : new FeatureExtractor();
        this.classInfoParser = new ClassInfoParser();
        this.trainingDataPath = ConfigManager.getInstance().getPersistenceConfig().trainingDataPath();
        this.classInfoCache = Collections.emptyMap();
    }
    
    /**
     * Creates a new TrainingDataGenerator with a custom training data path (for testing).
     * @param featureExtractor the feature extractor to use
     * @param trainingDataPath the path for training data files
     */
    public TrainingDataGenerator(FeatureExtractor featureExtractor, String trainingDataPath) {
        this.featureExtractor = featureExtractor != null ? featureExtractor : new FeatureExtractor();
        this.classInfoParser = new ClassInfoParser();
        this.trainingDataPath = trainingDataPath;
        this.classInfoCache = Collections.emptyMap();
    }
    
    /**
     * Creates a new TrainingDataGenerator with ClassInfo cache for enhanced features.
     * @param featureExtractor the feature extractor to use
     * @param trainingDataPath the path for training data files
     * @param classInfoCache pre-parsed class information map
     */
    public TrainingDataGenerator(FeatureExtractor featureExtractor, String trainingDataPath, 
                                  Map<String, ClassInfo> classInfoCache) {
        this.featureExtractor = featureExtractor != null ? featureExtractor : new FeatureExtractor();
        this.classInfoParser = new ClassInfoParser();
        this.trainingDataPath = trainingDataPath;
        this.classInfoCache = classInfoCache != null ? classInfoCache : Collections.emptyMap();
    }
    
    /**
     * Sets the class info cache for enhanced feature extraction.
     * @param classInfoCache map of class name to ClassInfo
     */
    public void setClassInfoCache(Map<String, ClassInfo> classInfoCache) {
        this.classInfoCache = classInfoCache != null ? classInfoCache : Collections.emptyMap();
    }
    
    /**
     * Loads class information from a source directory.
     * @param sourceDir the directory containing Java source files
     * @throws IOException if directory cannot be read
     */
    public void loadClassInfo(File sourceDir) throws IOException {
        this.classInfoCache = classInfoParser.parseDirectory(sourceDir);
        LOGGER.info(() -> "Loaded " + classInfoCache.size() + " class definitions for enhanced features");
    }
    
    /**
     * Gets the current class info cache.
     * @return the class info cache (unmodifiable)
     */
    public Map<String, ClassInfo> getClassInfoCache() {
        return Collections.unmodifiableMap(classInfoCache);
    }

    /**
     * Generates a training dataset from the memory service.
     * Each decision in memory is converted to a labeled training example.
     * 
     * @param memoryService the memory service containing user decisions
     * @return a TrainingDataset with features and labels
     */
    public TrainingDataset generateFromMemory(MemoryService memoryService) {
        if (memoryService == null) {
            return new TrainingDataset(featureExtractor.getFeatureCount());
        }
        
        Map<String, String> memoria = memoryService.getMemoria();
        TrainingDataset dataset = new TrainingDataset(featureExtractor.getFeatureCount());
        
        for (Map.Entry<String, String> entry : memoria.entrySet()) {
            String relationKey = entry.getKey();
            String decision = entry.getValue();
            
            try {
                TrainingExample example = createExampleFromRelation(relationKey, decision);
                if (example != null) {
                    dataset.addExample(example);
                }
            } catch (Exception e) {
                // Skip malformed entries and continue processing
                LOGGER.fine(() -> "Skipping malformed relation entry: " + relationKey);
            }
        }
        
        return dataset;
    }
    
    /**
     * Creates a training example from a relation string and decision.
     * Uses enhanced feature extraction with ClassInfo when available.
     * 
     * @param relationKey the relation key in format "Origen -> Destino : Tipo" or "Origen.field -> Destino : Tipo"
     * @param decision the decision ("aceptada" or "rechazada")
     * @return a TrainingExample or null if parsing fails
     */
    TrainingExample createExampleFromRelation(String relationKey, String decision) {
        if (relationKey == null || decision == null) {
            return null;
        }
        
        // Try to parse with field info first
        ParsedRelationWithField parsedWithField = parseRelationStringWithField(relationKey);
        if (parsedWithField != null) {
            return createExampleWithFieldInfo(parsedWithField, decision);
        }
        
        // Fall back to simple parsing
        ParsedRelation parsed = parseRelationString(relationKey);
        if (parsed == null) {
            return null;
        }
        
        // Look up ClassInfo from cache
        ClassInfo origenInfo = classInfoCache.get(parsed.origen);
        ClassInfo destinoInfo = classInfoCache.get(parsed.destino);
        
        // Extract field info if available
        String fieldName = null;
        String fieldType = null;
        if (origenInfo != null) {
            FieldInfo relationField = findRelationField(origenInfo, parsed.destino);
            if (relationField != null) {
                fieldName = relationField.name();
                fieldType = relationField.type();
            }
        }
        
        // Extract enhanced features with ClassInfo and field-level information
        double[] features = featureExtractor.extractFeatures(
            parsed.origen, 
            parsed.destino, 
            parsed.tipo,
            origenInfo,
            destinoInfo,
            fieldName,
            fieldType
        );
        
        // Assign label based on decision
        double label = assignLabel(decision);
        
        return new TrainingExample(features, label);
    }
    
    /**
     * Creates a training example with explicit field information.
     */
    private TrainingExample createExampleWithFieldInfo(ParsedRelationWithField parsed, String decision) {
        ClassInfo origenInfo = classInfoCache.get(parsed.origen);
        ClassInfo destinoInfo = classInfoCache.get(parsed.destino);
        
        // Get field type from ClassInfo if available
        String fieldType = null;
        if (origenInfo != null) {
            fieldType = origenInfo.getFields().stream()
                .filter(f -> f.name().equals(parsed.fieldName))
                .map(FieldInfo::type)
                .findFirst()
                .orElse(null);
        }
        
        double[] features = featureExtractor.extractFeatures(
            parsed.origen,
            parsed.destino,
            parsed.tipo,
            origenInfo,
            destinoInfo,
            parsed.fieldName,
            fieldType
        );
        
        double label = assignLabel(decision);
        return new TrainingExample(features, label);
    }
    
    /**
     * Finds a field in the source class that references the target class.
     */
    private FieldInfo findRelationField(ClassInfo sourceInfo, String targetClassName) {
        if (sourceInfo == null || targetClassName == null) {
            return null;
        }
        
        // Look for a field whose type or generic type matches the target
        return sourceInfo.getFields().stream()
            .filter(f -> {
                String type = f.type();
                String genericType = f.genericType();
                // Mismo criterio que MLRelationPredictor: identificador completo, no subcadena
                return TypeNames.references(type, targetClassName) ||
                       (genericType != null && genericType.equals(targetClassName));
            })
            .findFirst()
            .orElse(null);
    }

    /**
     * Parses a relation string into its components.
     * Expected format: "Origen -> Destino : Tipo"
     * 
     * @param relationString the relation string to parse
     * @return ParsedRelation or null if parsing fails
     */
    ParsedRelation parseRelationString(String relationString) {
        if (relationString == null || relationString.isBlank()) {
            return null;
        }
        
        Matcher matcher = RELATION_PATTERN.matcher(relationString.trim());
        if (!matcher.matches()) {
            return null;
        }
        
        String origen = matcher.group(1).trim();
        String destino = matcher.group(2).trim();
        String tipo = matcher.group(3).trim();
        
        if (origen.isEmpty() || destino.isEmpty() || tipo.isEmpty()) {
            return null;
        }
        
        return new ParsedRelation(origen, destino, tipo);
    }
    
    /**
     * Assigns a label based on the decision string.
     * "aceptada" -> 1.0
     * "rechazada" -> 0.0
     * 
     * @param decision the decision string
     * @return 1.0 for accepted, 0.0 for rejected
     */
    double assignLabel(String decision) {
        if (decision == null) {
            return 0.0;
        }
        return "aceptada".equalsIgnoreCase(decision.trim()) ? 1.0 : 0.0;
    }
    
    /**
     * Saves the dataset to the configured training data file.
     * 
     * @param dataset the dataset to save
     * @throws IOException if writing fails
     */
    public void saveDataset(TrainingDataset dataset) throws IOException {
        saveDataset(dataset, trainingDataPath);
    }
    
    /**
     * Saves the dataset to the specified path.
     * 
     * @param dataset the dataset to save
     * @param path the file path
     * @throws IOException if writing fails
     */
    public void saveDataset(TrainingDataset dataset, String path) throws IOException {
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset cannot be null");
        }
        dataset.saveToFile(path);
    }
    
    /**
     * Loads a dataset from the configured training data file.
     * 
     * @return the loaded dataset or null if file doesn't exist
     */
    public TrainingDataset loadDataset() {
        return loadDataset(trainingDataPath);
    }
    
    /**
     * Gets the configured training data path.
     * 
     * @return the training data file path
     */
    public String getTrainingDataPath() {
        return trainingDataPath;
    }
    
    /**
     * Gets the feature count used by this generator.
     * Returns 24 for enhanced features.
     * 
     * @return the number of features per example
     */
    public int getFeatureCount() {
        return featureExtractor.getFeatureCount();
    }
    
    /**
     * Checks if enhanced features are available (ClassInfo cache is populated).
     * 
     * @return true if ClassInfo cache has entries
     */
    public boolean hasEnhancedFeatures() {
        return !classInfoCache.isEmpty();
    }
    
    /**
     * Loads a dataset from the specified path.
     * 
     * @param path the file path
     * @return the loaded dataset or null if loading fails
     */
    public TrainingDataset loadDataset(String path) {
        try {
            return TrainingDataset.loadFromFile(path);
        } catch (IOException e) {
            LOGGER.warning(() -> "Could not load training data from: " + path);
            return null;
        }
    }
    
    /**
     * Checks if a dataset needs migration to the new feature format.
     * Old datasets have 12 features, new datasets have 24 features.
     * 
     * @param dataset the dataset to check
     * @return true if the dataset needs migration
     */
    public boolean needsMigration(TrainingDataset dataset) {
        if (dataset == null || dataset.size() == 0) {
            return false;
        }
        return dataset.getFeatureCount() < FeatureExtractor.FEATURE_COUNT;
    }
    
    /**
     * Detects if a training data file uses the old format (12 features).
     * 
     * @param path the file path to check
     * @return true if the file exists and uses old format, false otherwise
     */
    public boolean isOldFormat(String path) {
        TrainingDataset dataset = loadDataset(path);
        return needsMigration(dataset);
    }
    
    /**
     * Migrates a dataset from old format (12 features) to new format (24 features).
     * Pads missing features with 0.5 (neutral value) for backward compatibility.
     * 
     * @param oldDataset the dataset to migrate
     * @return a new dataset with 24 features per example
     */
    public TrainingDataset migrateDataset(TrainingDataset oldDataset) {
        if (oldDataset == null) {
            return new TrainingDataset(FeatureExtractor.FEATURE_COUNT);
        }
        
        int oldFeatureCount = oldDataset.getFeatureCount();
        int newFeatureCount = FeatureExtractor.FEATURE_COUNT;
        
        if (oldFeatureCount >= newFeatureCount) {
            LOGGER.info(() -> "Dataset already has " + oldFeatureCount + " features, no migration needed");
            return oldDataset;
        }
        
        LOGGER.info(() -> "Migrating dataset from " + oldFeatureCount + " to " + newFeatureCount + " features");
        
        TrainingDataset newDataset = new TrainingDataset(newFeatureCount);
        
        for (TrainingExample oldExample : oldDataset.getExamples()) {
            double[] oldFeatures = oldExample.getFeatures();
            double[] newFeatures = new double[newFeatureCount];
            
            // Copy existing features
            System.arraycopy(oldFeatures, 0, newFeatures, 0, 
                Math.min(oldFeatures.length, newFeatureCount));
            
            // Pad new features with 0.5 (neutral value)
            for (int i = oldFeatures.length; i < newFeatureCount; i++) {
                newFeatures[i] = 0.5;
            }
            
            newDataset.addExample(new TrainingExample(newFeatures, oldExample.getLabel()));
        }
        
        LOGGER.info(() -> "Migration complete: " + newDataset.size() + " examples migrated");
        return newDataset;
    }
    
    /**
     * Loads and migrates a dataset if needed.
     * If the dataset uses old format, it will be migrated to new format.
     * 
     * @param path the file path
     * @return the loaded (and possibly migrated) dataset, or null if loading fails
     */
    public TrainingDataset loadAndMigrateDataset(String path) {
        TrainingDataset dataset = loadDataset(path);
        if (dataset == null) {
            return null;
        }
        
        if (needsMigration(dataset)) {
            LOGGER.warning(() -> "Training data at " + path + " uses old format (" + 
                dataset.getFeatureCount() + " features). Migrating to " + 
                FeatureExtractor.FEATURE_COUNT + " features.");
            return migrateDataset(dataset);
        }
        
        return dataset;
    }
    
    /**
     * Regenerates training data from memory service with new features.
     * This is useful when the feature extraction has changed and we need
     * to regenerate all training examples with the new feature set.
     * 
     * @param memoryService the memory service containing user decisions
     * @param sourceDir the directory containing Java source files for ClassInfo
     * @return a new TrainingDataset with enhanced features
     * @throws IOException if source directory cannot be read
     */
    public TrainingDataset regenerateWithEnhancedFeatures(MemoryService memoryService, 
                                                           File sourceDir) throws IOException {
        // Load class information for enhanced features
        loadClassInfo(sourceDir);
        
        LOGGER.info(() -> "Regenerating training data with " + classInfoCache.size() + 
            " class definitions for enhanced features");
        
        // Generate new dataset with enhanced features
        return generateFromMemory(memoryService);
    }
    
    /**
     * Parses a relation string with field information.
     * Expected format: "Origen.fieldName -> Destino : Tipo"
     * 
     * @param relationString the relation string to parse
     * @return ParsedRelationWithField or null if parsing fails
     */
    ParsedRelationWithField parseRelationStringWithField(String relationString) {
        if (relationString == null || relationString.isBlank()) {
            return null;
        }
        
        Matcher matcher = RELATION_WITH_FIELD_PATTERN.matcher(relationString.trim());
        if (!matcher.matches()) {
            return null;
        }
        
        String origen = matcher.group(1).trim();
        String fieldName = matcher.group(2).trim();
        String destino = matcher.group(3).trim();
        String tipo = matcher.group(4).trim();
        
        if (origen.isEmpty() || fieldName.isEmpty() || destino.isEmpty() || tipo.isEmpty()) {
            return null;
        }
        
        return new ParsedRelationWithField(origen, fieldName, destino, tipo);
    }
    
    /**
     * Internal class to hold parsed relation components.
     */
    static class ParsedRelation {
        final String origen;
        final String destino;
        final String tipo;
        
        ParsedRelation(String origen, String destino, String tipo) {
            this.origen = origen;
            this.destino = destino;
            this.tipo = tipo;
        }
    }
    
    /**
     * Internal class to hold parsed relation components with field information.
     */
    static class ParsedRelationWithField {
        final String origen;
        final String fieldName;
        final String destino;
        final String tipo;
        
        ParsedRelationWithField(String origen, String fieldName, String destino, String tipo) {
            this.origen = origen;
            this.fieldName = fieldName;
            this.destino = destino;
            this.tipo = tipo;
        }
    }
}
