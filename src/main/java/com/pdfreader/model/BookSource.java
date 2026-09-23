package com.pdfreader.model;

/**
 * Indicates whether a book is stored in the local file system
 * or in Google Drive cloud storage.
 */
public enum BookSource {
    LOCAL("Local Storage"),
    CLOUD("Google Drive");

    private final String displayName;

    BookSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
