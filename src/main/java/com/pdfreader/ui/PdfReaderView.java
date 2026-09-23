package com.pdfreader.ui;

import com.pdfreader.model.Book;
import com.pdfreader.service.PdfService;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Embedded JavaFX PDF Reader Viewer component.
 * Supports page navigation, zoom in/out, fit width/page, jump to page,
 * and fullscreen mode with responsive background rendering.
 */
public class PdfReaderView extends BorderPane {

    private final PdfService pdfService = new PdfService();
    private final Runnable onCloseCallback;

    private Book currentBook;
    private int currentPageIndex = 0; // 0-based
    private double currentScale = 1.0;
    private boolean isFitWidth = false;
    private boolean isFitPage = false;
    private boolean isTwoPageMode = false;

    // UI elements
    private Label bookTitleLabel;
    private Button prevBtn;
    private Button nextBtn;
    private TextField pageInputField;
    private Label totalPagesLabel;
    private Button zoomOutBtn;
    private Button zoomInBtn;
    private ComboBox<String> zoomCombo;
    private Button twoPageBtn;
    private Button fullscreenBtn;
    private ImageView pageImageView;
    private ImageView rightPageImageView;
    private Region centerSpineDivider;
    private HBox bookSpreadContainer;
    private ScrollPane scrollPane;
    private ProgressIndicator loadingIndicator;
    private StackPane viewportStack;

    public PdfReaderView(Runnable onCloseCallback) {
        this.onCloseCallback = onCloseCallback;

        initUI();
        initKeyAndMouseHandlers();
    }

    private void initUI() {
        getStyleClass().add("pdf-reader-view");

        // --- Toolbar ---
        ToolBar toolBar = new ToolBar();
        toolBar.getStyleClass().add("reader-toolbar");

        Button backBtn = new Button("Back to Library");
        backBtn.setGraphic(AppIcons.createArrowLeftIcon(14));
        backBtn.getStyleClass().addAll("btn", "btn-secondary");
        backBtn.setOnAction(e -> closeReader());

        bookTitleLabel = new Label();
        bookTitleLabel.getStyleClass().add("reader-book-title");
        bookTitleLabel.setMaxWidth(280);

        Separator sep1 = new Separator();

        Button firstBtn = new Button();
        firstBtn.setGraphic(AppIcons.createFirstPageIcon(14));
        firstBtn.setTooltip(new Tooltip("First Page (Home)"));
        firstBtn.setOnAction(e -> goToPage(0));

        prevBtn = new Button("Prev");
        prevBtn.setGraphic(AppIcons.createArrowLeftIcon(14));
        prevBtn.setTooltip(new Tooltip("Previous Page (Left Arrow / Page Up)"));
        prevBtn.setOnAction(e -> goToPage(currentPageIndex - 1));

        pageInputField = new TextField("1");
        pageInputField.setPrefWidth(45);
        pageInputField.setAlignment(Pos.CENTER);
        pageInputField.setOnAction(e -> {
            try {
                int pageNum = Integer.parseInt(pageInputField.getText().trim());
                goToPage(pageNum - 1);
            } catch (NumberFormatException ex) {
                pageInputField.setText(String.valueOf(currentPageIndex + 1));
            }
        });

        totalPagesLabel = new Label("/ 0");
        totalPagesLabel.getStyleClass().add("reader-page-label");

        nextBtn = new Button("Next");
        nextBtn.setGraphic(AppIcons.createArrowRightIcon(14));
        nextBtn.setContentDisplay(ContentDisplay.RIGHT);
        nextBtn.setTooltip(new Tooltip("Next Page (Right Arrow / Page Down)"));
        nextBtn.setOnAction(e -> goToPage(currentPageIndex + 1));

        Button lastBtn = new Button();
        lastBtn.setGraphic(AppIcons.createLastPageIcon(14));
        lastBtn.setTooltip(new Tooltip("Last Page (End)"));
        lastBtn.setOnAction(e -> goToPage(pdfService.getPageCount() - 1));

        Separator sep2 = new Separator();

        zoomOutBtn = new Button("-");
        zoomOutBtn.setTooltip(new Tooltip("Zoom Out (Ctrl + Minus)"));
        zoomOutBtn.setOnAction(e -> changeZoom(currentScale - 0.15));

        zoomCombo = new ComboBox<>();
        zoomCombo.getItems().addAll("50%", "75%", "100%", "125%", "150%", "200%", "Fit Width", "Fit Page");
        zoomCombo.setValue("100%");
        zoomCombo.getStyleClass().add("reader-zoom-combo");
        zoomCombo.setCellFactory(lv -> new ListCell<String>() {
            {
                selectedProperty().addListener((obs, wasSel, isSel) -> updateStyle());
                hoverProperty().addListener((obs, wasHov, isHov) -> updateStyle());
            }

            private void updateStyle() {
                if (isEmpty() || getItem() == null) {
                    setStyle("-fx-background-color: #1e293b;");
                    return;
                }
                if (isSelected()) {
                    setStyle("-fx-background-color: #2563eb; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-padding: 6px 12px;");
                } else if (isHover()) {
                    setStyle("-fx-background-color: #334155; -fx-text-fill: #ffffff; -fx-font-weight: normal; -fx-padding: 6px 12px;");
                } else {
                    setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-font-weight: normal; -fx-padding: 6px 12px;");
                }
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                }
                updateStyle();
            }
        });

