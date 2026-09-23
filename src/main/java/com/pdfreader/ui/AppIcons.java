package com.pdfreader.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;

/**
 * Professional vector icons utility for JavaFX.
 * Provides crisp SVG-based icons without font-based emojis to prevent Windows tofu boxes.
 */
public final class AppIcons {

    private AppIcons() {}

    // --- Material / Feather standard 24x24 SVG paths ---
    private static final String PATH_FOLDER = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
    private static final String PATH_DETAILS = "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z";
    private static final String PATH_GRID = "M4 4h6v6H4V4zm10 0h6v6h-6V4zM4 14h6v6H4v-6zm10 0h6v6h-6v-6z";
    private static final String PATH_SPLIT = "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-8 16H5V5h6v14zm8 0h-6V5h6v14z";
    private static final String PATH_HOME = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
    private static final String PATH_ACCOUNT = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
    private static final String PATH_IMPORT = "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";
    private static final String PATH_REFRESH = "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z";
    private static final String PATH_SEARCH = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
    private static final String PATH_TRASH = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";
    private static final String PATH_OPEN_BOOK = "M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.65 1 5.75V21c1.45-1.1 3.55-1.5 5.5-1.5 1.95 0 4.05.4 5.5 1.5 1.45-1.1 3.55-1.5 5.5-1.5 1.17 0 2.39.15 3.5.5V5zm-2 13.5c-.89-.25-1.92-.35-3-.35-1.74 0-3.41.35-4.5 1V7c1.09-.65 2.76-1 4.5-1 1.08 0 2.11.1 3 .35v12.15z";
    private static final String PATH_DOWNLOAD = "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z";
    private static final String PATH_UPLOAD = "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z";
    private static final String PATH_SAVE_LINK = "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z";
    private static final String PATH_CHEVRON_UP = "M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z";
    private static final String PATH_CHEVRON_DOWN = "M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z";
    private static final String PATH_FULLSCREEN = "M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z";
    private static final String PATH_EYE = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    private static final String PATH_PEN = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";
    private static final String PATH_SETTINGS = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";
    private static final String PATH_ARROW_LEFT = "M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6 6 6 1.41-1.41z";
    private static final String PATH_ARROW_RIGHT = "M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z";
    private static final String PATH_FIRST_PAGE = "M18.41 16.59L13.82 12l4.59-4.59L17 6l-6 6 6 6zM6 6h2v12H6z";
    private static final String PATH_LAST_PAGE = "M5.59 7.41L10.18 12l-4.59 4.59L7 18l6-6-6-6zM16 6h2v12h-2z";

    /**
     * Creates a resizable SVG vector icon inside a positioned container.
     */
    public static Region createSvgIcon(String pathContent, double size, Paint fill) {
        SVGPath svg = new SVGPath();
        svg.setContent(pathContent);
        svg.setFill(fill);

        double scale = size / 24.0;
        svg.setScaleX(scale);
        svg.setScaleY(scale);

        StackPane container = new StackPane(svg);
        container.setPrefSize(size, size);
        container.setMinSize(size, size);
        container.setMaxSize(size, size);
        container.setAlignment(Pos.CENTER);
        return container;
    }

    public static Region createFolderIcon(double size, Color color) {
        return createSvgIcon(PATH_FOLDER, size, color != null ? color : Color.web("#4a5568"));
    }

    public static Region createFolderIcon(double size) {
        return createFolderIcon(size, Color.web("#f59e0b")); // amber folder
    }

    public static Region createDetailsIcon(double size) {
        return createSvgIcon(PATH_DETAILS, size, Color.web("#4a5568"));
    }

    public static Region createGridIcon(double size) {
        return createSvgIcon(PATH_GRID, size, Color.web("#4a5568"));
    }

    public static Region createSplitIcon(double size) {
        return createSvgIcon(PATH_SPLIT, size, Color.web("#4a5568"));
    }

    public static Region createHomeIcon(double size) {
        return createSvgIcon(PATH_HOME, size, Color.web("#4a5568"));
    }

