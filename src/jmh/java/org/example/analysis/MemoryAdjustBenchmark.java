package org.example.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.example.ml.ClassInfo;
import org.example.ml.FeatureExtractor;
import org.example.ml.LogisticRegressionModel;
import org.example.ml.MLRelationPredictor;
import org.example.ml.PredictionSource;
import org.example.ml.ScoreCombiner;
import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;
import org.example.testutil.SyntheticModel;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Ajuste de confianza por memoria con 1.000 decisiones sobre 500 relaciones candidatas.
 * Está en el paquete {@code org.example.analysis} para llamar al método package-private.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 3, time = 3)
@Fork(1)
public class MemoryAdjustBenchmark {

    private static final String[] TYPES = {"OneToOne", "OneToMany", "ManyToOne", "ManyToMany"};

    private IntelligentRelationAnalyzer analyzer;
    private List<RelacionDetectada> relations;

    @Setup(Level.Trial)
    public void setUp() {
        org.example.bench.BenchLogging.quiet();
        Map<String, ClassInfo> model = SyntheticModel.classes(200, 20261026L);
        MemoryService memory = MemoryService.inMemory(SyntheticModel.memory(model, 1000, 7L));
        if (memory.getMemoria().size() != 1000) {
            throw new IllegalStateException("Se esperaban 1000 decisiones");
        }
        MLRelationPredictor predictor = MLRelationPredictor.inMemory(
            memory, new FeatureExtractor(), new LogisticRegressionModel(42L), 10);
        analyzer = new IntelligentRelationAnalyzer(memory, predictor, new ScoreCombiner(0.5, 0.3, 0.2),
            List.of(), List.of(".java"));

        List<String> names = new ArrayList<>(model.keySet());
        relations = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            String o = names.get((i * 7) % names.size());
            String d = names.get((i * 13 + 5) % names.size());
            relations.add(new RelacionDetectada(o, d, TYPES[i % 4], 0.5 + (i % 50) / 100.0,
                PredictionSource.SEMANTIC));
        }
    }

    @Benchmark
    public List<RelacionDetectada> adjustByMemory() {
        return analyzer.ajustarPorMemoria(relations);
    }
}
