package org.example.api;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.analysis.MemoryKeys;
import org.example.api.ApiModels.FeedbackItem;
import org.example.ml.MLRelationPredictor;
import org.example.model.RelationKey;
import org.example.service.MemoryService;

/**
 * Guarda las decisiones del usuario y programa un reentrenamiento en segundo plano con debounce:
 * varias llamadas seguidas producen un solo reentrenamiento, {@code debounce} después de la última.
 */
public class FeedbackService implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(FeedbackService.class.getName());

    private final MemoryService memoryService;
    private final MLRelationPredictor predictor;
    private final Duration debounce;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pending;

    public FeedbackService(MemoryService memoryService, MLRelationPredictor predictor, Duration debounce) {
        this.memoryService = memoryService;
        this.predictor = predictor;
        this.debounce = debounce;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ml-retrain");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Valida y guarda las decisiones (una sola escritura a disco) y programa el reentrenamiento.
     *
     * @return número de decisiones guardadas
     */
    public int record(List<FeedbackItem> items) {
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest("Se esperaba una decisión o una lista de decisiones");
        }
        if (items.size() > ApiConfig.MAX_FEEDBACK_ITEMS) {
            throw ApiException.tooLarge("Como máximo " + ApiConfig.MAX_FEEDBACK_ITEMS + " decisiones por petición");
        }
        Map<String, Boolean> decisions = new LinkedHashMap<>();
        for (FeedbackItem item : items) {
            validate(item);
            RelationKey key = new RelationKey(item.source(), item.target(), item.type());
            decisions.put(MemoryKeys.format(key), "accepted".equals(item.decision()));
        }
        memoryService.recordarDecisiones(decisions);
        scheduleRetrain();
        return decisions.size();
    }

    private static void validate(FeedbackItem item) {
        if (item == null) {
            throw ApiException.badRequest("Decisión vacía");
        }
        for (String name : new String[] {item.source(), item.target()}) {
            if (name == null || !RelationSuggestionService.IDENTIFIER.matcher(name).matches()) {
                throw ApiException.badRequest("source y target deben ser identificadores válidos: " + name);
            }
        }
        if (item.type() == null || !RelationSuggestionService.RELATION_TYPES.contains(item.type())) {
            throw ApiException.badRequest("type debe ser OneToOne | OneToMany | ManyToOne | ManyToMany");
        }
        if (!"accepted".equals(item.decision()) && !"rejected".equals(item.decision())) {
            throw ApiException.badRequest("decision debe ser \"accepted\" o \"rejected\"");
        }
    }

    /** Reinicia la espera: solo se ejecuta el último reentrenamiento programado. */
    synchronized void scheduleRetrain() {
        if (predictor == null) {
            return;
        }
        if (pending != null) {
            pending.cancel(false);
        }
        pending = scheduler.schedule(this::retrain, debounce.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void retrain() {
        try {
            if (predictor.isMLAvailable()) {
                predictor.retrain();
                LOGGER.info(() -> "Modelo reentrenado con " + predictor.getDecisionCount() + " decisiones");
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Fallo al reentrenar el modelo", e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
