package org.example.ml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.analysis.JavaParsers;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.type.Type;

/**
 * Parses Java source files to extract enhanced ClassInfo with:
 * - Field annotations (including @Id detection)
 * - Generic types from collections
 * - Interfaces implemented
 * - Superclass information
 * - Constructor parameters
 * 
 * Implements Requirements 1.1, 1.2: Enhanced feature extraction for ML model.
 */
public class ClassInfoParser {

    private static final Logger LOGGER = Logger.getLogger(ClassInfoParser.class.getName());
    
    private final CardinalityAnalyzer cardinalityAnalyzer = new CardinalityAnalyzer();

    /**
     * Parses all Java files in a directory and returns a map of class name to ClassInfo.
     * 
     * @param sourceDir the directory to scan
     * @return map of class name to ClassInfo
     * @throws IOException if directory cannot be read
     */
    public Map<String, ClassInfo> parseDirectory(File sourceDir) throws IOException {
        return parseDirectory(sourceDir, null, null);
    }

    /**
     * Parses all Java files in a directory with configurable filters.
     * 
     * @param sourceDir the directory to scan
     * @param ignoredPaths paths to ignore (null for no filtering)
     * @param fileExtensions file extensions to include (null for .java only)
     * @return map of class name to ClassInfo
     * @throws IOException if directory cannot be read
     */
    public Map<String, ClassInfo> parseDirectory(File sourceDir, 
                                                  List<String> ignoredPaths,
                                                  List<String> fileExtensions) throws IOException {
        return parseDirectory(sourceDir, ignoredPaths, fileExtensions, false, new HashMap<>());
    }

    /**
     * Parsea el directorio una sola vez para el análisis completo: incluye también las interfaces
     * (marcadas con {@link ClassInfo#isInterfaceType()}) y respeta el orden en que se recorren los
     * ficheros. Con este resultado se obtienen las relaciones estructurales, los nombres para el
     * análisis semántico, las features de ML y las anotaciones JPA existentes.
     *
     * @return mapa en orden de recorrido (un nombre repetido conserva la posición de la primera vez)
     */
    public Map<String, ClassInfo> parseProject(File sourceDir,
                                               List<String> ignoredPaths,
                                               List<String> fileExtensions) throws IOException {
        return parseDirectory(sourceDir, ignoredPaths, fileExtensions, true, new LinkedHashMap<>());
    }

    private Map<String, ClassInfo> parseDirectory(File sourceDir,
                                                  List<String> ignoredPaths,
                                                  List<String> fileExtensions,
                                                  boolean includeInterfaces,
                                                  Map<String, ClassInfo> result) throws IOException {
        
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            LOGGER.warning(() -> "Invalid directory: " + sourceDir.getAbsolutePath());
            return result;
        }
        
        List<String> extensions = (fileExtensions != null && !fileExtensions.isEmpty())
            ? fileExtensions
            : List.of(".java");
        
        List<File> javaFiles;
        try (Stream<Path> paths = Files.walk(sourceDir.toPath())) {
            javaFiles = paths
            .filter(p -> {
                String pathStr = p.toString().replace('\\', '/');
                // Check ignored paths
                if (ignoredPaths != null) {
                    for (String ignored : ignoredPaths) {
                        if (pathStr.contains("/" + ignored + "/") ||
                            pathStr.endsWith("/" + ignored)) {
                            return false;
                        }
                    }
                }
                // Check extensions
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
        
        for (File file : javaFiles) {
            try {
                Map<String, ClassInfo> parsed = parseFile(file, includeInterfaces);
                result.putAll(parsed);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, e, () -> "Error parsing file: " + file.getName());
            }
        }
        
        LOGGER.fine(() -> "Parsed " + result.size() + " classes from " + sourceDir.getAbsolutePath());
        return result;
    }

    /**
     * Parses a single Java file and extracts ClassInfo for all classes.
     * 
     * @param file the Java file to parse
     * @return map of class name to ClassInfo
     * @throws IOException if file cannot be read
     */
    public Map<String, ClassInfo> parseFile(File file) throws IOException {
        return parseFile(file, false);
    }

    private Map<String, ClassInfo> parseFile(File file, boolean includeInterfaces) throws IOException {
        Map<String, ClassInfo> result = new LinkedHashMap<>();
        
        CompilationUnit cu = JavaParsers.parse(file);
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            if (!clazz.isInterface() || includeInterfaces) {
                ClassInfo classInfo = parseClass(clazz, packageName);
                classInfo.setInterfaceType(clazz.isInterface());
                result.put(classInfo.getClassName(), classInfo);
            }
        });
        
