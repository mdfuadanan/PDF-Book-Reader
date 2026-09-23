package com.pdfreader.ui;

import com.pdfreader.config.AppConfig;
import com.pdfreader.config.ConfigManager;
import com.pdfreader.service.BookService;
import com.pdfreader.service.DriveService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * First-time setup wizard displayed on initial launch.
 * Verifies local library and offers optional Google Drive sign-in.
 */
public class FirstRunWizard extends Dialog<Void> {

    private final BookService bookService;
    private final DriveService driveService;
    private final Stage ownerStage;
    private final Runnable onCompletedCallback;

    public FirstRunWizard(BookService bookService, DriveService driveService, Stage ownerStage, Runnable onCompletedCallback) {
        this.bookService = bookService;
        this.driveService = driveService;
        this.ownerStage = ownerStage;
        this.onCompletedCallback = onCompletedCallback;

        initOwner(ownerStage);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Welcome to PDF Book Reader - First Time Setup");
        setHeaderText("Welcome! Let's get your PDF Reader ready.");

        VBox content = buildContent();
        getDialogPane().setContent(content);

        ButtonType finishBtnType = new ButtonType("Get Started", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().add(finishBtnType);

        setResultConverter(dialogButton -> {
            AppConfig config = ConfigManager.getInstance().getConfig();
            config.setFirstRunCompleted(true);
            ConfigManager.getInstance().saveConfig();
            if (onCompletedCallback != null) {
                onCompletedCallback.run();
            }
            return null;
        });
    }

    private VBox buildContent() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(15));
        root.setPrefWidth(500);

        // Step 1: Local library
        VBox step1Box = new VBox(6);
        step1Box.setStyle("-fx-background-color: #f4f7f6; -fx-padding: 12px; -fx-background-radius: 6px; -fx-border-color: #e0e0e0; -fx-border-radius: 6px;");

        HBox step1Header = new HBox(8);
        step1Header.setAlignment(Pos.CENTER_LEFT);
        Label step1Title = new Label("1. Local Library Initialized");
        step1Title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        step1Header.getChildren().addAll(AppIcons.createFolderIcon(18), step1Title);

        Label step1Desc = new Label("A local 'Books' folder has been created at:\n"
                + bookService.getBooksDirectory().toString()
                + "\nA quickstart guide PDF has been added to help you get started.");
        step1Desc.setWrapText(true);
        step1Box.getChildren().addAll(step1Header, step1Desc);

        // Step 2: Google Drive Shared Folder
        VBox step2Box = new VBox(8);
        step2Box.setStyle("-fx-background-color: #f4f7f6; -fx-padding: 12px; -fx-background-radius: 6px; -fx-border-color: #e0e0e0; -fx-border-radius: 6px;");

        HBox step2Header = new HBox(8);
        step2Header.setAlignment(Pos.CENTER_LEFT);
        DriveIcon driveIcon = new DriveIcon(20);
        Label step2Title = new Label("2. Google Drive Shared Folder + Sign-In");
        step2Title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        step2Header.getChildren().addAll(driveIcon, step2Title);

        Label step2Desc = new Label("Paste a shared Drive folder link to browse and download PDFs. Sign in with Google when you want to upload, rename, or delete Drive files from inside the app.");
        step2Desc.setWrapText(true);

        step2Box.getChildren().addAll(step2Header, step2Desc);

        // Note
        Label noteLabel = new Label("You can paste your shared folder link at any time in the Google Drive panel.");
        noteLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #666; -fx-font-size: 11px;");

        root.getChildren().addAll(step1Box, step2Box, noteLabel);
        return root;
    }
}
