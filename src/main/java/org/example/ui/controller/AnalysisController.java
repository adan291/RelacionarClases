package org.example.ui.controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import org.example.controller.RelationAnalysisOrchestrator;
import org.example.model.RelacionDetectada;
import org.example.service.MemoryService;

import java.util.List;

/**
 * 🧠 AnalysisController
 * ---------------------
 * Coordina el análisis de relaciones y actualiza la UI (progreso, tabla y logs).
 */
public class AnalysisController {

    private final RelationAnalysisOrchestrator orchestrator = new RelationAnalysisOrchestrator();
    private final MemoryService memoryService = orchestrator.getMemoryService();

    public MemoryService getMemoryService() {
        return memoryService;
    }

    /**
     * Ejecuta el análisis en un hilo separado y actualiza los componentes.
     */
    public void ejecutarAnalisis(String path,
                                 TableView<RelacionDetectada> table,
                                 ProgressBar progressBar,
                                 Label progressLabel,
                                 Runnable onSuccess,
                                 Runnable onError,
                                 java.util.function.Consumer<String> logConsumer) {
        logConsumer.accept("🚀 Iniciando análisis de clases...");

        progressBar.setProgress(0);
        progressLabel.setText("Analizando archivos...");

        new Thread(() -> {
            try {
                List<RelacionDetectada> relaciones = orchestrator.ejecutarAnalisis(path);

                for (int i = 1; i <= 100; i++) {
                    double progress = i / 100.0;
                    Platform.runLater(() -> {
                        progressBar.setProgress(progress);
                        progressLabel.setText(String.format("Analizando... %.0f%%", progress * 100));
                    });
                    Thread.sleep(15);
                }

                Platform.runLater(() -> {
                    table.getItems().setAll(relaciones);
                    progressLabel.setText("✅ Análisis finalizado");
                    logConsumer.accept("✅ Análisis completado. Relaciones detectadas: " + relaciones.size());
                    onSuccess.run();
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progressLabel.setText("⚠️ Error en análisis");
                    logConsumer.accept("❌ Error durante el análisis: " + ex.getMessage());
                    onError.run();
                });
            }
        }).start();
    }
}
