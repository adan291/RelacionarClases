package org.example.ml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import org.example.config.ConfigManager;
import org.example.config.MLConfig;

/**
 * Logistic Regression model implemented in pure Java.
 * Uses gradient descent for training and sigmoid activation for predictions.
 * 
 * This lightweight model is designed for local training and inference
 * without external ML library dependencies.
 * 
 * Enhanced to support 24-feature vectors with backward compatibility
 * for legacy 12-feature models.
 * Implements Requirements 1.5: Enhanced training pipeline.
 */
public class LogisticRegressionModel {

    private static final Logger LOGGER = Logger.getLogger(LogisticRegressionModel.class.getName());
    
    /** Default feature count for new models (24 enhanced features) */
    public static final int DEFAULT_FEATURE_COUNT = FeatureExtractor.FEATURE_COUNT;
    
    /** Legacy feature count for backward compatibility */
    public static final int LEGACY_FEATURE_COUNT = FeatureExtractor.LEGACY_FEATURE_COUNT;
    
    /** Neutral padding value for missing features */
    public static final double NEUTRAL_PADDING_VALUE = 0.5;
    
    /** Feature names for logging and debugging (matches FeatureExtractor indices) */
    private static final String[] FEATURE_NAMES = {
        // Name Features (0-7)
        "classNameJaroWinkler",
        "classNameLevenshtein",
        "fieldNameSimilarity",
        "hasIdSuffix",
        "hasListSuffix",
        "isPluralNaming",
        "hasParentChildPattern",
        "namingConsistency",
        // Structure Features (8-15)
        "sourceFieldCount",
        "targetFieldCount",
        "hasInheritance",
        "sharedInterfaces",
        "commonPrefixLength",
        "commonSuffixLength",
        "hasExistingAnnotation",
        "hasIdField",
        // Cardinality Features (16-23)
        "isCollection",
        "isSet",
        "isList",
        "isMap",
        "isOptional",
        "isArray",
        "relationType_OneToOne",
        "relationType_OneToMany"
    };

    /**
     * Estado inmutable del modelo. Se publica entero a través de un campo volatile:
     * {@link #predict(double[])} lee una sola vez la referencia y nunca ve pesos a medio
     * entrenar, y {@link #train} trabaja sobre copias locales que solo se publican al final.
     */
    private record State(double[] weights, double bias, boolean trained) {
        static final State EMPTY = new State(null, 0.0, false);
    }

    private volatile State state = State.EMPTY;
    private final Random random;

    /**
     * Creates a new untrained LogisticRegressionModel.
     */
    public LogisticRegressionModel() {
        this.random = new Random();
    }

    /**
     * Creates a LogisticRegressionModel with a specific random seed for reproducibility.
     * 
     * @param seed random seed for weight initialization
     */
    public LogisticRegressionModel(long seed) {
        this.random = new Random(seed);
    }

    private LogisticRegressionModel(Random sharedRandom) {
        this.random = sharedRandom;
    }

    /**
     * Crea un modelo sin entrenar que comparte el generador aleatorio con este.
     * Se usa para reentrenar sobre una copia y publicarla después: el primer reentrenamiento
     * consume la misma secuencia aleatoria que si se hubiera entrenado este modelo en el sitio.
     *
     * @return un modelo nuevo, sin entrenar
     */
    public LogisticRegressionModel newUntrainedCopy() {
        return new LogisticRegressionModel(random);
    }
    
    /**
     * Creates a LogisticRegressionModel pre-initialized with the specified feature count.
     * Useful for creating models that will be trained with a specific number of features.
     * 
     * @param featureCount the number of features to initialize weights for
     * @param seed random seed for weight initialization
     */
    public LogisticRegressionModel(int featureCount, long seed) {
        this.random = new Random(seed);
        if (featureCount > 0) {
            this.state = initialState(featureCount);
        }
    }
    
