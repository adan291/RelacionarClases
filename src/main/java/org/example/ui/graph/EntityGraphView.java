package org.example.ui.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.example.model.RelacionDetectada;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * 📊 EntityGraphView
 * Visualización interactiva de entidades y sus relaciones como grafo.
 */
public class EntityGraphView {
    
    private static final Logger logger = Logger.getLogger(EntityGraphView.class.getName());

    private static final double NODE_RADIUS = 40;
    private static final double ARROW_SIZE = 10;
    
    private final Map<String, Point2D> nodePositions = new HashMap<>();
    private final Map<String, Circle> nodeCircles = new HashMap<>();
    private final Pane graphPane = new Pane();
    private double dragOffsetX, dragOffsetY;
    
    // Colores por tipo de relación
    private static final Map<String, Color> RELATION_COLORS = Map.of(
        "OneToOne", Color.web("#9C27B0"),
        "OneToMany", Color.web("#2196F3"),
        "ManyToOne", Color.web("#4CAF50"),
        "ManyToMany", Color.web("#FF9800")
    );

    /**
     * Muestra el grafo de relaciones en una nueva ventana.
     */
    public void show(List<RelacionDetectada> relaciones) {
        final int relationCount = relaciones.size();
        logger.info(() -> "Showing entity graph with " + relationCount + " relations");
        Stage stage = new Stage();
        stage.setTitle("📊 Grafo de Entidades");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Calcular posiciones iniciales
        calculateInitialPositions(relaciones);

        // Dibujar grafo
        drawGraph(relaciones);

        // Controles
        Button resetBtn = new Button("🔄 Reset");
        Button zoomInBtn = new Button("🔍+");
        Button zoomOutBtn = new Button("🔍-");
        
        resetBtn.setOnAction(e -> {
            calculateInitialPositions(relaciones);
            drawGraph(relaciones);
        });

        // Leyenda
        HBox legend = createLegend();
        
        HBox controls = new HBox(10, resetBtn, zoomInBtn, zoomOutBtn, legend);
        controls.setPadding(new Insets(10));

        StackPane graphContainer = new StackPane(graphPane);
        graphContainer.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-radius: 5;");

        root.setCenter(graphContainer);
        root.setBottom(controls);

        Scene scene = new Scene(root, 900, 700);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Calcula posiciones iniciales de los nodos en círculo.
     */
    private void calculateInitialPositions(List<RelacionDetectada> relaciones) {
        nodePositions.clear();
        
        // Obtener todas las entidades únicas
        java.util.Set<String> entities = new java.util.LinkedHashSet<>();
        for (RelacionDetectada rel : relaciones) {
            entities.add(rel.getOrigen());
            entities.add(rel.getDestino());
        }

        final int entityCount = entities.size();
        logger.fine(() -> "Calculating initial positions for " + entityCount + " entities");

        // Distribuir en círculo
        double centerX = 400;
        double centerY = 300;
        double radius = Math.min(250, 50 + entities.size() * 30);
        
        int i = 0;
        int total = entities.size();
        for (String entity : entities) {
            double angle = 2 * Math.PI * i / total - Math.PI / 2;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            nodePositions.put(entity, new Point2D(x, y));
            i++;
        }
        
        logger.fine(() -> "Node positions calculated with radius " + radius);
    }

    /**
     * Dibuja el grafo completo.
     */
    private void drawGraph(List<RelacionDetectada> relaciones) {
        final int relationCount = relaciones.size();
        final int nodeCount = nodePositions.size();
        logger.fine(() -> "Drawing graph with " + nodeCount + " nodes and " + relationCount + " relations");
        
        graphPane.getChildren().clear();
        nodeCircles.clear();

        // Primero dibujar las líneas (para que queden detrás)
        for (RelacionDetectada rel : relaciones) {
            drawRelation(rel);
        }

        // Luego dibujar los nodos
        for (Map.Entry<String, Point2D> entry : nodePositions.entrySet()) {
            drawNode(entry.getKey(), entry.getValue(), relaciones);
        }
        
        logger.fine(() -> "Graph rendering complete");
    }

    /**
     * Dibuja un nodo (entidad).
     */
    private void drawNode(String name, Point2D pos, List<RelacionDetectada> relaciones) {
        logger.finest(() -> "Drawing node: " + name + " at position (" + pos.getX() + ", " + pos.getY() + ")");
        
        // Círculo del nodo
        Circle circle = new Circle(pos.getX(), pos.getY(), NODE_RADIUS);
        circle.setFill(Color.WHITE);
        circle.setStroke(Color.web("#333"));
        circle.setStrokeWidth(2);
        circle.setCursor(Cursor.MOVE);

        // Texto del nombre
        Text text = new Text(name);
        text.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        text.setX(pos.getX() - text.getLayoutBounds().getWidth() / 2);
        text.setY(pos.getY() + 4);

        // Tooltip con info
        long relCount = relaciones.stream()
            .filter(r -> r.getOrigen().equals(name) || r.getDestino().equals(name))
            .count();
        Tooltip tooltip = new Tooltip(name + "\n" + relCount + " relaciones");
        tooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(circle, tooltip);

        // Drag & drop
        circle.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX() - circle.getCenterX();
            dragOffsetY = e.getSceneY() - circle.getCenterY();
        });

