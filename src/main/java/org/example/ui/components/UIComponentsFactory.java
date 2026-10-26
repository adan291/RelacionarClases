package org.example.ui.components;

import org.example.model.RelacionDetectada;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

/**
 * 🧱 UIComponentsFactory
 * ---------------------
 * Fábrica de componentes visuales reutilizables con estilo consistente.
 */
public class UIComponentsFactory {

    /** Crea un botón con color base */
    public static Button styledButton(String text, String color, Runnable onClick) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Segoe UI", 14));
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: derive(" + color + ", 20%); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;"));
        if (onClick != null) btn.setOnAction(e -> onClick.run());
        return btn;
    }

    /** Crea la tabla de relaciones */
    public static TableView<RelacionDetectada> createRelationTable() {
        TableView<RelacionDetectada> table = new TableView<>();

        TableColumn<RelacionDetectada, String> origenCol = new TableColumn<>("Clase Origen");
        origenCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getOrigen()));

        TableColumn<RelacionDetectada, String> destinoCol = new TableColumn<>("Clase Destino");
        destinoCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDestino()));

        TableColumn<RelacionDetectada, String> tipoCol = new TableColumn<>("Tipo");
        tipoCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTipo()));

        TableColumn<RelacionDetectada, String> confCol = new TableColumn<>("Confianza");
        confCol.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.2f", d.getValue().getConfianza())));

        table.getColumns().add(origenCol);
        table.getColumns().add(destinoCol);
        table.getColumns().add(tipoCol);
        table.getColumns().add(confCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(RelacionDetectada item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setStyle("");
                else {
                    double c = item.getConfianza();
                    String color = c > 0.85 ? "rgba(76,175,80,0.3)" :
                            c > 0.6 ? "rgba(255,235,59,0.3)" :
                                    "rgba(244,67,54,0.3)";
                    setStyle("-fx-background-color: " + color + ";");
                }
            }
        });

        return table;
    }

    /** Crea la barra de progreso + etiqueta */
    public static VBox createProgressSection(ProgressBar bar, Label label) {
        bar.setPrefWidth(600);
        VBox box = new VBox(5, label, bar);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10));
        return box;
    }

    /** Crea el área de logs */
    public static TextArea createLogArea() {
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(160);
        logArea.setStyle("-fx-font-family:'Consolas'; -fx-font-size:12px;");
        return logArea;
    }

    /** Crea una barra de botones */
    public static HBox createButtonBar(Button... buttons) {
        HBox hbox = new HBox(15, buttons);
        hbox.setAlignment(Pos.CENTER);
        hbox.setPadding(new Insets(15));
        return hbox;
    }
}