    public static Region createAccountIcon(double size) {
        return createSvgIcon(PATH_ACCOUNT, size, Color.web("#4a5568"));
    }

    public static Region createImportIcon(double size) {
        return createSvgIcon(PATH_IMPORT, size, Color.WHITE);
    }

    public static Region createRefreshIcon(double size) {
        return createSvgIcon(PATH_REFRESH, size, Color.web("#4a5568"));
    }

    public static Region createSearchIcon(double size) {
        return createSvgIcon(PATH_SEARCH, size, Color.web("#718096"));
    }

    public static Region createTrashIcon(double size) {
        return createSvgIcon(PATH_TRASH, size, Color.web("#ef4444"));
    }

    public static Region createOpenBookIcon(double size, Color color) {
        return createSvgIcon(PATH_OPEN_BOOK, size, color != null ? color : Color.web("#10b981"));
    }

    public static Region createDownloadIcon(double size, Color color) {
        return createSvgIcon(PATH_DOWNLOAD, size, color != null ? color : Color.WHITE);
    }

    public static Region createUploadIcon(double size, Color color) {
        return createSvgIcon(PATH_UPLOAD, size, color != null ? color : Color.web("#4a5568"));
    }

    public static Region createSaveLinkIcon(double size) {
        return createSvgIcon(PATH_SAVE_LINK, size, Color.WHITE);
    }

    public static Region createChevronUpIcon(double size) {
        return createSvgIcon(PATH_CHEVRON_UP, size, Color.web("#4a5568"));
    }

    public static Region createChevronDownIcon(double size) {
        return createSvgIcon(PATH_CHEVRON_DOWN, size, Color.web("#4a5568"));
    }

    public static Region createFullscreenIcon(double size) {
        return createSvgIcon(PATH_FULLSCREEN, size, Color.web("#4a5568"));
    }

    public static Region createSettingsIcon(double size) {
        return createSvgIcon(PATH_SETTINGS, size, Color.web("#4a5568"));
    }

    public static Region createEyeIcon(double size, Color color) {
        return createSvgIcon(PATH_EYE, size, color != null ? color : Color.web("#0284c7"));
    }

    public static Region createPenIcon(double size, Color color) {
        return createSvgIcon(PATH_PEN, size, color != null ? color : Color.web("#059669"));
    }

    public static Region createArrowLeftIcon(double size) {
        return createSvgIcon(PATH_ARROW_LEFT, size, Color.web("#4a5568"));
    }

    public static Region createArrowRightIcon(double size) {
        return createSvgIcon(PATH_ARROW_RIGHT, size, Color.web("#4a5568"));
    }

    public static Region createFirstPageIcon(double size) {
        return createSvgIcon(PATH_FIRST_PAGE, size, Color.web("#4a5568"));
    }

    public static Region createLastPageIcon(double size) {
        return createSvgIcon(PATH_LAST_PAGE, size, Color.web("#4a5568"));
    }

    /**
     * Professional book cover placeholder displayed while PDF page thumbnail is loading or missing.
     * Guaranteed no square/tofu boxes on any OS.
     */
    public static StackPane createBookCoverPlaceholder(double width, double height) {
        StackPane root = new StackPane();
        root.setPrefSize(width, height);
        root.setMinSize(width, height);
        root.setMaxSize(width, height);
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #334155, #1e293b); "
                + "-fx-background-radius: 4px; -fx-border-color: #475569; -fx-border-radius: 4px; -fx-border-width: 1px;");

        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);

        double iconSize = Math.max(14, Math.min(width * 0.45, 24));
        Region bookIcon = createOpenBookIcon(iconSize, Color.web("#94a3b8"));

        Label pdfTag = new Label("PDF");
        pdfTag.setStyle("-fx-font-size: 8px; -fx-font-weight: 800; -fx-text-fill: white; "
                + "-fx-background-color: #dc2626; -fx-padding: 1px 3px; -fx-background-radius: 3px;");

        box.getChildren().addAll(bookIcon, pdfTag);
        root.getChildren().add(box);
        return root;
    }
}
