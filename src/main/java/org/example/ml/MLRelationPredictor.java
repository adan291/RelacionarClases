package org.example.ml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.config.ConfigManager;
import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;

/**
 * Orchestrates ML-based relation prediction by integrating with the existing system.
 * Uses MemoryService for training data, FeatureExtractor for feature generation,
 * and LogisticRegressionModel for predictions.
 * 
 * Implements Requirements 4.1: Combine ML predictions with structural and semantic analysis.
 */
public class MLRelationPredictor {

    private static final Logger LOGGER = Logger.getLogger(MLRelationPredictor.class.getName());
    private static final String DEFAULT_MODEL_PATH = "ml_model.json";
    
    private final int minimumDecisionsThreshold;
    
    private final MemoryService memoryService;
    private final FeatureExtractor featureExtractor;
    private final TrainingDataGenerator trainingDataGenerator;
    private final ModelPersistence modelPersistence;
    private LogisticRegressionModel model;
    private final String modelPath;

    /**
     * Creates a new MLRelationPredictor with the specified dependencies.
     * 
     * @param memoryService service for accessing user decisions
     */
    public MLRelationPredictor(MemoryService memoryService) {
        this(memoryService, new FeatureExtractor(), new LogisticRegressionModel(), DEFAULT_MODEL_PATH,
             ConfigManager.getInstance().getMLConfig().minDecisions());
    }

    /**
     * Creates a new MLRelationPredictor with full dependency injection.
     * 
     * @param memoryService service for accessing user decisions
     * @param featureExtractor extractor for generating feature vectors
     * @param model the logistic regression model
     * @param modelPath path for model persistence
     */
    public MLRelationPredictor(MemoryService memoryService, FeatureExtractor featureExtractor,
                               LogisticRegressionModel model, String modelPath) {
        this(memoryService, featureExtractor, model, modelPath,
             ConfigManager.getInstance().getMLConfig().minDecisions());
    }
    
    /**
     * Creates a new MLRelationPredictor with full dependency injection including threshold.
     */
    public MLRelationPredictor(MemoryService memoryService, FeatureExtractor featureExtractor,
                               LogisticRegressionModel model, String modelPath, int minDecisions) {
        this.memoryService = memoryService;
        this.featureExtractor = featureExtractor != null ? featureExtractor : new FeatureExtractor();
        this.model = model != null ? model : new LogisticRegressionModel();
        this.modelPath = modelPath != null ? modelPath : DEFAULT_MODEL_PATH;
        this.minimumDecisionsThreshold = minDecisions;
        this.trainingDataGenerator = new TrainingDataGenerator(this.featureExtractor);
        this.modelPersistence = new ModelPersistence();
        
        // Try to load existing model
        loadModelIfExists();
    }


    /**
     * Predicts relations using the ML model.
     * 
     * @param classNames list of class names to analyze
     * @param classInfo structural information about each class (keyed by class name)
     * @return list of detected relations with ML confidence scores
     */
    public List<RelacionDetectada> predictRelations(List<String> classNames, 
                                                     Map<String, ClassInfo> classInfo) {
        List<RelacionDetectada> predictions = new ArrayList<>();
        
        if (classNames == null || classNames.size() < 2) {
            return predictions;
        }
        
        // Ensure model is available
        if (!isMLAvailable()) {
            LOGGER.info("ML not available (insufficient training data). Using heuristics.");
            return predictions;
        }
        
        // Ensure model is trained
        if (!model.isTrained()) {
            retrain();
            if (!model.isTrained()) {
                return predictions;
            }
        }
        
        // Generate predictions for all class pairs
        String[] relationTypes = {"OneToOne", "OneToMany", "ManyToOne", "ManyToMany"};
        
        for (int i = 0; i < classNames.size(); i++) {
            for (int j = 0; j < classNames.size(); j++) {
                if (i == j) continue;
                
                String origen = classNames.get(i);
                String destino = classNames.get(j);
                
                ClassInfo origenInfo = classInfo != null ? classInfo.get(origen) : null;
                ClassInfo destinoInfo = classInfo != null ? classInfo.get(destino) : null;
                
                // Find best relation type for this pair
                RelacionDetectada bestPrediction = null;
                double bestConfidence = 0.0;
                
                for (String tipo : relationTypes) {
                    // Try to find a field in origen that references destino for enhanced features
                    String fieldName = null;
                    String fieldType = null;
                    
                    if (origenInfo != null) {
                        for (FieldInfo field : origenInfo.getFields()) {
                            String genericType = field.genericType();
                            String baseType = field.type();
                            
                            // Check if field references the destination class
                            if (destino.equals(genericType) || 
                                baseType.contains(destino) ||
                                baseType.equals(destino)) {
                                fieldName = field.name();
                                fieldType = field.type();
                                break;
                            }
                        }
                    }
                    
                    // Extract features with field-level information when available
                    double[] features = featureExtractor.extractFeatures(
                        origen, destino, tipo, origenInfo, destinoInfo, fieldName, fieldType);
                    
                    try {
                        double confidence = model.predict(features);
                        if (confidence > bestConfidence) {
                            bestConfidence = confidence;
                            bestPrediction = new RelacionDetectada(origen, destino, tipo, confidence);
                        }
                    } catch (Exception e) {
                        // Skip this prediction if it fails
                        LOGGER.log(Level.WARNING, "Prediction failed for " + origen + " -> " + destino + ": " + e.getMessage());
                    }
                }
                
                // Only add predictions with reasonable confidence
                if (bestPrediction != null && bestConfidence > 0.3) {
                    predictions.add(bestPrediction);
                }
            }
        }
        
        return predictions;
    }

