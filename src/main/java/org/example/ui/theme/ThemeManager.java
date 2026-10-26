package org.example.ui.theme;

import java.net.URL;
import java.util.logging.Logger;

import org.example.service.ConfigService;

import javafx.scene.Scene;

/**
 * 🎨 ThemeManager
 * ----------------------------------------------------
 * Controla la carga dinámica de temas (claro / oscuro)
 * en la interfaz JavaFX sin necesidad de reiniciar.
 */
public class ThemeManager {

    private static final Logger LOGGER = Logger.getLogger(ThemeManager.class.getName());
    private static final String LIGHT_THEME = "/style-light.css";
    private static final String DARK_THEME = "/style-dark.css";

    private final ConfigService configService;

    public ThemeManager(ConfigService configService) {
        this.configService = configService;
    }

    /** Aplica el tema actual guardado */
    public void applySavedTheme(Scene scene) {
        String theme = configService.getTheme();
        applyTheme(scene, theme);
    }

    /** Aplica el tema especificado */
    public void applyTheme(Scene scene, String theme) {
        scene.getStylesheets().clear();
        String cssPath = theme.equals("dark") ? DARK_THEME : LIGHT_THEME;
        URL css = getClass().getResource(cssPath);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        } else {
            LOGGER.warning("No se pudo encontrar el archivo CSS: " + cssPath);
        }
        configService.setTheme(theme);
    }

    /** Alterna entre claro/oscuro */
    public void toggleTheme(Scene scene) {
        String current = configService.getTheme();
        String next = current.equals("dark") ? "light" : "dark";
        applyTheme(scene, next);
    }

    /** Devuelve true si está en modo oscuro */
    public boolean isDarkMode() {
        return configService.getTheme().equals("dark");
    }
}
