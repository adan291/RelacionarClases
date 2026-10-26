package org.example.analysis;


import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.example.ml.PredictionSource;
import org.example.model.RelacionDetectada;

/**
 * 📖 ExistingAnnotationScanner
 * Escanea clases Java para detectar anotaciones JPA existentes.
 * Permite comparar con predicciones y evitar sugerir relaciones ya anotadas.
 */
public class ExistingAnnotationScanner {

    private static final Logger LOGGER = Logger.getLogger(ExistingAnnotationScanner.class.getName());
    
    // Anotaciones JPA de relación que buscamos
    private static final List<String> JPA_ANNOTATIONS = List.of(
        "OneToOne", "OneToMany", "ManyToOne", "ManyToMany",
        "javax.persistence.OneToOne", "javax.persistence.OneToMany",
        "javax.persistence.ManyToOne", "javax.persistence.ManyToMany",
        "jakarta.persistence.OneToOne", "jakarta.persistence.OneToMany",
        "jakarta.persistence.ManyToOne", "jakarta.persistence.ManyToMany"
    );

    /**
     * Escanea un directorio y encuentra todas las relaciones JPA ya anotadas.
     * 
     * @param sourceDir directorio con archivos .java
     * @return lista de relaciones existentes encontradas
     */
    public List<RelacionDetectada> scanExistingAnnotations(File sourceDir) {
        List<RelacionDetectada> existingRelations = new ArrayList<>();
        
        if (sourceDir == null || !sourceDir.exists()) {
            return existingRelations;
        }

        try (Stream<java.nio.file.Path> paths = Files.walk(sourceDir.toPath())) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(path -> {
                     try {
                         List<RelacionDetectada> relations = scanFile(path.toFile());
                         existingRelations.addAll(relations);
                     } catch (Exception e) {
                         LOGGER.log(Level.FINE, "Error escaneando: " + path, e);
                     }
                 });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error recorriendo directorio", e);
        }

