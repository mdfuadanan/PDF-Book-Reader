package com.pdfreader.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service for loading and rendering PDF pages using Apache PDFBox.
 * Returns standard java.awt.image.BufferedImage so it remains decoupled
 * from JavaFX (Android can replace this with native PdfRenderer).
 */
public class PdfService implements AutoCloseable {
    private static final int DEFAULT_DPI = 150;
    private static final int MAX_CACHE_SIZE = 10;

    private PDDocument document;
    private PDFRenderer renderer;
    private File currentFile;
    private int pageCount = 0;

    // LRU cache for rendered page images: key = "pageIndex_scale"
    private final Map<String, BufferedImage> pageCache = new LinkedHashMap<String, BufferedImage>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    // Global in-memory cache for book cover thumbnails
    private static final Map<String, BufferedImage> COVER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Generates or retrieves a cached cover page thumbnail for the specified PDF file.
     * Renders page 0 at 72 DPI for fast display.
     */
    public static BufferedImage getCoverThumbnail(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists() || pdfFile.length() == 0) {
            return null;
        }
        String key = pdfFile.getAbsolutePath() + "_" + pdfFile.lastModified();
        if (COVER_CACHE.containsKey(key)) {
            return COVER_CACHE.get(key);
        }

        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            if (doc.getNumberOfPages() > 0) {
                PDFRenderer renderer = new PDFRenderer(doc);
                // 60 DPI is lightweight and sharp enough for small thumbnails
                BufferedImage thumb = renderer.renderImageWithDPI(0, 60, ImageType.RGB);
                COVER_CACHE.put(key, thumb);
                return thumb;
            }
        } catch (Exception e) {
            System.err.println("Could not generate thumbnail for " + pdfFile.getName() + ": " + e.getMessage());
        }
        return null;
    }

    public PdfService() {
    }

    /**
     * Loads a PDF file for viewing and rendering.
     */
    public synchronized void loadDocument(File file) throws IOException {
        close();
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("PDF file does not exist: " + (file != null ? file.getPath() : "null"));
        }
        this.currentFile = file;
        this.document = Loader.loadPDF(file);
        this.renderer = new PDFRenderer(document);
        this.pageCount = document.getNumberOfPages();
        this.pageCache.clear();
    }

    public synchronized int getPageCount() {
        return pageCount;
    }

    public synchronized File getCurrentFile() {
        return currentFile;
    }

    public synchronized boolean isDocumentLoaded() {
        return document != null;
    }

    /**
     * Renders a specific page at the requested zoom scale (1.0 = standard 150 DPI).
     *
     * @param pageIndex 0-based page index
     * @param scale     zoom factor (e.g. 1.0 = 100%, 1.5 = 150%)
     * @return BufferedImage of rendered page
     */
    public synchronized BufferedImage renderPage(int pageIndex, double scale) throws IOException {
        if (document == null || renderer == null) {
            throw new IllegalStateException("No PDF document is loaded");
        }
        if (pageIndex < 0 || pageIndex >= pageCount) {
            throw new IndexOutOfBoundsException("Page index " + pageIndex + " out of bounds (total pages: " + pageCount + ")");
        }

        // Round scale to two decimals for cache key stability
        double roundedScale = Math.round(scale * 100.0) / 100.0;
        String cacheKey = pageIndex + "_" + roundedScale;

        if (pageCache.containsKey(cacheKey)) {
            return pageCache.get(cacheKey);
        }

        float dpi = (float) (DEFAULT_DPI * roundedScale);
        // Clamp DPI to reasonable limits (minimum 36 DPI, maximum 300 DPI) to prevent OOM
        dpi = Math.max(36f, Math.min(300f, dpi));

        BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
        pageCache.put(cacheKey, image);
        return image;
    }

    /**
     * Gets the original aspect ratio (width / height) of the given page.
     */
    public synchronized double getPageAspectRatio(int pageIndex) {
        if (document == null || pageIndex < 0 || pageIndex >= pageCount) {
            return 1.0;
        }
        PDPage page = document.getPage(pageIndex);
        PDRectangle mediaBox = page.getMediaBox();
        if (mediaBox != null && mediaBox.getHeight() > 0) {
            return mediaBox.getWidth() / (double) mediaBox.getHeight();
        }
        return 1.0;
    }

    /**
     * Clears cached page renderings to free memory.
     */
    public synchronized void clearCache() {
        pageCache.clear();
    }

    @Override
    public synchronized void close() {
        if (document != null) {
            try {
                document.close();
            } catch (IOException e) {
                System.err.println("Error closing PDF document: " + e.getMessage());
            }
            document = null;
            renderer = null;
            currentFile = null;
            pageCount = 0;
            pageCache.clear();
        }
    }
}
