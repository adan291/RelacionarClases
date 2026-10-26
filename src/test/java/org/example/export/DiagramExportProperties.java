package org.example.export;

import java.util.List;

import org.example.model.RelacionDetectada;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for diagram export functionality.
 */
public class DiagramExportProperties {
    
    private final PlantUMLGenerator plantUMLGenerator = new PlantUMLGenerator();
    private final MermaidGenerator mermaidGenerator = new MermaidGenerator();
    private final DiagramExportService exportService = new DiagramExportService();
    
    /**
     * **Feature: diagram-export, Property 1: Valid PlantUML Syntax**
     * 
     * For any list of relations, the generated PlantUML output SHALL contain
     * valid @startuml and @enduml markers.
     * 
     * **Validates: Requirement 1.1**
     */
    @Property(tries = 100)
    void validPlantUMLSyntax(@ForAll("relations") List<RelacionDetectada> relations) {
        String output = plantUMLGenerator.generate(relations, ExportOptions.defaults());
        
        assert output.contains("@startuml") : "PlantUML must start with @startuml";
        assert output.contains("@enduml") : "PlantUML must end with @enduml";
        assert output.indexOf("@startuml") < output.indexOf("@enduml") : 
            "@startuml must come before @enduml";
    }
    
    /**
     * **Feature: diagram-export, Property 2: Valid Mermaid Syntax**
     * 
     * For any list of relations, the generated Mermaid output SHALL contain
     * valid erDiagram header.
     * 
     * **Validates: Requirement 2.1**
     */
    @Property(tries = 100)
    void validMermaidSyntax(@ForAll("relations") List<RelacionDetectada> relations) {
        String output = mermaidGenerator.generate(relations, ExportOptions.defaults());
        
        assert output.contains("erDiagram") : "Mermaid must contain erDiagram";
        assert output.contains("```mermaid") : "Mermaid must be in code block";
    }

    /**
     * **Feature: diagram-export, Property 3: All Entities Included**
     * 
     * For any list of relations, every class name from the relations SHALL
     * appear in the generated output.
     * 
     * **Validates: Requirements 1.2, 2.2**
     */
    @Property(tries = 100)
    void allEntitiesIncluded(@ForAll("relations") List<RelacionDetectada> relations) {
        if (relations.isEmpty()) return;
        
        String plantUML = plantUMLGenerator.generate(relations, ExportOptions.defaults());
        String mermaid = mermaidGenerator.generate(relations, ExportOptions.defaults());
        
        for (RelacionDetectada rel : relations) {
            assert plantUML.contains(rel.getOrigen()) : 
                "PlantUML must contain origin: " + rel.getOrigen();
            assert plantUML.contains(rel.getDestino()) : 
                "PlantUML must contain destination: " + rel.getDestino();
            // Mermaid uses UPPER_SNAKE_CASE for entity names
            String expectedMermaidName = mermaidGenerator.toMermaidName(rel.getOrigen());
            assert mermaid.contains(expectedMermaidName) : 
                "Mermaid must contain origin in UPPER_SNAKE_CASE: " + expectedMermaidName;
        }
    }
    
    /**
     * **Feature: diagram-export, Property 4: Cardinality Correctness**
     * 
     * For any relation, the generated output SHALL contain the correct
     * cardinality notation for the relation type.
     * 
     * **Validates: Requirements 1.3, 2.3**
     */
    @Property(tries = 100)
    void cardinalityCorrectness(@ForAll("singleRelation") RelacionDetectada relation) {
        List<RelacionDetectada> relations = List.of(relation);
        String plantUML = plantUMLGenerator.generate(relations, ExportOptions.defaults());
        String mermaid = mermaidGenerator.generate(relations, ExportOptions.defaults());
        
        // Verify PlantUML contains cardinality markers
        if (relation.getTipo().contains("Many")) {
            assert plantUML.contains("*") : "PlantUML Many relations must contain *";
        }
        if (relation.getTipo().contains("One")) {
            assert plantUML.contains("1") : "PlantUML One relations must contain 1";
        }
        
        // Verify Mermaid contains correct notation
        String expectedNotation = switch (relation.getTipo()) {
            case "OneToOne" -> "||--||";
            case "OneToMany" -> "||--o{";
            case "ManyToOne" -> "}o--||";
            case "ManyToMany" -> "}o--o{";
            default -> "--";
        };
        assert mermaid.contains(expectedNotation) : 
            "Mermaid must contain notation: " + expectedNotation;
    }
    
    /**
     * **Feature: diagram-export, Property 5: Filter Application**
     * 
     * For any list of relations and confidence threshold, the exported result
     * SHALL only contain relations meeting the threshold.
     * 
     * **Validates: Requirements 3.1, 3.2, 3.3**
     */
    @Property(tries = 100)
    void filterApplication(@ForAll("relations") List<RelacionDetectada> relations,
                          @ForAll("confidence") double minConfidence) {
        ExportOptions options = ExportOptions.defaults().withMinConfidence(minConfidence);
        ExportResult result = exportService.export(relations, options);
        
        long expectedCount = relations.stream()
            .filter(r -> r.getConfianza() >= minConfidence)
            .count();
        
        assert result.relationCount() == expectedCount : 
            "Filtered count should match: expected " + expectedCount + ", got " + result.relationCount();
    }
    
    // === Providers ===
    
    @Provide
    Arbitrary<List<RelacionDetectada>> relations() {
        return Arbitraries.integers().between(0, 5)
            .flatMap(count -> singleRelation().list().ofSize(count));
    }
    
    @Provide
    Arbitrary<RelacionDetectada> singleRelation() {
        Arbitrary<String> classNames = Arbitraries.of("User", "Order", "Product", "Category", "Item");
        Arbitrary<String> types = Arbitraries.of("OneToOne", "OneToMany", "ManyToOne", "ManyToMany");
        Arbitrary<Double> confidence = Arbitraries.doubles().between(0.0, 1.0);
        
        return Combinators.combine(classNames, classNames, types, confidence)
            .as((origen, destino, tipo, conf) -> 
                new RelacionDetectada(origen, destino, tipo, conf));
    }
    
    @Provide
    Arbitrary<Double> confidence() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }
}
