package org.example.ml;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.example.config.ConfigManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Handles persistence of trained LogisticRegressionModel to JSON files.
 * Supports saving and loading model state including weights, bias, and metadata.
 */
public class ModelPersistence {

    private static final String CURRENT_VERSION = "1.0";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    
    private final String defaultModelPath;
    
    /**
     * Creates a ModelPersistence using the configured model file path.
     */
    public ModelPersistence() {
        this.defaultModelPath = ConfigManager.getInstance().getPersistenceConfig().modelFilePath();
    }
    
    /**
     * Creates a ModelPersistence with a custom default path (for testing).
     * @param defaultPath the default path for model files
     */
    public ModelPersistence(String defaultPath) {
        this.defaultModelPath = defaultPath;
    }
    
    /**
     * Saves the model to the configured default path.
     * 
     * @param model the trained model to save
     * @throws IOException if writing fails
     * @throws IllegalArgumentException if model is null or not trained
     */
    public void saveModel(LogisticRegressionModel model) throws IOException {
        saveModel(model, defaultModelPath);
    }
    
    /**
     * Loads a model from the configured default path.
     * 
     * @return the loaded model, or null if file is corrupted or invalid
     */
    public LogisticRegressionModel loadModel() {
        return loadModel(defaultModelPath);
    }
    
    /**
     * Verifies integrity of the model file at the configured default path.
     * 
     * @return true if the file exists and contains a valid model
     */
    public boolean isModelValid() {
        return isModelValid(defaultModelPath);
    }
    
    /**
     * Gets the configured default model path.
     * 
     * @return the default model file path
     */
    public String getDefaultModelPath() {
        return defaultModelPath;
    }

    /**
     * Saves the model to a JSON file.
     * Includes version, weights, bias, and metadata.
     * 
     * @param model the trained model to save
     * @param path the file path to save to
     * @throws IOException if writing fails
     * @throws IllegalArgumentException if model is null or not trained
     */
    public void saveModel(LogisticRegressionModel model, String path) throws IOException {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }
        if (!model.isTrained()) {
            throw new IllegalArgumentException("Model must be trained before saving");
        }

        ModelState state = new ModelState();
        state.version = CURRENT_VERSION;
        state.featureCount = model.getFeatureCount();
        state.featureSchemaVersion = FeatureExtractor.FEATURE_COUNT;
        state.weights = model.getWeights();
        state.bias = model.getBias();
        state.trainedAt = Instant.now().toString();

        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(path), StandardCharsets.UTF_8)) {
            GSON.toJson(state, writer);
        }
    }


    /**
     * Loads a model from a JSON file.
     * Validates file integrity (version, weights count).
     * 
     * @param path the file path to load from
     * @return the loaded model, or null if file is corrupted or invalid
     */
    public LogisticRegressionModel loadModel(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        try (Reader reader = new InputStreamReader(
                new FileInputStream(path), StandardCharsets.UTF_8)) {
            ModelState state = GSON.fromJson(reader, ModelState.class);
            
            // Validate the loaded state
            if (!isStateValid(state)) {
                return null;
            }

            // Create and restore model
            LogisticRegressionModel model = new LogisticRegressionModel();
            model.setWeights(state.weights, state.bias);
            return model;

        } catch (IOException | JsonSyntaxException | IllegalArgumentException e) {
            // File doesn't exist, is corrupted, or has invalid data
            return null;
        }
    }

    /**
     * Verifies integrity of a model file.
     * 
     * @param path the file path to check
     * @return true if the file exists and contains a valid model
     */
    public boolean isModelValid(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        try (Reader reader = new InputStreamReader(
                new FileInputStream(path), StandardCharsets.UTF_8)) {
            ModelState state = GSON.fromJson(reader, ModelState.class);
            return isStateValid(state);
        } catch (IOException | JsonSyntaxException e) {
            return false;
        }
    }

    /**
     * Validates a ModelState object for integrity.
     * 
     * @param state the state to validate
     * @return true if the state is valid
     */
    private boolean isStateValid(ModelState state) {
        if (state == null) {
            return false;
        }
        if (state.version == null || state.version.isEmpty()) {
            return false;
        }
        if (state.weights == null || state.weights.length == 0) {
            return false;
        }
        if (state.featureCount != state.weights.length) {
            return false;
        }
        // Check for NaN or Infinite values in weights
        for (double weight : state.weights) {
            if (Double.isNaN(weight) || Double.isInfinite(weight)) {
                return false;
            }
        }
        return !Double.isNaN(state.bias) && !Double.isInfinite(state.bias);
    }

    /**
     * Internal class representing the persisted model state.
     * Fields are used for JSON serialization/deserialization.
     */
    @SuppressWarnings("unused")
    static class ModelState {
        String version;
        int featureCount;
        /** 
         * Feature schema version - corresponds to FeatureExtractor.FEATURE_COUNT at time of training.
         * Used for backward compatibility detection.
         * - 12 = legacy feature schema (LEGACY_FEATURE_COUNT)
         * - 24 = enhanced feature schema (FEATURE_COUNT)
         */
        int featureSchemaVersion;
        double[] weights;
        double bias;
        String trainedAt;
        Integer trainingExamples;
        Double accuracy;
    }
    
    /**
     * Gets the feature schema version from a model file.
     * Returns 0 if the file doesn't exist or is invalid.
     * Returns the actual featureSchemaVersion if present, otherwise falls back to featureCount.
     * 
     * @param path the file path to check
     * @return the feature schema version, or 0 if invalid
     */
    public int getFeatureSchemaVersion(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }

        try (Reader reader = new InputStreamReader(
                new FileInputStream(path), StandardCharsets.UTF_8)) {
            ModelState state = GSON.fromJson(reader, ModelState.class);
            if (state == null) {
                return 0;
            }
            // If featureSchemaVersion is set (non-zero), use it; otherwise fall back to featureCount
            return state.featureSchemaVersion > 0 ? state.featureSchemaVersion : state.featureCount;
        } catch (IOException | JsonSyntaxException e) {
            return 0;
        }
    }
    
    /**
     * Checks if a model file uses the legacy feature schema (12 features).
     * 
     * @param path the file path to check
     * @return true if the model uses legacy features
     */
    public boolean isLegacyModel(String path) {
        int schemaVersion = getFeatureSchemaVersion(path);
        return schemaVersion > 0 && schemaVersion == FeatureExtractor.LEGACY_FEATURE_COUNT;
    }
    
    /**
     * Checks if a model file uses the current feature schema.
     * 
     * @param path the file path to check
     * @return true if the model uses the current feature schema
     */
    public boolean isCurrentSchemaModel(String path) {
        int schemaVersion = getFeatureSchemaVersion(path);
        return schemaVersion == FeatureExtractor.FEATURE_COUNT;
    }
}
