package com.pdfreader.config;

import java.io.Serializable;

/**
 * Application configuration POJO stored locally as JSON.
 */
public class AppConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String booksDirectory = "Books";
    private String driveFolderName = "PDFBookReader";
    private String driveFolderId = "";
    private String sharedFolderUrl = "";
    private String folderPermission = "EDITOR"; // "EDITOR" or "VIEWER"
    private boolean driveCardCollapsed = false;
    private String credentialsPath = "credentials.json";
    private String tokensDirectory = "tokens";
    private boolean firstRunCompleted = false;
    private String userEmail = "";
    private double defaultZoom = 1.0;
    private String lastOpenedBookPath = "";

    public AppConfig() {
    }

    public String getBooksDirectory() {
        return booksDirectory;
    }

    public void setBooksDirectory(String booksDirectory) {
        this.booksDirectory = booksDirectory;
    }

    public String getDriveFolderName() {
        return driveFolderName;
    }

    public void setDriveFolderName(String driveFolderName) {
        this.driveFolderName = driveFolderName;
    }

    public String getDriveFolderId() {
        return driveFolderId;
    }

    public void setDriveFolderId(String driveFolderId) {
        this.driveFolderId = driveFolderId;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    public void setCredentialsPath(String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    public String getTokensDirectory() {
        return tokensDirectory;
    }

    public void setTokensDirectory(String tokensDirectory) {
        this.tokensDirectory = tokensDirectory;
    }

    public boolean isFirstRunCompleted() {
        return firstRunCompleted;
    }

    public void setFirstRunCompleted(boolean firstRunCompleted) {
        this.firstRunCompleted = firstRunCompleted;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public double getDefaultZoom() {
        return defaultZoom;
    }

    public void setDefaultZoom(double defaultZoom) {
        this.defaultZoom = defaultZoom;
    }

    public String getLastOpenedBookPath() {
        return lastOpenedBookPath;
    }

    public void setLastOpenedBookPath(String lastOpenedBookPath) {
        this.lastOpenedBookPath = lastOpenedBookPath;
    }

    public String getSharedFolderUrl() {
        return sharedFolderUrl != null ? sharedFolderUrl : "";
    }

    public void setSharedFolderUrl(String sharedFolderUrl) {
        this.sharedFolderUrl = sharedFolderUrl != null ? sharedFolderUrl.trim() : "";
    }

    public String getFolderPermission() {
        return (folderPermission != null && folderPermission.equalsIgnoreCase("EDITOR")) ? "EDITOR" : "VIEWER";
    }

    public void setFolderPermission(String folderPermission) {
        this.folderPermission = (folderPermission != null && folderPermission.equalsIgnoreCase("EDITOR")) ? "EDITOR" : "VIEWER";
    }

    public boolean isDriveCardCollapsed() {
        return driveCardCollapsed;
    }

    public void setDriveCardCollapsed(boolean driveCardCollapsed) {
        this.driveCardCollapsed = driveCardCollapsed;
    }
}
