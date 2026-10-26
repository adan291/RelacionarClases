package org.example.api;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;

import org.example.analysis.IntelligentRelationAnalyzer;
import org.example.config.MLConfig;
import org.example.ml.FeatureExtractor;
import org.example.ml.LogisticRegressionModel;
import org.example.ml.MLRelationPredictor;
import org.example.ml.ScoreCombiner;
import org.example.service.MemoryService;

import io.javalin.Javalin;

/**
 * Arranque de la API de relaciones, sin JavaFX.
 *
 * <pre>
 *   java -jar target/detection-relations-1.0-SNAPSHOT-api.jar
 *   RELATIONS_PORT=8095 RELATIONS_DATA_DIR=D:\datos\relaciones java -jar ...-api.jar
 * </pre>
 *
 * Todas las dependencias se crean aquí y se pasan por constructor: el camino del análisis no
 * usa {@code ConfigManager} ni el directorio de trabajo.
 */
public final class RelationsApiServer implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(RelationsApiServer.class.getName());

    private final Javalin app;
    private final FeedbackService feedback;

    private RelationsApiServer(Javalin app, FeedbackService feedback) {
        this.app = app;
        this.feedback = feedback;
    }

    /**
     * Construye la API con sus dependencias (memoria y modelo en {@code dataDir}), sin arrancarla.
     */
    public static RelationsApiServer create(ApiConfig config) throws IOException {
        Files.createDirectories(config.dataDir());
        MemoryService memory = new MemoryService(config.memoryFile().toString());
        MLRelationPredictor predictor = new MLRelationPredictor(memory, new FeatureExtractor(),
            new LogisticRegressionModel(), config.modelFile().toString(), config.minDecisions());
        // Si hay datos y no había modelo guardado, se entrena antes de aceptar peticiones
        if (predictor.isMLAvailable()) {
            predictor.ensureTrained();
        }
        MLConfig weights = MLConfig.DEFAULT;
        IntelligentRelationAnalyzer analyzer = new IntelligentRelationAnalyzer(memory, predictor,
            new ScoreCombiner(weights.mlWeight(), weights.structuralWeight(), weights.semanticWeight()),
            List.of(), List.of(".java"));
        RelationSuggestionService suggestions = new RelationSuggestionService(analyzer, memory, predictor);
        FeedbackService feedback = new FeedbackService(memory, predictor, config.retrainDebounce());
        return new RelationsApiServer(RelationsApi.create(config, suggestions, feedback), feedback);
    }

    public RelationsApiServer start() {
        app.start();
        LOGGER.info(() -> "API de relaciones escuchando en http://" + ApiConfig.HOST + ":" + app.port() + RelationsApi.BASE);
        return this;
    }

    public int port() {
        return app.port();
    }

    public Javalin app() {
        return app;
    }

    @Override
    public void close() {
        app.stop();
        feedback.close();
    }

    public static void main(String[] args) throws IOException {
        ApiConfig config = ApiConfig.fromEnv();
        LOGGER.info(() -> "Datos en " + config.dataDir() + "; CORS para " + config.allowedOrigins());
        // Sin try-with-resources: el servidor debe seguir abierto tras salir de main; lo cierra el shutdown hook
        RelationsApiServer server = create(config).start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "api-shutdown"));
    }
}