    /**
     * Checks if ML prediction is available.
     * Returns false if memory has fewer than 5 decisions.
     * Triggers model training if needed.
     * 
     * @return true if ML can be used for predictions
     */
    public boolean isMLAvailable() {
        if (memoryService == null) {
            return false;
        }
        
        int decisionCount = memoryService.getMemoria().size();
        
        if (decisionCount < minimumDecisionsThreshold) {
            return false;
        }
        
        // If we have enough data but model isn't trained, try to train it
        if (!model.isTrained()) {
            retrain();
        }
        
        return model.isTrained();
    }

    /**
     * Returns the minimum number of decisions required for ML to be available.
     * 
     * @return the minimum threshold (5)
     */
    public int getMinimumDecisionsThreshold() {
        return minimumDecisionsThreshold;
    }

    /**
     * Gets the current number of decisions in memory.
     * 
     * @return number of decisions
     */
    public int getDecisionCount() {
        return memoryService != null ? memoryService.getMemoria().size() : 0;
    }


    /**
     * Forces re-training of the model from current memory data.
     * Generates training data from memory, trains the model, and persists it.
     * 
     * Implements Requirements 3.2: Persist model weights to local file for reuse.
     */
    public void retrain() {
        if (memoryService == null) {
            LOGGER.severe("Cannot retrain: MemoryService is null");
            return;
        }
        
        int decisionCount = memoryService.getMemoria().size();
        if (decisionCount < minimumDecisionsThreshold) {
            LOGGER.info("Not enough training data (" + decisionCount + 
                " decisions, need " + minimumDecisionsThreshold + ")");
            return;
        }
        
        try {
            // Generate training data from memory
            TrainingDataset dataset = trainingDataGenerator.generateFromMemory(memoryService);
            
            if (dataset == null || dataset.size() == 0) {
                LOGGER.severe("Failed to generate training data");
                return;
            }
            
            LOGGER.info("Training ML model with " + dataset.size() + " examples...");
            
            // Train the model
            model.train(dataset, 0.1, 1000);
            
            // Persist the trained model
            try {
                modelPersistence.saveModel(model, modelPath);
                LOGGER.info("Model trained and saved to " + modelPath);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Model trained but failed to save: " + e.getMessage(), e);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Training failed: " + e.getMessage(), e);
        }
    }

    /**
     * Attempts to load an existing model from disk.
     */
    private void loadModelIfExists() {
        LogisticRegressionModel loadedModel = modelPersistence.loadModel(modelPath);
        if (loadedModel != null && loadedModel.isTrained()) {
            this.model = loadedModel;
            LOGGER.info("Loaded existing ML model from " + modelPath);
        }
    }

    /**
     * Gets the underlying model (for testing purposes).
     * 
     * @return the logistic regression model
     */
    public LogisticRegressionModel getModel() {
        return model;
    }

    /**
     * Predicts the confidence for a specific relation.
     * 
     * @param origen origin class name
     * @param destino destination class name
     * @param tipo relation type
     * @param origenInfo structural info for origin class (can be null)
     * @param destinoInfo structural info for destination class (can be null)
     * @return confidence score [0, 1] or -1 if ML is not available
     */
    public double predictConfidence(String origen, String destino, String tipo,
                                    ClassInfo origenInfo, ClassInfo destinoInfo) {
        if (!isMLAvailable() || !model.isTrained()) {
            return -1.0;
        }
        
        try {
            double[] features = featureExtractor.extractFeatures(origen, destino, tipo, origenInfo, destinoInfo);
            return model.predict(features);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Prediction failed: " + e.getMessage());
            return -1.0;
        }
    }
}
