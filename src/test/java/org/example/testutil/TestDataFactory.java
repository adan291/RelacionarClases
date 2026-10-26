package org.example.testutil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.example.ml.TrainingDataset;
import org.example.model.AnalysisSnapshot;
import org.example.model.RelacionDetectada;

/**
 * Factory class for creating test data objects.
 * Provides methods to create relations, snapshots, and training data for testing.
 */
public class TestDataFactory {

    private static final String[] RELATION_TYPES = {
        "OneToMany", "ManyToOne", "ManyToMany", "OneToOne"
    };

    private static final String[] SAMPLE_CLASSES = {
        "User", "Order", "Product", "Category", "Customer", 
        "Invoice", "Payment", "Address", "Employee", "Department"
    };

    private static final Random RANDOM = new Random();

    /**
     * Creates a single RelacionDetectada with specified parameters.
     *
     * @param source the source class name
     * @param target the target class name
     * @param type the relation type (e.g., "OneToMany", "ManyToOne")
     * @param confidence the confidence score (0.0 to 1.0)
     * @return a new RelacionDetectada instance
     */
    public static RelacionDetectada createRelation(String source, String target, 
                                                    String type, double confidence) {
        return new RelacionDetectada(source, target, type, confidence);
    }


    /**
     * Creates a list of sample relations for testing.
     *
     * @param count the number of relations to create
     * @return a list of RelacionDetectada instances
     */
    public static List<RelacionDetectada> createSampleRelations(int count) {
        List<RelacionDetectada> relations = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String source = SAMPLE_CLASSES[i % SAMPLE_CLASSES.length];
            String target = SAMPLE_CLASSES[(i + 1) % SAMPLE_CLASSES.length];
            String type = RELATION_TYPES[i % RELATION_TYPES.length];
            double confidence = 0.5 + (RANDOM.nextDouble() * 0.5); // 0.5 to 1.0
            
            relations.add(createRelation(source, target, type, confidence));
        }
        
        return relations;
    }

    /**
     * Creates a sample AnalysisSnapshot for testing.
     *
     * @param projectName the project name
     * @param relations the list of relations to include
     * @return a new AnalysisSnapshot instance
     */
    public static AnalysisSnapshot createSnapshot(String projectName, 
                                                   List<RelacionDetectada> relations) {
        String id = UUID.randomUUID().toString();
        String projectPath = "/test/projects/" + projectName;
        String description = "Test snapshot for " + projectName;
        
        // Create some sample decisions
        Map<String, Boolean> decisions = new HashMap<>();
        if (relations != null) {
            for (int i = 0; i < relations.size(); i++) {
                RelacionDetectada rel = relations.get(i);
                // Alternate between accepted and rejected for variety
                if (i % 3 != 2) { // 2/3 have decisions, 1/3 pending
                    decisions.put(rel.getClaveMemoria(), i % 2 == 0);
                }
            }
        }
        
        return AnalysisSnapshot.fromRelaciones(id, projectPath, projectName, 
                                                description, relations, decisions);
    }

    /**
     * Creates a snapshot with a specific ID for deterministic testing.
     *
     * @param id the snapshot ID
     * @param projectName the project name
     * @param relations the list of relations
     * @param decisions the decisions map
     * @return a new AnalysisSnapshot instance
     */
    public static AnalysisSnapshot createSnapshotWithId(String id, String projectName,
                                                         List<RelacionDetectada> relations,
                                                         Map<String, Boolean> decisions) {
        String projectPath = "/test/projects/" + projectName;
        String description = "Test snapshot for " + projectName;
        
        return AnalysisSnapshot.fromRelaciones(id, projectPath, projectName,
                                                description, relations, decisions);
    }


    /**
     * Creates a TrainingDataset with sample training examples.
     *
     * @param exampleCount the number of training examples to create
     * @return a new TrainingDataset instance
     */
    public static TrainingDataset createTrainingData(int exampleCount) {
        // Standard feature count based on FeatureExtractor
        int featureCount = 10;
        TrainingDataset dataset = new TrainingDataset(featureCount);
        
        for (int i = 0; i < exampleCount; i++) {
            double[] features = createRandomFeatures(featureCount);
            // Alternate labels for balanced dataset
            double label = (i % 2 == 0) ? 1.0 : 0.0;
            dataset.addExample(features, label);
        }
        
        return dataset;
    }

    /**
     * Creates a TrainingDataset with specific feature patterns for testing.
     *
     * @param acceptedCount number of accepted examples
     * @param rejectedCount number of rejected examples
     * @return a new TrainingDataset instance
     */
    public static TrainingDataset createTrainingDataWithLabels(int acceptedCount, 
                                                                int rejectedCount) {
        int featureCount = 10;
        TrainingDataset dataset = new TrainingDataset(featureCount);
        
        // Add accepted examples (label = 1.0)
        for (int i = 0; i < acceptedCount; i++) {
            double[] features = createRandomFeatures(featureCount);
            // Bias features slightly toward acceptance patterns
            features[0] = 0.7 + (RANDOM.nextDouble() * 0.3);
            dataset.addExample(features, 1.0);
        }
        
        // Add rejected examples (label = 0.0)
        for (int i = 0; i < rejectedCount; i++) {
            double[] features = createRandomFeatures(featureCount);
            // Bias features slightly toward rejection patterns
            features[0] = RANDOM.nextDouble() * 0.3;
            dataset.addExample(features, 0.0);
        }
        
        return dataset;
    }

    /**
     * Creates random feature values for testing.
     *
     * @param count the number of features
     * @return an array of random feature values (0.0 to 1.0)
     */
    private static double[] createRandomFeatures(int count) {
        double[] features = new double[count];
        for (int i = 0; i < count; i++) {
            features[i] = RANDOM.nextDouble();
        }
        return features;
    }

    /**
     * Creates a RelacionDetectada with bidirectional properties set.
     *
     * @param source the source class name
     * @param target the target class name
     * @param type the relation type
     * @param confidence the confidence score
     * @param isOwnerSide whether this is the owner side
     * @param mappedBy the mappedBy field name (for inverse side)
     * @return a new RelacionDetectada with bidirectional properties
     */
    public static RelacionDetectada createBidirectionalRelation(String source, String target,
                                                                 String type, double confidence,
                                                                 boolean isOwnerSide, 
                                                                 String mappedBy) {
        RelacionDetectada relation = createRelation(source, target, type, confidence);
        relation.setBidirectional(true);
        relation.setOwnerSide(isOwnerSide);
        relation.setMappedBy(mappedBy);
        return relation;
    }

    /**
     * Creates a RelacionDetectada marked as having an existing annotation.
     *
     * @param source the source class name
     * @param target the target class name
     * @param type the relation type
     * @param confidence the confidence score
     * @return a new RelacionDetectada with existingAnnotation set to true
     */
    public static RelacionDetectada createExistingAnnotationRelation(String source, String target,
                                                                      String type, double confidence) {
        RelacionDetectada relation = createRelation(source, target, type, confidence);
        relation.setExistingAnnotation(true);
        return relation;
    }
}