        zoomCombo.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-background-color: transparent; -fx-text-fill: #ffffff; -fx-font-weight: 600;");
                }
            }
        });
        zoomCombo.setOnAction(e -> handleZoomSelection(zoomCombo.getValue()));

        zoomInBtn = new Button("+");
        zoomInBtn.setTooltip(new Tooltip("Zoom In (Ctrl + Plus)"));
        zoomInBtn.setOnAction(e -> changeZoom(currentScale + 0.15));

        twoPageBtn = new Button("Two Pages");
        twoPageBtn.setGraphic(AppIcons.createOpenBookIcon(14, javafx.scene.paint.Color.web("#cbd5e1")));
        twoPageBtn.getStyleClass().addAll("btn", "btn-secondary");
        twoPageBtn.setTooltip(new Tooltip("Toggle Two-Page Book Spread (Like a Real Book)"));
        twoPageBtn.setOnAction(e -> toggleTwoPageMode());

        Separator sep3 = new Separator();

        fullscreenBtn = new Button("Fullscreen");
        fullscreenBtn.setGraphic(AppIcons.createFullscreenIcon(14));
        fullscreenBtn.setTooltip(new Tooltip("Toggle Fullscreen (F11)"));
        fullscreenBtn.setOnAction(e -> toggleFullscreen());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolBar.getItems().addAll(
                backBtn,
                sep1,
                bookTitleLabel,
                spacer,
                firstBtn,
                prevBtn,
                pageInputField,
                totalPagesLabel,
                nextBtn,
                lastBtn,
                sep2,
                zoomOutBtn,
                zoomCombo,
                zoomInBtn,
                twoPageBtn,
                sep3,
                fullscreenBtn
        );

        setTop(toolBar);

        // --- Viewport ---
        pageImageView = new ImageView();
        pageImageView.setPreserveRatio(true);
        pageImageView.setSmooth(true);
        pageImageView.setCache(true);
        pageImageView.getStyleClass().add("book-page-view");

        rightPageImageView = new ImageView();
        rightPageImageView.setPreserveRatio(true);
        rightPageImageView.setSmooth(true);
        rightPageImageView.setCache(true);
        rightPageImageView.getStyleClass().add("book-page-view");
        rightPageImageView.setVisible(false);
        rightPageImageView.setManaged(false);

        centerSpineDivider = new Region();
        centerSpineDivider.getStyleClass().add("book-spine-divider");
        centerSpineDivider.setPrefWidth(3);
        centerSpineDivider.setVisible(false);
        centerSpineDivider.setManaged(false);

        bookSpreadContainer = new HBox(8, pageImageView, centerSpineDivider, rightPageImageView);
        bookSpreadContainer.setAlignment(Pos.CENTER);
        bookSpreadContainer.getStyleClass().add("book-spread-container");

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        loadingIndicator.setVisible(false);

        viewportStack = new StackPane(bookSpreadContainer, loadingIndicator);
        viewportStack.setAlignment(Pos.CENTER);
        viewportStack.setPadding(new Insets(20));
        viewportStack.getStyleClass().add("reader-viewport-container");

        scrollPane = new ScrollPane(viewportStack);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("reader-scroll-pane");

        // Floating on-screen side buttons for page navigation
        Button leftFloatingBtn = new Button();
        leftFloatingBtn.setGraphic(AppIcons.createArrowLeftIcon(20));
        leftFloatingBtn.getStyleClass().addAll("btn", "floating-side-btn");
        leftFloatingBtn.setTooltip(new Tooltip("Previous Page (Left Arrow / < / Mouse Side Button)"));
        leftFloatingBtn.setOnAction(e -> goToPage(currentPageIndex - (isTwoPageMode ? 2 : 1)));
        StackPane.setAlignment(leftFloatingBtn, Pos.CENTER_LEFT);
        StackPane.setMargin(leftFloatingBtn, new Insets(0, 0, 0, 16));

        Button rightFloatingBtn = new Button();
        rightFloatingBtn.setGraphic(AppIcons.createArrowRightIcon(20));
        rightFloatingBtn.getStyleClass().addAll("btn", "floating-side-btn");
        rightFloatingBtn.setTooltip(new Tooltip("Next Page (Right Arrow / > / Mouse Side Button)"));
        rightFloatingBtn.setOnAction(e -> goToPage(currentPageIndex + (isTwoPageMode ? 2 : 1)));
        StackPane.setAlignment(rightFloatingBtn, Pos.CENTER_RIGHT);
        StackPane.setMargin(rightFloatingBtn, new Insets(0, 16, 0, 0));

        // Disable state binding
        prevBtn.disabledProperty().addListener((obs, oldV, newV) -> leftFloatingBtn.setDisable(newV));
        nextBtn.disabledProperty().addListener((obs, oldV, newV) -> rightFloatingBtn.setDisable(newV));

        StackPane centerContainer = new StackPane(scrollPane, leftFloatingBtn, rightFloatingBtn);
        setCenter(centerContainer);
    }

    private void initKeyAndMouseHandlers() {
        // Ctrl + Mouse Wheel for smooth zooming
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                event.consume();
                if (event.getDeltaY() > 0) {
                    changeZoom(currentScale + 0.1);
                } else if (event.getDeltaY() < 0) {
                    changeZoom(currentScale - 0.1);
                }
            }
        });

        // Mouse side buttons support (Back / Forward)
        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.BACK) {
                goToPage(currentPageIndex - 1);
                event.consume();
            } else if (event.getButton() == javafx.scene.input.MouseButton.FORWARD) {
                goToPage(currentPageIndex + 1);
                event.consume();
            }
        });

        // Scene-wide key event filter so keyboard side keys work regardless of which child control has focus
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, sceneKeyFilter);
            }
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, sceneKeyFilter);
            }
        });
    }

    private final javafx.event.EventHandler<KeyEvent> sceneKeyFilter = event -> {
        if (!isVisible()) return;

        // When user is typing inside the page jump field, allow digits, Enter, etc.
        if (pageInputField.isFocused()) {
            if (event.getCode() == KeyCode.ESCAPE) {
                requestFocus();
                event.consume();
            }
            return;
        }

        // Side Navigation Keys:
        // Left: Left Arrow, '<' (Comma or Less), Page Up
        if (event.getCode() == KeyCode.LEFT 
                || event.getCode() == KeyCode.COMMA 
                || event.getCode() == KeyCode.LESS 
                || event.getCode() == KeyCode.PAGE_UP) {
            goToPage(currentPageIndex - (isTwoPageMode ? 2 : 1));
            event.consume();
        }
        // Right: Right Arrow, '>' (Period or Greater), Page Down, Space
        else if (event.getCode() == KeyCode.RIGHT 
                || event.getCode() == KeyCode.PERIOD 
                || event.getCode() == KeyCode.GREATER 
                || event.getCode() == KeyCode.PAGE_DOWN 
                || event.getCode() == KeyCode.SPACE) {
            goToPage(currentPageIndex + (isTwoPageMode ? 2 : 1));
            event.consume();
        }
        else if (event.getCode() == KeyCode.HOME) {
            goToPage(0);
            event.consume();
        }
        else if (event.getCode() == KeyCode.END) {
            goToPage(pdfService.getPageCount() - 1);
            event.consume();
        }
        else if (event.getCode() == KeyCode.F11) {
            toggleFullscreen();
            event.consume();
        }
        else if (event.getCode() == KeyCode.ESCAPE) {
            Stage stage = getStage();
            if (stage != null && stage.isFullScreen()) {
                stage.setFullScreen(false);
            } else {
                closeReader();
            }
            event.consume();
        }
        else if (event.isControlDown() && (event.getCode() == KeyCode.PLUS || event.getCode() == KeyCode.EQUALS)) {
            changeZoom(currentScale + 0.15);
            event.consume();
        }
        else if (event.isControlDown() && event.getCode() == KeyCode.MINUS) {
            changeZoom(currentScale - 0.15);
            event.consume();
        }
    };

    public void openBook(Book book) {
        this.currentBook = book;
        this.currentPageIndex = 0;
        this.currentScale = 1.0;
        this.isFitWidth = false;
        this.isFitPage = false;
        this.bookTitleLabel.setText(book.getTitle());
        this.bookTitleLabel.setTooltip(new Tooltip(book.getTitle()));

        try {
            pdfService.loadDocument(new File(book.getPath()));
            int total = pdfService.getPageCount();
            totalPagesLabel.setText("/ " + total);
            goToPage(0);
            requestFocus();
        } catch (Exception e) {
            showError("Failed to open PDF", e.getMessage());
        }
    }

    private void toggleTwoPageMode() {
        isTwoPageMode = !isTwoPageMode;
        twoPageBtn.setText(isTwoPageMode ? "Single Page" : "Two Pages");
        twoPageBtn.setGraphic(isTwoPageMode ? AppIcons.createDetailsIcon(14) : AppIcons.createOpenBookIcon(14, javafx.scene.paint.Color.web("#cbd5e1")));
        rightPageImageView.setVisible(isTwoPageMode);
        rightPageImageView.setManaged(isTwoPageMode);
        centerSpineDivider.setVisible(isTwoPageMode);
        centerSpineDivider.setManaged(isTwoPageMode);
        goToPage(currentPageIndex);
    }

    public void goToPage(int index) {
        if (!pdfService.isDocumentLoaded()) return;

        int total = pdfService.getPageCount();
        if (index < 0) index = 0;
        if (index >= total) index = total - 1;

        boolean forward = index >= currentPageIndex;
        currentPageIndex = index;

        if (isTwoPageMode) {
            int rightPageNum = Math.min(total, currentPageIndex + 2);
            if (currentPageIndex + 1 == rightPageNum) {
                pageInputField.setText(String.valueOf(currentPageIndex + 1));
            } else {
                pageInputField.setText((currentPageIndex + 1) + "-" + rightPageNum);
            }
        } else {
            pageInputField.setText(String.valueOf(currentPageIndex + 1));
        }

        prevBtn.setDisable(currentPageIndex == 0);
        nextBtn.setDisable(isTwoPageMode ? (currentPageIndex + 1 >= total - 1) : (currentPageIndex >= total - 1));

        renderCurrentPage(forward);
    }

    private void renderCurrentPage(boolean forward) {
        if (!pdfService.isDocumentLoaded()) return;

        loadingIndicator.setVisible(true);

        double effectiveScale = calculateEffectiveScale();
        int leftIndex = currentPageIndex;
        int rightIndex = isTwoPageMode ? (currentPageIndex + 1) : -1;
        int total = pdfService.getPageCount();

        Task<Image[]> renderTask = new Task<>() {
            @Override
            protected Image[] call() throws Exception {
                BufferedImage leftBuf = pdfService.renderPage(leftIndex, effectiveScale);
                Image leftImg = SwingFXUtils.toFXImage(leftBuf, null);
                Image rightImg = null;
                if (rightIndex >= 0 && rightIndex < total) {
                    BufferedImage rightBuf = pdfService.renderPage(rightIndex, effectiveScale);
                    rightImg = SwingFXUtils.toFXImage(rightBuf, null);
                }
                return new Image[]{leftImg, rightImg};
            }
        };

        renderTask.setOnSucceeded(event -> {
            Image[] images = renderTask.getValue();
            pageImageView.setImage(images[0]);
            if (isTwoPageMode) {
                rightPageImageView.setImage(images[1]);
            }
            loadingIndicator.setVisible(false);
            scrollPane.setVvalue(0);
            playSlideAnimation(forward);
        });

        renderTask.setOnFailed(event -> {
            loadingIndicator.setVisible(false);
            Throwable err = renderTask.getException();
            System.err.println("Page render error: " + (err != null ? err.getMessage() : "unknown"));
        });

        Thread renderThread = new Thread(renderTask, "pdf-page-renderer");
        renderThread.setDaemon(true);
        renderThread.start();
    }

    private void playSlideAnimation(boolean forward) {
        double offset = forward ? 120.0 : -120.0;
        bookSpreadContainer.setTranslateX(offset);
        bookSpreadContainer.setOpacity(0.4);

        TranslateTransition tt = new TranslateTransition(Duration.millis(240), bookSpreadContainer);
        tt.setToX(0);
        tt.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition ft = new FadeTransition(Duration.millis(240), bookSpreadContainer);
        ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(tt, ft);
        pt.play();
    }

    private double calculateEffectiveScale() {
        double baseWidth = isTwoPageMode ? (595.0 * 2.0) : 595.0;
        if (isFitWidth) {
            double viewportWidth = scrollPane.getViewportBounds().getWidth() - 60;
            if (viewportWidth > 100) {
                return Math.max(0.3, Math.min(3.0, (viewportWidth / baseWidth)));
            }
        } else if (isFitPage) {
            double viewportHeight = scrollPane.getViewportBounds().getHeight() - 60;
            if (viewportHeight > 100) {
                return Math.max(0.3, Math.min(3.0, (viewportHeight / 842.0)));
            }
        }
        return isTwoPageMode ? (currentScale * 0.85) : currentScale;
    }

    private void handleZoomSelection(String zoomVal) {
        if (zoomVal == null) return;
        switch (zoomVal) {
            case "Fit Width" -> {
                isFitWidth = true;
                isFitPage = false;
                renderCurrentPage(true);
            }
            case "Fit Page" -> {
                isFitPage = true;
                isFitWidth = false;
                renderCurrentPage(true);
            }
            default -> {
                isFitWidth = false;
                isFitPage = false;
                try {
                    String num = zoomVal.replace("%", "").trim();
                    double pct = Double.parseDouble(num);
                    changeZoom(pct / 100.0);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private void changeZoom(double newScale) {
        isFitWidth = false;
        isFitPage = false;
        newScale = Math.max(0.3, Math.min(3.0, newScale));
        this.currentScale = Math.round(newScale * 100.0) / 100.0;

        String label = (int) (currentScale * 100) + "%";
        zoomCombo.setValue(label);
        renderCurrentPage(true);
    }

    private void toggleFullscreen() {
        Stage stage = getStage();
        if (stage != null) {
            stage.setFullScreen(!stage.isFullScreen());
            fullscreenBtn.setText(stage.isFullScreen() ? "Exit Fullscreen" : "Fullscreen");
            fullscreenBtn.setGraphic(AppIcons.createFullscreenIcon(14));
        }
    }

    private Stage getStage() {
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return null;
    }

    public void closeReader() {
        pdfService.close();
        pageImageView.setImage(null);
        Stage stage = getStage();
        if (stage != null && stage.isFullScreen()) {
            stage.setFullScreen(false);
        }
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
