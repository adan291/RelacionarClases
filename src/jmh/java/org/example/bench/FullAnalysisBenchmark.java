package org.example.bench;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.example.analysis.IntelligentRelationAnalyzer;
import org.example.ml.ClassInfo;
import org.example.ml.FeatureExtractor;
import org.example.ml.LogisticRegressionModel;
import org.example.ml.MLRelationPredictor;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Análisis completo (estructural + semántico + ML + memoria + bidireccional) sobre un modelo
 * sintético en memoria de 50, 200 y 500 clases, con 20 decisiones en memoria (ML activo).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 5)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class FullAnalysisBenchmark {

    @Param({"50", "200", "500"})
    public int classes;

    private Map<String, ClassInfo> model;
    private IntelligentRelationAnalyzer analyzer;

    @Setup(Level.Trial)
    public void setUp() {
        BenchLogging.quiet();
        model = SyntheticModel.classes(classes, 20261026L);
        MemoryService memory = MemoryService.inMemory(SyntheticModel.memory(model, 20, 7L));
        MLRelationPredictor predictor = MLRelationPredictor.inMemory(
            memory, new FeatureExtractor(), new LogisticRegressionModel(42L), 10);
        if (predictor.ensureTrained() == null) {
            throw new IllegalStateException("El modelo ML no se pudo entrenar");
        }
        analyzer = new IntelligentRelationAnalyzer(memory, predictor, new ScoreCombiner(0.5, 0.3, 0.2),
            List.of(), List.of(".java"));
    }

    @Benchmark
    public List<RelacionDetectada> analyze() {
        return analyzer.analizar(model);
    }
}
