package org.example.ml;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Represents a dataset of training examples for the ML model.
 * Supports adding examples, splitting for validation, and JSON serialization.
 */
public class TrainingDataset {
    
    private List<TrainingExample> examples;
    private int featureCount;
    
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    
    /**
     * Default constructor for JSON deserialization.
     */
    public TrainingDataset() {
        this.examples = new ArrayList<>();
        this.featureCount = 0;
    }
    
    /**
     * Creates a new dataset with specified feature count.
     * @param featureCount the number of features in each example
     */
    public TrainingDataset(int featureCount) {
        this.examples = new ArrayList<>();
        this.featureCount = featureCount;
    }
    
    /**
     * Adds a training example to the dataset.
     * @param features the feature vector
     * @param label the label (1.0 for accepted, 0.0 for rejected)
     */
    public void addExample(double[] features, double label) {
        if (features != null && featureCount == 0) {
            featureCount = features.length;
        }
        examples.add(new TrainingExample(features, label));
    }

    /**
     * Adds a training example to the dataset.
     * @param example the training example to add
     */
    public void addExample(TrainingExample example) {
        if (example != null) {
            if (featureCount == 0 && example.getFeatures() != null) {
                featureCount = example.getFeatures().length;
            }
            examples.add(example);
        }
    }
    
    /**
     * @return the number of examples in the dataset
     */
    public int size() {
        return examples.size();
    }
    
    /**
     * @return unmodifiable list of all examples
     */
    public List<TrainingExample> getExamples() {
        return Collections.unmodifiableList(examples);
    }
    
    /**
     * @return the number of features per example
     */
    public int getFeatureCount() {
        return featureCount;
    }
    
    public void setFeatureCount(int featureCount) {
        this.featureCount = featureCount;
    }
    
    /**
     * Splits the dataset into training and validation sets.
     * @param trainRatio ratio of examples to use for training (0.0 to 1.0)
     * @return a new dataset containing the validation examples
     */
    public TrainingDataset split(double trainRatio) {
        if (trainRatio < 0.0 || trainRatio > 1.0) {
            throw new IllegalArgumentException("trainRatio must be between 0.0 and 1.0");
        }
        
        List<TrainingExample> shuffled = new ArrayList<>(examples);
        Collections.shuffle(shuffled);
        
        int trainSize = (int) (shuffled.size() * trainRatio);
        
        TrainingDataset validationSet = new TrainingDataset(featureCount);
        
        // Keep training examples in this dataset
        this.examples = new ArrayList<>(shuffled.subList(0, trainSize));
        
        // Return validation examples
        for (int i = trainSize; i < shuffled.size(); i++) {
            validationSet.addExample(shuffled.get(i));
        }
        
        return validationSet;
    }
    
    /**
     * Serializes the dataset to JSON string.
     * @return JSON representation of the dataset
     */
    public String toJson() {
        return GSON.toJson(this);
    }
    
    /**
     * Deserializes a dataset from JSON string.
     * @param json the JSON string
     * @return the deserialized dataset
     */
    public static TrainingDataset fromJson(String json) {
        return GSON.fromJson(json, TrainingDataset.class);
    }
    
    /**
     * Saves the dataset to a file.
     * @param path the file path
     * @throws IOException if writing fails
     */
    public void saveToFile(String path) throws IOException {
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(path), StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
    }
    
    /**
     * Loads a dataset from a file.
     * @param path the file path
     * @return the loaded dataset
     * @throws IOException if reading fails
     */
    public static TrainingDataset loadFromFile(String path) throws IOException {
        try (Reader reader = new InputStreamReader(
                new FileInputStream(path), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, TrainingDataset.class);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainingDataset that = (TrainingDataset) o;
        return featureCount == that.featureCount && 
               Objects.equals(examples, that.examples);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(examples, featureCount);
    }
    
    @Override
    public String toString() {
        return "TrainingDataset{" +
                "size=" + examples.size() +
                ", featureCount=" + featureCount +
                '}';
    }
}
