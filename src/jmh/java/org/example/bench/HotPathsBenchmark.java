package org.example.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.example.analysis.SemanticSimilarity;
import org.example.ml.ClassInfo;
import org.example.ml.FeatureExtractor;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * Piezas calientes medidas por separado sobre 1.000 pares de nombres del modelo sintético:
 * similitud de nombres y extracción de features (24 dimensiones) para los cuatro tipos.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class HotPathsBenchmark {

    private static final String[] TYPES = {"OneToOne", "OneToMany", "ManyToOne", "ManyToMany"};
    private static final int PAIRS = 1000;

    private String[] origins;
    private String[] targets;
    private ClassInfo[] originInfo;
    private ClassInfo[] targetInfo;
    private FeatureExtractor extractor;

    @Setup(Level.Trial)
    public void setUp() {
        BenchLogging.quiet();
        Map<String, ClassInfo> model = SyntheticModel.classes(200, 20261026L);
        List<String> names = new ArrayList<>(model.keySet());
        Random random = new Random(99L);
        origins = new String[PAIRS];
        targets = new String[PAIRS];
        originInfo = new ClassInfo[PAIRS];
        targetInfo = new ClassInfo[PAIRS];
        for (int i = 0; i < PAIRS; i++) {
            origins[i] = names.get(random.nextInt(names.size()));
            targets[i] = names.get(random.nextInt(names.size()));
            originInfo[i] = model.get(origins[i]);
            targetInfo[i] = model.get(targets[i]);
        }
        extractor = new FeatureExtractor();
    }

    /** 1.000 llamadas a SemanticSimilarity.compare. */
    @Benchmark
    public void nameSimilarity(Blackhole bh) {
        for (int i = 0; i < PAIRS; i++) {
            bh.consume(SemanticSimilarity.compare(origins[i], targets[i]));
        }
    }

    /** 1.000 pares × 4 tipos de relación. */
    @Benchmark
    public void featureExtraction(Blackhole bh) {
        for (int i = 0; i < PAIRS; i++) {
            for (String type : TYPES) {
                bh.consume(extractor.extractFeatures(origins[i], targets[i], type,
                    originInfo[i], targetInfo[i], null, null));
            }
        }
    }
}
