package org.example.service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.config.ConfigManager;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 * Gestiona una memoria persistente en JSON para recordar decisiones del usuario.
 * Proporciona métodos para cargar, guardar, consultar, añadir y borrar la memoria.
 *
 * <p>Si el fichero está corrupto, se copia a {@code <fichero>.corrupt} y se empieza con la
 * memoria vacía. La escritura es atómica: se escribe en un temporal del mismo directorio y se
 * renombra encima del fichero.</p>
 */
public class MemoryService {

    private static final Logger LOGGER = Logger.getLogger(MemoryService.class.getName());
    private static final Gson gson = new Gson();
    private static final Type MEMORY_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    
    /** Ruta del fichero; {@code null} en una memoria solo en RAM. */
    private final String filePath;

    // memoria: clave = "Origen -> Destino : Tipo", valor = "aceptada" | "rechazada"
    private Map<String, String> memoria = new HashMap<>();

    /**
     * Creates a MemoryService using the configured memory file path.
     */
    public MemoryService() {
        this.filePath = ConfigManager.getInstance().getPersistenceConfig().memoryFilePath();
        loadFromDisk();
    }
    
    /**
     * Creates a MemoryService with a custom file path (for testing).
     * @param filePath the path to the memory file
     */
    public MemoryService(String filePath) {
        this.filePath = filePath;
        loadFromDisk();
    }

    private MemoryService(Map<String, String> initial) {
        this.filePath = null;
        this.memoria = new HashMap<>(initial);
    }

    /**
     * Crea una memoria que no lee ni escribe en disco (tests, benchmarks, API sin persistencia).
     *
     * @param initial decisiones iniciales (se copian)
     * @return memoria en RAM
     */
    public static MemoryService inMemory(Map<String, String> initial) {
        return new MemoryService(initial != null ? initial : Map.of());
    }

    /**
     * Carga el fichero JSON de memoria en la estructura interna.
     * Si no existe o hay error, inicializa una memoria vacía.
     */
    public synchronized void cargar() {
        loadFromDisk();
    }
    
    private synchronized void loadFromDisk() {
        if (filePath == null) {
            return;
        }
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            LOGGER.info(() -> "No se encontró memoria previa. Se creará una nueva en: " + filePath);
            memoria = new HashMap<>();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, String> loaded = gson.fromJson(reader, MEMORY_TYPE);
            memoria = (loaded != null) ? new HashMap<>(loaded) : new HashMap<>();
            memoria.values().removeIf(v -> v == null);
            final int memoriaSize = memoria.size();
            LOGGER.info(() -> "Memoria cargada: " + memoriaSize + " relaciones");
        } catch (JsonParseException e) {
            // JSON mal formado o con otra estructura: se aparta y se empieza de cero
            Path backup = Path.of(filePath + ".corrupt");
            try {
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.log(Level.WARNING, e, () -> "Memoria corrupta en " + filePath
                        + ". Copia guardada en " + backup + "; se empieza con memoria vacía");
            } catch (IOException copyError) {
                LOGGER.log(Level.SEVERE, copyError, () -> "Memoria corrupta en " + filePath
                        + " y no se pudo copiar a " + backup);
            }
            memoria = new HashMap<>();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error leyendo memoria: " + e.getMessage(), e);
            memoria = new HashMap<>();
        }
    }

    /**
     * Guarda la memoria actual en disco (archivo JSON) de forma atómica.
     */
    public synchronized void guardar() {
        if (filePath == null) {
            return;
        }
        Path target = Path.of(filePath).toAbsolutePath();
        Path dir = target.getParent();
        Path tmp = null;
        try {
            if (dir != null) {
                Files.createDirectories(dir);
            }
            tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(memoria, writer);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.fine("Memoria guardada correctamente");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error guardando memoria: " + e.getMessage(), e);
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // no se puede hacer más
                }
            }
        }
    }

    /**
     * Añade o actualiza una decisión en la memoria y la persiste.
     *
     * @param relacion texto que representa la relación (por ejemplo: "Oficina -> Ciudad : ManyToOne")
     * @param aceptada true si fue aceptada, false si fue rechazada
     */
    public synchronized void recordarDecision(String relacion, boolean aceptada) {
        if (relacion == null || relacion.isBlank()) return;
        memoria.put(relacion, aceptada ? "aceptada" : "rechazada");

        guardar();
    }

    /**
     * Añade varias decisiones y escribe el fichero una sola vez.
     *
     * @param decisiones clave de relación → true si fue aceptada
     */
    public synchronized void recordarDecisiones(Map<String, Boolean> decisiones) {
        if (decisiones == null || decisiones.isEmpty()) return;
        decisiones.forEach((relacion, aceptada) -> {
            if (relacion != null && !relacion.isBlank() && aceptada != null) {
                memoria.put(relacion, aceptada ? "aceptada" : "rechazada");
            }
        });

        guardar();
    }

    /**
     * Devuelve una vista inmutable de la memoria actual.
     */
    public synchronized Map<String, String> getMemoria() {
        return Collections.unmodifiableMap(new HashMap<>(memoria));
    }

    /**
     * @return ruta del fichero de memoria, o {@code null} si es una memoria en RAM
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Borra toda la memoria y lo persiste (archivo quedará vacío).
     */
    public synchronized void clearMemoria() {
        memoria.clear();

        guardar();
        LOGGER.info("Memoria borrada");
    }

    /**
     * Elimina una entrada concreta de la memoria (si existe) y la persiste.
     *
     * @param relacion clave exacta a eliminar
     */
    public synchronized void removeDecision(String relacion) {
        if (relacion == null) return;
        if (memoria.remove(relacion) != null) {
            guardar();
        }
    }
}
