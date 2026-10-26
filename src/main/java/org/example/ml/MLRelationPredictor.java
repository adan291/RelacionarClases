package org.example.ml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.example.config.ConfigManager;
import org.example.config.PersistenceConfig;
import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;

/**
 * Orchestrates ML-based relation prediction by integrating with the existing system.
 * Uses MemoryService for training data, FeatureExtractor for feature generation,
 * and LogisticRegressionModel for predictions.
 *
 * <p>Concurrencia: el modelo activo se publica en un {@link AtomicReference}. Reentrenar crea
 * un modelo nuevo, lo entrena aparte y lo sustituye de una vez, así que las predicciones en curso
 * siguen usando el modelo anterior completo. Los entrenamientos se serializan entre sí.</p>
 *
 * Implements Requirements 4.1: Combine ML predictions with structural and semantic analysis.
 */
public class MLRelationPredictor {

    private static final Logger LOGGER = Logger.getLogger(MLRelationPredictor.class.getName());
    private static final String DEFAULT_MODEL_PATH = "ml_model.json";
    private static final double LEARNING_RATE = 0.1;
    private static final int ITERATIONS = 1000;
    
    private final int minimumDecisionsThreshold;
    
    private final MemoryService memoryService;
    private final FeatureExtractor featureExtractor;
    private final TrainingDataGenerator trainingDataGenerator;
    private final ModelPersistence modelPersistence;
    private final AtomicReference<LogisticRegressionModel> model;
    /** Fábrica de modelos nuevos para cada reentrenamiento (conserva la semilla del modelo inicial). */
    private final Supplier<LogisticRegressionModel> modelFactory;
    /** Ruta del modelo en disco; {@code null} significa que no se persiste. */
    private final String modelPath;
    private final Object trainingLock = new Object();

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
     * Un {@code modelPath} nulo usa la ruta por defecto ({@value #DEFAULT_MODEL_PATH}).
     */
    public MLRelationPredictor(MemoryService memoryService, FeatureExtractor featureExtractor,
                               LogisticRegressionModel model, String modelPath, int minDecisions) {
        this(memoryService, featureExtractor, model,
             modelPath != null ? modelPath : DEFAULT_MODEL_PATH, minDecisions, true);
    }

    private MLRelationPredictor(MemoryService memoryService, FeatureExtractor featureExtractor,
                                LogisticRegressionModel model, String modelPath, int minDecisions,
                                boolean loadExisting) {
        this.memoryService = memoryService;
        this.featureExtractor = featureExtractor != null ? featureExtractor : new FeatureExtractor();
        LogisticRegressionModel initial = model != null ? model : new LogisticRegressionModel();
        this.model = new AtomicReference<>(initial);
        this.modelFactory = seededFactory(initial);
        this.modelPath = modelPath;
        this.minimumDecisionsThreshold = minDecisions;
        this.trainingDataGenerator = new TrainingDataGenerator(
            this.featureExtractor, PersistenceConfig.DEFAULT.trainingDataPath());
        this.modelPersistence = new ModelPersistence(modelPath != null ? modelPath : DEFAULT_MODEL_PATH);
        
        if (loadExisting && modelPath != null) {
            loadModelIfExists();
        }
    }

    /**
     * Crea un predictor que no lee ni escribe el modelo en disco (tests, benchmarks).
     */
    public static MLRelationPredictor inMemory(MemoryService memoryService, FeatureExtractor featureExtractor,
                                               LogisticRegressionModel model, int minDecisions) {
        return new MLRelationPredictor(memoryService, featureExtractor, model, null, minDecisions, false);
    }

    /**
     * Si el modelo inicial no está entrenado, los reentrenamientos usan una semilla derivada de él
     * para que dos ejecuciones con el mismo modelo inicial den los mismos pesos.
     */
    private static Supplier<LogisticRegressionModel> seededFactory(LogisticRegressionModel initial) {
        return initial::newUntrainedCopy;
    }


    /**
     * Predicts relations using the ML model.
     * Si hay datos suficientes y todavía no hay modelo entrenado, lo entrena una vez.
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
            LOGGER.fine("ML not available (insufficient training data). Using heuristics.");
            return predictions;
        }
        
        LogisticRegressionModel current = ensureTrained();
        if (current == null) {
            return predictions;
        }
        
        // Generate predictions for all class pairs
        int n = classNames.size();
        ClassInfo[] infos = new ClassInfo[n];
        for (int i = 0; i < n; i++) {
            infos[i] = classInfo != null ? classInfo.get(classNames.get(i)) : null;
        }
        
        for (int i = 0; i < n; i++) {
            String origen = classNames.get(i);
            ClassInfo origenInfo = infos[i];
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                
                String destino = classNames.get(j);
                ClassInfo destinoInfo = infos[j];
                
                // El campo que referencia al destino no depende del tipo de relación:
                // se busca una vez por par (antes se repetía para cada uno de los 4 tipos)
                FieldInfo field = findFieldReferencing(origenInfo, destino);
                String fieldName = field != null ? field.name() : null;
                String fieldType = field != null ? field.type() : null;
                
                // Solo las features 22 y 23 dependen del tipo: el vector se calcula una vez por par
                double[] features = featureExtractor.extractFeatures(
                    origen, destino, RELATION_TYPES[0], origenInfo, destinoInfo, fieldName, fieldType);
                
                PairScore best = bestType(current, origen, destino, features);
                
                // Only add predictions with reasonable confidence
                if (best != null && best.confidence() > 0.3) {
                    predictions.add(new RelacionDetectada(origen, destino,
                        RELATION_TYPES[best.typeIndex()], best.confidence()));
                }
            }
        }
        
        return predictions;
    }

    private static final String[] RELATION_TYPES = {"OneToOne", "OneToMany", "ManyToOne", "ManyToMany"};

    /**
     * Prueba los cuatro tipos sobre el mismo vector de features y devuelve el de mayor confianza
     * (en empate gana el primero, como antes).
     */
    private PairScore bestType(LogisticRegressionModel current, String origen, String destino,
                               double[] features) {
        PairScore best = null;
        for (int t = 0; t < RELATION_TYPES.length; t++) {
            FeatureExtractor.applyRelationType(features, RELATION_TYPES[t]);
            try {
                double confidence = current.predict(features);
                if (confidence > (best != null ? best.confidence() : 0.0)) {
                    best = new PairScore(t, confidence);
                }
            } catch (Exception e) {
                // Skip this prediction if it fails
                LOGGER.log(Level.WARNING, "Prediction failed for " + origen + " -> " + destino + ": " + e.getMessage());
            }
        }
        return best;
    }

    /**
     * Busca en el origen el primer campo que referencia al destino.
     */
    static FieldInfo findFieldReferencing(ClassInfo origenInfo, String destino) {
        if (origenInfo == null) {
            return null;
        }
        for (FieldInfo field : origenInfo.getFields()) {
            String genericType = field.genericType();
            String baseType = field.type();
            
            // Check if field references the destination class
            if (destino.equals(genericType) || 
                baseType.contains(destino) ||
                baseType.equals(destino)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Indica si hay decisiones suficientes en memoria para usar ML.
     * No entrena ni escribe nada: para eso están {@link #ensureTrained()} y {@link #retrain()}.
     * 
     * @return true si la memoria tiene al menos el mínimo de decisiones configurado
     */
    public boolean isMLAvailable() {
        if (memoryService == null) {
            return false;
        }
        return memoryService.getMemoria().size() >= minimumDecisionsThreshold;
    }

    /**
     * @return true si hay datos suficientes y además ya hay un modelo entrenado listo para predecir
     */
    public boolean isModelReady() {
        return isMLAvailable() && model.get().isTrained();
    }

    /**
     * Devuelve el modelo entrenado; si no lo hay y hay datos suficientes, lo entrena ahora.
     *
     * @return el modelo entrenado, o {@code null} si no se puede entrenar
     */
    public LogisticRegressionModel ensureTrained() {
        LogisticRegressionModel current = model.get();
        if (current.isTrained()) {
            return current;
        }
        if (!isMLAvailable()) {
            return null;
        }
        synchronized (trainingLock) {
            current = model.get();
            if (!current.isTrained()) {
                retrain();
                current = model.get();
            }
        }
        return current.isTrained() ? current : null;
    }

    /**
     * Returns the minimum number of decisions required for ML to be available.
     * 
     * @return the minimum threshold
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
     * Entrena un modelo nuevo y, si sale bien, lo publica y lo persiste (si hay ruta).
     * 
     * Implements Requirements 3.2: Persist model weights to local file for reuse.
     */
    public void retrain() {
        if (memoryService == null) {
            LOGGER.severe("Cannot retrain: MemoryService is null");
            return;
        }
        
        synchronized (trainingLock) {
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
                
                LogisticRegressionModel candidate = modelFactory.get();
                candidate.train(dataset, LEARNING_RATE, ITERATIONS);
                model.set(candidate);
                
                if (modelPath != null) {
                    try {
                        modelPersistence.saveModel(candidate, modelPath);
                        LOGGER.info("Model trained and saved to " + modelPath);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Model trained but failed to save: " + e.getMessage(), e);
                    }
                }
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Training failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Attempts to load an existing model from disk.
     */
    private void loadModelIfExists() {
        LogisticRegressionModel loadedModel = modelPersistence.loadModel(modelPath);
        if (loadedModel != null && loadedModel.isTrained()) {
            this.model.set(loadedModel);
            LOGGER.info("Loaded existing ML model from " + modelPath);
        }
    }

    /**
     * Gets the current model (for testing purposes).
     * 
     * @return the logistic regression model currently published
     */
    public LogisticRegressionModel getModel() {
        return model.get();
    }

    /**
     * @return ruta del modelo en disco, o {@code null} si el predictor no persiste
     */
    public String getModelPath() {
        return modelPath;
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
        if (!isMLAvailable()) {
            return -1.0;
        }
        LogisticRegressionModel current = ensureTrained();
        if (current == null) {
            return -1.0;
        }
        
        try {
            double[] features = featureExtractor.extractFeatures(origen, destino, tipo, origenInfo, destinoInfo);
            return current.predict(features);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Prediction failed: " + e.getMessage());
            return -1.0;
        }
    }
}