    /**
     * Creates a LogisticRegressionModel pre-initialized with the default 24 features.
     * 
     * @param initializeWithDefaults if true, initializes with DEFAULT_FEATURE_COUNT weights
     */
    public LogisticRegressionModel(boolean initializeWithDefaults) {
        this.random = new Random();
        if (initializeWithDefaults) {
            this.state = initialState(DEFAULT_FEATURE_COUNT);
        }
    }

    /**
     * Computes the sigmoid function: 1 / (1 + e^(-x))
     * 
     * @param x input value
     * @return sigmoid output in range (0, 1)
     */
    public double sigmoid(double x) {
        // Handle extreme values to avoid overflow
        if (x > 700) {
            return 1.0;
        }
        if (x < -700) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-x));
    }


    /**
     * Predicts the probability that a relation is correct.
     * Calculates weighted sum + bias, applies sigmoid, and clamps invalid values.
     * Supports backward compatibility by padding features if needed.
     * 
     * @param features input feature vector
     * @return probability in range [0, 1]
     * @throws IllegalStateException if model is not trained
     */
    public double predict(double[] features) {
        State s = state; // una sola lectura: pesos y bias coherentes entre sí
        if (!s.trained() || s.weights() == null) {
            throw new IllegalStateException("Model must be trained before making predictions");
        }
        if (features == null) {
            return clampOutput(0.5);
        }
        double[] weights = s.weights();
        
        // Handle feature vector size mismatch with padding
        double[] adjustedFeatures = adjustFeatureVector(features, weights.length);

        // Calculate weighted sum: w1*x1 + w2*x2 + ... + wn*xn + bias
        double weightedSum = s.bias();
        for (int i = 0; i < adjustedFeatures.length; i++) {
            weightedSum += weights[i] * adjustedFeatures[i];
        }

        // Apply sigmoid activation
        double prediction = sigmoid(weightedSum);

        // Clamp NaN/Inf to valid range
        return clampOutput(prediction);
    }
    
    /**
     * Adjusts feature vector to match model's expected size.
     * Pads with NEUTRAL_PADDING_VALUE (0.5) if features are shorter than weights.
     * Truncates if features are longer than weights.
     * 
     * This enables backward compatibility between models trained with different
     * feature counts (e.g., legacy 12-feature models with new 24-feature extractors).
     * 
     * @param features original feature vector
     * @return adjusted feature vector matching weights length
     */
    private static double[] adjustFeatureVector(double[] features, int weightCount) {
        if (features.length == weightCount) {
            return features;
        }
        
        double[] adjusted = new double[weightCount];
        
        // Copy existing features
        int copyLength = Math.min(features.length, weightCount);
        System.arraycopy(features, 0, adjusted, 0, copyLength);
        
        // Pad remaining with neutral value
        for (int i = copyLength; i < weightCount; i++) {
            adjusted[i] = NEUTRAL_PADDING_VALUE;
        }
        
        return adjusted;
    }

    /**
     * Clamps output to valid range [0, 1], handling NaN and infinite values.
     * NaN -> 0.5, +Inf -> 1.0, -Inf -> 0.0
     * 
     * @param value the value to clamp
     * @return clamped value in [0, 1]
     */
    private double clampOutput(double value) {
        if (Double.isNaN(value)) {
            return 0.5;
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? 1.0 : 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Trains the model using gradient descent optimization.
     * 
     * @param dataset training data with features and labels
     * @param learningRate step size for gradient descent (default: 0.1)
     * @param iterations number of training iterations (default: 1000)
     * @throws IllegalArgumentException if dataset is null or empty
     */
    public void train(TrainingDataset dataset, double learningRate, int iterations) {
        if (dataset == null || dataset.size() == 0) {
            throw new IllegalArgumentException("Training dataset cannot be null or empty");
        }

        var examples = dataset.getExamples();
        int featureCount = examples.get(0).getFeatures().length;
        int n = examples.size();

        // Initialize weights randomly in range [-0.5, 0.5].
        // Se entrena sobre copias locales; el estado publicado no cambia hasta el final.
        State init = initialState(featureCount);
        double[] weights = init.weights();
        double bias = init.bias();

        // Gradient descent optimization
        for (int iter = 0; iter < iterations; iter++) {
            double[] gradientWeights = new double[featureCount];
            double gradientBias = 0.0;

            // Calculate gradients over all examples
            for (TrainingExample example : examples) {
                double[] features = example.getFeatures();
                double label = example.getLabel();

                // Forward pass: calculate prediction
                double weightedSum = bias;
                for (int j = 0; j < featureCount; j++) {
                    weightedSum += weights[j] * features[j];
                }
                double prediction = sigmoid(weightedSum);

                // Calculate error
                double error = prediction - label;

                // Accumulate gradients
                for (int j = 0; j < featureCount; j++) {
                    gradientWeights[j] += error * features[j];
                }
                gradientBias += error;
            }

            // Update weights and bias
            for (int j = 0; j < featureCount; j++) {
                weights[j] -= learningRate * (gradientWeights[j] / n);
            }
            bias -= learningRate * (gradientBias / n);
        }

        this.state = new State(weights, bias, true);
        
        // Log feature importance after training (Requirement 7.3)
        logFeatureImportance();
    }


    /**
     * Trains the model with parameters from configuration.
     * Uses learningRate and maxIterations from MLConfig.
     * 
     * @param dataset training data with features and labels
     */
    public void train(TrainingDataset dataset) {
        MLConfig config = ConfigManager.getInstance().getMLConfig();
        train(dataset, config.learningRate(), config.maxIterations());
    }
    
    /**
     * Logs feature importance based on weight magnitudes.
     * Identifies and logs the most influential features after training.
     * Implements Requirement 7.3: Log feature importance after training.
     */
    public void logFeatureImportance() {
        State s = state;
        if (!s.trained() || s.weights() == null) {
            LOGGER.warning("Cannot log feature importance: model not trained");
            return;
        }
        if (!LOGGER.isLoggable(java.util.logging.Level.INFO)) {
            return;
        }
        double[] weights = s.weights();
        double bias = s.bias();
        
        // Create list of feature importance entries
        List<FeatureImportance> importanceList = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            String featureName = i < FEATURE_NAMES.length ? FEATURE_NAMES[i] : "feature_" + i;
            importanceList.add(new FeatureImportance(i, featureName, weights[i]));
        }
        
        // Sort by absolute weight magnitude (descending)
        importanceList.sort(Comparator.comparingDouble(FeatureImportance::absoluteWeight).reversed());
        
        // Log feature importance
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Feature Importance (sorted by |weight|) ===\n");
        sb.append(String.format("%-5s %-25s %12s %12s%n", "Rank", "Feature", "Weight", "|Weight|"));
        sb.append("-".repeat(56)).append("\n");
        
        for (int rank = 0; rank < importanceList.size(); rank++) {
            FeatureImportance fi = importanceList.get(rank);
            sb.append(String.format("%-5d %-25s %12.6f %12.6f%n", 
                rank + 1, fi.name(), fi.weight(), fi.absoluteWeight()));
        }
        
        sb.append("-".repeat(56)).append("\n");
        sb.append(String.format("Bias: %.6f%n", bias));
        sb.append(String.format("Total features: %d%n", weights.length));
        
        // Identify top 5 most influential features
        sb.append("\nTop 5 Most Influential Features:\n");
        for (int i = 0; i < Math.min(5, importanceList.size()); i++) {
            FeatureImportance fi = importanceList.get(i);
            String direction = fi.weight() > 0 ? "positive" : "negative";
            sb.append(String.format("  %d. %s (%.4f, %s influence)%n", 
                i + 1, fi.name(), fi.absoluteWeight(), direction));
        }
        
        LOGGER.info(sb.toString());
    }
    
    /**
     * Gets feature importance as a sorted list.
     * Useful for programmatic access to feature weights.
     * 
     * @return list of FeatureImportance sorted by absolute weight (descending), or empty list if not trained
     */
    public List<FeatureImportance> getFeatureImportance() {
        State s = state;
        if (!s.trained() || s.weights() == null) {
            return List.of();
        }
        double[] weights = s.weights();
        
        List<FeatureImportance> importanceList = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            String featureName = i < FEATURE_NAMES.length ? FEATURE_NAMES[i] : "feature_" + i;
            importanceList.add(new FeatureImportance(i, featureName, weights[i]));
        }
        
        importanceList.sort(Comparator.comparingDouble(FeatureImportance::absoluteWeight).reversed());
        return importanceList;
    }
    
    /**
     * Record representing feature importance information.
     * 
     * @param index the feature index in the feature vector
     * @param name the human-readable feature name
     * @param weight the trained weight value
     */
    public record FeatureImportance(int index, String name, double weight) {
        /**
         * @return the absolute value of the weight (importance magnitude)
         */
        public double absoluteWeight() {
            return Math.abs(weight);
        }
    }

    /**
     * Creates an initial (untrained) state with random weights in range [-0.5, 0.5].
     * El orden de llamadas a {@code random} es el mismo que en la versión anterior
     * (primero los pesos, luego el bias), así que los modelos con semilla dan los mismos resultados.
     *
     * @param featureCount number of features/weights
     */
    private State initialState(int featureCount) {
        double[] weights = new double[featureCount];
        for (int i = 0; i < featureCount; i++) {
            weights[i] = random.nextDouble() - 0.5;
        }
        double bias = random.nextDouble() - 0.5;
        return new State(weights, bias, false);
    }

    /**
     * @return the trained model weights, or null if not trained
     */
    public double[] getWeights() {
        double[] weights = state.weights();
        return weights != null ? weights.clone() : null;
    }

    /**
     * Restores the model from saved weights.
     * 
     * @param weights the weights to restore
     * @param bias the bias to restore
     */
    public void setWeights(double[] weights, double bias) {
        if (weights == null) {
            throw new IllegalArgumentException("Weights cannot be null");
        }
        this.state = new State(weights.clone(), bias, true);
    }

    /**
     * @return the model bias
     */
    public double getBias() {
        return state.bias();
    }

    /**
     * @return true if the model has been trained
     */
    public boolean isTrained() {
        return state.trained();
    }

    /**
     * @return the number of features this model expects, or 0 if not trained
     */
    public int getFeatureCount() {
        double[] weights = state.weights();
        return weights != null ? weights.length : 0;
    }
    
    /**
     * Checks if this model uses the legacy 12-feature format.
     * 
     * @return true if the model has LEGACY_FEATURE_COUNT weights
     */
    public boolean isLegacyModel() {
        return getFeatureCount() == LEGACY_FEATURE_COUNT;
    }
    
    /**
     * Checks if this model uses the enhanced 24-feature format.
     * 
     * @return true if the model has DEFAULT_FEATURE_COUNT weights
     */
    public boolean isEnhancedModel() {
        return getFeatureCount() == DEFAULT_FEATURE_COUNT;
    }
    
    /**
     * Resizes the model to support a different feature count.
     * Preserves existing weights and pads/truncates as needed.
     * 
     * @param newFeatureCount the new number of features
     */
    public synchronized void resizeWeights(int newFeatureCount) {
        if (newFeatureCount <= 0) {
            throw new IllegalArgumentException("Feature count must be positive");
        }
        State s = state;
        double[] weights = s.weights();
        
        if (weights == null) {
            State init = initialState(newFeatureCount);
            this.state = new State(init.weights(), init.bias(), s.trained());
            return;
        }
        
        if (weights.length == newFeatureCount) {
            return;
        }
        
        double[] newWeights = new double[newFeatureCount];
        int copyLength = Math.min(weights.length, newFeatureCount);
        System.arraycopy(weights, 0, newWeights, 0, copyLength);
        
        // Initialize new weights with small random values
        for (int i = copyLength; i < newFeatureCount; i++) {
            newWeights[i] = random.nextDouble() - 0.5;
        }
        
        this.state = new State(newWeights, s.bias(), s.trained());
    }
}