package com.pdfreader.service;

import com.pdfreader.config.ConfigManager;
import com.pdfreader.model.Book;
import com.pdfreader.model.BookSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service managing local PDF library storage and file operations.
 * Completely independent of UI components for Android portability.
 */
public class BookService {
    private final Path booksDirectory;

    public BookService() {
        String configuredDir = ConfigManager.getInstance().getConfig().getBooksDirectory();
        this.booksDirectory = Paths.get(configuredDir).toAbsolutePath();
        initLibrary();
    }

    public BookService(Path customBooksDirectory) {
        this.booksDirectory = customBooksDirectory.toAbsolutePath();
        initLibrary();
    }

    /**
     * Initializes the local library by ensuring the directory exists
     * and creating a starter guide PDF if the directory is empty.
     */
    private void initLibrary() {
        try {
            if (!Files.exists(booksDirectory)) {
                Files.createDirectories(booksDirectory);
            }
            if (isLibraryEmpty()) {
                createStarterGuidePdf();
            }
        } catch (IOException e) {
            System.err.println("Error initializing books directory: " + e.getMessage());
        }
    }

    public Path getBooksDirectory() {
        return booksDirectory;
    }

    public boolean isLibraryEmpty() {
        try (Stream<Path> stream = Files.list(booksDirectory)) {
            return stream.noneMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"));
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Scans the local Books directory and returns all PDF files as Book models.
     */
    public List<Book> listLocalBooks() {
        List<Book> books = new ArrayList<>();
        if (!Files.exists(booksDirectory)) {
            return books;
        }

        try (Stream<Path> stream = Files.list(booksDirectory)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                  .forEach(path -> {
                      try {
                          File file = path.toFile();
                          Book book = new Book(
                                  file.getAbsolutePath(),
                                  file.getName(),
                                  file.getAbsolutePath(),
                                  file.length(),
                                  file.lastModified(),
                                  BookSource.LOCAL
                          );
                          books.add(book);
                      } catch (Exception e) {
                          System.err.println("Error reading book: " + path + ": " + e.getMessage());
                      }
                  });
        } catch (IOException e) {
            System.err.println("Failed to list local books: " + e.getMessage());
        }

        books.sort(Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));
        return books;
    }

    /**
     * Imports a PDF from an external location into the local Books folder.
     */
    public Book importBook(File sourceFile) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Source file does not exist");
        }
        if (!sourceFile.getName().toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Selected file is not a PDF");
        }

        String baseName = sourceFile.getName();
        Path targetPath = booksDirectory.resolve(baseName);

        // Handle duplicates by adding a counter
        if (Files.exists(targetPath)) {
            String nameWithoutExt = baseName.substring(0, baseName.lastIndexOf('.'));
            int count = 1;
            while (Files.exists(targetPath)) {
                targetPath = booksDirectory.resolve(nameWithoutExt + " (" + count + ").pdf");
                count++;
            }
        }

        Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        File savedFile = targetPath.toFile();

