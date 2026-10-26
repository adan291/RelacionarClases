package org.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized configuration manager singleton.
 * Loads configuration from properties file with type-safe accessors.
 */
public class ConfigManager {
    
    private static final Logger logger = Logger.getLogger(ConfigManager.class.getName());
    private static final String CONFIG_FILE = "config.properties";
    
    private static ConfigManager instance;
    private final Properties properties = new Properties();
    private boolean loaded = false;
    
    // Cached config objects
    private MLConfig mlConfig;
    private AnalysisConfig analysisConfig;
    private UIConfig uiConfig;
    private PersistenceConfig persistenceConfig;
    
    private ConfigManager() {}
    
    /**
     * Gets the singleton instance.
     * @return the ConfigManager instance
     */
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }
    
    /**
     * Loads configuration from the properties file.
     * Falls back to defaults if file is missing.
     */
    public void load() {
        if (loaded) {
            return;
        }
        
        Path configPath = Paths.get(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                properties.load(is);
                logger.info("Configuration loaded from " + CONFIG_FILE);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load config file, using defaults", e);
            }
        } else {
            logger.info("Config file not found, using default values");
        }
        
        // Build config objects
        buildConfigObjects();
        loaded = true;
    }

    
    private void buildConfigObjects() {
        mlConfig = new MLConfig(
            getInt("ml.minDecisions", MLConfig.DEFAULT.minDecisions()),
            getDouble("ml.weight.ml", MLConfig.DEFAULT.mlWeight()),
            getDouble("ml.weight.structural", MLConfig.DEFAULT.structuralWeight()),
            getDouble("ml.weight.semantic", MLConfig.DEFAULT.semanticWeight()),
            getDouble("ml.learningRate", MLConfig.DEFAULT.learningRate()),
            getInt("ml.maxIterations", MLConfig.DEFAULT.maxIterations())
        );
        
        analysisConfig = new AnalysisConfig(
            getDouble("analysis.defaultConfidenceThreshold", AnalysisConfig.DEFAULT.defaultConfidenceThreshold()),
            getDouble("analysis.highConfidenceThreshold", AnalysisConfig.DEFAULT.highConfidenceThreshold()),
            getList("analysis.ignoredPaths", AnalysisConfig.DEFAULT.ignoredPaths()),
            getList("analysis.fileExtensions", AnalysisConfig.DEFAULT.fileExtensions())
        );
        
        uiConfig = new UIConfig(
            getInt("ui.bulkActionConfirmationThreshold", UIConfig.DEFAULT.bulkActionConfirmationThreshold()),
            getInt("ui.windowWidth", UIConfig.DEFAULT.windowWidth()),
            getInt("ui.windowHeight", UIConfig.DEFAULT.windowHeight()),
            getString("ui.defaultTheme", UIConfig.DEFAULT.defaultTheme())
        );
        
        persistenceConfig = new PersistenceConfig(
            getString("persistence.memoryFilePath", PersistenceConfig.DEFAULT.memoryFilePath()),
            getString("persistence.snapshotsDirectory", PersistenceConfig.DEFAULT.snapshotsDirectory()),
            getString("persistence.modelFilePath", PersistenceConfig.DEFAULT.modelFilePath()),
            getString("persistence.trainingDataPath", PersistenceConfig.DEFAULT.trainingDataPath())
        );
    }
    
    // === Type-safe getters ===
    
    /**
     * Gets a string value from configuration.
     * @param key the configuration key
     * @param defaultValue the default value if key is missing
     * @return the configured value or default
     */
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * Gets an integer value from configuration.
     * @param key the configuration key
     * @param defaultValue the default value if key is missing or invalid
     * @return the configured value or default
     */
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warning(() -> "Invalid integer for key " + key + ": " + value);
            return defaultValue;
        }
    }
    
    /**
     * Gets a double value from configuration.
     * @param key the configuration key
     * @param defaultValue the default value if key is missing or invalid
     * @return the configured value or default
     */
    public double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            logger.warning(() -> "Invalid double for key " + key + ": " + value);
            return defaultValue;
        }
    }
    
    /**
     * Gets a boolean value from configuration.
     * @param key the configuration key
     * @param defaultValue the default value if key is missing
     * @return the configured value or default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
    
    /**
     * Gets a list of strings from configuration (comma-separated).
     * @param key the configuration key
     * @param defaultValue the default value if key is missing
     * @return the configured list or default
     */
    public List<String> getList(String key, List<String> defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
    
    // === Config object getters ===
    
    public MLConfig getMLConfig() {
        ensureLoaded();
        return mlConfig;
    }
    
    public AnalysisConfig getAnalysisConfig() {
        ensureLoaded();
        return analysisConfig;
    }
    
    public UIConfig getUIConfig() {
        ensureLoaded();
        return uiConfig;
    }
    
    public PersistenceConfig getPersistenceConfig() {
        ensureLoaded();
        return persistenceConfig;
    }
    
    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }
    
    /**
     * Resets the singleton for testing purposes.
     */
    public static synchronized void reset() {
        instance = null;
    }
}
