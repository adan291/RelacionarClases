package org.example.controller;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.example.model.RelacionDetectada;
import org.example.service.DetectorRelacionesService;
import org.example.service.MemoryService;

/**
 * 🎯 RelationAnalysisOrchestrator
 * ----------------------------------
 * Orquesta el análisis de relaciones entre clases.
 * Usa el nuevo servicio inteligente que combina:
 *  - Análisis estructural
 *  - Análisis semántico
 *  - Aprendizaje persistente (memoria)
 */
public class RelationAnalysisOrchestrator {

    private static final Logger LOGGER = Logger.getLogger(RelationAnalysisOrchestrator.class.getName());
    
    private final DetectorRelacionesService detectorService;
    private final MemoryService memoryService;

    public RelationAnalysisOrchestrator() {
        this.detectorService = new DetectorRelacionesService();
        this.memoryService = detectorService.getMemoryService();
    }

    /**
     * Ejecuta el análisis completo sobre el directorio indicado.
     *
     * @param sourcePath ruta donde están los archivos .java (por ejemplo: src/main/java/org/example/ejemplos)
     * @return lista de relaciones detectadas con nivel de confianza
     */
    public List<RelacionDetectada> ejecutarAnalisis(String sourcePath) {
        try {
            File folder = new File(sourcePath);
            if (!folder.exists() || !folder.isDirectory()) {
                LOGGER.warning("Ruta no válida: " + sourcePath);
                return Collections.emptyList();
            }

            LOGGER.info("Analizando clases en: " + sourcePath);

            List<RelacionDetectada> relaciones = detectorService.ejecutarAnalisis(sourcePath);

            if (relaciones.isEmpty()) {
                LOGGER.warning("No se encontraron relaciones detectadas");
            } else {
                LOGGER.info("Análisis completado correctamente");
            }

            LOGGER.info("Memoria contiene " + memoryService.getMemoria().size() + " relaciones aprendidas");
            return relaciones;

        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error durante el análisis: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Variante del análisis que reporta progreso al UI (pantalla de carga).
     *
     * @param sourcePath ruta del código fuente
     * @param progressUpdater callback para actualizar progreso en la interfaz
     * @return lista de relaciones detectadas
     */
    public List<RelacionDetectada> ejecutarAnalisisConProgreso(String sourcePath,
                                                               BiConsumer<Double, String> progressUpdater) {
        try {
            LOGGER.info("Ejecutando análisis con progreso...");

            File folder = new File(sourcePath);
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".java"));
            if (files == null || files.length == 0) {
                LOGGER.warning("No hay archivos .java en: " + sourcePath);
                return Collections.emptyList();
            }

            int total = files.length;
            int count = 0;

            // Actualizar visualmente por cada archivo (sin delay artificial)
            for (File file : files) {
                double progress = (double) ++count / total;
                progressUpdater.accept(progress, file.getName());
            }

            // Cuando termina el "escaneo", ejecutamos el análisis real
            return detectorService.ejecutarAnalisis(sourcePath);

        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error en análisis con progreso", e);
            return Collections.emptyList();
        }
    }

    public DetectorRelacionesService getDetectorService() {
        return detectorService;
    }

    /**
     * @return el analizador que usa el orquestador (el mismo en todos los análisis)
     */
    public org.example.analysis.IntelligentRelationAnalyzer getAnalyzer() {
        return detectorService.getAnalyzer();
    }

    public MemoryService getMemoryService() {
        return memoryService;
    }
}
