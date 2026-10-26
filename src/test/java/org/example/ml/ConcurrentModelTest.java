package org.example.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.service.MemoryService;
import org.junit.jupiter.api.Test;

/**
 * Predecir mientras se reentrena no debe fallar ni ver pesos a medio calcular.
 */
class ConcurrentModelTest {

    private static TrainingDataset dataset(int n) {
        TrainingDataset ds = new TrainingDataset(FeatureExtractor.FEATURE_COUNT);
        for (int i = 0; i < n; i++) {
            double[] f = new double[FeatureExtractor.FEATURE_COUNT];
            for (int j = 0; j < f.length; j++) {
                f[j] = ((i * 31 + j * 7) % 10) / 10.0;
            }
            ds.addExample(new TrainingExample(f, i % 2));
        }
        return ds;
    }

    @Test
    void predictWhileTrainingNeverThrowsAndStaysInRange() throws Exception {
        LogisticRegressionModel model = new LogisticRegressionModel(7L);
        model.train(dataset(20), 0.1, 50);
        double[] probe = new double[FeatureExtractor.FEATURE_COUNT];
        java.util.Arrays.fill(probe, 0.5);

        AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> readers = new ArrayList<>();
            for (int t = 0; t < 3; t++) {
                readers.add(pool.submit(() -> {
                    int calls = 0;
                    while (!stop.get()) {
                        double p = model.predict(probe);
                        assertTrue(p >= 0.0 && p <= 1.0);
                        calls++;
                    }
                    return calls;
                }));
            }
            Future<?> trainer = pool.submit(() -> {
                for (int i = 0; i < 30; i++) {
                    model.train(dataset(40), 0.1, 200);
                }
            });
            trainer.get(60, TimeUnit.SECONDS);
            stop.set(true);
            for (Future<Integer> r : readers) {
                assertTrue(r.get(10, TimeUnit.SECONDS) > 0);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void retrainPublishesNewModelInstanceAtomically() {
        MemoryService memory = MemoryService.inMemory(Map.of());
        for (int i = 0; i < 12; i++) {
            memory.recordarDecision("A" + i + " -> B" + i + " : OneToMany", i % 2 == 0);
        }
        MLRelationPredictor predictor = MLRelationPredictor.inMemory(
            memory, new FeatureExtractor(), new LogisticRegressionModel(42L), 10);

        LogisticRegressionModel before = predictor.getModel();
        assertEquals(false, before.isTrained());
        LogisticRegressionModel trained = predictor.ensureTrained();

        assertTrue(trained.isTrained());
        assertTrue(before != trained, "El reentreno trabaja sobre una copia y la publica");
        assertEquals(false, before.isTrained(), "El modelo anterior no se modifica");
    }
}
