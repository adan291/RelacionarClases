package org.example.codegen;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.model.RelacionDetectada;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

/**
 * 🔧 JPACodeGenerator
 * Genera y aplica anotaciones JPA directamente en archivos Java.
 */
public class JPACodeGenerator {

    private static final Logger LOGGER = Logger.getLogger(JPACodeGenerator.class.getName());

    /**
     * Resultado de una operación de generación de código.
     */
    public static class GenerationResult {
        public final String className;
        public final String fieldName;
        public final boolean success;
        public final String message;
        public final String preview;

        public GenerationResult(String className, String fieldName, boolean success, String message, String preview) {
            this.className = className;
            this.fieldName = fieldName;
            this.success = success;
            this.message = message;
            this.preview = preview;
        }
    }

    /**
     * Genera preview del código que se insertaría sin modificar archivos.
     */
    public List<GenerationResult> previewChanges(File sourceDir, List<RelacionDetectada> relaciones) {
        List<GenerationResult> results = new ArrayList<>();
        
        // Agrupar relaciones por clase origen
        Map<String, List<RelacionDetectada>> byClass = groupByOrigin(relaciones);
        
        for (Map.Entry<String, List<RelacionDetectada>> entry : byClass.entrySet()) {
            String className = entry.getKey();
            File classFile = findClassFile(sourceDir, className);
            
            if (classFile == null) {
                results.add(new GenerationResult(className, null, false, 
                    "Archivo no encontrado", null));
                continue;
            }
            
            for (RelacionDetectada rel : entry.getValue()) {
                if (rel.isExistingAnnotation()) {
                    results.add(new GenerationResult(className, rel.getDestino().toLowerCase(),
                        true, "Ya anotada", "// Sin cambios necesarios"));
                    continue;
                }
                
                String preview = generateAnnotationCode(rel);
                results.add(new GenerationResult(className, rel.getDestino().toLowerCase(),
                    true, "Listo para aplicar", preview));
            }
        }
        
        return results;
    }

    /**
     * Aplica las anotaciones JPA a los archivos Java.
     * 
     * @param sourceDir directorio con los archivos .java
     * @param relaciones relaciones a anotar
     * @return lista de resultados de la operación
     */
    public List<GenerationResult> applyAnnotations(File sourceDir, List<RelacionDetectada> relaciones) {
        List<GenerationResult> results = new ArrayList<>();
        
        // Agrupar relaciones por clase origen
        Map<String, List<RelacionDetectada>> byClass = groupByOrigin(relaciones);
        
        for (Map.Entry<String, List<RelacionDetectada>> entry : byClass.entrySet()) {
            String className = entry.getKey();
            List<RelacionDetectada> classRelations = entry.getValue();
            
            File classFile = findClassFile(sourceDir, className);
            if (classFile == null) {
                results.add(new GenerationResult(className, null, false,
                    "Archivo " + className + ".java no encontrado", null));
                continue;
            }
            
            try {
                List<GenerationResult> classResults = applyToClass(classFile, classRelations);
                results.addAll(classResults);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error procesando " + className, e);
                results.add(new GenerationResult(className, null, false,
                    "Error: " + e.getMessage(), null));
            }
        }
        
        return results;
    }

    /**
     * Aplica anotaciones a una clase específica.
     */
    private List<GenerationResult> applyToClass(File classFile, List<RelacionDetectada> relations) 
            throws IOException {
        List<GenerationResult> results = new ArrayList<>();
        
        CompilationUnit cu = StaticJavaParser.parse(classFile);
        boolean modified = false;
        
        Optional<ClassOrInterfaceDeclaration> classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class);
        if (classDecl.isEmpty()) {
            results.add(new GenerationResult(classFile.getName(), null, false,
                "No se encontró declaración de clase", null));
            return results;
        }
        
        ClassOrInterfaceDeclaration clazz = classDecl.get();
        