        return new Book(
                savedFile.getAbsolutePath(),
                savedFile.getName(),
                savedFile.getAbsolutePath(),
                savedFile.length(),
                savedFile.lastModified(),
                BookSource.LOCAL
        );
    }

    /**
     * Deletes a book from the local storage.
     */
    public boolean deleteBook(Book book) {
        if (book == null || !book.isLocal()) {
            return false;
        }
        try {
            Path path = Paths.get(book.getPath());
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            System.err.println("Failed to delete book: " + book.getTitle() + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Renames a local PDF within the managed Books directory.
     */
    public Book renameBook(Book book, String newFileName) throws IOException {
        if (book == null || !book.isLocal()) {
            throw new IllegalArgumentException("Only local books can be renamed");
        }
        if (newFileName == null || newFileName.isBlank()) {
            throw new IllegalArgumentException("File name cannot be blank");
        }

        String cleanName = newFileName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!cleanName.toLowerCase().endsWith(".pdf")) {
            cleanName += ".pdf";
        }

        Path sourcePath = Paths.get(book.getPath()).toAbsolutePath().normalize();
        Path libraryRoot = booksDirectory.toAbsolutePath().normalize();
        if (!sourcePath.startsWith(libraryRoot)) {
            throw new IOException("Book is outside the local library folder");
        }
        if (!Files.exists(sourcePath)) {
            throw new IOException("Original file does not exist");
        }

        Path targetPath = libraryRoot.resolve(cleanName).normalize();
        if (!targetPath.startsWith(libraryRoot)) {
            throw new IOException("Invalid file name");
        }
        if (sourcePath.equals(targetPath)) {
            return book;
        }
        if (Files.exists(targetPath)) {
            throw new IOException("A book named '" + cleanName + "' already exists");
        }

        Files.move(sourcePath, targetPath);
        File renamedFile = targetPath.toFile();
        return new Book(
                renamedFile.getAbsolutePath(),
                renamedFile.getName(),
                renamedFile.getAbsolutePath(),
                renamedFile.length(),
                renamedFile.lastModified(),
                BookSource.LOCAL
        );
    }

    /**
     * Generates a starter guide PDF inside the Books directory on first run.
     */
    public void createStarterGuidePdf() {
        Path guidePath = booksDirectory.resolve("Welcome to PDF Book Reader.pdf");
        if (Files.exists(guidePath)) {
            return;
        }

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font codeFont = new PDType1Font(Standard14Fonts.FontName.COURIER);

                // Title
                cs.beginText();
                cs.setFont(titleFont, 24);
                cs.setLeading(30f);
                cs.newLineAtOffset(50, 750);
                cs.showText("Welcome to PDF Book Reader!");
                cs.newLine();

                // Subtitle
                cs.setFont(bodyFont, 14);
                cs.setLeading(22f);
                cs.showText("A modular desktop PDF reader with Google Drive integration.");
                cs.newLine();
                cs.newLine();

                // Section 1: Features
                cs.setFont(titleFont, 16);
                cs.setLeading(24f);
                cs.showText("Key Features:");
                cs.newLine();

                cs.setFont(bodyFont, 12);
                cs.setLeading(18f);
                cs.showText("1. Local PDF Library: Automatically scans your 'Books' folder.");
                cs.newLine();
                cs.showText("2. Built-in Viewer: Crisp rendering, page navigation, zoom, and fullscreen.");
                cs.newLine();
                cs.showText("3. Google Drive Integration: Connect via OAuth2 to manually upload and download.");
                cs.newLine();
                cs.showText("4. No Automatic Sync: You have complete control over cloud transfers.");
                cs.newLine();
                cs.newLine();

                // Section 2: Getting Started
                cs.setFont(titleFont, 16);
                cs.setLeading(24f);
                cs.showText("Quick Tips:");
                cs.newLine();

                cs.setFont(bodyFont, 12);
                cs.setLeading(18f);
                cs.showText("- Double click any book in the list to open it.");
                cs.newLine();
                cs.showText("- Use Next / Previous buttons or arrow keys to flip pages.");
                cs.newLine();
                cs.showText("- Use Zoom In / Out or Fit Width for comfortable reading.");
                cs.newLine();
                cs.showText("- Click 'Upload to Drive' on any local book to store a copy in your Google Drive.");
                cs.newLine();
                cs.showText("- Click 'Import PDF' on the toolbar to add books from any folder on your computer.");
                cs.newLine();
                cs.newLine();

                // Section 3: Android Ready
                cs.setFont(titleFont, 16);
                cs.setLeading(24f);
                cs.showText("Designed for Modularity:");
                cs.newLine();
                cs.setFont(bodyFont, 12);
                cs.showText("All core business services (BookService, DriveService, ConfigManager)");
                cs.newLine();
                cs.showText("are decoupled from JavaFX, ready for future Android implementation.");
                cs.endText();
            }

            document.save(guidePath.toFile());
            System.out.println("Created starter PDF guide at: " + guidePath);
        } catch (Exception e) {
            System.err.println("Could not create starter PDF guide: " + e.getMessage());
        }
    }
}
