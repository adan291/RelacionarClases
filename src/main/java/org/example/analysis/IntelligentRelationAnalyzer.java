package org.example.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import org.example.model.RelationKey;
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

            // 1️⃣ Un único parseo del directorio (antes eran tres: RelationDetector, ClassInfoParser
            // y ExistingAnnotationScanner). Todo lo demás se deriva de ClassInfo/FieldInfo.
            Map<String, ClassInfo> proyecto = classInfoParser.parseProject(dir, ignoredPaths, fileExtensions);

            // Mismo orden de iteración que el mapa que usaba RelationDetector (un HashMap rellenado
            // en el orden de recorrido), para que los empates salgan igual que antes
            Map<String, ClassInfo> porHash = new HashMap<>();
            proyecto.forEach(porHash::put);
            List<RelacionDetectada> relacionesEstructurales = RelationDetector.detectRelations(porHash);
            List<String> classNames = new ArrayList<>(new HashSet<>(porHash.keySet()));

            // Para ML las interfaces van sin información, como cuando ClassInfoParser no las incluía
            Map<String, ClassInfo> mlInfo = new HashMap<>();
            Map<String, ClassInfo> soloClases = new HashMap<>();
            proyecto.forEach((name, info) -> {
                mlInfo.put(name, info.isInterfaceType() ? new ClassInfo(name) : info);
                if (!info.isInterfaceType()) {
                    soloClases.put(name, info);
                }
            });
            classInfoCache = soloClases;
            LOGGER.fine(() -> "Parsed enhanced ClassInfo for " + soloClases.size() + " classes");

            relaciones = analizarPipeline(relacionesEstructurales, classNames, mlInfo);

            // 8️⃣ Anotaciones existentes (derivadas del mismo parseo) y marcar/filtrar
            List<RelacionDetectada> existingAnnotations = annotationScanner.scanExistingAnnotations(proyecto);
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
     * Analiza un modelo ya construido en memoria (sin leer disco). No toca el estado de la UI
     * ({@link #getLastInconsistencies()}, {@link #getClassInfoCache()}).
     *
     * @param clases nombre de clase → información de la clase; el orden del mapa se respeta
     * @return relaciones detectadas, ordenadas por confianza descendente
     */
    public List<RelacionDetectada> analizar(Map<String, ClassInfo> clases) {
        if (clases == null || clases.isEmpty()) {
            return new ArrayList<>();
        }
        List<RelacionDetectada> estructurales = RelationDetector.detectRelations(clases);
        return analizarPipeline(estructurales, new ArrayList<>(clases.keySet()), clases);
    }

    /**
     * Pasos comunes a los dos puntos de entrada: semántico, ML, combinación, memoria,
     * ordenación y detección bidireccional.
     */
    private List<RelacionDetectada> analizarPipeline(List<RelacionDetectada> relacionesEstructurales,
                                                     List<String> classNames,
                                                     Map<String, ClassInfo> classInfo) {
        List<RelacionDetectada> relaciones = new ArrayList<>();

        // 1️⃣ Mark structural relations with their source
        for (RelacionDetectada r : relacionesEstructurales) {
            r.setSource(PredictionSource.STRUCTURAL);
        }
        relaciones.addAll(relacionesEstructurales);

        LOGGER.fine(() -> "Relaciones estructurales detectadas: " + relacionesEstructurales.size());

        // 2️⃣ Inferir relaciones semánticas (por similitud de nombres)
        // Caché de similitudes de este análisis: la matriz de pares se calcula una vez y la
        // reutiliza el ajuste por memoria
        NameSimilarityCache sims = new NameSimilarityCache(classNames);
        List<RelacionDetectada> relacionesSemanticas = inferirRelacionesSemanticas(classNames, sims);
        
        // Mark semantic relations with their source
        for (RelacionDetectada r : relacionesSemanticas) {
            r.setSource(PredictionSource.SEMANTIC);
        }
        relaciones.addAll(relacionesSemanticas);

        LOGGER.fine(() -> "Relaciones semánticas inferidas: " + relacionesSemanticas.size());

        // 3️⃣ ML Predictions (if available)
        List<RelacionDetectada> relacionesML = obtenerPrediccionesML(classNames, classInfo);
        if (!relacionesML.isEmpty()) {
            LOGGER.fine(() -> "Relaciones ML predichas: " + relacionesML.size());
        }

        // 4️⃣ Combine scores using ScoreCombiner
        relaciones = combinarPuntuaciones(relaciones, relacionesML);

        // 5️⃣ Ajustar confianza según memoria previa
        List<RelacionDetectada> ajustadas = ajustarPorMemoria(relaciones, sims);

        // 6️⃣ Eliminar duplicados y ordenar por confianza descendente
        relaciones = new ArrayList<>(new LinkedHashSet<>(ajustadas));
        relaciones.sort(Comparator.comparingDouble(RelacionDetectada::getConfianza).reversed());

        // 7️⃣ Detectar relaciones bidireccionales
        BidirectionalRelationDetector bidirectionalDetector = new BidirectionalRelationDetector();
        relaciones = bidirectionalDetector.detectBidirectional(relaciones);
        
        long bidirCount = relaciones.stream().filter(RelacionDetectada::isBidirectional).count();
        if (bidirCount > 0) {
            LOGGER.fine(() -> "Relaciones bidireccionales detectadas: " + bidirCount);
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
    private List<RelacionDetectada> obtenerPrediccionesML(List<String> classNames,
                                                          Map<String, ClassInfo> classInfoCache) {
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
        // Puntuaciones por clave (origen, destino, tipo). ScoreInfo es inmutable: cada fuente
        // sustituye la entrada por una copia actualizada.
        // LinkedHashMap: el orden de salida (y por tanto los empates al ordenar y la detección
        // bidireccional, que se queda con la última relación de cada par) no depende del hash.
        Map<RelationKey, ScoreInfo> scoreMap = new LinkedHashMap<>();
        
        // Process base relations (structural and semantic)
        for (RelacionDetectada r : relacionesBase) {
            RelationKey key = RelationKey.of(r);
            ScoreInfo info = scoreMap.get(key);
            if (info == null) {
                info = ScoreInfo.empty(key);
            }
            if (r.getSource() == PredictionSource.STRUCTURAL) {
                info = info.withStructural(r.getConfianza());
            } else if (r.getSource() == PredictionSource.SEMANTIC) {
                info = info.withSemanticMax(r.getConfianza());
            }
            scoreMap.put(key, info);
        }
        
        // Process ML relations
        for (RelacionDetectada r : relacionesML) {
            RelationKey key = RelationKey.of(r);
            ScoreInfo info = scoreMap.get(key);
            if (info == null) {
                info = ScoreInfo.empty(key);
            }
            scoreMap.put(key, info.withMlMax(r.getConfianza()));
        }
        
        // Combine scores and create final relations
        List<RelacionDetectada> resultado = new ArrayList<>(scoreMap.size());
        for (ScoreInfo info : scoreMap.values()) {
            double combinedScore;
            PredictionSource primarySource;
            
            // If we have ML score, use weighted combination
            if (info.mlScore() > 0) {
                combinedScore = scoreCombiner.combine(info.mlScore(), info.structuralScore(), info.semanticScore());
                primarySource = scoreCombiner.getPrimarySource(info.mlScore(), info.structuralScore(), info.semanticScore());
            } else {
                // No ML available, use simple combination of structural and semantic
                // Structural gets higher weight when no ML
                combinedScore = info.structuralScore() * 0.6 + info.semanticScore() * 0.4;
                primarySource = info.structuralScore() >= info.semanticScore() ? 
                    PredictionSource.STRUCTURAL : PredictionSource.SEMANTIC;
            }
            
            RelationKey key = info.key();
            resultado.add(new RelacionDetectada(key.origen(), key.destino(), key.tipo(), combinedScore, primarySource));
        }
        
        return resultado;
    }

    private static final String[] TIPOS = {"OneToOne", "OneToMany", "ManyToOne", "ManyToMany"};

    /**
     * Intenta inferir relaciones adicionales comparando nombres de clases (semántico).
     * La similitud de cada par se calcula una vez (antes, una vez por cada uno de los 4 tipos)
     * y queda en la caché para el ajuste por memoria.
     */
    private List<RelacionDetectada> inferirRelacionesSemanticas(List<String> classNames, NameSimilarityCache sims) {
        List<RelacionDetectada> result = new ArrayList<>();
        int n = classNames.size();
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = sims.id(classNames.get(i));
        }

        for (int i = 0; i < n; i++) {
            String origen = classNames.get(i);
            for (int j = 0; j < n; j++) {
                String destino = classNames.get(j);
                if (origen.equals(destino)) {
                    continue;
                }
                double sim = sims.sim(ids[i], ids[j]);
                if (sim > 0.7) {
                    double confianza = Math.min(1.0, 0.5 + sim * 0.5);
                    for (String tipo : TIPOS) {
                        result.add(new RelacionDetectada(origen, destino, tipo, confianza));
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
    List<RelacionDetectada> ajustarPorMemoria(List<RelacionDetectada> relaciones) {
        return ajustarPorMemoria(relaciones, new NameSimilarityCache(List.of()));
    }

    private static final double MEMORY_THRESHOLD = 0.75;

    /**
     * Mismo cálculo que antes (media de similitud de origen, destino y tipo; umbral 0,75; +0,4 por
     * aceptada y −0,5 por rechazada), pero con la memoria indexada: las claves se parsean una vez,
     * las similitudes de nombres se cachean y, para cada origen, solo se recorren las decisiones
     * que pueden superar el umbral. El orden de las sumas es el de la memoria, como antes.
     */
    private List<RelacionDetectada> ajustarPorMemoria(List<RelacionDetectada> relaciones, NameSimilarityCache sims) {
        MemoryIndex index = MemoryIndex.build(memoryService.getMemoria(), sims);
        List<RelacionDetectada> resultado = new ArrayList<>(relaciones.size());
        Map<String, int[]> candidatesByOrigin = new HashMap<>();

        for (RelacionDetectada r : relaciones) {
            double boost = 0.0;
            boolean rechazada = false;

            if (index.size() > 0) {
                int originId = sims.id(r.getOrigen());
                int destinoId = sims.id(r.getDestino());
                int[] candidates = candidatesByOrigin.computeIfAbsent(r.getOrigen(),
                    o -> index.candidates(originId, sims, MEMORY_THRESHOLD));

                for (int c : candidates) {
                    MemoryIndex.Entry entry = index.entry(c);
                    double simOrigen = sims.sim(originId, entry.originId());
                    double simDestino = sims.sim(destinoId, entry.destinoId());
                    double simTipo = r.getTipo().equals(entry.key().tipo()) ? 1.0 : 0.5;

                    double similitud = (simOrigen + simDestino + simTipo) / 3.0;

                    if (similitud > MEMORY_THRESHOLD) {
                        if (entry.accepted()) boost += similitud * 0.4;
                        if (entry.rejected()) {
                            rechazada = true;
                            boost -= similitud * 0.5;
                        }
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
