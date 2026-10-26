package org.example.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * Pantalla inicial con barra de progreso que se actualiza dinámicamente
 * durante el análisis de clases.
 */
public class LoadingScreen {

    private final Stage stage;
    private final ProgressBar progressBar;
    private final Label statusLabel;

    public LoadingScreen(Stage stage) {
        this.stage = stage;
        this.progressBar = new ProgressBar(0);
        this.statusLabel = new Label("Iniciando análisis...");
    }

    public Consumer<String> showAndReturnUpdater(Runnable onFinished) {
        Label title = new Label("🧠 Analizando relaciones entre clases...");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        progressBar.setPrefWidth(300);
        progressBar.setStyle("-fx-accent: #3498db;");

        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #7f8c8d;");

        VBox layout = new VBox(20, title, progressBar, statusLabel);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: linear-gradient(to bottom, #fdfdfd, #eaeaea);");

        Scene scene = new Scene(layout, 520, 300);
        stage.setScene(scene);
        stage.show();

        // Devuelve un "updater" que el detector usará para actualizar la barra
        return (String filename) -> Platform.runLater(() -> {
            double newProgress = progressBar.getProgress() + 0.05;
            progressBar.setProgress(Math.min(1.0, newProgress));
            statusLabel.setText("Analizando: " + filename);

            if (newProgress >= 1.0) {
                onFinished.run();
            }
        });
    }

    /** Permite actualizar el progreso numéricamente (usado por RelationDetector) */
    public void updateProgress(double progress, String fileName) {
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            statusLabel.setText("Analizando: " + fileName);
        });
    }

    public void finish(Runnable onFinished) {
        Platform.runLater(() -> {
            progressBar.setProgress(1);
            statusLabel.setText("✅ Análisis completado");
            onFinished.run();
        });
    }
}
