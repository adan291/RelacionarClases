package org.example.logging;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Centralized logging configuration manager.
 * Initializes logging with console and file handlers using custom formatting.
 */
public class LoggingConfig {
    
    private static final String CONFIG_FILE = "logging.properties";
    private static final String LOGS_DIR = "logs";
    private static final String LOG_FILE_PATTERN = "logs/app_%g.log";
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_FILE_COUNT = 10;
    
    private static boolean initialized = false;
    private static FileHandler fileHandler;
    
    private LoggingConfig() {
        // Private constructor for utility class
    }
    
    /**
     * Initialize the logging system.
     * Creates logs directory, configures handlers, and applies formatting.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            // Create logs directory if it doesn't exist
            Path logsPath = Paths.get(LOGS_DIR);
            if (!Files.exists(logsPath)) {
                Files.createDirectories(logsPath);
            }
            
            // Try to load configuration from file first
            if (!loadConfigFromFile()) {
                // Fall back to programmatic configuration
                configureLoggingProgrammatically();
            }
            
            initialized = true;
            
            Logger logger = Logger.getLogger(LoggingConfig.class.getName());
            logger.info("Logging system initialized");
            
        } catch (Exception e) {
            // Note: Using System.err here is intentional since logging is not yet available
            System.err.println("[FATAL] Failed to initialize logging system: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
    
    private static boolean loadConfigFromFile() {
        Path configPath = Paths.get(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                LogManager.getLogManager().readConfiguration(is);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }
    
    private static void configureLoggingProgrammatically() throws IOException {
        // Get root logger
        Logger rootLogger = Logger.getLogger("");
        
        // Remove default handlers
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
        
        // Create custom formatter
        AppFormatter formatter = new AppFormatter();
        
        // Console handler
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(formatter);
        rootLogger.addHandler(consoleHandler);
        
        // File handler with rotation
        fileHandler = new FileHandler(LOG_FILE_PATTERN, MAX_FILE_SIZE, MAX_FILE_COUNT, true);
        fileHandler.setLevel(Level.ALL);
        fileHandler.setFormatter(formatter);
        rootLogger.addHandler(fileHandler);
        
        // Set root logger level
        rootLogger.setLevel(Level.INFO);
        
        // Set package-specific levels
        Logger.getLogger("org.example").setLevel(Level.INFO);
    }
    
    /**
     * Set the logging level for a specific logger.
     * @param loggerName The name of the logger (e.g., "org.example.service")
     * @param level The desired logging level
     */
    public static void setLevel(String loggerName, Level level) {
        Logger logger = Logger.getLogger(loggerName);
        logger.setLevel(level);
    }
    
    /**
     * Set the global logging level.
     * @param level The desired logging level
     */
    public static void setGlobalLevel(Level level) {
        Logger.getLogger("").setLevel(level);
    }
    
    /**
     * Shutdown the logging system cleanly.
     * Closes file handlers to ensure all logs are flushed.
     */
    public static synchronized void shutdown() {
        if (fileHandler != null) {
            fileHandler.close();
            fileHandler = null;
        }
        initialized = false;
    }
}
