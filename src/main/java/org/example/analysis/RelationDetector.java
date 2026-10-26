package org.example.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.model.RelacionDetectada;

import com.github.javaparser.ParseProblemException;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.Type;

/**
 * 🧩 RelationDetector
 * --------------------
 * Analiza las clases Java para detectar relaciones estructurales básicas.
 * Genera objetos {@link RelacionDetectada} con alta confianza (estructural).
 *
 * Este detector no usa IA; se centra en:
 *  - Campos directos → ManyToOne
 *  - Colecciones → OneToMany
 *
 * Las coincidencias semánticas son manejadas por {@link org.example.logic.SemanticRelationDetector}.
 */
public class RelationDetector {

    private static final Logger LOGGER = Logger.getLogger(RelationDetector.class.getName());
    
    /** Mapa: nombre de clase → lista de tipos de campo detectados */
    private final Map<String, List<String>> classFields = new HashMap<>();

    // 🔹 Interfaz para actualizar progreso (opcional)
    public interface ProgressListener {
        void onProgress(double progress, String currentFile);
    }

    /**
     * Carga todas las clases (.java) del directorio dado
     * y registra sus campos (para posterior análisis).
     *
     * @param sourceDir carpeta donde buscar las clases
     * @param listener  callback opcional para mostrar progreso
     */
    public void loadClasses(File sourceDir, ProgressListener listener) throws IOException {
        loadClasses(sourceDir, listener, null, null);
    }
    
    /**
     * Carga clases del directorio dado con filtros configurables.
     *
     * @param sourceDir carpeta donde buscar las clases
     * @param listener  callback opcional para mostrar progreso
     * @param ignoredPaths lista de rutas a ignorar (null para no filtrar)
     * @param fileExtensions lista de extensiones a incluir (null para .java por defecto)
     */
    public void loadClasses(File sourceDir, ProgressListener listener, 
                           List<String> ignoredPaths, List<String> fileExtensions) throws IOException {
        if (!sourceDir.exists()) {
            throw new IOException("El directorio especificado no existe: " + sourceDir.getAbsolutePath());
        }
        
        // Default to .java if no extensions specified
        List<String> extensions = (fileExtensions != null && !fileExtensions.isEmpty()) 
            ? fileExtensions 
            : List.of(".java");

        // Cada carga empieza de cero: reutilizar el detector no mezcla análisis sucesivos
        classFields.clear();

        List<File> javaFiles;
        try (Stream<Path> paths = Files.walk(sourceDir.toPath())) {
            javaFiles = paths
                .filter(p -> {
                    String pathStr = p.toString().replace('\\', '/');
                    // Check if path should be ignored
                    if (ignoredPaths != null) {
                        for (String ignored : ignoredPaths) {
                            if (pathStr.contains("/" + ignored + "/") || 
                                pathStr.endsWith("/" + ignored) ||
                                pathStr.contains("\\" + ignored + "\\") ||
                                pathStr.endsWith("\\" + ignored)) {
                                return false;
                            }
                        }
                    }
                    // Check file extension
                    for (String ext : extensions) {
                        if (pathStr.endsWith(ext)) {
                            return true;
                        }
                    }
                    return false;
                })
                .map(p -> p.toFile())
                .collect(Collectors.toList());
        }

        int total = javaFiles.size();
        int count = 0;

        for (File file : javaFiles) {
            try {
                CompilationUnit cu = JavaParsers.parse(file);

                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                    List<String> fields = clazz.getFields().stream()
                            .map(this::getFieldType)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    classFields.put(clazz.getNameAsString(), fields);
                });
            } catch (ParseProblemException | IOException e) {
                // Un fichero que no compila no debe tumbar el análisis del resto
                LOGGER.log(Level.WARNING, () -> "No se pudo analizar " + file + ": " + e.getMessage());
            }

            count++;
            if (listener != null) {
                double progress = (double) count / total;
                listener.onProgress(progress, file.getName());
            }
        }

        LOGGER.fine(() -> "Clases cargadas: " + classFields.keySet());
    }

    /**
     * Devuelve el tipo base de un campo (por ejemplo: "Ciudad", "List<Empleado>", etc.)
     */
    private String getFieldType(FieldDeclaration field) {
        Type type = field.getElementType();
        return type != null ? type.asString() : null;
    }

    /**
     * Detecta relaciones estructurales directas y las devuelve como objetos {@link RelacionDetectada}.
     */
    public List<RelacionDetectada> detectRelationsAsObjects() {
        List<RelacionDetectada> relations = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : classFields.entrySet()) {
            String className = entry.getKey();
            List<String> fields = entry.getValue();

            for (String fieldType : fields) {
                if (classFields.containsKey(fieldType)) {
                    // Campo directo de otra clase → ManyToOne
                    relations.add(new RelacionDetectada(className, fieldType, "ManyToOne", 0.95));
                }
                else if (isCollectionOfEntity(fieldType)) {
                    String innerType = extractGenericType(fieldType);
                    if (classFields.containsKey(innerType)) {
                        relations.add(new RelacionDetectada(className, innerType, "OneToMany", 0.95));
                    }
                }
            }
        }

        // Eliminar duplicados
        return new ArrayList<>(new LinkedHashSet<>(relations));
    }

    /** Comprueba si el campo es una colección genérica */
    private boolean isCollectionOfEntity(String fieldType) {
        return fieldType.startsWith("List<") || fieldType.startsWith("Set<") || fieldType.startsWith("Collection<");
    }

    /** Extrae el tipo interno de un campo genérico (por ejemplo: "List<Usuario>" → "Usuario") */
    private String extractGenericType(String fieldType) {
        int start = fieldType.indexOf('<');
        int end = fieldType.indexOf('>');
        if (start >= 0 && end > start) {
            return fieldType.substring(start + 1, end).trim();
        }
        return fieldType;
    }

    /**
     * Devuelve la lista de clases encontradas (para análisis semántico posterior).
     */
    public Set<String> getClassNames() {
        return new HashSet<>(classFields.keySet());
    }

    /**
     * 🔄 Sobrecarga sin listener — permite usar el método como antes.
     */
    public void loadClasses(File sourceDir) throws IOException {
        loadClasses(sourceDir, null, null, null);
    }
    
    /**
     * Carga clases con filtros pero sin listener de progreso.
     */
    public void loadClasses(File sourceDir, List<String> ignoredPaths, List<String> fileExtensions) throws IOException {
        loadClasses(sourceDir, null, ignoredPaths, fileExtensions);
    }
}