        circle.setOnMouseDragged(e -> {
            double newX = e.getSceneX() - dragOffsetX;
            double newY = e.getSceneY() - dragOffsetY;
            
            circle.setCenterX(newX);
            circle.setCenterY(newY);
            text.setX(newX - text.getLayoutBounds().getWidth() / 2);
            text.setY(newY + 4);
            
            nodePositions.put(name, new Point2D(newX, newY));
            
            // Redibujar líneas
            drawGraph(relaciones);
        });

        // Hover effect
        circle.setOnMouseEntered(e -> {
            circle.setFill(Color.web("#e3f2fd"));
            circle.setStrokeWidth(3);
        });
        circle.setOnMouseExited(e -> {
            circle.setFill(Color.WHITE);
            circle.setStrokeWidth(2);
        });

        nodeCircles.put(name, circle);
        graphPane.getChildren().addAll(circle, text);
    }

    /**
     * Dibuja una relación (línea con flecha).
     */
    private void drawRelation(RelacionDetectada rel) {
        Point2D from = nodePositions.get(rel.getOrigen());
        Point2D to = nodePositions.get(rel.getDestino());
        
        if (from == null || to == null) {
            logger.warning(() -> "Cannot draw relation: missing node position for " + 
                rel.getOrigen() + " -> " + rel.getDestino());
            return;
        }
        
        logger.finest(() -> "Drawing relation: " + rel.getOrigen() + " -> " + 
            rel.getDestino() + " (" + rel.getTipo() + ")");

        // Calcular puntos de inicio/fin en el borde del círculo
        double angle = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
        
        double startX = from.getX() + NODE_RADIUS * Math.cos(angle);
        double startY = from.getY() + NODE_RADIUS * Math.sin(angle);
        double endX = to.getX() - NODE_RADIUS * Math.cos(angle);
        double endY = to.getY() - NODE_RADIUS * Math.sin(angle);

        // Offset para relaciones bidireccionales (evitar superposición)
        if (rel.isBidirectional()) {
            double offset = 8;
            double perpAngle = angle + Math.PI / 2;
            if (!rel.isOwnerSide()) {
                offset = -offset;
            }
            startX += offset * Math.cos(perpAngle);
            startY += offset * Math.sin(perpAngle);
            endX += offset * Math.cos(perpAngle);
            endY += offset * Math.sin(perpAngle);
        }

        // Línea
        Line line = new Line(startX, startY, endX, endY);
        Color color = RELATION_COLORS.getOrDefault(rel.getTipo(), Color.GRAY);
        line.setStroke(color);
        line.setStrokeWidth(2);
        
        // Estilo según tipo
        if (rel.getTipo().contains("Many")) {
            line.getStrokeDashArray().addAll(5.0, 5.0);
        }

        // Flecha
        Polygon arrow = createArrow(endX, endY, angle, color);

        // Etiqueta del tipo
        double midX = (startX + endX) / 2;
        double midY = (startY + endY) / 2;
        
        Label label = new Label(getShortType(rel.getTipo()));
        label.setFont(Font.font("Segoe UI", 9));
        label.setTextFill(color);
        label.setStyle("-fx-background-color: white; -fx-padding: 1 3;");
        label.setLayoutX(midX - 15);
        label.setLayoutY(midY - 8);

        // Tooltip en la línea
        String tooltipText = rel.getOrigen() + " → " + rel.getDestino() + 
            "\nTipo: " + rel.getTipo() +
            "\nConfianza: " + String.format("%.0f%%", rel.getConfianza() * 100) +
            (rel.isBidirectional() ? "\n🔄 Bidireccional" : "");
        Tooltip lineTooltip = new Tooltip(tooltipText);
        Tooltip.install(line, lineTooltip);

        graphPane.getChildren().addAll(line, arrow, label);
    }

    /**
     * Crea una flecha.
     */
    private Polygon createArrow(double x, double y, double angle, Color color) {
        double x1 = x - ARROW_SIZE * Math.cos(angle - Math.PI / 6);
        double y1 = y - ARROW_SIZE * Math.sin(angle - Math.PI / 6);
        double x2 = x - ARROW_SIZE * Math.cos(angle + Math.PI / 6);
        double y2 = y - ARROW_SIZE * Math.sin(angle + Math.PI / 6);

        Polygon arrow = new Polygon(x, y, x1, y1, x2, y2);
        arrow.setFill(color);
        return arrow;
    }

    /**
     * Obtiene abreviatura del tipo.
     */
    private String getShortType(String tipo) {
        return switch (tipo) {
            case "OneToOne" -> "1:1";
            case "OneToMany" -> "1:N";
            case "ManyToOne" -> "N:1";
            case "ManyToMany" -> "N:M";
            default -> tipo;
        };
    }

    /**
     * Crea la leyenda de colores.
     */
    private HBox createLegend() {
        HBox legend = new HBox(15);
        legend.setPadding(new Insets(0, 0, 0, 20));

        for (Map.Entry<String, Color> entry : RELATION_COLORS.entrySet()) {
            Circle dot = new Circle(6, entry.getValue());
            Label label = new Label(getShortType(entry.getKey()));
            label.setFont(Font.font("Segoe UI", 10));
            
            HBox item = new HBox(5, dot, label);
            legend.getChildren().add(item);
        }

        return legend;
    }
}