        for (RelacionDetectada rel : relations) {
            if (rel.isExistingAnnotation()) {
                results.add(new GenerationResult(rel.getOrigen(), rel.getDestino().toLowerCase(),
                    true, "Ya anotada, sin cambios", null));
                continue;
            }
            
            // Buscar el campo correspondiente
            String fieldName = findFieldName(clazz, rel.getDestino());
            if (fieldName == null) {
                results.add(new GenerationResult(rel.getOrigen(), rel.getDestino().toLowerCase(),
                    false, "Campo no encontrado para " + rel.getDestino(), null));
                continue;
            }
            
            Optional<FieldDeclaration> field = clazz.getFieldByName(fieldName);
            if (field.isEmpty()) {
                results.add(new GenerationResult(rel.getOrigen(), fieldName,
                    false, "Campo " + fieldName + " no encontrado", null));
                continue;
            }
            
            // Verificar si ya tiene anotación JPA
            if (hasJPAAnnotation(field.get())) {
                results.add(new GenerationResult(rel.getOrigen(), fieldName,
                    true, "Ya tiene anotación JPA", null));
                continue;
            }
            
            // Agregar anotación
            addAnnotation(cu, field.get(), rel);
            modified = true;
            
            String preview = generateAnnotationCode(rel);
            results.add(new GenerationResult(rel.getOrigen(), fieldName,
                true, "Anotación aplicada", preview));
        }
        
        // Guardar archivo si hubo modificaciones
        if (modified) {
            // Crear backup
            File backup = new File(classFile.getPath() + ".bak");
            Files.copy(classFile.toPath(), backup.toPath(), 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            // Guardar archivo modificado
            try (FileWriter writer = new FileWriter(classFile)) {
                writer.write(cu.toString());
            }
        }
        
        return results;
    }

    /**
     * Agrega la anotación JPA al campo.
     */
    private void addAnnotation(CompilationUnit cu, FieldDeclaration field, RelacionDetectada rel) {
        String tipo = rel.getTipo();
        
        // Agregar imports necesarios
        addImportIfMissing(cu, "jakarta.persistence." + tipo);
        
        if (tipo.equals("ManyToOne") || (tipo.equals("OneToOne") && rel.isOwnerSide())) {
            addImportIfMissing(cu, "jakarta.persistence.JoinColumn");
            addImportIfMissing(cu, "jakarta.persistence.FetchType");
            
            // @ManyToOne(fetch = FetchType.LAZY)
            NormalAnnotationExpr annotation = new NormalAnnotationExpr();
            annotation.setName(tipo);
            annotation.addPair("fetch", "FetchType.LAZY");
            field.addAnnotation(annotation);
            
            // @JoinColumn(name = "destino_id")
            NormalAnnotationExpr joinCol = new NormalAnnotationExpr();
            joinCol.setName("JoinColumn");
            joinCol.addPair("name", new StringLiteralExpr(rel.getDestino().toLowerCase() + "_id"));
            field.addAnnotation(joinCol);
            
        } else if (tipo.equals("OneToMany")) {
            addImportIfMissing(cu, "jakarta.persistence.CascadeType");
            addImportIfMissing(cu, "jakarta.persistence.FetchType");
            
            NormalAnnotationExpr annotation = new NormalAnnotationExpr();
            annotation.setName(tipo);
            
            if (rel.isBidirectional() && !rel.isOwnerSide()) {
                annotation.addPair("mappedBy", new StringLiteralExpr(
                    rel.getMappedBy() != null ? rel.getMappedBy() : rel.getOrigen().toLowerCase()));
            }
            annotation.addPair("cascade", "CascadeType.ALL");
            annotation.addPair("fetch", "FetchType.LAZY");
            field.addAnnotation(annotation);
            
        } else if (tipo.equals("OneToOne") && !rel.isOwnerSide()) {
            NormalAnnotationExpr annotation = new NormalAnnotationExpr();
            annotation.setName(tipo);
            annotation.addPair("mappedBy", new StringLiteralExpr(
                rel.getMappedBy() != null ? rel.getMappedBy() : rel.getOrigen().toLowerCase()));
            field.addAnnotation(annotation);
            
        } else if (tipo.equals("ManyToMany")) {
            addImportIfMissing(cu, "jakarta.persistence.FetchType");
            
            NormalAnnotationExpr annotation = new NormalAnnotationExpr();
            annotation.setName(tipo);
            annotation.addPair("fetch", "FetchType.LAZY");
            
            if (rel.isBidirectional() && !rel.isOwnerSide()) {
                annotation.addPair("mappedBy", new StringLiteralExpr(
                    rel.getMappedBy() != null ? rel.getMappedBy() : rel.getOrigen().toLowerCase() + "s"));
            }
            field.addAnnotation(annotation);
        }
    }

