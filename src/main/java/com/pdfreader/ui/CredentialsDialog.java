package com.pdfreader.ui;

import com.pdfreader.config.AppConfig;
import com.pdfreader.config.ConfigManager;
import com.pdfreader.service.DriveService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Dialog for managing Google Drive API OAuth 2.0 credentials and login status.
 */
public class CredentialsDialog extends Dialog<Boolean> {

    private final DriveService driveService;
    private final Runnable onAuthChangeCallback;

    private Label statusLabel;
    private Label pathLabel;
    private Button signInBtn;
    private Button signOutBtn;
    private ProgressIndicator progressIndicator;

    public CredentialsDialog(DriveService driveService, Stage ownerStage, Runnable onAuthChangeCallback) {
        this.driveService = driveService;
        this.onAuthChangeCallback = onAuthChangeCallback;

        initOwner(ownerStage);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Google Drive Setup & Authentication");
        setHeaderText("Configure Google Drive API Connection");

        VBox content = buildContent();
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        updateStatus();
    }

    private VBox buildContent() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setPrefWidth(480);

        // Status section
        TitledPane statusPane = new TitledPane();
        statusPane.setText("Connection Status");
        statusPane.setCollapsible(false);

        VBox statusBox = new VBox(8);
        statusBox.setPadding(new Insets(10));

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-weight: bold;");

        pathLabel = new Label();
        pathLabel.setWrapText(true);
        pathLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(24, 24);
        progressIndicator.setVisible(false);

        HBox authButtonsBox = new HBox(10);
        authButtonsBox.setAlignment(Pos.CENTER_LEFT);

        signInBtn = new Button("Sign In with Google");
        signInBtn.getStyleClass().addAll("btn", "btn-primary");
        signInBtn.setOnAction(e -> handleSignIn());

        signOutBtn = new Button("Sign Out");
        signOutBtn.getStyleClass().addAll("btn", "btn-danger");
        signOutBtn.setOnAction(e -> handleSignOut());

        authButtonsBox.getChildren().addAll(signInBtn, signOutBtn, progressIndicator);
        statusBox.getChildren().addAll(statusLabel, pathLabel, authButtonsBox);
        statusPane.setContent(statusBox);

        // Credentials file section
        TitledPane filePane = new TitledPane();
        filePane.setText("OAuth2 Credentials File (credentials.json)");
        filePane.setCollapsible(false);

        VBox fileBox = new VBox(8);
        fileBox.setPadding(new Insets(10));

        Label desc = new Label("To connect to Google Drive, download 'credentials.json' (Desktop App Client ID) from Google Cloud Console.");
        desc.setWrapText(true);

        Button browseBtn = new Button("Browse & Import credentials.json...");
        browseBtn.setOnAction(e -> handleBrowseCredentials());

        fileBox.getChildren().addAll(desc, browseBtn);
        filePane.setContent(fileBox);

        // Steps guide
        TitledPane guidePane = new TitledPane();
        guidePane.setText("How to Get Google Drive Credentials (3 Quick Steps)");
        guidePane.setExpanded(false);

        VBox guideBox = new VBox(6);
        guideBox.setPadding(new Insets(8));
        Label step1 = new Label("1. Go to console.cloud.google.com and create a project.");
        Label step2 = new Label("2. Enable the 'Google Drive API' in APIs & Services.");
        Label step3 = new Label("3. Create OAuth 2.0 Client ID (Application Type: Desktop App).");
        Label step4 = new Label("4. Download the JSON and click 'Browse & Import' above.");
        step1.setWrapText(true);
        step2.setWrapText(true);
        step3.setWrapText(true);
        step4.setWrapText(true);
        guideBox.getChildren().addAll(step1, step2, step3, step4);
        guidePane.setContent(guideBox);

        root.getChildren().addAll(statusPane, filePane, guidePane);
        return root;
    }

    private void updateStatus() {
        boolean hasFile = driveService.hasCredentialsFile();
        String validationError = driveService.getCredentialsValidationError();
        boolean hasValidFile = validationError == null;
        boolean isAuth = driveService.isAuthenticated();

        if (isAuth) {
            statusLabel.setText("Connected: " + driveService.getUserEmail());
            statusLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
            signInBtn.setDisable(true);
            signOutBtn.setDisable(false);
        } else if (hasFile && hasValidFile) {
            statusLabel.setText("Credentials found, but not signed in.");
            statusLabel.setStyle("-fx-text-fill: #e65100; -fx-font-weight: bold;");
            signInBtn.setDisable(false);
            signOutBtn.setDisable(true);
        } else if (hasFile) {
            statusLabel.setText("Invalid credentials.json.");
            statusLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
            signInBtn.setDisable(true);
            signOutBtn.setDisable(true);
        } else {
            statusLabel.setText("No credentials.json file found.");
            statusLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
            signInBtn.setDisable(true);
            signOutBtn.setDisable(true);
        }

        File credFile = driveService.getCredentialsFile();
        if (credFile != null && credFile.exists()) {
            String message = "File path: " + credFile.getAbsolutePath();
            if (validationError != null) {
                message += "\n\n" + validationError;
            }
            pathLabel.setText(message);
        } else {
            pathLabel.setText("File path: Not located (Expected in app root directory)");
        }
    }

    private void handleBrowseCredentials() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Google OAuth credentials.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));

        File file = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (file != null) {
            try {
                File target = new File("credentials.json");
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

                AppConfig config = ConfigManager.getInstance().getConfig();
                config.setCredentialsPath(target.getAbsolutePath());
                ConfigManager.getInstance().saveConfig();

                updateStatus();

                String validationError = driveService.getCredentialsValidationError();
                Alert alert = new Alert(validationError == null ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                alert.setTitle(validationError == null ? "Credentials Imported" : "Invalid Credentials File");
                alert.setHeaderText(null);
                alert.setContentText(validationError == null
                        ? "credentials.json imported successfully! You can now click 'Sign In with Google'."
                        : validationError);
                alert.showAndWait();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Import Error");
                alert.setHeaderText("Failed to copy credentials file");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }

    private void handleSignIn() {
        progressIndicator.setVisible(true);
        signInBtn.setDisable(true);

        Thread authThread = new Thread(() -> {
            try {
                boolean success = driveService.authenticate();
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    updateStatus();
                    if (success) {
                        if (onAuthChangeCallback != null) {
                            onAuthChangeCallback.run();
                        }
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Success");
                        alert.setHeaderText(null);
                        alert.setContentText("Successfully connected to Google Drive!\nFolder 'PDFBookReader' is ready.");
                        alert.showAndWait();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    signInBtn.setDisable(false);
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Authentication Error");
                    alert.setHeaderText("Google Sign-In failed");
                    alert.setContentText(ex.getMessage());
                    alert.showAndWait();
                });
            }
        }, "google-drive-auth-thread");
        authThread.setDaemon(true);
        authThread.start();
    }

    private void handleSignOut() {
        driveService.signOut();
        updateStatus();
        if (onAuthChangeCallback != null) {
            onAuthChangeCallback.run();
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Signed Out");
        alert.setHeaderText(null);
        alert.setContentText("You have been signed out from Google Drive.");
        alert.showAndWait();
    }
}
