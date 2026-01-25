package me.almana.moderationplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.logging.Level;

public class ConfigManager {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_PATH = "mods/data/moderationplus_config.json";

    private JsonObject config;

    public ConfigManager() {
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(CONFIG_PATH);
        if (!configFile.exists()) {
            try {
                if (configFile.getParentFile() != null) {
                    configFile.getParentFile().mkdirs();
                }
                try (java.io.InputStream in = getClass().getClassLoader()
                        .getResourceAsStream("moderationplus_config.json")) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, configFile.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        logger.at(Level.WARNING).log("Default config resource not found!");
                    }
                }
            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log("Failed to copy default config");
            }
        }

        try (FileReader reader = new FileReader(configFile)) {
            config = GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to load config, using defaults");
            config = new JsonObject();
        }

        if (config == null) {
            config = new JsonObject();
        }

        // Ensure defaults exist
        if (!config.has("jail")) {
            JsonObject jail = new JsonObject();
            jail.addProperty("radius", 10.0);
            config.add("jail", jail);
            saveConfig();
        }
    }

    public long getDatabaseFlushIntervalSeconds() {
        if (!config.has("database"))
            return 600;
        JsonObject db = config.getAsJsonObject("database");
        if (!db.has("flush_interval_seconds"))
            return 600;
        return db.get("flush_interval_seconds").getAsLong();
    }

    public void saveConfig() {
        File configFile = new File(CONFIG_PATH);
        try {
            configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to save config");
        }
    }

    public void setJailLocation(double x, double y, double z) {
        JsonObject jail;
        if (config.has("jail")) {
            jail = config.getAsJsonObject("jail");
        } else {
            jail = new JsonObject();
            config.add("jail", jail);
        }
        jail.addProperty("x", x);
        jail.addProperty("y", y);
        jail.addProperty("z", z);
        saveConfig();
    }

    public void setJailRadius(double radius) {
        JsonObject jail;
        if (config.has("jail")) {
            jail = config.getAsJsonObject("jail");
        } else {
            jail = new JsonObject();
            config.add("jail", jail);
        }
        jail.addProperty("radius", radius);
        saveConfig();
    }

    public double[] getJailLocation() {
        if (!config.has("jail")) {
            return null;
        }
        JsonObject jail = config.getAsJsonObject("jail");
        if (!jail.has("x") || !jail.has("y") || !jail.has("z")) {
            return null;
        }
        return new double[] {
                jail.get("x").getAsDouble(),
                jail.get("y").getAsDouble(),
                jail.get("z").getAsDouble()
        };
    }

    public double getJailRadius() {
        if (!config.has("jail")) {
            return 10.0;
        }
        JsonObject jail = config.getAsJsonObject("jail");
        if (!jail.has("radius")) {
            return 10.0;
        }
        return jail.get("radius").getAsDouble();
    }

    public boolean hasJailLocation() {
        return config.has("jail") && getJailLocation() != null;
    }

    public boolean isWebPanelEnabled() {
        if (!config.has("web_panel")) {
            return false;
        }
        JsonObject webPanel = config.getAsJsonObject("web_panel");
        return webPanel.has("enabled") && webPanel.get("enabled").getAsBoolean();
    }

    public void setWebPanelEnabled(boolean enabled) {
        JsonObject webPanel;
        if (config.has("web_panel")) {
            webPanel = config.getAsJsonObject("web_panel");
        } else {
            webPanel = new JsonObject();
            config.add("web_panel", webPanel);
        }
        webPanel.addProperty("enabled", enabled);
        saveConfig();
    }

    public long getWebPanelPollIntervalSeconds() {
        if (!config.has("web_panel")) {
            return 30;
        }
        JsonObject webPanel = config.getAsJsonObject("web_panel");
        if (!webPanel.has("poll_interval_seconds")) {
            return 30;
        }
        return webPanel.get("poll_interval_seconds").getAsLong();
    }

    public String getWebPanelUrl() {
        if (!config.has("web_panel")) {
            return "http://localhost:3000";
        }
        JsonObject webPanel = config.getAsJsonObject("web_panel");
        if (!webPanel.has("url")) {
            return "http://localhost:3000";
        }
        return webPanel.get("url").getAsString();
    }

    public String getWebPanelPollUrl() {
        if (!config.has("web_panel")) {
            return null;
        }
        JsonObject webPanel = config.getAsJsonObject("web_panel");
        if (!webPanel.has("poll_url")) {
            return null;
        }
        return webPanel.get("poll_url").getAsString();
    }

    public String getWebPanelClaimUrl() {
        if (!config.has("web_panel")) {
            return null;
        }
        JsonObject webPanel = config.getAsJsonObject("web_panel");
        if (!webPanel.has("claim_url")) {
            return null;
        }
        return webPanel.get("claim_url").getAsString();
    }
}
