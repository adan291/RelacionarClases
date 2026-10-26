package org.example.service;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ⚙️ ConfigService (Versión completa)
 * ------------------------------------
 * Guarda preferencias persistentes del usuario:
 *  - Modo visual (claro / oscuro)
 *  - Tamaño y posición de la ventana
 *  - Última carpeta analizada
 *  - Cualquier otra clave personalizada
 *
 * Se almacena en "config.properties" en el directorio raíz.
 */
public class ConfigService {

    private static final Logger LOGGER = Logger.getLogger(ConfigService.class.getName());
    private static final String CONFIG_FILE = "config.properties";
    private final Properties props = new Properties();

    public ConfigService() {
        load();
    }

    /** Cargar configuraciones desde archivo */
    private void load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                props.load(reader);
                LOGGER.config("Configuración cargada: " + props);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error cargando configuración: " + e.getMessage(), e);
            }
        }
    }

    /** Guardar configuraciones en disco */
    private void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            props.store(writer, "Configuración del usuario - Intelligent Relation Analyzer");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error guardando configuración: " + e.getMessage(), e);
        }
    }

    // ========================================================
    // 🔹 Métodos genéricos
    // ========================================================

    /** Obtener valor de propiedad (con valor por defecto) */
    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    /** Establecer valor de propiedad y guardar */
    public void set(String key, String value) {
        props.setProperty(key, value);
        save();
    }

    // ========================================================
    // 🔹 Tema visual
    // ========================================================

    public String getTheme() {
        return props.getProperty("theme", "dark");
    }

    public void setTheme(String theme) {
        set("theme", theme);
    }

    // ========================================================
    // 🔹 Ventana
    // ========================================================

    public double getWindowX() {
        return Double.parseDouble(props.getProperty("window.x", "100"));
    }

    public double getWindowY() {
        return Double.parseDouble(props.getProperty("window.y", "100"));
    }

    public double getWindowWidth() {
        return Double.parseDouble(props.getProperty("window.width", "950"));
    }

    public double getWindowHeight() {
        return Double.parseDouble(props.getProperty("window.height", "650"));
    }

    public void saveWindowBounds(double x, double y, double w, double h) {
        props.setProperty("window.x", String.valueOf(x));
        props.setProperty("window.y", String.valueOf(y));
        props.setProperty("window.width", String.valueOf(w));
        props.setProperty("window.height", String.valueOf(h));
        save();
    }

    // ========================================================
    // 🔹 Última ruta analizada
    // ========================================================

    public String getLastPath() {
        return props.getProperty("last.path", "src/main/java/org/example/ejemplos");
    }

    public void setLastPath(String path) {
        props.setProperty("last.path", path);
        save();
    }

    // ========================================================
    // 🔹 Limpieza total
    // ========================================================

    public void clear() {
        props.clear();
        save();
    }
}
