package org.example.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.example.ml.ClassInfo;
import org.example.ml.FeatureExtractor;
import org.example.ml.LogisticRegressionModel;
import org.example.ml.MLRelationPredictor;
import org.example.ml.ScoreCombiner;
import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;
import org.example.testutil.SyntheticModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Test de equivalencia: el análisis completo sobre el modelo sintético debe dar exactamente las
 * mismas relaciones (origen, destino, tipo, fuente, bidireccionalidad, mappedBy, orden) y la misma
 * confianza (con tolerancia 1e-9 por el orden de las sumas) que la salida guardada en
 * {@code src/test/resources/golden/}.
 *
 * <p>La salida de referencia se generó con el código anterior a la optimización. Si un cambio
 * altera los resultados a propósito, se regenera con {@code -Dgolden.update=true} y se documenta
 * en el commit.</p>
 */
class AnalysisEquivalenceTest {

    private static final double TOLERANCE = 1e-9;

    static List<RelacionDetectada> run(int classes, int decisions) {
        Map<String, ClassInfo> model = SyntheticModel.classes(classes, 20261026L);
        MemoryService memory = MemoryService.inMemory(SyntheticModel.memory(model, decisions, 7L));
        MLRelationPredictor predictor = MLRelationPredictor.inMemory(
            memory, new FeatureExtractor(), new LogisticRegressionModel(42L), 10);
        assertNotNull(predictor.ensureTrained(), "El modelo debe entrenarse con la memoria sintética");
        IntelligentRelationAnalyzer analyzer = new IntelligentRelationAnalyzer(
            memory, predictor, new ScoreCombiner(0.5, 0.3, 0.2), List.of(), List.of(".java"));
        return analyzer.analizar(model);
    }

    static String format(RelacionDetectada r) {
        return String.join("|", r.getOrigen(), r.getDestino(), r.getTipo(),
            Double.toString(r.getConfianza()),
            String.valueOf(r.getSource()), String.valueOf(r.isBidirectional()),
            String.valueOf(r.isOwnerSide()), String.valueOf(r.getMappedBy()));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "synthetic-40c-25d, 40, 25",
        "synthetic-30c-1000d, 30, 1000"
    })
    void analysisMatchesGoldenOutput(String name, int classes, int decisions) throws IOException {
        List<String> actual = run(classes, decisions).stream().map(AnalysisEquivalenceTest::format).toList();
        assertTrue(actual.size() > 50, "El escenario debe producir un número de relaciones significativo");

        if (Boolean.getBoolean("golden.update")) {
            Path out = Path.of("src", "test", "resources", "golden", name + ".txt");
            Files.createDirectories(out.getParent());
            Files.write(out, actual, StandardCharsets.UTF_8);
            return;
        }

        List<String> expected = readGolden(name);
        assertEquals(expected.size(), actual.size(), "Número de relaciones distinto");
        for (int i = 0; i < expected.size(); i++) {
            String[] e = expected.get(i).split("\\|", -1);
            String[] a = actual.get(i).split("\\|", -1);
            String where = "posición " + i + ": esperado " + expected.get(i) + " / actual " + actual.get(i);
            for (int f = 0; f < e.length; f++) {
                if (f == 3) {
                    double de = Double.parseDouble(e[f]);
                    double da = Double.parseDouble(a[f]);
                    if (Math.abs(de - da) > TOLERANCE) {
                        fail("Confianza distinta en " + where);
                    }
                } else {
                    assertEquals(e[f], a[f], where);
                }
            }
        }
    }

    private static List<String> readGolden(String name) throws IOException {
        try (InputStream in = AnalysisEquivalenceTest.class.getResourceAsStream("/golden/" + name + ".txt")) {
            assertNotNull(in, "Falta la salida de referencia golden/" + name + ".txt");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            for (String line : text.split("\\R")) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }
}
