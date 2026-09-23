package com.pdfreader;

import com.pdfreader.config.AppConfig;
import com.pdfreader.config.ConfigManager;
import com.pdfreader.service.BookService;
import com.pdfreader.service.DriveService;
import com.pdfreader.ui.FirstRunWizard;
import com.pdfreader.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Main JavaFX Application class for PDF Book Reader.
 */
public class PdfBookReaderApp extends Application {

    private BookService bookService;
    private DriveService driveService;
    private MainView mainView;

    @Override
    public void start(Stage primaryStage) {
        // Initialize services
        this.bookService = new BookService();
        this.driveService = new DriveService();

        // Create UI
        this.mainView = new MainView(primaryStage, bookService, driveService);

        Scene scene = new Scene(mainView, 1100, 720);

        // Apply CSS stylesheet
        URL cssUrl = getClass().getResource("/css/modern-theme.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        primaryStage.setTitle("PDF Book Reader");
        primaryStage.setMinWidth(920);
        primaryStage.setMinHeight(600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Check if first run wizard should be shown
        AppConfig config = ConfigManager.getInstance().getConfig();
        if (!config.isFirstRunCompleted()) {
            Platform.runLater(() -> {
                FirstRunWizard wizard = new FirstRunWizard(bookService, driveService, primaryStage, () -> {
                    mainView.loadLocalBooks();
                    mainView.updateDriveStatusUI();
                    mainView.loadDriveBooks();
                });
                wizard.showAndWait();
            });
        }

        // Clean shutdown
        primaryStage.setOnCloseRequest(event -> {
            ConfigManager.getInstance().saveConfig();
        });
    }

    @Override
    public void stop() {
        ConfigManager.getInstance().saveConfig();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