        return existingRelations;
    }

    /**
     * Escanea un archivo Java individual.
     */
    private List<RelacionDetectada> scanFile(File file) throws IOException {
        List<RelacionDetectada> relations = new ArrayList<>();
        
        CompilationUnit cu = JavaParsers.parse(file);
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            String className = clazz.getNameAsString();
            
            clazz.getFields().forEach(field -> {
                Optional<AnnotationInfo> annotation = findJPAAnnotation(field);
                if (annotation.isPresent()) {
                    AnnotationInfo info = annotation.get();
                    String targetType = extractTargetType(field, info);
                    
                    if (targetType != null && !targetType.isEmpty()) {
                        RelacionDetectada rel = new RelacionDetectada(
                            className, targetType, info.type, 1.0, PredictionSource.STRUCTURAL
                        );
                        rel.setExistingAnnotation(true);
                        
                        // Extraer mappedBy si existe
                        if (info.mappedBy != null) {
                            rel.setBidirectional(true);
                            rel.setOwnerSide(false);
                            rel.setMappedBy(info.mappedBy);
                        }
                        
                        relations.add(rel);
                    }
                }
            });
        });
        
        return relations;
    }

    /**
     * Busca una anotación JPA de relación en un campo.
     */
    private Optional<AnnotationInfo> findJPAAnnotation(FieldDeclaration field) {
        for (AnnotationExpr annotation : field.getAnnotations()) {
            String name = annotation.getNameAsString();
            
            // Verificar si es una anotación JPA
            String relationType = extractRelationType(name);
            if (relationType != null) {
                AnnotationInfo info = new AnnotationInfo();
                info.type = relationType;
                
                // Extraer mappedBy si es NormalAnnotationExpr
                if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
                    for (MemberValuePair pair : normalAnnotation.getPairs()) {
                        if ("mappedBy".equals(pair.getNameAsString())) {
                            info.mappedBy = pair.getValue().toString().replace("\"", "");
                        }
                    }
                }
                
                return Optional.of(info);
            }
        }
        return Optional.empty();
    }

    /**
     * Relaciones JPA ya anotadas, a partir de un modelo ya parseado (sin volver a leer los ficheros).
     * Usa las mismas reglas que {@link #scanExistingAnnotations(File)}: primera anotación de relación
     * del campo, tipo destino sin {@code []} y, si es genérico, lo que hay entre '<' y el último '>'.
     *
     * @param clases modelo parseado (incluidas interfaces)
     * @return relaciones existentes, marcadas como tales
     */
    public List<RelacionDetectada> scanExistingAnnotations(Map<String, org.example.ml.ClassInfo> clases) {
        List<RelacionDetectada> existingRelations = new ArrayList<>();
        for (Map.Entry<String, org.example.ml.ClassInfo> entry : clases.entrySet()) {
            for (org.example.ml.FieldInfo field : entry.getValue().getFields()) {
                String type = null;
                for (String annotation : field.annotations()) {
                    type = relationTypeOf(annotation);
                    if (type != null) {
                        break;
                    }
                }
                if (type == null || field.type() == null) {
                    continue;
                }
                String targetType = extractTargetType(elementType(field.type()));
                if (targetType == null || targetType.isEmpty()) {
                    continue;
                }
                RelacionDetectada rel = new RelacionDetectada(
                    entry.getKey(), targetType, type, 1.0, PredictionSource.STRUCTURAL);
                rel.setExistingAnnotation(true);
                if (field.mappedBy() != null) {
                    rel.setBidirectional(true);
                    rel.setOwnerSide(false);
                    rel.setMappedBy(field.mappedBy());
                }
                existingRelations.add(rel);
            }
        }
        return existingRelations;
    }

    private static String elementType(String type) {
        String t = type.trim();
        while (t.endsWith("[]")) {
            t = t.substring(0, t.length() - 2).trim();
        }
        return t;
    }

    /**
     * @param annotationName nombre de la anotación tal y como aparece en el código
     * @return OneToOne, OneToMany, ManyToOne o ManyToMany, o null si no es una anotación JPA de relación
     */
    public static String relationTypeOf(String annotationName) {
        for (String jpaAnnotation : JPA_ANNOTATIONS) {
            if (jpaAnnotation.equals(annotationName) || jpaAnnotation.endsWith("." + annotationName)) {
                int lastDot = jpaAnnotation.lastIndexOf('.');
                return lastDot >= 0 ? jpaAnnotation.substring(lastDot + 1) : jpaAnnotation;
            }
        }
        return null;
    }

    /**
     * Extrae el tipo de relación de una anotación.
     */
    private String extractRelationType(String annotationName) {
        for (String jpaAnnotation : JPA_ANNOTATIONS) {
            if (jpaAnnotation.equals(annotationName) || jpaAnnotation.endsWith("." + annotationName)) {
                // Extraer solo el nombre simple (OneToMany, etc.)
                int lastDot = jpaAnnotation.lastIndexOf('.');
                return lastDot >= 0 ? jpaAnnotation.substring(lastDot + 1) : jpaAnnotation;
            }
        }
        return null;
    }

    /**
     * Extrae el tipo destino de un campo anotado.
     */
    private String extractTargetType(FieldDeclaration field, AnnotationInfo info) {
        return extractTargetType(field.getElementType().asString());
    }

    private static String extractTargetType(String fieldType) {
        // Si es colección, extraer tipo genérico
        if (fieldType.contains("<")) {
            int start = fieldType.indexOf('<');
            int end = fieldType.lastIndexOf('>');
            if (start >= 0 && end > start) {
                return fieldType.substring(start + 1, end).trim();
            }
        }
        
        return fieldType;
    }

    /**
     * Compara relaciones detectadas con existentes y marca las que ya están anotadas.
     * 
     * @param detected relaciones detectadas por el analizador
     * @param existing relaciones ya anotadas en el código
     * @return lista filtrada sin las relaciones ya existentes
     */
    public List<RelacionDetectada> filterAlreadyAnnotated(
            List<RelacionDetectada> detected, 
            List<RelacionDetectada> existing) {
        
        // Crear mapa de relaciones existentes para búsqueda rápida
        Map<String, RelacionDetectada> existingMap = new HashMap<>();
        for (RelacionDetectada rel : existing) {
            existingMap.put(rel.getClaveMemoria(), rel);
        }
        
        List<RelacionDetectada> filtered = new ArrayList<>();
        for (RelacionDetectada rel : detected) {
            String key = rel.getClaveMemoria();
            if (existingMap.containsKey(key)) {
                // Marcar como ya existente pero incluir para referencia
                rel.setExistingAnnotation(true);
                rel.setConfianza(1.0); // Confianza máxima porque ya existe
            }
            filtered.add(rel);
        }
        
        return filtered;
    }

    /**
     * Detecta inconsistencias entre relaciones detectadas y existentes.
     * Por ejemplo: detectamos ManyToOne pero está anotado como OneToOne.
     */
    public List<AnnotationInconsistency> findInconsistencies(
            List<RelacionDetectada> detected,
            List<RelacionDetectada> existing) {
        
        List<AnnotationInconsistency> inconsistencies = new ArrayList<>();
        
        Map<String, RelacionDetectada> existingByPair = new HashMap<>();
        for (RelacionDetectada rel : existing) {
            String pairKey = rel.getOrigen() + "->" + rel.getDestino();
            existingByPair.put(pairKey, rel);
        }
        
        for (RelacionDetectada det : detected) {
            String pairKey = det.getOrigen() + "->" + det.getDestino();
            RelacionDetectada exist = existingByPair.get(pairKey);
            
            if (exist != null && !det.getTipo().equals(exist.getTipo())) {
                inconsistencies.add(new AnnotationInconsistency(
                    det.getOrigen(), det.getDestino(),
                    exist.getTipo(), det.getTipo(),
                    det.getConfianza()
                ));
            }
        }
        
        return inconsistencies;
    }

    /**
     * Información de una anotación encontrada.
     */
    private static class AnnotationInfo {
        String type;
        String mappedBy;
    }

    /**
     * Representa una inconsistencia entre anotación existente y detectada.
     */
    public static class AnnotationInconsistency {
        public final String origen;
        public final String destino;
        public final String existingType;
        public final String suggestedType;
        public final double confidence;

        public AnnotationInconsistency(String origen, String destino, 
                                       String existingType, String suggestedType,
                                       double confidence) {
            this.origen = origen;
            this.destino = destino;
            this.existingType = existingType;
            this.suggestedType = suggestedType;
            this.confidence = confidence;
        }

        @Override
        public String toString() {
            return String.format("⚠️ %s -> %s: anotado como %s, sugerido %s (%.0f%% confianza)",
                origen, destino, existingType, suggestedType, confidence * 100);
        }
    }
}
