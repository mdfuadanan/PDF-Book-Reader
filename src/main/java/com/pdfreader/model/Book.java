package com.pdfreader.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Model representing a PDF book.
 * Fully decoupled from UI frameworks for easy portability to Android.
 */
public class Book implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private String id;
    private String title;
    private String path;
    private long fileSizeBytes;
    private long lastModifiedEpochMs;
    private BookSource source;
    private String driveFileId;
    private int pageCount;

    public Book() {
    }

    public Book(String id, String title, String path, long fileSizeBytes, long lastModifiedEpochMs, BookSource source) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.fileSizeBytes = fileSizeBytes;
        this.lastModifiedEpochMs = lastModifiedEpochMs;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public long getLastModifiedEpochMs() {
        return lastModifiedEpochMs;
    }

    public void setLastModifiedEpochMs(long lastModifiedEpochMs) {
        this.lastModifiedEpochMs = lastModifiedEpochMs;
    }

    public BookSource getSource() {
        return source;
    }

    public void setSource(BookSource source) {
        this.source = source;
    }

    public String getDriveFileId() {
        return driveFileId;
    }

    public void setDriveFileId(String driveFileId) {
        this.driveFileId = driveFileId;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public boolean isLocal() {
        return source == BookSource.LOCAL;
    }

    public boolean isCloud() {
        return source == BookSource.CLOUD;
    }

    /**
     * Formats bytes into human-readable B, KB, MB, GB.
     */
    public String getFormattedFileSize() {
        if (fileSizeBytes <= 0) {
            return isCloud() ? "-" : "0 B";
        }
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(fileSizeBytes) / Math.log10(1024));
        if (digitGroups >= units.length) {
            digitGroups = units.length - 1;
        }
        return String.format("%.1f %s", fileSizeBytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    /**
     * Formats last modified epoch milliseconds into standard date-time string.
     */
    public String getFormattedLastModified() {
        if (lastModifiedEpochMs <= 0) {
            return "Unknown";
        }
        try {
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(lastModifiedEpochMs),
                    ZoneId.systemDefault()
            );
            return dateTime.format(DATE_FORMATTER);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return Objects.equals(id, book.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return title != null ? title : (id != null ? id : "Untitled PDF");
    }
}
