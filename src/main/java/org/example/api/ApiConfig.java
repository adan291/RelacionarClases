package org.example.api;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.config.MLConfig;

/**
 * Configuración de la API, leída de variables de entorno.
 *
 * <ul>
 *   <li>{@code RELATIONS_PORT}: puerto (8095).</li>
 *   <li>{@code RELATIONS_ALLOWED_ORIGINS}: orígenes CORS separados por comas
 *       ({@code http://localhost:3000,http://127.0.0.1:3000}).</li>
 *   <li>{@code RELATIONS_DATA_DIR}: directorio de la memoria y del modelo
 *       ({@code ~/.relacionar-clases}). Nunca se usa el directorio de trabajo.</li>
 *   <li>{@code RELATIONS_MIN_DECISIONS}: decisiones mínimas para usar ML (10).</li>
 *   <li>{@code RELATIONS_RETRAIN_DEBOUNCE_MS}: espera antes de reentrenar tras el feedback (2000).</li>
 * </ul>
 * Siempre escucha en 127.0.0.1.
 */
public record ApiConfig(String host, int port, Set<String> allowedOrigins, Path dataDir,
                        int minDecisions, Duration retrainDebounce) {

    public static final String HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8095;
    public static final Set<String> DEFAULT_ORIGINS = Set.of("http://localhost:3000", "http://127.0.0.1:3000");

    public static final long MAX_BODY_BYTES = 1024L * 1024L;
    public static final int MAX_ENTITIES = 500;
    public static final int MAX_FIELDS_PER_ENTITY = 200;
    public static final int MAX_FEEDBACK_ITEMS = 1000;

    public static final String MEMORY_FILE = "memoria_relaciones.json";
    public static final String MODEL_FILE = "ml_model.json";

    public Path memoryFile() {
        return dataDir.resolve(MEMORY_FILE);
    }

    public Path modelFile() {
        return dataDir.resolve(MODEL_FILE);
    }

    public static ApiConfig fromEnv() {
        return fromEnv(System.getenv());
    }

    public static ApiConfig fromEnv(Map<String, String> env) {
        int port = parseInt(env.get("RELATIONS_PORT"), DEFAULT_PORT, "RELATIONS_PORT");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("RELATIONS_PORT fuera de rango: " + port);
        }
        String origins = env.get("RELATIONS_ALLOWED_ORIGINS");
        Set<String> allowed = origins == null || origins.isBlank()
            ? DEFAULT_ORIGINS
            : Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        String dir = env.get("RELATIONS_DATA_DIR");
        Path dataDir = dir == null || dir.isBlank()
            ? Path.of(System.getProperty("user.home"), ".relacionar-clases")
            : Path.of(dir);
        int minDecisions = parseInt(env.get("RELATIONS_MIN_DECISIONS"), MLConfig.DEFAULT.minDecisions(),
            "RELATIONS_MIN_DECISIONS");
        long debounceMs = parseInt(env.get("RELATIONS_RETRAIN_DEBOUNCE_MS"), 2000, "RELATIONS_RETRAIN_DEBOUNCE_MS");
        return new ApiConfig(HOST, port, allowed, dataDir.toAbsolutePath().normalize(), minDecisions,
            Duration.ofMillis(Math.max(0, debounceMs)));
    }

    private static int parseInt(String value, int defaultValue, String name) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " no es un número: " + value, e);
        }
    }
}
