package org.example;

import java.util.logging.Logger;

import org.example.config.ConfigManager;
import org.example.logging.LoggingConfig;
import org.example.ui.RelationAnalyzerAppView;

import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {
    
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    @Override
    public void start(Stage stage) {
        LOGGER.info("Starting Intelligent Relation Analyzer");
        new RelationAnalyzerAppView(stage).show();
    }
    
    @Override
    public void stop() {
        LOGGER.info("Shutting down application");
        LoggingConfig.shutdown();
    }

    public static void main(String[] args) {
        // Initialize logging system before anything else
        LoggingConfig.initialize();
        
        // Load application configuration
        ConfigManager.getInstance().load();
        Logger.getLogger(App.class.getName()).info("Configuration loaded");
        
        // Add shutdown hook for clean logging closure
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LoggingConfig.shutdown();
        }));
        
        launch();
    }
}
