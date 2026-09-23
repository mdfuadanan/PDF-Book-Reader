package com.pdfreader;

import com.pdfreader.model.Book;
import com.pdfreader.model.BookSource;
import com.pdfreader.service.DriveService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DriveLinkTest {

    @Test
    void testExtractIdFromFileShareLink() {
        String url1 = "https://drive.google.com/file/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs/view?usp=sharing";
        assertEquals("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs", DriveService.extractIdFromShareLink(url1));

        String url2 = "https://drive.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUvWxYz_12345";
        assertEquals("1AbCdEfGhIjKlMnOpQrStUvWxYz_12345", DriveService.extractIdFromShareLink(url2));

        String url3 = "https://drive.google.com/uc?id=1XyZ9876543210abcdef_";
        assertEquals("1XyZ9876543210abcdef_", DriveService.extractIdFromShareLink(url3));
    }

    @Test
    void testExtractIdFromFolderShareLink() {
        String folderUrl = "https://drive.google.com/drive/folders/1wXyZ9876543210abcdefghijklmnop?usp=drive_link";
        assertEquals("1wXyZ9876543210abcdefghijklmnop", DriveService.extractIdFromShareLink(folderUrl));
        assertTrue(DriveService.isFolderShareLink(folderUrl));
    }

    @Test
    void testExtractIdFromBareId() {
        String bareId = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs";
        assertEquals(bareId, DriveService.extractIdFromShareLink(bareId));
        assertFalse(DriveService.isFolderShareLink(bareId));
    }

    @Test
    void testExtractIdFromEmptyOrNull() {
        assertEquals("", DriveService.extractIdFromShareLink(null));
        assertEquals("", DriveService.extractIdFromShareLink("   "));
        assertFalse(DriveService.isFolderShareLink(null));
    }

    @Test
    void testDetectPermissionFromUrl() {
        assertEquals("VIEWER", DriveService.detectPermissionFromUrl("https://drive.google.com/drive/folders/12345/view"));
        assertEquals("VIEWER", DriveService.detectPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=sharing&role=viewer"));
        assertEquals("COMMENTER", DriveService.detectPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=commenter"));
        assertEquals("COMMENTER", DriveService.detectPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=sharing&role=commenter"));
        assertEquals("EDITOR", DriveService.detectPermissionFromUrl("https://drive.google.com/drive/folders/12345/edit"));
        assertEquals("EDITOR", DriveService.detectPermissionFromUrl("https://drive.google.com/drive/folders/12345?role=writer"));
        assertEquals("EDITOR", DriveService.detectPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=editor"));
        // Priority order: When no Editor or Commenter is found, neutral links default to Viewer
        assertEquals("VIEWER", DriveService.detectPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=drive_link"));
        assertEquals("VIEWER", DriveService.detectPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=sharing"));
        assertEquals("VIEWER", DriveService.detectPermissionFromUrl(null));
    }

    @Test
    void testDetectExplicitPermissionFromUrl() {
        assertEquals("VIEWER", DriveService.detectExplicitPermissionFromUrl("https://drive.google.com/drive/folders/12345/view"));
        assertEquals("VIEWER", DriveService.detectExplicitPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=sharing&role=viewer"));
        assertEquals("COMMENTER", DriveService.detectExplicitPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=commenter"));
        assertEquals("COMMENTER", DriveService.detectExplicitPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=sharing&role=commenter"));
        assertEquals("EDITOR", DriveService.detectExplicitPermissionFromUrl("https://drive.google.com/drive/folders/12345/edit"));
        assertEquals("EDITOR", DriveService.detectExplicitPermissionFromUrl("https://drive.google.com/drive/folders/12345?role=writer"));
        assertEquals("EDITOR", DriveService.detectExplicitPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=editor"));
        // Neutral links return null to trigger HTML probe
        assertNull(DriveService.detectExplicitPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=drive_link"));
        assertNull(DriveService.detectExplicitPermissionFromUrl("https://drive.google.com/drive/folders/12345?usp=sharing"));
        assertNull(DriveService.detectExplicitPermissionFromUrl("12345abcdefghijklmnop"));
        assertNull(DriveService.detectExplicitPermissionFromUrl(null));
    }

    @Test
    void testParseHumanSizeToBytes() {
        assertEquals(2621440L, DriveService.parseHumanSizeToBytes("2.5 MB"));
        assertEquals(524288L, DriveService.parseHumanSizeToBytes("512 KB"));
        assertEquals(1073741824L, DriveService.parseHumanSizeToBytes("1 GB"));
        assertEquals(1048576L, DriveService.parseHumanSizeToBytes("1 M"));
        assertEquals(500L, DriveService.parseHumanSizeToBytes("500 B"));
        assertEquals(0L, DriveService.parseHumanSizeToBytes(""));
        assertEquals(0L, DriveService.parseHumanSizeToBytes(null));
    }

    @Test
    void testBookFormattedFileSize() {
        Book cloudBookNoSize = new Book("id1", "Cloud Book.pdf", "https://...", 0, System.currentTimeMillis(), BookSource.CLOUD);
        assertEquals("-", cloudBookNoSize.getFormattedFileSize());

        Book cloudBookWithSize = new Book("id2", "Cloud Book 2.pdf", "https://...", 2621440L, System.currentTimeMillis(), BookSource.CLOUD);
        assertEquals("2.5 MB", cloudBookWithSize.getFormattedFileSize());

        Book localBookEmpty = new Book("path1", "Local.pdf", "path1", 0, System.currentTimeMillis(), BookSource.LOCAL);
        assertEquals("0 B", localBookEmpty.getFormattedFileSize());

        Book localBookWithSize = new Book("path2", "Local2.pdf", "path2", 1048576L, System.currentTimeMillis(), BookSource.LOCAL);
        assertEquals("1.0 MB", localBookWithSize.getFormattedFileSize());
    }

    @Test
    void testDetectPermissionFromHtml() {
        String editorHtml1 = "<html><script>window['_DRIVE_data'] = {\"canAddChildren\":true,\"role\":\"writer\"};</script></html>";
        assertEquals("EDITOR", DriveService.detectPermissionFromHtml(editorHtml1));

        String editorHtml2 = "<div><button aria-label=\"New\">New</button></div>";
        assertEquals("EDITOR", DriveService.detectPermissionFromHtml(editorHtml2));

        String commenterHtml = "<html><script>window['_DRIVE_data'] = {\"role\":\"commenter\",\"canComment\":true};</script></html>";
        assertEquals("COMMENTER", DriveService.detectPermissionFromHtml(commenterHtml));

        String viewerHtml1 = "<html><script>window['_DRIVE_data'] = {\"canAddChildren\":false,\"role\":\"reader\"};</script></html>";
        assertEquals("VIEWER", DriveService.detectPermissionFromHtml(viewerHtml1));

        String viewerHtml2 = "<div class=\"view-only-banner\">view-only</div>";
        assertEquals("VIEWER", DriveService.detectPermissionFromHtml(viewerHtml2));

        assertNull(DriveService.detectPermissionFromHtml("<html><body>No permissions found</body></html>"));
        assertNull(DriveService.detectPermissionFromHtml(null));
    }

    @Test
    void testFileSizeCache() {
        String testId = "test_file_id_123456789";
        assertEquals(0L, DriveService.getCachedFileSize(testId));

        DriveService.cacheFileSize(testId, 5242880L);
        assertEquals(5242880L, DriveService.getCachedFileSize(testId));

        // Negative or zero or blank shouldn't overwrite valid cache
        DriveService.cacheFileSize(testId, 0L);
        assertEquals(5242880L, DriveService.getCachedFileSize(testId));

        DriveService.cacheFileSize(null, 100L);
        assertEquals(0L, DriveService.getCachedFileSize(null));
    }

    @Test
    void testDetectRoleFromAnyoneWithLinkPayload() {
        String editorHtml = "AF_initDataCallback({key: 'ds:1', data:[... [2,[[4,\"anyoneWithLink\",null,1,null,2,0]],0]]});";
        assertEquals("EDITOR", DriveService.detectPermission(null, editorHtml));
        assertTrue(DriveService.isEditorMatch(null, editorHtml));

        String commenterHtml = "AF_initDataCallback({key: 'ds:1', data:[... [2,[[4,\"anyoneWithLink\",null,1,null,4,0]],0]]});";
        assertEquals("COMMENTER", DriveService.detectPermission(null, commenterHtml));
        assertTrue(DriveService.isCommenterMatch(null, commenterHtml));

        String viewerHtml = "AF_initDataCallback({key: 'ds:1', data:[... [2,[[4,\"anyoneWithLink\",null,1,null,3,0]],0]]});";
        assertEquals("VIEWER", DriveService.detectPermission(null, viewerHtml));
        assertTrue(DriveService.isViewerMatch(null, viewerHtml));
    }
}
