package org.example.ui.theme;

import javafx.animation.FillTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * ThemeToggleSwitch — Botón elegante para alternar modo claro/oscuro.
 * Robustez: espera a que la Scene esté disponible y llama a ThemeManager.toggleTheme(scene).
 */
public class ThemeToggleSwitch {

    private final ToggleButton toggle;
    private final StackPane container;
    private final Circle knob;
    private final StackPane track;

    private final double width = 52;
    private final double height = 28;
    private final double knobOffset = (width - height) / 2.0;

    public ThemeToggleSwitch(ThemeManager manager) {

        toggle = new ToggleButton();
        toggle.setCursor(Cursor.HAND);
        toggle.setFocusTraversable(false);
        toggle.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        knob = new Circle((height / 2) - 4);
        track = new StackPane();

        container = new StackPane(track, knob);
        container.setMinSize(width, height);
        container.setMaxSize(width, height);
        container.setPadding(new Insets(3));

        // Inicializar colores/posiciones según tema guardado (si manager ya lo sabe)
        boolean dark = manager.isDarkMode();
        applyVisualState(dark, false);

        // Encapsulamos el container como graphic del toggle
        toggle.setGraphic(container);

        // Si la Scene todavía no existe, escuchamos hasta que esté disponible
        container.sceneProperty().addListener((obs, oldS, newS) -> {
            if (oldS == null && newS != null) {
                manager.applySavedTheme(newS);
                boolean actualDark = manager.isDarkMode();
                toggle.setSelected(actualDark);
                applyVisualState(actualDark, false);
            }
        });

        // Acción al pulsar: cambia tema a través del ThemeManager (usa la Scene actual)
        toggle.setOnAction(e -> {
            javafx.scene.Scene scene = container.getScene();
            if (scene == null) {
                // defensivo: si no hay Scene no hacemos nada
                return;
            }
            // Alternar tema en el manager (él se encarga de guardar en config)
            manager.toggleTheme(scene);
            boolean newDark = manager.isDarkMode();
            animateSwitch(newDark);
        });
    }

    /** Anima y aplica colores inmediatamente (si no queremos animación). */
    private void applyVisualState(boolean dark, boolean animate) {
        Color trackColor = dark ? Color.web("#2f3440") : Color.web("#e6e6e6");
        Color knobColor = dark ? Color.DEEPSKYBLUE : Color.GOLD;
        track.setBackground(new Background(new BackgroundFill(trackColor, new CornerRadii(height / 2), Insets.EMPTY)));

        double x = dark ? knobOffset : -knobOffset;
        if (animate) {
            TranslateTransition tt = new TranslateTransition(Duration.seconds(0.28), knob);
            tt.setToX(x);
            tt.play();
            FillTransition ft = new FillTransition(Duration.seconds(0.28), knob, (Color)knob.getFill(), knobColor);
            ft.play();
        } else {
            knob.setTranslateX(x);
            knob.setFill(knobColor);
        }
    }

    /** Ejecuta animación al alternar */
    private void animateSwitch(boolean darkMode) {
        double to = darkMode ? knobOffset : -knobOffset;
        TranslateTransition slide = new TranslateTransition(Duration.seconds(0.28), knob);
        slide.setToX(to);

        Color fromColor = (Color) knob.getFill();
        Color toColor = darkMode ? Color.DEEPSKYBLUE : Color.GOLD;
        FillTransition fill = new FillTransition(Duration.seconds(0.28), knob, fromColor, toColor);

        ParallelTransition pt = new ParallelTransition(slide, fill);
        pt.play();

        // actualizar track color al acabar para mejor contraste
        pt.setOnFinished(ev -> {
            Color trackColor = darkMode ? Color.web("#2f3440") : Color.web("#e6e6e6");
            track.setBackground(new Background(new BackgroundFill(trackColor, new CornerRadii(height / 2), Insets.EMPTY)));
        });
    }

    /** Nodo que debes insertar en tu header (ej: header.getChildren().add(themeToggle.getNode())) */
    public StackPane getNode() {
        return container;
    }

    /** Acceso al ToggleButton si necesitas comportamientos avanzados */
    public ToggleButton getToggle() {
        return toggle;
    }
}
