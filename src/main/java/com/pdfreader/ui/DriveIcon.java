package com.pdfreader.ui;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * Authentic vector-based Google Drive icon rendered on a JavaFX Canvas.
 * Provides pixel-crisp, professional visuals without blurry emojis.
 */
public class DriveIcon extends Region {

    private final Canvas canvas;

    public DriveIcon(double size) {
        setPrefSize(size, size);
        setMinSize(size, size);
        setMaxSize(size, size);

        canvas = new Canvas(size, size);
        getChildren().add(canvas);
        draw(size);
    }

    private void draw(double size) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, size, size);

        double s = size / 64.0;

        // Yellow Segment (Top-Left diagonal bar)
        gc.setFill(Color.web("#FFBA08"));
        double[] xYellow = new double[]{ 61.0 * s, 42.7 * s, 21.3 * s, 39.6 * s };
        double[] yYellow = new double[]{ 35.3 * s, 3.6 * s, 3.6 * s, 35.3 * s };
        gc.fillPolygon(xYellow, yYellow, 4);

        // Green Segment (Left-Down diagonal bar)
        gc.setFill(Color.web("#0F9D58"));
        double[] xGreen = new double[]{ 18.3 * s, 0.0 * s, 10.7 * s, 29.0 * s };
        double[] yGreen = new double[]{ 8.8 * s, 40.5 * s, 59.0 * s, 27.3 * s };
        gc.fillPolygon(xGreen, yGreen, 4);

        // Blue Segment (Bottom horizontal bar)
        gc.setFill(Color.web("#4285F4"));
        double[] xBlue = new double[]{ 28.7 * s, 18.0 * s, 53.3 * s, 64.0 * s };
        double[] yBlue = new double[]{ 40.5 * s, 59.0 * s, 59.0 * s, 40.5 * s };
        gc.fillPolygon(xBlue, yBlue, 4);
    }

    /**
     * Creates a combined Drive icon + PDF badge for table rows.
     */
    public static HBox createDrivePdfBadge(double size) {
        HBox badge = new HBox(4);
        badge.setAlignment(Pos.CENTER);

        DriveIcon icon = new DriveIcon(size);
        Label pdfTag = new Label("PDF");
        pdfTag.setStyle("-fx-font-size: 9px; -fx-font-weight: 800; -fx-text-fill: #dc2626; -fx-background-color: #fee2e2; -fx-padding: 1px 4px; -fx-background-radius: 4px;");

        badge.getChildren().addAll(icon, pdfTag);
        return badge;
    }
}
