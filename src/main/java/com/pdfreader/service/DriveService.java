package com.pdfreader.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.FileList;
import com.pdfreader.config.AppConfig;
import com.pdfreader.config.ConfigManager;
import com.pdfreader.model.Book;
import com.pdfreader.model.BookSource;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service encapsulating Google Drive API v3 interactions.
 * Fully decoupled from UI frameworks.
 *
 * NOTE: As per requirements, NO automatic sync is performed.
 * All uploads and downloads are strictly manual actions triggered by the user.
 */
public class DriveService {
    private static final String APPLICATION_NAME = "PDF Book Reader Desktop";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);

    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
            .executor(Executors.newFixedThreadPool(12, r -> {
                Thread t = new Thread(r, "drive-http-worker");
                t.setDaemon(true);
                return t;
            }))
            .build();

    private static final Map<String, Long> FILE_SIZE_CACHE = new ConcurrentHashMap<>();

    public static void cacheFileSize(String fileId, long sizeBytes) {
        if (fileId != null && !fileId.isBlank() && sizeBytes > 0) {
            FILE_SIZE_CACHE.put(fileId, sizeBytes);
        }
    }

    public static long getCachedFileSize(String fileId) {
        if (fileId == null) return 0;
        return FILE_SIZE_CACHE.getOrDefault(fileId, 0L);
    }

    private Drive driveService;
    private String userEmail = "";
    private String appFolderId = "";

    public interface TransferListener {
        void onProgress(String statusMessage, double progressFraction);
        void onComplete(String successMessage);
        void onError(String errorMessage, Throwable throwable);
    }

    public DriveService() {
        // Attempt restoring authentication if tokens exist
        tryAutoAuthenticate();
    }

    /**
     * Checks if credentials.json file exists.
     */
    public boolean hasCredentialsFile() {
        File credFile = getCredentialsFile();
        return credFile != null && credFile.exists() && credFile.length() > 0;
    }

    /**
     * Checks whether credentials.json is a Google OAuth Desktop app client file.
     *
     * @return null when valid; otherwise a user-facing validation error.
     */
    public String getCredentialsValidationError() {
        File credFile = getCredentialsFile();
        if (credFile == null || !credFile.exists() || credFile.length() == 0) {
            return "No credentials.json file was found.";
        }

        try {
            String rawJson = Files.readString(credFile.toPath(), StandardCharsets.UTF_8);
            if (!rawJson.contains("\"installed\"")) {
                if (rawJson.contains("\"web\"")) {
                    return "This is a Web OAuth client. Please create and import an OAuth client with Application type: Desktop app.";
                }
                return "This file is not a Google OAuth Desktop app credentials file. Please import the JSON downloaded from Google Cloud Console > APIs & Services > Credentials > OAuth client ID > Desktop app.";
            }

            GoogleClientSecrets clientSecrets;
            try (InputStream in = new ByteArrayInputStream(rawJson.getBytes(StandardCharsets.UTF_8))) {
                clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in, StandardCharsets.UTF_8));
            }

            GoogleClientSecrets.Details details = clientSecrets.getDetails();
            if (details == null
                    || details.getClientId() == null || details.getClientId().isBlank()
                    || details.getClientSecret() == null || details.getClientSecret().isBlank()
                    || details.getAuthUri() == null || details.getAuthUri().isBlank()
                    || details.getTokenUri() == null || details.getTokenUri().isBlank()) {
                return "The credentials file is missing required OAuth fields: client_id, client_secret, auth_uri, or token_uri.";
            }

            if (details.getClientId().contains("YOUR_GOOGLE_CLIENT_ID")
                    || details.getClientSecret().contains("YOUR_GOOGLE_CLIENT_SECRET")) {
                return "This is the sample credentials file. Please import the real downloaded OAuth Desktop app JSON from Google Cloud Console.";
            }
            return null;
        } catch (Exception e) {
            return "Could not read credentials.json as a Google OAuth credentials file: " + e.getMessage();
        }
    }

    public boolean hasValidCredentialsFile() {
        return getCredentialsValidationError() == null;
    }

    /**
     * Resolves the credentials.json file from config or standard locations.
     */
    public File getCredentialsFile() {
        AppConfig config = ConfigManager.getInstance().getConfig();
        if (config.getCredentialsPath() != null && !config.getCredentialsPath().isBlank()) {
            File custom = new File(config.getCredentialsPath());
            if (custom.exists()) return custom;
        }

        File defaultLocal = new File("credentials.json");
        if (defaultLocal.exists()) return defaultLocal;

        File inHome = new File(System.getProperty("user.home"), ".pdfbookreader/credentials.json");
        if (inHome.exists()) return inHome;

        return defaultLocal;
    }

    /**
     * Checks whether the user is currently authenticated with Google Drive.
     */
    public boolean isAuthenticated() {
        return driveService != null;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getAppFolderId() {
        return appFolderId;
    }

    /**
     * Attempts silent authentication using stored tokens if available.
     */
    public boolean tryAutoAuthenticate() {
        if (!hasValidCredentialsFile()) {
            return false;
        }
        AppConfig config = ConfigManager.getInstance().getConfig();
        File tokensDir = new File(config.getTokensDirectory());
        if (!tokensDir.exists() || Objects.requireNonNull(tokensDir.list()).length == 0) {
            return false;
        }

        try {
            return authenticateInternal(false);
        } catch (Exception e) {
            System.err.println("Auto-authentication with stored token failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initiates Google OAuth2 authentication.
     * Launches browser if interactive login is needed.
     */
    public boolean authenticate() throws Exception {
        return authenticateInternal(true);
    }

    private synchronized boolean authenticateInternal(boolean allowBrowserPrompt) throws Exception {
        File credFile = getCredentialsFile();
        if (credFile == null || !credFile.exists()) {
            throw new FileNotFoundException("Google Drive credentials file not found. Please place 'credentials.json' in the application directory.");
        }
        String validationError = getCredentialsValidationError();
        if (validationError != null) {
            throw new IOException(validationError);
        }

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        AppConfig config = ConfigManager.getInstance().getConfig();
        File tokensFolder = new File(config.getTokensDirectory());

        try (InputStream in = new FileInputStream(credFile)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in, StandardCharsets.UTF_8));

            FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(tokensFolder);
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(dataStoreFactory)
                    .setAccessType("offline")
                    .build();

            Credential credential;
            if (allowBrowserPrompt) {
                // Fixed port or dynamic local receiver
                LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
                credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
            } else {
                credential = flow.loadCredential("user");
                if (credential == null || (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 0 && credential.getRefreshToken() == null)) {
                    return false;
                }
            }

            if (credential == null) {
                return false;
            }

            HttpRequestInitializer requestInitializer = request -> {
                credential.initialize(request);
                request.setConnectTimeout(30000);
                request.setReadTimeout(60000);
            };

            this.driveService = new Drive.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            // Fetch user info & ensure app folder
            fetchUserInfo();
            this.appFolderId = getOrCreateAppFolder();

            // Persist folder ID
            config.setDriveFolderId(this.appFolderId);
            config.setUserEmail(this.userEmail);
            ConfigManager.getInstance().saveConfig();

            return true;
        }
    }

    /**
     * Signs out the user by clearing stored tokens.
     */
    public synchronized void signOut() {
        this.driveService = null;
        this.userEmail = "";
        this.appFolderId = "";

        AppConfig config = ConfigManager.getInstance().getConfig();
        config.setUserEmail("");
        ConfigManager.getInstance().saveConfig();

        File tokensFolder = new File(config.getTokensDirectory());
        if (tokensFolder.exists() && tokensFolder.isDirectory()) {
            File[] files = tokensFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
    }

    private void fetchUserInfo() {
        try {
            About about = driveService.about().get().setFields("user(displayName, emailAddress)").execute();
            if (about != null && about.getUser() != null) {
                this.userEmail = about.getUser().getEmailAddress();
                if (this.userEmail == null || this.userEmail.isBlank()) {
                    this.userEmail = about.getUser().getDisplayName();
                }
            }
        } catch (Exception e) {
            this.userEmail = "Connected User";
        }
    }

    /**
     * Finds or creates the "PDFBookReader" folder in Google Drive.
     */
    public synchronized String getOrCreateAppFolder() throws IOException {
        if (driveService == null) {
            throw new IllegalStateException("Google Drive service is not authenticated");
        }

        AppConfig config = ConfigManager.getInstance().getConfig();
        String targetFolderName = config.getDriveFolderName();
        if (targetFolderName == null || targetFolderName.isBlank()) {
            targetFolderName = "PDFBookReader";
        }

        // Check if existing folder ID in config is still valid
        if (config.getDriveFolderId() != null && !config.getDriveFolderId().isBlank()) {
            try {
                com.google.api.services.drive.model.File existing = driveService.files()
                        .get(config.getDriveFolderId())
                        .setFields("id, name, trashed")
                        .execute();
                if (existing != null && Boolean.FALSE.equals(existing.getTrashed())) {
                    return existing.getId();
                }
            } catch (Exception ignored) {
                // Folder ID in config not found or accessible; will search by name
            }
        }

        // Search for existing folder by name
        String query = "mimeType = 'application/vnd.google-apps.folder' and name = '" +
                targetFolderName.replace("'", "\\'") + "' and trashed = false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        List<com.google.api.services.drive.model.File> files = result.getFiles();
        if (files != null && !files.isEmpty()) {
            return files.get(0).getId();
        }

        // Create new folder
        com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
        folderMetadata.setName(targetFolderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");

        com.google.api.services.drive.model.File createdFolder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute();

        return createdFolder.getId();
    }

    /**
     * Lists all PDF books stored inside the app's Drive folder.
     */
    public synchronized List<Book> listCloudBooks() throws IOException {
        if (driveService == null) {
            throw new IllegalStateException("Google Drive service is not authenticated");
        }

        if (appFolderId == null || appFolderId.isBlank()) {
            appFolderId = getOrCreateAppFolder();
        }

        List<Book> cloudBooks = new ArrayList<>();
        String query = "'" + appFolderId + "' in parents and mimeType = 'application/pdf' and trashed = false";

        String pageToken = null;
        do {
            FileList result = driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, size, modifiedTime)")
                    .setPageToken(pageToken)
                    .execute();

            if (result.getFiles() != null) {
                for (com.google.api.services.drive.model.File f : result.getFiles()) {
                    long size = f.getSize() != null ? f.getSize() : 0L;
                    long modified = f.getModifiedTime() != null ? f.getModifiedTime().getValue() : 0L;

                    Book book = new Book(
                            f.getId(),
                            f.getName(),
                            f.getId(),
                            size,
                            modified,
                            BookSource.CLOUD
                    );
                    book.setDriveFileId(f.getId());
                    cloudBooks.add(book);
                }
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        cloudBooks.sort(Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));
        return cloudBooks;
    }

    /**
     * Manually uploads a local book to the Google Drive folder.
     * Progress and completion are reported via the listener.
     */
    public synchronized Book uploadBook(Book localBook, TransferListener listener) {
        if (driveService == null) {
            if (listener != null) listener.onError("Drive is not authenticated", null);
            return null;
        }

        try {
            if (appFolderId == null || appFolderId.isBlank()) {
                appFolderId = getOrCreateAppFolder();
            }

            File localFile = new File(localBook.getPath());
            if (!localFile.exists()) {
                throw new FileNotFoundException("Local file not found: " + localBook.getPath());
            }

            if (listener != null) {
                listener.onProgress("Uploading " + localFile.getName() + "...", 0.1);
            }

            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(localFile.getName());
            fileMetadata.setParents(Collections.singletonList(appFolderId));

            FileContent mediaContent = new FileContent("application/pdf", localFile);

            com.google.api.services.drive.model.File uploadedFile = driveService.files()
                    .create(fileMetadata, mediaContent)
                    .setFields("id, name, size, modifiedTime")
                    .execute();

            if (listener != null) {
                listener.onProgress("Upload completed!", 1.0);
            }

            long size = uploadedFile.getSize() != null ? uploadedFile.getSize() : localFile.length();
            long modified = uploadedFile.getModifiedTime() != null ? uploadedFile.getModifiedTime().getValue() : System.currentTimeMillis();

            Book cloudBook = new Book(
                    uploadedFile.getId(),
                    uploadedFile.getName(),
                    uploadedFile.getId(),
                    size,
                    modified,
                    BookSource.CLOUD
            );
            cloudBook.setDriveFileId(uploadedFile.getId());

            if (listener != null) {
                listener.onComplete("Successfully uploaded '" + localFile.getName() + "' to Google Drive.");
            }

            return cloudBook;
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Upload failed: " + e.getMessage(), e);
            }
            return null;
        }
    }

    /**
     * Uploads a local PDF directly into the linked shared folder using the Drive API.
     * This is intentionally in-app only: no browser, external window, or drag-and-drop flow.
     */
    public synchronized Book uploadBookToSharedFolder(Book localBook, String folderUrlOrId, TransferListener listener) {
        try {
            ensureWriteApiAuthenticated();

            String folderId = extractIdFromShareLink(folderUrlOrId);
            if (folderId.isBlank()) {
                folderId = folderUrlOrId != null ? folderUrlOrId.trim() : "";
            }
            if (folderId.isBlank()) {
                throw new IOException("No Google Drive folder is linked.");
            }

            File localFile = new File(localBook.getPath());
            if (!localFile.exists()) {
                throw new FileNotFoundException("Local file not found: " + localBook.getPath());
            }

            if (listener != null) {
                listener.onProgress("Uploading " + localFile.getName() + "...", 0.15);
            }

            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(localFile.getName());
            fileMetadata.setParents(Collections.singletonList(folderId));

            FileContent mediaContent = new FileContent("application/pdf", localFile);
            com.google.api.services.drive.model.File uploadedFile = driveService.files()
                    .create(fileMetadata, mediaContent)
                    .setFields("id, name, size, modifiedTime")
                    .execute();

            if (listener != null) {
                listener.onProgress("Upload completed.", 1.0);
            }

            long size = uploadedFile.getSize() != null ? uploadedFile.getSize() : localFile.length();
            long modified = uploadedFile.getModifiedTime() != null ? uploadedFile.getModifiedTime().getValue() : System.currentTimeMillis();
            Book cloudBook = new Book(
                    uploadedFile.getId(),
                    uploadedFile.getName(),
                    "https://drive.google.com/file/d/" + uploadedFile.getId(),
                    size,
                    modified,
                    BookSource.CLOUD
            );
            cloudBook.setDriveFileId(uploadedFile.getId());

            if (listener != null) {
                listener.onComplete("Uploaded '" + localFile.getName() + "' to Google Drive.");
            }
            return cloudBook;
        } catch (Exception e) {
            if (listener != null) {
                listener.onError(e.getMessage(), e);
            }
            return null;
        }
    }

    public synchronized Book renameCloudBook(Book cloudBook, String newName) throws IOException {
        ensureWriteApiAuthenticated();
        String fileId = getCloudFileId(cloudBook);
        if (fileId.isBlank()) {
            throw new IOException("Invalid Google Drive file ID.");
        }
        if (newName == null || newName.isBlank()) {
            throw new IOException("File name cannot be blank.");
        }

        com.google.api.services.drive.model.File metadata = new com.google.api.services.drive.model.File();
        metadata.setName(newName);
        com.google.api.services.drive.model.File updated = driveService.files()
                .update(fileId, metadata)
                .setFields("id, name, size, modifiedTime")
                .execute();

        long size = updated.getSize() != null ? updated.getSize() : cloudBook.getFileSizeBytes();
        long modified = updated.getModifiedTime() != null ? updated.getModifiedTime().getValue() : System.currentTimeMillis();
        Book renamed = new Book(
                updated.getId(),
                updated.getName(),
                "https://drive.google.com/file/d/" + updated.getId(),
                size,
                modified,
                BookSource.CLOUD
        );
        renamed.setDriveFileId(updated.getId());
        return renamed;
    }

    public synchronized void deleteCloudBook(Book cloudBook) throws IOException {
        ensureWriteApiAuthenticated();
        String fileId = getCloudFileId(cloudBook);
        if (fileId.isBlank()) {
            throw new IOException("Invalid Google Drive file ID.");
        }
        driveService.files().delete(fileId).execute();
    }

    private void ensureWriteApiAuthenticated() throws IOException {
        if (driveService != null) {
            return;
        }
        if (!tryAutoAuthenticate()) {
            throw new IOException("Google Drive sign-in is required for in-app upload, rename, and delete.");
        }
    }

    private String getCloudFileId(Book cloudBook) {
        if (cloudBook == null) return "";
        if (cloudBook.getDriveFileId() != null && !cloudBook.getDriveFileId().isBlank()) {
            return cloudBook.getDriveFileId();
        }
        return cloudBook.getId() != null ? cloudBook.getId() : "";
    }

    /**
     * Manually downloads a book from Google Drive into the local Books folder.
     * Progress and completion are reported via the listener.
     */
    public synchronized Book downloadBook(Book cloudBook, Path destinationDirectory, TransferListener listener) {
        if (driveService == null) {
            if (listener != null) listener.onError("Drive is not authenticated", null);
            return null;
        }

        try {
            if (!Files.exists(destinationDirectory)) {
                Files.createDirectories(destinationDirectory);
            }

            String fileName = cloudBook.getTitle();
            Path targetPath = destinationDirectory.resolve(fileName);

            // Avoid overwriting by adding numbering if identical file exists
            if (Files.exists(targetPath)) {
                String baseName = fileName.endsWith(".pdf") ? fileName.substring(0, fileName.length() - 4) : fileName;
                int counter = 1;
                while (Files.exists(targetPath)) {
                    targetPath = destinationDirectory.resolve(baseName + " (" + counter + ").pdf");
                    counter++;
                }
            }

            if (listener != null) {
                listener.onProgress("Downloading " + fileName + "...", 0.2);
            }

            File targetFile = targetPath.toFile();
            try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                driveService.files().get(cloudBook.getDriveFileId())
                        .executeMediaAndDownloadTo(outputStream);
            }

            if (listener != null) {
                listener.onProgress("Download completed!", 1.0);
            }

            Book localBook = new Book(
                    targetFile.getAbsolutePath(),
                    targetFile.getName(),
                    targetFile.getAbsolutePath(),
                    targetFile.length(),
                    targetFile.lastModified(),
                    BookSource.LOCAL
            );

            if (listener != null) {
                listener.onComplete("Successfully downloaded '" + targetFile.getName() + "' to local library.");
            }

            return localBook;
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Download failed: " + e.getMessage(), e);
            }
            return null;
        }
    }

    private static final Pattern FOLDER_PATTERN = Pattern.compile("folders/([a-zA-Z0-9_-]+)");
    private static final Pattern FILE_PATTERN = Pattern.compile("(?:file/d/|id=|d/)([a-zA-Z0-9_-]+)");

    /**
     * Extracts Google Drive file ID or folder ID from various shared URL formats.
     */
    public static String extractIdFromShareLink(String link) {
        if (link == null || link.isBlank()) {
            return "";
        }
        String trimmed = link.trim();
        Matcher folderMatcher = FOLDER_PATTERN.matcher(trimmed);
        if (folderMatcher.find()) {
            return folderMatcher.group(1);
        }
        Matcher fileMatcher = FILE_PATTERN.matcher(trimmed);
        if (fileMatcher.find()) {
            return fileMatcher.group(1);
        }
        // If string contains no URL punctuation and is a bare ID
        if (!trimmed.contains("/") && !trimmed.contains("?") && trimmed.length() >= 15) {
            return trimmed;
        }
        return "";
    }

    /**
     * Checks if the given link points to a folder.
     */
    public static boolean isFolderShareLink(String link) {
        if (link == null) return false;
        return link.contains("folders/");
    }

    /**
     * Downloads a publicly shared PDF from Google Drive without any OAuth sign-in.
     * Follows HTTP redirects from https://drive.google.com/uc?export=download&id=<fileId>.
     */
    public Book downloadPublicSharedFile(String fileId, Path destinationDirectory, String defaultFileName, TransferListener listener) {
        if (fileId == null || fileId.isBlank()) {
            if (listener != null) listener.onError("Invalid Google Drive file ID", null);
            return null;
        }

        try {
            if (!Files.exists(destinationDirectory)) {
                Files.createDirectories(destinationDirectory);
            }

            if (listener != null) {
                listener.onProgress("Connecting to Google Drive shared file...", 0.1);
            }

            String downloadUrl = "https://drive.google.com/uc?export=download&id=" + fileId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PDFBookReader/1.0")
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            if (listener != null) {
                listener.onProgress("Connecting to Google Drive download stream...", 0.2);
            }

            HttpResponse<InputStream> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP error " + response.statusCode() + " when accessing shared file. Ensure permissions are set to 'Anyone with the link'.");
            }

            long totalBytes = response.headers().firstValue("Content-Length").map(s -> {
                try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
            }).orElse(0L);

            // Determine file name from Content-Disposition header if present
            String fileName = defaultFileName;
            Optional<String> contentDisp = response.headers().firstValue("Content-Disposition");
            if (contentDisp.isPresent()) {
                String header = contentDisp.get();
                if (header.contains("filename=")) {
                    int start = header.indexOf("filename=") + 9;
                    int end = header.indexOf(';', start);
                    String extracted = (end == -1 ? header.substring(start) : header.substring(start, end)).trim();
                    extracted = extracted.replace("\"", "");
                    if (!extracted.isBlank()) {
                        fileName = extracted;
                    }
                }
            }

            if (fileName == null || fileName.isBlank() || !fileName.toLowerCase().endsWith(".pdf")) {
                fileName = "Drive_Book_" + (fileId.length() > 8 ? fileId.substring(0, 8) : fileId) + ".pdf";
            }

            Path targetPath = destinationDirectory.resolve(fileName);
            // Avoid overwriting
            if (Files.exists(targetPath)) {
                String base = fileName.substring(0, fileName.lastIndexOf('.'));
                int counter = 1;
                while (Files.exists(targetPath)) {
                    targetPath = destinationDirectory.resolve(base + " (" + counter + ").pdf");
                    counter++;
                }
            }

            File targetFile = targetPath.toFile();
            try (InputStream in = response.body();
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile), 65536)) {
                byte[] buffer = new byte[65536];
                int read;
                long bytesRead = 0;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    bytesRead += read;
                    if (listener != null) {
                        if (totalBytes > 0) {
                            double fraction = Math.min(0.2 + 0.78 * ((double) bytesRead / totalBytes), 0.98);
                            listener.onProgress(String.format("Downloading: %.1f MB / %.1f MB (%.0f%%)",
                                    bytesRead / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0), (fraction * 100)), fraction);
                        } else {
                            listener.onProgress(String.format("Downloading: %.1f MB...", bytesRead / (1024.0 * 1024.0)), -1.0);
                        }
                    }
                }
            }

            if (targetFile.length() > 0) {
                FILE_SIZE_CACHE.put(fileId, targetFile.length());
            }

            if (listener != null) {
                listener.onProgress("Download completed!", 1.0);
            }

            Book downloadedBook = new Book(
                    targetFile.getAbsolutePath(),
                    targetFile.getName(),
                    targetFile.getAbsolutePath(),
                    targetFile.length(),
                    targetFile.lastModified(),
                    BookSource.LOCAL
            );

            if (listener != null) {
                listener.onComplete("Successfully imported '" + targetFile.getName() + "' without sign-in!");
            }

            return downloadedBook;
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Failed to download shared file: " + e.getMessage(), e);
            }
            return null;
        }
    }

    /**
     * Streams a shared PDF file from Google Drive into a temporary online reading cache directory.
     * Allows viewing cloud PDFs directly in the embedded reader without modifying the local Books folder.
     */
    public File streamCloudFileForOnlineReading(String fileId, String defaultFileName, TransferListener listener) throws Exception {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("Invalid Google Drive file ID");
        }

        Path cacheDir = Paths.get(System.getProperty("java.io.tmpdir"), "pdf_book_reader_online_cache");
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }

        // Sanitize file name
        String safeName = (defaultFileName != null && !defaultFileName.isBlank()) ? defaultFileName : ("online_" + fileId + ".pdf");
        if (!safeName.toLowerCase().endsWith(".pdf")) {
            safeName += ".pdf";
        }
        safeName = safeName.replaceAll("[^a-zA-Z0-9._-]", "_");

        Path targetCachePath = cacheDir.resolve(fileId + "_" + safeName);
        File targetFile = targetCachePath.toFile();

        // If already cached and valid size, return immediately!
        if (targetFile.exists() && targetFile.length() > 1024) {
            if (listener != null) {
                listener.onProgress("Loaded from online cache...", 1.0);
                listener.onComplete("Ready for online reading");
            }
            return targetFile;
        }

        if (listener != null) {
            listener.onProgress("Connecting to Google Drive shared file...", 0.1);
        }

        String downloadUrl = "https://drive.google.com/uc?export=download&id=" + fileId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PDFBookReader/1.0")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        if (listener != null) {
            listener.onProgress("Streaming PDF data for online reading...", 0.25);
        }

        HttpResponse<InputStream> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP error " + response.statusCode() + " when streaming cloud PDF. Ensure the folder is shared with link access.");
        }

        long totalBytes = response.headers().firstValue("Content-Length").map(s -> {
            try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
        }).orElse(0L);

        File tempWritingFile = new File(targetFile.getAbsolutePath() + ".tmp");
        tempWritingFile.deleteOnExit();

        try (InputStream in = response.body();
             OutputStream out = new BufferedOutputStream(new FileOutputStream(tempWritingFile), 65536)) {
            byte[] buffer = new byte[65536];
            int read;
            long bytesRead = 0;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                bytesRead += read;
                if (listener != null && totalBytes > 0) {
                    double progress = 0.25 + 0.70 * ((double) bytesRead / totalBytes);
                    listener.onProgress(String.format("Streaming: %.1f MB / %.1f MB (%.0f%%)",
                            bytesRead / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0), (progress * 100)), Math.min(progress, 0.95));
                }
            }
        }

        if (tempWritingFile.renameTo(targetFile) || (targetFile.exists() && targetFile.delete() && tempWritingFile.renameTo(targetFile))) {
            targetFile.deleteOnExit();
        } else {
            targetFile = tempWritingFile;
        }

        if (targetFile.length() > 0) {
            FILE_SIZE_CACHE.put(fileId, targetFile.length());
        }

        if (listener != null) {
            listener.onProgress("Streaming complete!", 1.0);
            listener.onComplete("Ready for reading");
        }

        return targetFile;
    }

    /**
     * Lists PDF books from a public or shared Google Drive folder without requiring any OAuth sign-in.
     * Queries embedded folderview and Drive folder HTML, extracting file IDs, titles, and dates.
     */
    public List<Book> listSharedFolderBooks(String folderIdOrUrl) throws Exception {
        if (folderIdOrUrl == null || folderIdOrUrl.isBlank()) {
            return Collections.emptyList();
        }

        String folderId = extractIdFromShareLink(folderIdOrUrl);
        if (folderId.isEmpty()) {
            folderId = folderIdOrUrl.trim();
        }

        Map<String, Book> foundBooks = new LinkedHashMap<>();

        // 1. Query embeddedfolderview
        String embeddedUrl = "https://drive.google.com/embeddedfolderview?id=" + folderId + "#list";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(embeddedUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String html = response.body();
                parseFolderHtml(html, foundBooks);
            }
        } catch (Exception e) {
            System.err.println("Warning: failed to query embeddedfolderview: " + e.getMessage());
        }

        // 2. If nothing found, query the direct folder page
        if (foundBooks.isEmpty()) {
            String directUrl = "https://drive.google.com/drive/folders/" + folderId;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(directUrl))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String html = response.body();
                    parseFolderHtml(html, foundBooks);
                }
            } catch (Exception e) {
                System.err.println("Warning: failed to query direct folder page: " + e.getMessage());
            }
        }

        return new ArrayList<>(foundBooks.values());
    }

    private void parseFolderHtml(String html, Map<String, Book> foundBooks) {
        if (html == null || html.isBlank()) return;

        // Pattern 1: flip-entry with title, id, and optional size
        Pattern flipPattern = Pattern.compile("<div class=[\"']flip-entry[\"'][^>]*id=[\"']entry-([a-zA-Z0-9_-]{20,})[\"']([\\s\\S]*?)(?=<div class=[\"']flip-entry[\"']|$)", Pattern.CASE_INSENSITIVE);
        Matcher flipMatcher = flipPattern.matcher(html);
        while (flipMatcher.find()) {
            String fileId = flipMatcher.group(1);
            String block = flipMatcher.group(2);

            Pattern titleSub = Pattern.compile("<div class=[\"']flip-entry-title[\"']>([^<]+)</div>", Pattern.CASE_INSENSITIVE);
            Matcher tm = titleSub.matcher(block);
            String title = tm.find() ? tm.group(1).trim() : "";

            Pattern sizeSub = Pattern.compile("<div class=[\"']flip-entry-size[\"']>([^<]+)</div>", Pattern.CASE_INSENSITIVE);
            Matcher sm = sizeSub.matcher(block);
            long sizeBytes = sm.find() ? parseHumanSizeToBytes(sm.group(1).trim()) : 0;
            if (sizeBytes <= 0) {
                sizeBytes = FILE_SIZE_CACHE.getOrDefault(fileId, 0L);
            } else {
                FILE_SIZE_CACHE.put(fileId, sizeBytes);
            }

            if (isPdf(title)) {
                foundBooks.put(fileId, new Book(fileId, cleanTitle(title), "https://drive.google.com/file/d/" + fileId, sizeBytes, System.currentTimeMillis(), BookSource.CLOUD));
            }
        }

        // Pattern 2: href to /file/d/<id>/ with title
        Pattern hrefPattern = Pattern.compile("href=[\"'](?:https://drive\\.google\\.com)?/file/d/([a-zA-Z0-9_-]{20,})/[^\"']*[\"'][^>]*>([\\s\\S]*?)</a>", Pattern.CASE_INSENSITIVE);
        Matcher hrefMatcher = hrefPattern.matcher(html);
        while (hrefMatcher.find()) {
            String fileId = hrefMatcher.group(1);
            String inner = hrefMatcher.group(2);
            Pattern titleSub = Pattern.compile("<div class=[\"']flip-entry-title[\"']>([^<]+)</div>", Pattern.CASE_INSENSITIVE);
            Matcher tm = titleSub.matcher(inner);
            String title = tm.find() ? tm.group(1).trim() : "";
            if (title.isEmpty()) {
                title = inner.replaceAll("<[^>]+>", "").trim();
            }
            if (!title.isEmpty() && isPdf(title)) {
                long sizeBytes = FILE_SIZE_CACHE.getOrDefault(fileId, 0L);
                foundBooks.putIfAbsent(fileId, new Book(fileId, cleanTitle(title), "https://drive.google.com/file/d/" + fileId, sizeBytes, System.currentTimeMillis(), BookSource.CLOUD));
            }
        }

        // Pattern 3: JSON array format ["id", ["title.pdf", ...], ..., size]
        Pattern jsonPattern = Pattern.compile("\\[[\"']([a-zA-Z0-9_-]{20,})[\"'],\\[[\"']([^\"']+\\.pdf)[\"']([^\\]]*)\\]", Pattern.CASE_INSENSITIVE);
        Matcher jsonMatcher = jsonPattern.matcher(html);
        while (jsonMatcher.find()) {
            String fileId = jsonMatcher.group(1);
            String title = jsonMatcher.group(2).trim();
            String rest = jsonMatcher.group(3);
            long sizeBytes = 0;
            Matcher numMatcher = Pattern.compile("[\"']?([0-9]{4,12})[\"']?").matcher(rest);
            if (numMatcher.find()) {
                try { sizeBytes = Long.parseLong(numMatcher.group(1)); } catch (Exception ignored) {}
            }
            if (sizeBytes <= 0) {
                sizeBytes = FILE_SIZE_CACHE.getOrDefault(fileId, 0L);
            } else {
                FILE_SIZE_CACHE.put(fileId, sizeBytes);
            }
            foundBooks.putIfAbsent(fileId, new Book(fileId, cleanTitle(title), "https://drive.google.com/file/d/" + fileId, sizeBytes, System.currentTimeMillis(), BookSource.CLOUD));
        }

        // Pattern 4: Generic drive JSON blob: "<id>" ... "<title>.pdf"
        Pattern blobPattern = Pattern.compile("\"([a-zA-Z0-9_-]{25,})\"[^\\]}]{1,300}?\"([^\"\\\\]+\\.pdf)\"", Pattern.CASE_INSENSITIVE);
        Matcher blobMatcher = blobPattern.matcher(html);
        while (blobMatcher.find()) {
            String fileId = blobMatcher.group(1);
            String title = blobMatcher.group(2).trim();
            long sizeBytes = FILE_SIZE_CACHE.getOrDefault(fileId, 0L);
            foundBooks.putIfAbsent(fileId, new Book(fileId, cleanTitle(title), "https://drive.google.com/file/d/" + fileId, sizeBytes, System.currentTimeMillis(), BookSource.CLOUD));
        }
    }

    private final ExecutorService sizeResolveExecutor = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r, "drive-size-resolver");
        t.setDaemon(true);
        return t;
    });

    /**
     * Resolves exact file sizes in the background for any cloud books missing a size.
     */
    public void resolveCloudBookSizesAsync(List<Book> books, Consumer<Book> onBookUpdated) {
        if (books == null || books.isEmpty()) return;
        for (Book book : books) {
            if (book.getFileSizeBytes() <= 0 && book.getId() != null && !book.getId().isBlank()) {
                Long cachedSize = FILE_SIZE_CACHE.get(book.getId());
                if (cachedSize != null && cachedSize > 0) {
                    book.setFileSizeBytes(cachedSize);
                    if (onBookUpdated != null) {
                        onBookUpdated.accept(book);
                    }
                    continue;
                }
                sizeResolveExecutor.submit(() -> {
                    long size = fetchRemoteFileSize(book.getId());
                    if (size > 0) {
                        book.setFileSizeBytes(size);
                        if (onBookUpdated != null) {
                            onBookUpdated.accept(book);
                        }
                    }
                });
            }
        }
    }

    /**
     * Queries Google Drive headers (HEAD / Range: bytes=0-0) to get Content-Length without downloading file body.
     */
    public long fetchRemoteFileSize(String fileId) {
        if (fileId == null || fileId.isBlank()) return 0;
        Long cached = FILE_SIZE_CACHE.get(fileId);
        if (cached != null && cached > 0) return cached;

        try {
            String url = "https://drive.google.com/uc?export=download&id=" + fileId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PDFBookReader/1.0")
                    .header("Range", "bytes=0-0")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<Void> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            long resolved = 0;
            if (response.statusCode() == 206) {
                Optional<String> contentRange = response.headers().firstValue("Content-Range");
                if (contentRange.isPresent()) {
                    String cr = contentRange.get();
                    int slash = cr.lastIndexOf('/');
                    if (slash != -1) {
                        String totalStr = cr.substring(slash + 1).trim();
                        try {
                            resolved = Long.parseLong(totalStr);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } else if (response.statusCode() == 200) {
                Optional<String> contentLen = response.headers().firstValue("Content-Length");
                if (contentLen.isPresent()) {
                    try {
                        resolved = Long.parseLong(contentLen.get().trim());
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (resolved > 0) {
                FILE_SIZE_CACHE.put(fileId, resolved);
                return resolved;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static long parseHumanSizeToBytes(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String clean = raw.trim().toUpperCase();
        try {
            if (clean.endsWith("TB")) {
                double val = Double.parseDouble(clean.substring(0, clean.length() - 2).trim());
                return (long) (val * 1024L * 1024L * 1024L * 1024L);
            } else if (clean.endsWith("GB") || clean.endsWith("G")) {
                int len = clean.endsWith("GB") ? 2 : 1;
                double val = Double.parseDouble(clean.substring(0, clean.length() - len).trim());
                return (long) (val * 1024L * 1024L * 1024L);
            } else if (clean.endsWith("MB") || clean.endsWith("M")) {
                int len = clean.endsWith("MB") ? 2 : 1;
                double val = Double.parseDouble(clean.substring(0, clean.length() - len).trim());
                return (long) (val * 1024L * 1024L);
            } else if (clean.endsWith("KB") || clean.endsWith("K")) {
                int len = clean.endsWith("KB") ? 2 : 1;
                double val = Double.parseDouble(clean.substring(0, clean.length() - len).trim());
                return (long) (val * 1024L);
            } else if (clean.endsWith("B")) {
                double val = Double.parseDouble(clean.substring(0, clean.length() - 1).trim());
                return (long) val;
            } else {
                return Long.parseLong(clean.replaceAll("[^0-9]", ""));
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isPdf(String name) {
        return name != null && name.toLowerCase().endsWith(".pdf");
    }

    private String cleanTitle(String raw) {
        if (raw == null) return "Unknown Book.pdf";
        String clean = raw.replaceAll("&amp;", "&")
                          .replaceAll("&quot;", "\"")
                          .replaceAll("&lt;", "<")
                          .replaceAll("&gt;", ">")
                          .replaceAll("&#39;", "'")
                          .trim();
        return clean.isEmpty() ? "Unknown Book.pdf" : clean;
    }

    public String getSharedFolderUrl() {
        return ConfigManager.getInstance().getConfig().getSharedFolderUrl();
    }

    public void setSharedFolderUrl(String url) {
        AppConfig config = ConfigManager.getInstance().getConfig();
        config.setSharedFolderUrl(url);
        String id = extractIdFromShareLink(url);
        config.setDriveFolderId(id);
        ConfigManager.getInstance().saveConfig();
    }

    public void unlinkSharedFolder() {
        AppConfig config = ConfigManager.getInstance().getConfig();
        config.setSharedFolderUrl("");
        config.setDriveFolderId("");
        config.setFolderPermission("VIEWER");
        config.setDriveCardCollapsed(false);
        ConfigManager.getInstance().saveConfig();
    }

    public boolean hasSharedFolder() {
        String url = getSharedFolderUrl();
        return url != null && !url.isBlank();
    }

    public String getFolderPermission() {
        return ConfigManager.getInstance().getConfig().getFolderPermission();
    }

    public void setFolderPermission(String permission) {
        AppConfig config = ConfigManager.getInstance().getConfig();
        config.setFolderPermission(permission);
        ConfigManager.getInstance().saveConfig();
    }

    public boolean isEditor() {
        return "EDITOR".equalsIgnoreCase(getFolderPermission());
    }

    public boolean isCommenter() {
        return "COMMENTER".equalsIgnoreCase(getFolderPermission());
    }

    public boolean isViewer() {
        return "VIEWER".equalsIgnoreCase(getFolderPermission());
    }

    public boolean canUpload() {
        return isEditor();
    }

    private static final Pattern ANYONE_WITH_LINK_ROLE_PATTERN = Pattern.compile(
            "\"anyoneWithLink\"[^\\]]*?,\\s*([1-5])\\s*,\\s*0\\s*\\]",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Checks if URL or HTML indicates EDITOR permission.
     * Priority 1 in detection order.
     */
    public static boolean isEditorMatch(String url, String html) {
        if (url != null && !url.isBlank()) {
            String lower = url.toLowerCase();
            boolean hasCommenter = lower.contains("commenter") || lower.contains("role=commenter") || lower.contains("role%3dcommenter");
            boolean hasViewer = lower.contains("role=viewer") || lower.contains("role%3dviewer")
                    || lower.contains("role=reader") || lower.contains("role%3dreader")
                    || lower.contains("usp=viewer") || lower.contains("/view");

            if (!hasCommenter && !hasViewer) {
                if (lower.contains("editor") || lower.contains("writer") || lower.contains("/edit")
                        || lower.contains("role=writer") || lower.contains("role%3dwriter")
                        || lower.contains("role=editor") || lower.contains("role%3deditor")
                        || lower.contains("usp=editor") || lower.contains("canedit")) {
                    return true;
                }
            }
        }

        if (html != null && !html.isBlank()) {
            Matcher m = ANYONE_WITH_LINK_ROLE_PATTERN.matcher(html);
            if (m.find()) {
                try {
                    int code = Integer.parseInt(m.group(1));
                    if (code == 1 || code == 2) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }

            Pattern editorPattern = Pattern.compile(
                    "(?:\"canAddChildren\"\\s*:\\s*true|"
                    + "\\\\\"canAddChildren\\\\\"\\s*:\\s*true|"
                    + "\"canUpload\"\\s*:\\s*true|"
                    + "\\\\\"canUpload\\\\\"\\s*:\\s*true|"
                    + "\"canEdit\"\\s*:\\s*true|"
                    + "\\\\\"canEdit\\\\\"\\s*:\\s*true|"
                    + "\"canWrite\"\\s*:\\s*true|"
                    + "\\\\\"canWrite\\\\\"\\s*:\\s*true|"
                    + "\"role\"\\s*:\\s*\"(?:writer|editor|owner)\"|"
                    + "\\\\\"role\\\\\"\\s*:\\s*\\\\\"(?:writer|editor|owner)\\\\\"|"
                    + "\\[\"canAddChildren\"\\s*,\\s*true\\]|"
                    + "aria-label=[\"']New[\"']|"
                    + "data-tooltip=[\"']New[\"'])",
                    Pattern.CASE_INSENSITIVE
            );
            if (editorPattern.matcher(html).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if URL or HTML indicates COMMENTER permission.
     * Priority 2 in detection order.
     */
    public static boolean isCommenterMatch(String url, String html) {
        if (url != null && !url.isBlank()) {
            String lower = url.toLowerCase();
            if (lower.contains("commenter") || lower.contains("role=commenter")
                    || lower.contains("role%3dcommenter") || lower.contains("usp=commenter")
                    || lower.contains("comment")) {
                return true;
            }
        }

        if (html != null && !html.isBlank()) {
            Matcher m = ANYONE_WITH_LINK_ROLE_PATTERN.matcher(html);
            if (m.find()) {
                try {
                    int code = Integer.parseInt(m.group(1));
                    if (code == 4 || code == 5) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }

            Pattern commenterPattern = Pattern.compile(
                    "(?:\"role\"\\s*:\\s*\"commenter\"|"
                    + "\\\\\"role\\\\\"\\s*:\\s*\\\\\"commenter\\\\\"|"
                    + "\"canComment\"\\s*:\\s*true)",
                    Pattern.CASE_INSENSITIVE
            );
            if (commenterPattern.matcher(html).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if URL or HTML indicates VIEWER permission.
     * Priority 3 in detection order.
     */
    public static boolean isViewerMatch(String url, String html) {
        if (url != null && !url.isBlank()) {
            String lower = url.toLowerCase();
            if (lower.contains("viewer") || lower.contains("reader")
                    || lower.contains("role=viewer") || lower.contains("role%3dviewer")
                    || lower.contains("role=reader") || lower.contains("role%3dreader")
                    || lower.contains("usp=viewer") || lower.contains("/view")
                    || lower.contains("viewonly") || lower.contains("read-only")) {
                return true;
            }
        }

        if (html != null && !html.isBlank()) {
            Matcher m = ANYONE_WITH_LINK_ROLE_PATTERN.matcher(html);
            if (m.find()) {
                try {
                    int code = Integer.parseInt(m.group(1));
                    if (code == 3) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }

            if (html.contains("https://drive.google.com/viewer/main")) {
                return true;
            }

            Pattern viewerPattern = Pattern.compile(
                    "(?:\"canAddChildren\"\\s*:\\s*false|"
                    + "\\\\\"canAddChildren\\\\\"\\s*:\\s*false|"
                    + "\"role\"\\s*:\\s*\"(?:reader|viewer)\"|"
                    + "\\\\\"role\\\\\"\\s*:\\s*\\\\\"(?:reader|viewer)\\\\\"|"
                    + "\\[\"canAddChildren\"\\s*,\\s*false\\]|"
                    + "view-only|"
                    + "\"isReadOnly\"\\s*:\\s*true)",
                    Pattern.CASE_INSENSITIVE
            );
            if (viewerPattern.matcher(html).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Detects permission role in priority order:
     * 1. EDITOR: Check for write/edit indicators
     * 2. COMMENTER: Check for comment indicators
     * 3. VIEWER: Default for standard/neutral Google Drive shared links
     */
    public static String detectPermission(String url, String html) {
        if (isEditorMatch(url, html)) {
            return "EDITOR";
        }
        if (isCommenterMatch(url, html)) {
            return "COMMENTER";
        }
        // Neutral or unconfirmed links default to VIEWER (download/read-only)
        return "VIEWER";
    }

    public static String detectPermissionFromUrl(String url) {
        if (url == null || url.isBlank()) return "VIEWER";
        return detectPermission(url, null);
    }

    /**
     * Checks if the URL explicitly indicates a role, returning null for neutral URLs.
     */
    public static String detectExplicitPermissionFromUrl(String url) {
        if (url == null || url.isBlank()) return null;
        if (isEditorMatch(url, null)) return "EDITOR";
        if (isCommenterMatch(url, null)) return "COMMENTER";
        if (isViewerMatch(url, null)) return "VIEWER";
        return null;
    }

    public static String detectPermissionFromHtml(String html) {
        if (html == null || html.isBlank()) return null;
        if (isEditorMatch(null, html)) return "EDITOR";
        if (isCommenterMatch(null, html)) return "COMMENTER";
        if (isViewerMatch(null, html)) return "VIEWER";
        return null;
    }

    /**
     * Probes Google Drive with a real in-app upload and re-download.
     * If the probe file cannot be created and downloaded successfully, the folder is treated as Viewer.
     */
    public String autoDetectFolderPermission(String folderUrlOrId) {
        if (folderUrlOrId == null || folderUrlOrId.isBlank()) {
            setFolderPermission("VIEWER");
            return "VIEWER";
        }

        String folderId = extractIdFromShareLink(folderUrlOrId);
        if (folderId.isEmpty()) folderId = folderUrlOrId.trim();

        String role = canUploadAndDownloadProbe(folderId) ? "EDITOR" : "VIEWER";
        setFolderPermission(role);
        return role;
    }

    private boolean canUploadAndDownloadProbe(String folderId) {
        if (folderId == null || folderId.isBlank()) {
            return false;
        }

        Path probePath = null;
        String probeFileId = "";
        try {
            ensureWriteApiAuthenticated();
            probePath = Files.createTempFile("pdf-reader-drive-probe-", ".pdf");
            String probePdf = "%PDF-1.4\n"
                    + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                    + "2 0 obj << /Type /Pages /Count 0 >> endobj\n"
                    + "trailer << /Root 1 0 R >>\n"
                    + "%%EOF\n";
            Files.writeString(probePath, probePdf, StandardCharsets.US_ASCII);

            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(".pdf-book-reader-permission-probe-" + UUID.randomUUID() + ".pdf");
            fileMetadata.setParents(Collections.singletonList(folderId));

            com.google.api.services.drive.model.File created = driveService.files()
                    .create(fileMetadata, new FileContent("application/pdf", probePath.toFile()))
                    .setFields("id, name, size")
                    .execute();
            probeFileId = created.getId();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            driveService.files().get(probeFileId).executeMediaAndDownloadTo(outputStream);
            return outputStream.size() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (probeFileId != null && !probeFileId.isBlank() && driveService != null) {
                try {
                    driveService.files().delete(probeFileId).execute();
                } catch (Exception ignored) {}
            }
            if (probePath != null) {
                try {
                    Files.deleteIfExists(probePath);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Stores legacy hint-based permission, normalizing all download-only roles to Viewer.
     */
    public void autoDetectAndStorePermission(String folderUrl, String html) {
        String role = detectPermission(folderUrl, html);
        if (!"EDITOR".equalsIgnoreCase(role)) {
            role = "VIEWER";
        }
        setFolderPermission(role);
    }
}