    /**
     * Agrega un import si no existe.
     */
    private void addImportIfMissing(CompilationUnit cu, String importName) {
        boolean exists = cu.getImports().stream()
            .anyMatch(i -> i.getNameAsString().equals(importName));
        
        if (!exists) {
            cu.addImport(new ImportDeclaration(importName, false, false));
        }
    }

    /**
     * Verifica si un campo ya tiene anotación JPA de relación.
     */
    private boolean hasJPAAnnotation(FieldDeclaration field) {
        return field.getAnnotations().stream()
            .anyMatch(a -> {
                String name = a.getNameAsString();
                return name.equals("OneToOne") || name.equals("OneToMany") ||
                       name.equals("ManyToOne") || name.equals("ManyToMany");
            });
    }

    /**
     * Busca el nombre del campo que corresponde al tipo destino.
     */
    private String findFieldName(ClassOrInterfaceDeclaration clazz, String targetType) {
        for (FieldDeclaration field : clazz.getFields()) {
            String fieldType = field.getElementType().asString();
            
            // Coincidencia directa
            if (fieldType.equals(targetType)) {
                return field.getVariable(0).getNameAsString();
            }
            
            // Coincidencia en genérico (List<TargetType>)
            if (fieldType.contains("<" + targetType + ">")) {
                return field.getVariable(0).getNameAsString();
            }
        }
        return null;
    }

    /**
     * Busca el archivo .java de una clase.
     */
    private File findClassFile(File sourceDir, String className) {
        try {
            return Files.walk(sourceDir.toPath())
                .filter(p -> p.getFileName().toString().equals(className + ".java"))
                .map(p -> p.toFile())
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Agrupa relaciones por clase origen.
     */
    private Map<String, List<RelacionDetectada>> groupByOrigin(List<RelacionDetectada> relaciones) {
        Map<String, List<RelacionDetectada>> map = new HashMap<>();
        for (RelacionDetectada rel : relaciones) {
            map.computeIfAbsent(rel.getOrigen(), k -> new ArrayList<>()).add(rel);
        }
        return map;
    }

    /**
     * Genera el código de anotación como string para preview.
     */
    private String generateAnnotationCode(RelacionDetectada rel) {
        StringBuilder sb = new StringBuilder();
        String tipo = rel.getTipo();
        String destino = rel.getDestino();
        
        switch (tipo) {
            case "ManyToOne" -> {
                sb.append("@ManyToOne(fetch = FetchType.LAZY)\n");
                sb.append("@JoinColumn(name = \"").append(destino.toLowerCase()).append("_id\")\n");
                sb.append("private ").append(destino).append(" ").append(destino.toLowerCase()).append(";");
            }
            case "OneToMany" -> {
                sb.append("@OneToMany(");
                if (rel.isBidirectional() && !rel.isOwnerSide()) {
                    String mappedBy = rel.getMappedBy() != null ? rel.getMappedBy() : rel.getOrigen().toLowerCase();
                    sb.append("mappedBy = \"").append(mappedBy).append("\", ");
                }
                sb.append("cascade = CascadeType.ALL, fetch = FetchType.LAZY)\n");
                sb.append("private List<").append(destino).append("> ").append(destino.toLowerCase()).append("s;");
            }
            case "OneToOne" -> {
                if (rel.isOwnerSide()) {
                    sb.append("@OneToOne(fetch = FetchType.LAZY)\n");
                    sb.append("@JoinColumn(name = \"").append(destino.toLowerCase()).append("_id\")\n");
                } else {
                    String mappedBy = rel.getMappedBy() != null ? rel.getMappedBy() : rel.getOrigen().toLowerCase();
                    sb.append("@OneToOne(mappedBy = \"").append(mappedBy).append("\")\n");
                }
                sb.append("private ").append(destino).append(" ").append(destino.toLowerCase()).append(";");
            }
            case "ManyToMany" -> {
                sb.append("@ManyToMany(fetch = FetchType.LAZY");
                if (rel.isBidirectional() && !rel.isOwnerSide()) {
                    String mappedBy = rel.getMappedBy() != null ? rel.getMappedBy() : rel.getOrigen().toLowerCase() + "s";
                    sb.append(", mappedBy = \"").append(mappedBy).append("\"");
                }
                sb.append(")\n");
                sb.append("private Set<").append(destino).append("> ").append(destino.toLowerCase()).append("s;");
            }
            default -> sb.append("// Tipo no soportado: ").append(tipo);
        }
        
        return sb.toString();
    }
}
