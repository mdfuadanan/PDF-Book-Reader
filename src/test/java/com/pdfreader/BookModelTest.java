package com.pdfreader;

import com.pdfreader.model.Book;
import com.pdfreader.model.BookSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BookModelTest {

    @Test
    void testBookAttributesAndFormatting() {
        Book book = new Book("test-id", "Sample.pdf", "/path/to/Sample.pdf", 1024 * 1024 * 2 + 500 * 1024, 1726700000000L, BookSource.LOCAL);

        assertEquals("test-id", book.getId());
        assertEquals("Sample.pdf", book.getTitle());
        assertEquals("/path/to/Sample.pdf", book.getPath());
        assertTrue(book.isLocal());
        assertFalse(book.isCloud());

        // File size formatting (approx 2.5 MB)
        String formattedSize = book.getFormattedFileSize();
        assertTrue(formattedSize.contains("MB") || formattedSize.contains("2.5"));

        // Last modified date formatting
        String formattedDate = book.getFormattedLastModified();
        assertNotNull(formattedDate);
        assertNotEquals("Unknown", formattedDate);
    }

    @Test
    void testEqualityBasedOnId() {
        Book book1 = new Book("id-123", "Title 1", "path1", 100, 1000, BookSource.LOCAL);
        Book book2 = new Book("id-123", "Title 2", "path2", 200, 2000, BookSource.CLOUD);
        Book book3 = new Book("id-999", "Title 1", "path1", 100, 1000, BookSource.LOCAL);

        assertEquals(book1, book2);
        assertNotEquals(book1, book3);
        assertEquals(book1.hashCode(), book2.hashCode());
    }
}
