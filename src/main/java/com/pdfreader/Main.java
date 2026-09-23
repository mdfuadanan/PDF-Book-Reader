package com.pdfreader;

/**
 * Standard launcher entry point.
 * Calling Application.launch from a non-Application class ensures
 * compatibility across modular and non-modular execution environments.
 */
public class Main {
    public static void main(String[] args) {
        PdfBookReaderApp.main(args);
    }
}
