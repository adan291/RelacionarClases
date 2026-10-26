package org.example.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Evaluates the ML model using k-fold cross-validation.
 * Calculates accuracy, precision, recall, and F1-score.
 */
public class ModelEvaluator {
    
    private static final Logger LOGGER = Logger.getLogger(ModelEvaluator.class.getName());
    private static final double ACCURACY_THRESHOLD = 0.60;
    private static final double CLASSIFICATION_THRESHOLD = 0.5;
    
    /**
     * Evaluates the model using k-fold cross-validation.
     * 
     * @param dataset the training dataset to evaluate
     * @param folds number of folds for cross-validation (default: 5)
     * @return evaluation metrics averaged across all folds
     */
    public EvaluationMetrics evaluate(TrainingDataset dataset, int folds) {
        if (dataset == null || dataset.size() == 0) {
            LOGGER.warning("Cannot evaluate empty dataset");
            return new EvaluationMetrics(0.0, 0.0, 0.0, 0.0);
        }
        
        if (folds < 2) {
            folds = 2;
        }
        
        if (folds > dataset.size()) {
            folds = dataset.size();
        }
        
        List<TrainingExample> examples = new ArrayList<>(dataset.getExamples());
        Collections.shuffle(examples);
        
        int foldSize = examples.size() / folds;
        
        double totalAccuracy = 0.0;
        double totalPrecision = 0.0;
        double totalRecall = 0.0;
        double totalF1 = 0.0;
        int validFolds = 0;
        
        for (int i = 0; i < folds; i++) {
            // Create train and test sets for this fold
            int testStart = i * foldSize;
            int testEnd = (i == folds - 1) ? examples.size() : (i + 1) * foldSize;
            
            TrainingDataset trainSet = new TrainingDataset(dataset.getFeatureCount());
            TrainingDataset testSet = new TrainingDataset(dataset.getFeatureCount());
            
            for (int j = 0; j < examples.size(); j++) {
                if (j >= testStart && j < testEnd) {
                    testSet.addExample(examples.get(j));
                } else {
                    trainSet.addExample(examples.get(j));
                }
            }
            
            // Skip if train or test set is empty
            if (trainSet.size() == 0 || testSet.size() == 0) {
                continue;
            }
            
            // Train model on training set
            LogisticRegressionModel model = new LogisticRegressionModel();
            try {
                model.train(trainSet, 0.1, 1000);
            } catch (Exception e) {
                LOGGER.warning("Training failed for fold " + i + ": " + e.getMessage());
                continue;
            }
            
            // Evaluate on test set
            FoldMetrics foldMetrics = evaluateFold(model, testSet);
            
            totalAccuracy += foldMetrics.accuracy;
            totalPrecision += foldMetrics.precision;
            totalRecall += foldMetrics.recall;
            totalF1 += foldMetrics.f1;
            validFolds++;
        }
        
        if (validFolds == 0) {
            LOGGER.warning("No valid folds for evaluation");
            return new EvaluationMetrics(0.0, 0.0, 0.0, 0.0);
        }
        
        // Average metrics across folds
        double avgAccuracy = totalAccuracy / validFolds;
        double avgPrecision = totalPrecision / validFolds;
        double avgRecall = totalRecall / validFolds;
        double avgF1 = totalF1 / validFolds;
        
        // Check accuracy threshold and log warning if needed
        checkAccuracyThreshold(avgAccuracy);
        
        return new EvaluationMetrics(avgAccuracy, avgPrecision, avgRecall, avgF1);
    }

    
    /**
     * Evaluates the model with default 5 folds.
     * 
     * @param dataset the training dataset to evaluate
     * @return evaluation metrics
     */
    public EvaluationMetrics evaluate(TrainingDataset dataset) {
        return evaluate(dataset, 5);
    }
    
    /**
     * Evaluates a single fold and returns metrics.
     */
    private FoldMetrics evaluateFold(LogisticRegressionModel model, TrainingDataset testSet) {
        int truePositives = 0;
        int falsePositives = 0;
        int trueNegatives = 0;
        int falseNegatives = 0;
        
        for (TrainingExample example : testSet.getExamples()) {
            double prediction = model.predict(example.getFeatures());
            boolean predictedPositive = prediction >= CLASSIFICATION_THRESHOLD;
            boolean actualPositive = example.getLabel() >= CLASSIFICATION_THRESHOLD;
            
            if (predictedPositive && actualPositive) {
                truePositives++;
            } else if (predictedPositive && !actualPositive) {
                falsePositives++;
            } else if (!predictedPositive && actualPositive) {
                falseNegatives++;
            } else {
                trueNegatives++;
            }
        }
        
        int total = truePositives + falsePositives + trueNegatives + falseNegatives;
        
        double accuracy = total > 0 ? (double) (truePositives + trueNegatives) / total : 0.0;
        
        double precision = (truePositives + falsePositives) > 0 
                ? (double) truePositives / (truePositives + falsePositives) 
                : 0.0;
        
        double recall = (truePositives + falseNegatives) > 0 
                ? (double) truePositives / (truePositives + falseNegatives) 
                : 0.0;
        
        double f1 = (precision + recall) > 0 
                ? 2 * (precision * recall) / (precision + recall) 
                : 0.0;
        
        return new FoldMetrics(accuracy, precision, recall, f1);
    }
    
    /**
     * Checks if accuracy is below threshold and logs warning.
     * 
     * @param accuracy the calculated accuracy
     */
    private void checkAccuracyThreshold(double accuracy) {
        if (accuracy < ACCURACY_THRESHOLD) {
            LOGGER.warning(String.format(
                "Model accuracy (%.2f%%) is below the recommended threshold of %.0f%%. " +
                "Consider collecting more training data to improve predictions.",
                accuracy * 100, ACCURACY_THRESHOLD * 100));
        }
    }
    
    /**
     * Internal class to hold metrics for a single fold.
     */
    private static class FoldMetrics {
        final double accuracy;
        final double precision;
        final double recall;
        final double f1;
        
        FoldMetrics(double accuracy, double precision, double recall, double f1) {
            this.accuracy = accuracy;
            this.precision = precision;
            this.recall = recall;
            this.f1 = f1;
        }
    }
}