        return result;
    }

    /**
     * Parses a class declaration and extracts all relevant information.
     * 
     * @param clazz the class declaration
     * @param packageName the package name
     * @return populated ClassInfo
     */
    private ClassInfo parseClass(ClassOrInterfaceDeclaration clazz, String packageName) {
        ClassInfo classInfo = new ClassInfo(clazz.getNameAsString());
        
        // Parse interfaces
        clazz.getImplementedTypes().forEach(type -> 
            classInfo.addInterface(type.getNameAsString())
        );
        
        // Parse superclass
        clazz.getExtendedTypes().stream()
            .findFirst()
            .ifPresent(type -> classInfo.setSuperClass(type.getNameAsString()));
        
        // Parse fields with annotations
        for (FieldDeclaration field : clazz.getFields()) {
            parseField(field, classInfo);
        }
        
        // Parse constructor parameters
        clazz.getConstructors().stream()
            .max((c1, c2) -> Integer.compare(c1.getParameters().size(), c2.getParameters().size()))
            .ifPresent(constructor -> parseConstructor(constructor, classInfo));
        
        return classInfo;
    }

    /**
     * Parses a field declaration and adds FieldInfo to ClassInfo.
     * Extracts annotations, generic types, and collection information.
     * 
     * @param field the field declaration
     * @param classInfo the ClassInfo to populate
     */
    private void parseField(FieldDeclaration field, ClassInfo classInfo) {
        for (VariableDeclarator variable : field.getVariables()) {
            String fieldName = variable.getNameAsString();
            Type fieldType = variable.getType();
            String typeString = fieldType.asString();
            
            // Extract annotations
            List<String> annotations = field.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .collect(Collectors.toList());
            
            // Determine if collection and extract generic type
            boolean isCollection = cardinalityAnalyzer.isCollectionType(typeString) ||
                                   cardinalityAnalyzer.isArrayType(typeString);
            String genericType = isCollection ? cardinalityAnalyzer.extractGenericType(typeString) : null;
            
            // Create FieldInfo
            FieldInfo fieldInfo = new FieldInfo(fieldName, typeString, isCollection, genericType, annotations,
                relationMappedBy(field));
            classInfo.addField(fieldInfo);
            
            // Also add to legacy fieldTypes list
            classInfo.addFieldType(typeString);
            
            // Track collection types
            if (isCollection && genericType != null) {
                classInfo.addCollectionType(genericType);
            }
        }
    }

    /**
     * {@code mappedBy} de la primera anotación JPA de relación del campo (igual que hacía
     * ExistingAnnotationScanner al recorrer los ficheros por su cuenta).
     */
    private static String relationMappedBy(FieldDeclaration field) {
        for (AnnotationExpr annotation : field.getAnnotations()) {
            if (org.example.analysis.ExistingAnnotationScanner.relationTypeOf(annotation.getNameAsString()) == null) {
                continue;
            }
            if (annotation instanceof NormalAnnotationExpr normal) {
                for (MemberValuePair pair : normal.getPairs()) {
                    if ("mappedBy".equals(pair.getNameAsString())) {
                        return pair.getValue().toString().replace("\"", "");
                    }
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Parses constructor parameters and adds them to ClassInfo.
     * 
     * @param constructor the constructor declaration
     * @param classInfo the ClassInfo to populate
     */
    private void parseConstructor(ConstructorDeclaration constructor, ClassInfo classInfo) {
        constructor.getParameters().forEach(param -> {
            String paramType = param.getType().asString();
            // Extract base type for generics
            if (paramType.contains("<")) {
                paramType = paramType.substring(0, paramType.indexOf('<'));
            }
            classInfo.addConstructorParam(paramType);
        });
    }

    /**
     * Parses a single class from source code string.
     * Useful for testing and dynamic parsing.
     * 
     * @param sourceCode the Java source code
     * @return ClassInfo for the first class found, or null if none
     */
    public ClassInfo parseSource(String sourceCode) {
        try {
            CompilationUnit cu = JavaParsers.parse(sourceCode);
            
            return cu.findFirst(ClassOrInterfaceDeclaration.class)
                .filter(c -> !c.isInterface())
                .map(c -> parseClass(c, ""))
                .orElse(null);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> "Error parsing source code");
            return null;
        }
    }

    /**
     * Checks if a ClassInfo has an @Id annotated field.
     * 
     * @param classInfo the ClassInfo to check
     * @return true if the class has an @Id field
     */
    public static boolean hasIdField(ClassInfo classInfo) {
        if (classInfo == null) return false;
        return classInfo.getFields().stream().anyMatch(FieldInfo::isIdField);
    }

    /**
     * Gets all fields with JPA relation annotations.
     * 
     * @param classInfo the ClassInfo to check
     * @return list of FieldInfo with relation annotations
     */
    public static List<FieldInfo> getRelationFields(ClassInfo classInfo) {
        if (classInfo == null) return List.of();
        return classInfo.getFields().stream()
            .filter(f -> f.hasAnnotation("OneToOne") || f.hasAnnotation("OneToMany") ||
                        f.hasAnnotation("ManyToOne") || f.hasAnnotation("ManyToMany"))
            .collect(Collectors.toList());
    }

    /**
     * Gets all collection fields from a ClassInfo.
     * 
     * @param classInfo the ClassInfo to check
     * @return list of collection FieldInfo
     */
    public static List<FieldInfo> getCollectionFields(ClassInfo classInfo) {
        if (classInfo == null) return List.of();
        return classInfo.getFields().stream()
            .filter(FieldInfo::isCollection)
            .collect(Collectors.toList());
    }
}
