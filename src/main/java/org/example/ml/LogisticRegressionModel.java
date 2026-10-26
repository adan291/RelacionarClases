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

    private double[] weights;
    private double bias;
    private boolean trained;
    private final Random random;

    /**
     * Creates a new untrained LogisticRegressionModel.
     */
    public LogisticRegressionModel() {
        this.weights = null;
        this.bias = 0.0;
        this.trained = false;
        this.random = new Random();
    }

    /**
     * Creates a LogisticRegressionModel with a specific random seed for reproducibility.
     * 
     * @param seed random seed for weight initialization
     */
    public LogisticRegressionModel(long seed) {
        this.weights = null;
        this.bias = 0.0;
        this.trained = false;
        this.random = new Random(seed);
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
        this.trained = false;
        if (featureCount > 0) {
            initializeWeights(featureCount);
        } else {
            this.weights = null;
            this.bias = 0.0;
        }
    }
    
    /**
     * Creates a LogisticRegressionModel pre-initialized with the default 24 features.
     * 
     * @param initializeWithDefaults if true, initializes with DEFAULT_FEATURE_COUNT weights
     */
    public LogisticRegressionModel(boolean initializeWithDefaults) {
        this.random = new Random();
        this.trained = false;
        if (initializeWithDefaults) {
            initializeWeights(DEFAULT_FEATURE_COUNT);
        } else {
            this.weights = null;
            this.bias = 0.0;
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
        if (!trained || weights == null) {
            throw new IllegalStateException("Model must be trained before making predictions");
        }
        if (features == null) {
            return clampOutput(0.5);
        }
        
        // Handle feature vector size mismatch with padding
        double[] adjustedFeatures = adjustFeatureVector(features);

        // Calculate weighted sum: w1*x1 + w2*x2 + ... + wn*xn + bias
        double weightedSum = bias;
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
    private double[] adjustFeatureVector(double[] features) {
        if (features.length == weights.length) {
            return features;
        }
        
        double[] adjusted = new double[weights.length];
        
        // Copy existing features
        int copyLength = Math.min(features.length, weights.length);
        System.arraycopy(features, 0, adjusted, 0, copyLength);
        
        // Pad remaining with neutral value
        for (int i = copyLength; i < weights.length; i++) {
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

        // Initialize weights randomly in range [-0.5, 0.5]
        initializeWeights(featureCount);

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

        this.trained = true;
        
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
        if (!trained || weights == null) {
            LOGGER.warning("Cannot log feature importance: model not trained");
            return;
        }
        
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
        if (!trained || weights == null) {
            return List.of();
        }
        
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
     * Initializes weights randomly in range [-0.5, 0.5].
     * 
     * @param featureCount number of features/weights
     */
    private void initializeWeights(int featureCount) {
        weights = new double[featureCount];
        for (int i = 0; i < featureCount; i++) {
            weights[i] = random.nextDouble() - 0.5;
        }
        bias = random.nextDouble() - 0.5;
    }

    /**
     * @return the trained model weights, or null if not trained
     */
    public double[] getWeights() {
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
        this.weights = weights.clone();
        this.bias = bias;
        this.trained = true;
    }

    /**
     * @return the model bias
     */
    public double getBias() {
        return bias;
    }

    /**
     * @return true if the model has been trained
     */
    public boolean isTrained() {
        return trained;
    }

    /**
     * @return the number of features this model expects, or 0 if not trained
     */
    public int getFeatureCount() {
        return weights != null ? weights.length : 0;
    }
    
    /**
     * Checks if this model uses the legacy 12-feature format.
     * 
     * @return true if the model has LEGACY_FEATURE_COUNT weights
     */
    public boolean isLegacyModel() {
        return weights != null && weights.length == LEGACY_FEATURE_COUNT;
    }
    
    /**
     * Checks if this model uses the enhanced 24-feature format.
     * 
     * @return true if the model has DEFAULT_FEATURE_COUNT weights
     */
    public boolean isEnhancedModel() {
        return weights != null && weights.length == DEFAULT_FEATURE_COUNT;
    }
    
    /**
     * Resizes the model to support a different feature count.
     * Preserves existing weights and pads/truncates as needed.
     * 
     * @param newFeatureCount the new number of features
     */
    public void resizeWeights(int newFeatureCount) {
        if (newFeatureCount <= 0) {
            throw new IllegalArgumentException("Feature count must be positive");
        }
        
        if (weights == null) {
            initializeWeights(newFeatureCount);
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
        
        this.weights = newWeights;
    }
}
