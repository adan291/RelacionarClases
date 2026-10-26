package org.example.ml;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a single training example for the ML model.
 * Contains a feature vector and a label (1.0 = accepted, 0.0 = rejected).
 */
public class TrainingExample {
    
    private double[] features;
    private double label;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public TrainingExample() {
    }
    
    /**
     * Creates a new training example.
     * @param features the feature vector
     * @param label the label (1.0 for accepted, 0.0 for rejected)
     */
    public TrainingExample(double[] features, double label) {
        this.features = features != null ? Arrays.copyOf(features, features.length) : new double[0];
        this.label = label;
    }
    
    public double[] getFeatures() {
        return features != null ? Arrays.copyOf(features, features.length) : new double[0];
    }
    
    public void setFeatures(double[] features) {
        this.features = features != null ? Arrays.copyOf(features, features.length) : new double[0];
    }
    
    public double getLabel() {
        return label;
    }
    
    public void setLabel(double label) {
        this.label = label;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainingExample that = (TrainingExample) o;
        return Double.compare(that.label, label) == 0 && 
               Arrays.equals(features, that.features);
    }
    
    @Override
    public int hashCode() {
        int result = Objects.hash(label);
        result = 31 * result + Arrays.hashCode(features);
        return result;
    }
    
    @Override
    public String toString() {
        return "TrainingExample{" +
                "features=" + Arrays.toString(features) +
                ", label=" + label +
                '}';
    }
}
