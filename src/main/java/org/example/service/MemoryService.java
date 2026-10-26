package org.example.service;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.config.ConfigManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Gestiona una memoria persistente en JSON para recordar decisiones del usuario.
 * Proporciona métodos para cargar, guardar, consultar, añadir y borrar la memoria.
 */
public class MemoryService {

    private static final Logger LOGGER = Logger.getLogger(MemoryService.class.getName());
    private static final Gson gson = new Gson();
    
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

    /**
     * Carga el fichero JSON de memoria en la estructura interna.
     * Si no existe o hay error, inicializa una memoria vacía.
     */
    public synchronized void cargar() {
        loadFromDisk();
    }
    
    private void loadFromDisk() {
        File f = new File(filePath);
        if (!f.exists()) {
            LOGGER.info(() -> "No se encontró memoria previa. Se creará una nueva en: " + filePath);
            memoria = new HashMap<>();
            return;
        }

        try (FileReader reader = new FileReader(f)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = gson.fromJson(reader, type);
            memoria = (loaded != null) ? loaded : new HashMap<>();
            final int memoriaSize = memoria.size();
            LOGGER.info(() -> "Memoria cargada: " + memoriaSize + " relaciones");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error leyendo memoria: " + e.getMessage(), e);
            memoria = new HashMap<>();
        }
    }

    /**
     * Guarda la memoria actual en disco (archivo JSON).
     */
    public synchronized void guardar() {
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(memoria, writer);
            LOGGER.fine("Memoria guardada correctamente");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error guardando memoria: " + e.getMessage(), e);
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
     * Devuelve una vista inmutable de la memoria actual.
     */
    public synchronized Map<String, String> getMemoria() {
        return Collections.unmodifiableMap(new HashMap<>(memoria));
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
        if (memoria.remove(relacion) != null) guardar();
    }
}
