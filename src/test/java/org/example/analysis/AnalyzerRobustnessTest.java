package org.example.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.ml.FeatureExtractor;
import org.example.ml.LogisticRegressionModel;
import org.example.ml.MLRelationPredictor;
import org.example.ml.ScoreCombiner;
import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regresiones de la revisión: reutilizar el analizador y ficheros que no parsean.
 */
class AnalyzerRobustnessTest {

    @TempDir
    Path tempDir;

    private IntelligentRelationAnalyzer newAnalyzer() {
        MemoryService memory = MemoryService.inMemory(Map.of());
        MLRelationPredictor predictor = MLRelationPredictor.inMemory(
            memory, new FeatureExtractor(), new LogisticRegressionModel(42L), 10);
        return new IntelligentRelationAnalyzer(memory, predictor, new ScoreCombiner(0.5, 0.3, 0.2),
            List.of("target", "build"), List.of(".java"));
    }

    private static void write(Path dir, String name, String source) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), source);
    }

    private static Set<String> classesIn(List<RelacionDetectada> relations) {
        return relations.stream()
            .flatMap(r -> java.util.stream.Stream.of(r.getOrigen(), r.getDestino()))
            .collect(Collectors.toSet());
    }

    @Test
    void reusingAnalyzerWithTwoDirectoriesDoesNotMixResults() throws IOException {
        Path first = tempDir.resolve("first");
        write(first, "Cliente.java", "public class Cliente { private Long id; private java.util.List<Pedido> pedidos; }");
        write(first, "Pedido.java", "public class Pedido { private Long id; private Cliente cliente; }");

        Path second = tempDir.resolve("second");
        write(second, "Autor.java", "public class Autor { private Long id; private java.util.List<Libro> libros; }");
        write(second, "Libro.java", "public class Libro { private Long id; private Autor autor; }");

        IntelligentRelationAnalyzer analyzer = newAnalyzer();
        List<RelacionDetectada> r1 = analyzer.analizar(first.toString());
        List<RelacionDetectada> r2 = analyzer.analizar(second.toString());

        assertEquals(Set.of("Cliente", "Pedido"), classesIn(r1));
        assertEquals(Set.of("Autor", "Libro"), classesIn(r2),
            "El segundo análisis no debe contener clases del primero");
        assertEquals(Set.of("Autor", "Libro"), analyzer.getClassInfoCache().keySet());
    }

    @Test
    void reusingRelationDetectorClearsPreviousClasses() throws IOException {
        Path first = tempDir.resolve("a");
        write(first, "Uno.java", "public class Uno { private Dos dos; }");
        write(first, "Dos.java", "public class Dos { }");
        Path second = tempDir.resolve("b");
        write(second, "Tres.java", "public class Tres { }");

        RelationDetector detector = new RelationDetector();
        detector.loadClasses(first.toFile());
        detector.loadClasses(second.toFile());

        assertEquals(Set.of("Tres"), detector.getClassNames());
        assertTrue(detector.detectRelationsAsObjects().isEmpty());
    }

    @Test
    void unparseableFileDoesNotAbortAnalysisOfTheOthers() throws IOException {
        Path project = tempDir.resolve("mixed");
        write(project, "Broken.java", "public class Broken { private Long id ");
        write(project, "Order.java", "public class Order { private Long id; private Customer customer; }");
        write(project, "Customer.java", "public class Customer { private Long id; }");

        List<RelacionDetectada> relations = newAnalyzer().analizar(project.toString());

        assertTrue(relations.stream().anyMatch(r -> r.getOrigen().equals("Order")
                && r.getDestino().equals("Customer") && r.getTipo().equals("ManyToOne")),
            "La relación estructural Order -> Customer debe detectarse aunque otro fichero falle");
        assertFalse(classesIn(relations).contains("Broken"));
    }

    @Test
    void lastInconsistenciesAreResetBetweenAnalyses() throws IOException {
        Path annotated = tempDir.resolve("annotated");
        write(annotated, "Team.java", """
            import jakarta.persistence.*;
            public class Team { @OneToOne private Player player; }
            """);
        write(annotated, "Player.java", "public class Player { private Long id; }");
        Path plain = tempDir.resolve("plain");
        write(plain, "Solo.java", "public class Solo { }");

        IntelligentRelationAnalyzer analyzer = newAnalyzer();
        analyzer.analizar(annotated.toString());
        analyzer.analizar(plain.toString());

        assertTrue(analyzer.getLastInconsistencies().isEmpty(),
            "Las inconsistencias del análisis anterior no deben arrastrarse");
    }
}
