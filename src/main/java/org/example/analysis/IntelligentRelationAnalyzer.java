package org.example.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.example.config.AnalysisConfig;
import org.example.config.ConfigManager;
import org.example.ml.ClassInfo;
import org.example.ml.ClassInfoParser;
import org.example.ml.MLRelationPredictor;
import org.example.ml.PredictionSource;
import org.example.ml.ScoreCombiner;
import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;

/**
 * 🔮 IntelligentRelationAnalyzer
 * ----------------------------------
 * Combina cuatro niveles de análisis:
 * 1️⃣ Estructural (RelationDetector)
 * 2️⃣ Semántico (nombres similares)
 * 3️⃣ Aprendizaje basado en memoria (MemoryService)
 * 4️⃣ Machine Learning (MLRelationPredictor) - NEW
 *
 * Devuelve una lista de relaciones detectadas con nivel de confianza (0–1).
 * 
 * Implements Requirements 4.1: Combine ML predictions with structural and semantic analysis.
 */
public class IntelligentRelationAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(IntelligentRelationAnalyzer.class.getName());
    
    private final RelationDetector relationDetector;
    private final MemoryService memoryService;
    private final MLRelationPredictor mlPredictor;
    private final ScoreCombiner scoreCombiner;
    private final ExistingAnnotationScanner annotationScanner;
    private final ClassInfoParser classInfoParser;
    
    // Configuration from ConfigManager
    private final List<String> ignoredPaths;
    private final List<String> fileExtensions;
    
    // Almacena inconsistencias encontradas
    private List<ExistingAnnotationScanner.AnnotationInconsistency> lastInconsistencies = new ArrayList<>();
    
    // Cache for parsed class info
    private Map<String, ClassInfo> classInfoCache = new HashMap<>();

    public IntelligentRelationAnalyzer(MemoryService memoryService) {
        this.relationDetector = new RelationDetector();
        this.annotationScanner = new ExistingAnnotationScanner();
        this.memoryService = memoryService;
        this.mlPredictor = new MLRelationPredictor(memoryService);
        this.scoreCombiner = new ScoreCombiner();
        this.classInfoParser = new ClassInfoParser();
        
        // Load configuration from ConfigManager
        AnalysisConfig config = ConfigManager.getInstance().getAnalysisConfig();
        this.ignoredPaths = config.ignoredPaths();
        this.fileExtensions = config.fileExtensions();
    }
    
    /**
     * Constructor with dependency injection for testing.
     */
    public IntelligentRelationAnalyzer(MemoryService memoryService, 
                                        MLRelationPredictor mlPredictor,
                                        ScoreCombiner scoreCombiner) {
        this.relationDetector = new RelationDetector();
        this.annotationScanner = new ExistingAnnotationScanner();
        this.memoryService = memoryService;
        this.mlPredictor = mlPredictor != null ? mlPredictor : new MLRelationPredictor(memoryService);
        this.scoreCombiner = scoreCombiner != null ? scoreCombiner : new ScoreCombiner();
        this.classInfoParser = new ClassInfoParser();
        
        // Load configuration from ConfigManager
        AnalysisConfig config = ConfigManager.getInstance().getAnalysisConfig();
        this.ignoredPaths = config.ignoredPaths();
        this.fileExtensions = config.fileExtensions();
    }
    
    /**
     * Constructor with full dependency injection for testing (including config).
     */
    public IntelligentRelationAnalyzer(MemoryService memoryService, 
                                        MLRelationPredictor mlPredictor,
                                        ScoreCombiner scoreCombiner,
                                        List<String> ignoredPaths,
                                        List<String> fileExtensions) {
        this.relationDetector = new RelationDetector();
        this.annotationScanner = new ExistingAnnotationScanner();
        this.memoryService = memoryService;
        this.mlPredictor = mlPredictor != null ? mlPredictor : new MLRelationPredictor(memoryService);
        this.scoreCombiner = scoreCombiner != null ? scoreCombiner : new ScoreCombiner();
        this.classInfoParser = new ClassInfoParser();
        this.ignoredPaths = ignoredPaths != null ? ignoredPaths : AnalysisConfig.DEFAULT.ignoredPaths();
        this.fileExtensions = fileExtensions != null ? fileExtensions : AnalysisConfig.DEFAULT.fileExtensions();
    }


    /**
     * Analiza las clases de un directorio y detecta relaciones con nivel de confianza.
     *
     * @param sourcePath ruta del directorio con los .java
     * @return lista de relaciones detectadas
     */
    public List<RelacionDetectada> analizar(String sourcePath) {
        List<RelacionDetectada> relaciones = new ArrayList<>();
        // El estado de la UI se reinicia en cada análisis para no arrastrar datos del anterior
        lastInconsistencies = new ArrayList<>();
        classInfoCache = new HashMap<>();

        try {
            File dir = new File(sourcePath);
            if (!dir.exists() || !dir.isDirectory()) {
                LOGGER.warning(() -> "Directorio no válido: " + sourcePath);
                return Collections.emptyList();
            }

            // 1️⃣ Cargar clases y obtener relaciones estructurales
            // Use configured ignored paths and file extensions
            relationDetector.loadClasses(dir, ignoredPaths, fileExtensions);
            List<RelacionDetectada> relacionesEstructurales = relationDetector.detectRelationsAsObjects();
            
            // Parse enhanced class info for ML features
            classInfoCache = classInfoParser.parseDirectory(dir, ignoredPaths, fileExtensions);
            LOGGER.fine(() -> "Parsed enhanced ClassInfo for " + classInfoCache.size() + " classes");
            
            // Mark structural relations with their source
            for (RelacionDetectada r : relacionesEstructurales) {
                r.setSource(PredictionSource.STRUCTURAL);
            }
            relaciones.addAll(relacionesEstructurales);

            LOGGER.info(() -> "Relaciones estructurales detectadas: " + relacionesEstructurales.size());

            // 2️⃣ Inferir relaciones semánticas (por similitud de nombres)
            List<String> classNames = new ArrayList<>(relationDetector.getClassNames());
            List<RelacionDetectada> relacionesSemanticas = inferirRelacionesSemanticas(classNames);
            
            // Mark semantic relations with their source
            for (RelacionDetectada r : relacionesSemanticas) {
                r.setSource(PredictionSource.SEMANTIC);
            }
            relaciones.addAll(relacionesSemanticas);

            LOGGER.info(() -> "Relaciones semánticas inferidas: " + relacionesSemanticas.size());

            // 3️⃣ ML Predictions (if available)
            List<RelacionDetectada> relacionesML = obtenerPrediccionesML(classNames);
            if (!relacionesML.isEmpty()) {
                LOGGER.info(() -> "Relaciones ML predichas: " + relacionesML.size());
            }

            // 4️⃣ Combine scores using ScoreCombiner
            relaciones = combinarPuntuaciones(relaciones, relacionesML);

            // 5️⃣ Ajustar confianza según memoria previa
            List<RelacionDetectada> ajustadas = ajustarPorMemoria(relaciones);

            // 6️⃣ Eliminar duplicados y ordenar por confianza descendente
            relaciones = new ArrayList<>(new LinkedHashSet<>(ajustadas));
            relaciones.sort(Comparator.comparingDouble(RelacionDetectada::getConfianza).reversed());

            // 7️⃣ Detectar relaciones bidireccionales
            BidirectionalRelationDetector bidirectionalDetector = new BidirectionalRelationDetector();
            relaciones = bidirectionalDetector.detectBidirectional(relaciones);
            
            long bidirCount = relaciones.stream().filter(RelacionDetectada::isBidirectional).count();
            if (bidirCount > 0) {
                LOGGER.info(() -> "Relaciones bidireccionales detectadas: " + bidirCount);
            }

            // 8️⃣ Escanear anotaciones existentes y marcar/filtrar
            List<RelacionDetectada> existingAnnotations = annotationScanner.scanExistingAnnotations(dir);
            if (!existingAnnotations.isEmpty()) {
                LOGGER.info(() -> "Anotaciones JPA existentes encontradas: " + existingAnnotations.size());
                relaciones = annotationScanner.filterAlreadyAnnotated(relaciones, existingAnnotations);
                
                // Detectar inconsistencias
                lastInconsistencies = annotationScanner.findInconsistencies(relaciones, existingAnnotations);
                if (!lastInconsistencies.isEmpty()) {
                    LOGGER.warning(() -> "Inconsistencias detectadas: " + lastInconsistencies.size());
                    lastInconsistencies.forEach(i -> LOGGER.warning(() -> "   " + i.toString()));
                }
            }

            final int totalRelaciones = relaciones.size();
            LOGGER.info(() -> "Total relaciones detectadas: " + totalRelaciones);

        } catch (RuntimeException | java.io.IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Error analizando relaciones: " + e.getMessage());
        }

        return relaciones;
    }


    /**
     * Obtiene predicciones ML si hay suficientes datos de entrenamiento.
     * Implements Requirements 2.4: Fall back to heuristics when ML unavailable.
     * Implements Requirements 6.1, 6.3: Handle edge cases gracefully.
     * 
     * Enhanced to pass full ClassInfo with field-level information for improved
     * feature extraction (Requirements 1.1-1.4).
     */
    private List<RelacionDetectada> obtenerPrediccionesML(List<String> classNames) {
        // Check if ML is available (requires minimum 5 decisions)
        if (mlPredictor == null || !mlPredictor.isMLAvailable()) {
            int decisionCount = mlPredictor != null ? mlPredictor.getDecisionCount() : 0;
            int threshold = mlPredictor != null ? mlPredictor.getMinimumDecisionsThreshold() : 5;
            
            LOGGER.fine(() -> "ML no disponible: " + decisionCount + "/" + threshold + 
                " decisiones. Usando heurísticas existentes.");
            return new ArrayList<>();
        }
        
        try {
            // Use cached ClassInfo with enhanced field-level information
            // This ensures ML predictions benefit from:
            // - Field annotations (@Id, @OneToMany, etc.)
            // - Generic types from collections
            // - Interface implementations
            // - Superclass information
            // - Constructor parameters
            Map<String, ClassInfo> classInfoMap = new HashMap<>();
            for (String className : classNames) {
                ClassInfo cachedInfo = classInfoCache.get(className);
                if (cachedInfo != null) {
                    classInfoMap.put(className, cachedInfo);
                    final String logClassName = className;
                    final int fieldCount = cachedInfo.getFieldCount();
                    LOGGER.finest(() -> "Using enhanced ClassInfo for " + logClassName + 
                        " with " + fieldCount + " fields");
                } else {
                    // Fallback to basic ClassInfo if not in cache
                    classInfoMap.put(className, new ClassInfo(className));
                    final String logClassName = className;
                    LOGGER.finest(() -> "Using basic ClassInfo for " + logClassName);
                }
            }
            
            // Get ML predictions with enhanced ClassInfo
            List<RelacionDetectada> mlRelaciones = mlPredictor.predictRelations(classNames, classInfoMap);
            
            // Mark all ML relations with their source
            for (RelacionDetectada r : mlRelaciones) {
                r.setSource(PredictionSource.ML);
            }
            
            final int predictionCount = mlRelaciones.size();
            final int classCount = classInfoMap.size();
            LOGGER.fine(() -> "ML predictions generated: " + predictionCount + 
                " using enhanced ClassInfo from " + classCount + " classes");
            
            return mlRelaciones;
            
        } catch (Exception e) {
            // Implements Requirements 6.3: If model training fails, continue using heuristics
            LOGGER.log(Level.WARNING, e, () -> "Error en predicción ML, continuando con heurísticas: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Combina puntuaciones de diferentes fuentes usando ScoreCombiner.
     * Implements Requirements 4.2: Weight ML=50%, Structural=30%, Semantic=20%.
     * Implements Requirements 4.3: Indicate source of each prediction.
     */
    private List<RelacionDetectada> combinarPuntuaciones(List<RelacionDetectada> relacionesBase,
                                                          List<RelacionDetectada> relacionesML) {
        // Create a map to track scores by relation key.
        // LinkedHashMap: el orden de salida (y por tanto los empates al ordenar y la detección
        // bidireccional, que se queda con la última relación de cada par) no depende del hash.
        Map<String, ScoreInfo> scoreMap = new LinkedHashMap<>();
        
        // Process base relations (structural and semantic)
        for (RelacionDetectada r : relacionesBase) {
            String key = r.getClaveMemoria();
            ScoreInfo info = scoreMap.computeIfAbsent(key, k -> new ScoreInfo(r));
            
            if (r.getSource() == PredictionSource.STRUCTURAL) {
                info.structuralScore = r.getConfianza();
            } else if (r.getSource() == PredictionSource.SEMANTIC) {
                info.semanticScore = Math.max(info.semanticScore, r.getConfianza());
            }
        }
        
        // Process ML relations
        for (RelacionDetectada r : relacionesML) {
            String key = r.getClaveMemoria();
            ScoreInfo info = scoreMap.computeIfAbsent(key, k -> new ScoreInfo(r));
            info.mlScore = Math.max(info.mlScore, r.getConfianza());
        }
        
        // Combine scores and create final relations
        List<RelacionDetectada> resultado = new ArrayList<>();
        for (ScoreInfo info : scoreMap.values()) {
            double combinedScore;
            PredictionSource primarySource;
            
            // If we have ML score, use weighted combination
            if (info.mlScore > 0) {
                combinedScore = scoreCombiner.combine(info.mlScore, info.structuralScore, info.semanticScore);
                primarySource = scoreCombiner.getPrimarySource(info.mlScore, info.structuralScore, info.semanticScore);
            } else {
                // No ML available, use simple combination of structural and semantic
                // Structural gets higher weight when no ML
                combinedScore = info.structuralScore * 0.6 + info.semanticScore * 0.4;
                primarySource = info.structuralScore >= info.semanticScore ? 
                    PredictionSource.STRUCTURAL : PredictionSource.SEMANTIC;
            }
            
            RelacionDetectada combined = new RelacionDetectada(
                info.relacion.getOrigen(),
                info.relacion.getDestino(),
                info.relacion.getTipo(),
                combinedScore,
                primarySource
            );
            resultado.add(combined);
        }
        
        return resultado;
    }
    
    /**
     * Helper class to track scores from different sources.
     */
    private static class ScoreInfo {
        RelacionDetectada relacion;
        double mlScore = 0.0;
        double structuralScore = 0.0;
        double semanticScore = 0.0;
        
        ScoreInfo(RelacionDetectada relacion) {
            this.relacion = relacion;
        }
    }


    /**
     * Intenta inferir relaciones adicionales comparando nombres de clases (semántico).
     */
    private List<RelacionDetectada> inferirRelacionesSemanticas(List<String> classNames) {
        List<RelacionDetectada> result = new ArrayList<>();
        List<String> tipos = List.of("OneToOne", "OneToMany", "ManyToOne", "ManyToMany");

        for (String origen : classNames) {
            for (String destino : classNames) {
                if (!origen.equals(destino)) {
                    for (String tipo : tipos) {
                        double sim = SemanticSimilarity.compare(origen, destino);
                        if (sim > 0.7) {
                            double confianza = Math.min(1.0, 0.5 + sim * 0.5);
                            result.add(new RelacionDetectada(origen, destino, tipo, confianza));
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Ajusta la confianza de las relaciones según lo aprendido en memoria.
     * Devuelve una nueva lista con las confianzas actualizadas.
     */
    private List<RelacionDetectada> ajustarPorMemoria(List<RelacionDetectada> relaciones) {
        Map<String, String> memoria = memoryService.getMemoria();
        List<RelacionDetectada> resultado = new ArrayList<>();

        for (RelacionDetectada r : relaciones) {
            double boost = 0.0;
            boolean rechazada = false;

            for (Map.Entry<String, String> entry : memoria.entrySet()) {
                String clave = entry.getKey();
                String decision = entry.getValue();
                String[] partes = parseRelacion(clave);
                if (partes == null) continue;

                double simOrigen = SemanticSimilarity.compare(r.getOrigen(), partes[0]);
                double simDestino = SemanticSimilarity.compare(r.getDestino(), partes[1]);
                double simTipo = r.getTipo().equals(partes[2]) ? 1.0 : 0.5;

                double similitud = (simOrigen + simDestino + simTipo) / 3.0;

                if (similitud > 0.75) {
                    if ("aceptada".equals(decision)) boost += similitud * 0.4;
                    if ("rechazada".equals(decision)) {
                        rechazada = true;
                        boost -= similitud * 0.5;
                    }
                }
            }

            double nuevaConf = Math.max(0, Math.min(1, r.getConfianza() + boost));
            if (rechazada && nuevaConf < 0.4) {
                nuevaConf = 0.0; // marcada como descartada
            }

            // Crear nuevo objeto preservando la fuente de predicción
            resultado.add(new RelacionDetectada(
                    r.getOrigen(),
                    r.getDestino(),
                    r.getTipo(),
                    nuevaConf,
                    r.getSource()
            ));
        }

        return resultado;
    }

    /**
     * Extrae las partes de una relación con formato "A -> B : Tipo".
     */
    private String[] parseRelacion(String texto) {
        try {
            String[] partes = texto.split(" -> | : ");
            if (partes.length < 3) return null;
            return new String[]{partes[0].trim(), partes[1].trim(), partes[2].trim()};
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gets the ML predictor for testing or external access.
     * @return the ML relation predictor
     */
    public MLRelationPredictor getMLPredictor() {
        return mlPredictor;
    }
    
    /**
     * Checks if ML prediction is currently available.
     * @return true if ML can be used for predictions
     */
    public boolean isMLAvailable() {
        return mlPredictor != null && mlPredictor.isMLAvailable();
    }
    
    /**
     * Gets inconsistencies found in the last analysis.
     * @return list of annotation inconsistencies
     */
    public List<ExistingAnnotationScanner.AnnotationInconsistency> getLastInconsistencies() {
        return lastInconsistencies;
    }
    
    /**
     * Checks if a path should be ignored based on configured ignored paths.
     * @param path the path to check
     * @return true if the path should be ignored
     */
    public boolean shouldIgnorePath(String path) {
        if (path == null || ignoredPaths == null) {
            return false;
        }
        String normalizedPath = path.replace('\\', '/');
        for (String ignoredPath : ignoredPaths) {
            if (normalizedPath.contains("/" + ignoredPath + "/") || 
                normalizedPath.endsWith("/" + ignoredPath) ||
                normalizedPath.startsWith(ignoredPath + "/") ||
                normalizedPath.equals(ignoredPath)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if a file has a supported extension.
     * @param fileName the file name to check
     * @return true if the file extension is supported
     */
    public boolean hasSupportedExtension(String fileName) {
        if (fileName == null || fileExtensions == null || fileExtensions.isEmpty()) {
            return true; // Default to accepting all if no extensions configured
        }
        for (String ext : fileExtensions) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets the configured ignored paths.
     * @return list of ignored paths
     */
    public List<String> getIgnoredPaths() {
        return ignoredPaths;
    }
    
    /**
     * Gets the configured file extensions.
     * @return list of supported file extensions
     */
    public List<String> getFileExtensions() {
        return fileExtensions;
    }
    
    /**
     * Gets the cached ClassInfo map from the last analysis.
     * @return map of class name to ClassInfo
     */
    public Map<String, ClassInfo> getClassInfoCache() {
        return classInfoCache;
    }
    
    /**
     * Gets ClassInfo for a specific class from the cache.
     * @param className the class name to look up
     * @return ClassInfo or null if not found
     */
    public ClassInfo getClassInfo(String className) {
        return classInfoCache.get(className);
    }
}
