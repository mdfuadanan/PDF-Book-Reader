package com.pdfreader;

import com.pdfreader.model.Book;
import com.pdfreader.service.BookService;
import com.pdfreader.service.PdfService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BookServiceAndPdfTest {

    @TempDir
    Path tempBooksDir;

    private BookService bookService;
    private PdfService pdfService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(tempBooksDir);
        pdfService = new PdfService();
    }

    @AfterEach
    void tearDown() {
        pdfService.close();
    }

    @Test
    void testLocalLibraryInitializationAndGuideCreation() {
        assertTrue(Files.exists(tempBooksDir), "Books directory should exist");

        // Starter PDF should have been created
        List<Book> books = bookService.listLocalBooks();
        assertFalse(books.isEmpty(), "Starter book guide should be found");
        assertEquals("Welcome to PDF Book Reader.pdf", books.get(0).getTitle());
        assertTrue(books.get(0).getFileSizeBytes() > 0);
    }

    @Test
    void testImportAndDeleteBook() throws Exception {
        // Create a dummy PDF file outside the books directory
        Path outsideFile = Files.createTempFile("external-book", ".pdf");
        // Write simple minimal valid PDF content
        bookService.createStarterGuidePdf();
        File source = tempBooksDir.resolve("Welcome to PDF Book Reader.pdf").toFile();

        // Import
        Book imported = bookService.importBook(source);
        assertNotNull(imported);
        assertTrue(imported.getTitle().contains("Welcome"));

        // Delete imported book
        boolean deleted = bookService.deleteBook(imported);
        assertTrue(deleted);
    }

    @Test
    void testPdfRendering() throws Exception {
        List<Book> books = bookService.listLocalBooks();
        assertFalse(books.isEmpty());

        Book guideBook = books.get(0);
        File pdfFile = new File(guideBook.getPath());

        pdfService.loadDocument(pdfFile);
        assertTrue(pdfService.isDocumentLoaded());
        assertTrue(pdfService.getPageCount() >= 1);

        // Render page 0 at 1.0 zoom
        BufferedImage image = pdfService.renderPage(0, 1.0);
        assertNotNull(image);
        assertTrue(image.getWidth() > 100);
        assertTrue(image.getHeight() > 100);

        // Aspect ratio
        double ratio = pdfService.getPageAspectRatio(0);
        assertTrue(ratio > 0.5 && ratio < 1.0, "A4 portrait aspect ratio should be around 0.7");
    }
}
