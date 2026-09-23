package com.pdfreader;

import com.pdfreader.config.AppConfig;
import com.pdfreader.config.ConfigManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigManagerTest {

    @Test
    void testConfigDefaultsAndPersistence() {
        ConfigManager manager = ConfigManager.getInstance();
        assertNotNull(manager);

        AppConfig config = manager.getConfig();
        assertNotNull(config);
        assertEquals("Books", config.getBooksDirectory());
        assertEquals("PDFBookReader", config.getDriveFolderName());
        assertTrue(config.getCredentialsPath() != null && config.getCredentialsPath().endsWith("credentials.json"));

        // Update zoom
        config.setDefaultZoom(1.25);
        config.setFolderPermission("VIEWER");
        config.setDriveCardCollapsed(true);
        manager.saveConfig();

        assertEquals(1.25, manager.getConfig().getDefaultZoom());
        assertEquals("VIEWER", manager.getConfig().getFolderPermission());
        assertTrue(manager.getConfig().isDriveCardCollapsed());

        // Restore to EDITOR
        config.setFolderPermission("EDITOR");
        config.setDriveCardCollapsed(false);
        manager.saveConfig();
        assertEquals("EDITOR", manager.getConfig().getFolderPermission());
        assertFalse(manager.getConfig().isDriveCardCollapsed());
    }
}
