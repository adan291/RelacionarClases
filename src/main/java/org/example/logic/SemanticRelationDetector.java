package org.example.logic;

import org.example.analysis.SemanticSimilarity;
import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;

import java.util.*;

/**
 * Detector semántico que propone relaciones entre entidades
 * y aprende por similitud de nombres (tipo IA básica con confianza).
 */
public class SemanticRelationDetector {

    private static final List<String> TIPOS_RELACION = List.of(
            "OneToOne", "OneToMany", "ManyToOne", "ManyToMany"
    );

    /**
     * Genera sugerencias de relaciones entre clases con nivel de confianza.
     */
    public static List<RelacionDetectada> inferRelations(List<String> classNames, MemoryService memory) {
        List<RelacionDetectada> sugerencias = new ArrayList<>();
        Map<String, String> memoria = memory.getMemoria();

        for (String origen : classNames) {
            for (String destino : classNames) {
                if (!origen.equals(destino)) {
                    for (String tipo : TIPOS_RELACION) {

                        double confianza = 0.5; // base inicial
                        boolean rechazada = false;

                        // 🔍 Analizar memoria con similitud semántica
                        for (Map.Entry<String, String> entrada : memoria.entrySet()) {
                            String aprendida = entrada.getKey();
                            String decision = entrada.getValue();

                            String[] partes = aprendida.split(" -> | : ");
                            if (partes.length < 3) continue;

                            String origenPrev = partes[0];
                            String destinoPrev = partes[1];
                            String tipoPrev = partes[2];

                            double simOrigen = SemanticSimilarity.compare(origen, origenPrev);
                            double simDestino = SemanticSimilarity.compare(destino, destinoPrev);
                            double simTipo = tipo.equals(tipoPrev) ? 1.0 : 0.5;

                            double similitudTotal = (simOrigen + simDestino + simTipo) / 3.0;

                            if (similitudTotal > 0.75) {
                                if ("rechazada".equals(decision)) {
                                    rechazada = true;
                                    confianza -= similitudTotal * 0.3;
                                } else if ("aceptada".equals(decision)) {
                                    confianza += similitudTotal * 0.4;
                                }
                            }
                        }

                        // 🔧 Ajustar límites
                        confianza = Math.max(0.0, Math.min(1.0, confianza));

                        if (!rechazada && confianza >= 0.4) {
                            sugerencias.add(new RelacionDetectada(origen, destino, tipo, confianza));
                        }
                    }
                }
            }
        }

        // Ordenar por confianza descendente
        sugerencias.sort(Comparator.comparingDouble(RelacionDetectada::getConfianza).reversed());

        // Eliminar duplicados
        return new ArrayList<>(new LinkedHashSet<>(sugerencias));
    }
}
