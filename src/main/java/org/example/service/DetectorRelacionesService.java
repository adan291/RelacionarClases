package org.example.service;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.example.analysis.IntelligentRelationAnalyzer;
import org.example.model.RelacionDetectada;

/**
 * 🧠 DetectorRelacionesService
 * --------------------------------
 * Servicio central que usa el analizador inteligente de relaciones
 * (estructura + semántica + memoria persistente).
 */
public class DetectorRelacionesService {

    private static final Logger LOGGER = Logger.getLogger(DetectorRelacionesService.class.getName());
    private final MemoryService memoryService;
    private final IntelligentRelationAnalyzer analyzer;

    public DetectorRelacionesService() {
        this.memoryService = new MemoryService();
        this.analyzer = new IntelligentRelationAnalyzer(memoryService);
    }

    /**
     * Ejecuta el análisis completo sobre el directorio indicado.
     *
     * @param ruta ruta del directorio con las clases Java
     * @return lista de relaciones detectadas con nivel de confianza
     */
    public List<RelacionDetectada> ejecutarAnalisis(String ruta) {
        try {
            LOGGER.info("Ejecutando análisis inteligente...");
            List<RelacionDetectada> relaciones = analyzer.analizar(ruta);

            if (relaciones.isEmpty()) {
                LOGGER.warning("No se detectaron relaciones");
                return Collections.emptyList();
            }

            LOGGER.info("Relaciones detectadas: " + relaciones.size());
            relaciones.forEach(r ->
                    LOGGER.fine(String.format(" - %s -> %s : %s [%.2f]",
                            r.getOrigen(), r.getDestino(), r.getTipo(), r.getConfianza()))
            );

            LOGGER.info("Memoria actual: " + memoryService.getMemoria().size() + " relaciones aprendidas");
            return relaciones;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error ejecutando análisis: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Devuelve el servicio de memoria (para su uso desde la UI u otros módulos).
     */
    public MemoryService getMemoryService() {
        return memoryService;
    }

    /**
     * Devuelve el analizador que usa este servicio. La UI lo necesita para leer las
     * inconsistencias del último análisis y reentrenar el mismo predictor que se usa al analizar.
     */
    public IntelligentRelationAnalyzer getAnalyzer() {
        return analyzer;
    }
}
