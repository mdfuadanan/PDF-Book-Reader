package com.pdfreader.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Manages configuration persistence via JSON using Gson.
 * Completely independent of UI for Android portability.
 */
public class ConfigManager {
    private static final String CONFIG_FILE_NAME = "pdf_book_reader_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ConfigManager instance;

    private AppConfig config;
    private final File configFile;

    private ConfigManager() {
        this.configFile = new File(CONFIG_FILE_NAME);
        this.config = loadConfig();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private AppConfig loadConfig() {
        if (configFile.exists() && configFile.length() > 0) {
            try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                AppConfig loaded = GSON.fromJson(reader, AppConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException e) {
                System.err.println("Could not load config file, falling back to defaults: " + e.getMessage());
            }
        }
        AppConfig newConfig = new AppConfig();
        saveConfig(newConfig);
        return newConfig;
    }

    public synchronized void saveConfig() {
        saveConfig(this.config);
    }

    private synchronized void saveConfig(AppConfig configToSave) {
        try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            GSON.toJson(configToSave, writer);
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
        }
    }

    public AppConfig getConfig() {
        return config;
    }

    public void updateConfig(AppConfig config) {
        this.config = config;
        saveConfig();
    }
}
