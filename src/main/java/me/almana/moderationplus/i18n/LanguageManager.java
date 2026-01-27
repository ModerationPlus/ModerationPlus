package me.almana.moderationplus.i18n;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.Collections;
import java.util.Set;

public class LanguageManager {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final String LANG_DIR = "mods/data/moderationplus/lang";
    private static final String FALLBACK_LOCALE = "en_us";

    // Locale -> Key -> Translation
    private volatile Map<String, Map<String, String>> translations = new ConcurrentHashMap<>();
    // Player Locale Cache: UUID -> Optional<Locale>
    private final Map<java.util.UUID, java.util.Optional<String>> localeCache = new ConcurrentHashMap<>();
    private String defaultLocale = FALLBACK_LOCALE;
    private me.almana.moderationplus.storage.StorageManager storageManager;

    public void init(String configuredDefaultLocale) {
        loadLanguageFiles();
        
        if (configuredDefaultLocale != null && translations.containsKey(configuredDefaultLocale)) {
            this.defaultLocale = configuredDefaultLocale;
            logger.at(Level.INFO).log("[Lang] Default = %s", defaultLocale);
        } else {
            if (configuredDefaultLocale != null) {
                logger.at(Level.WARNING).log("[Lang] Invalid default locale '%s', falling back to %s", 
                    configuredDefaultLocale, FALLBACK_LOCALE);
            }
            this.defaultLocale = FALLBACK_LOCALE;
            logger.at(Level.INFO).log("[Lang] Default = %s", defaultLocale);
        }
    }

    // Set StorageManager reference
    public void setStorageManager(me.almana.moderationplus.storage.StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    // Load all language files
    private void loadLanguageFiles() {
        saveDefaultLanguageFile();

        File langDir = new File(LANG_DIR);
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            logger.at(Level.WARNING).log("[Lang] No language files found in %s", LANG_DIR);
            return;
        }

        for (File file : files) {
            String locale = file.getName().replace(".json", "");
            loadLanguageFile(locale, file);
        }
    }

    private void saveDefaultLanguageFile() {
        try {
            File langDir = new File(LANG_DIR);
            if (!langDir.exists()) {
                langDir.mkdirs();
            }

            File enUsFile = new File(langDir, "en_us.json");
            if (!enUsFile.exists()) {
                try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("lang/en_us.json")) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, enUsFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        logger.at(Level.INFO).log("[Lang] Extracted default en_us.json");
                    } else {
                        logger.at(Level.WARNING).log("[Lang] Default en_us.json not found in JAR");
                    }
                }
            }
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("[Lang] Failed to extract default language file");
        }
    }

    // Load single language file
    private void loadLanguageFile(String locale, File file) {
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                logger.at(Level.WARNING).log("[Lang] Invalid JSON in %s, skipping", file.getName());
                return;
            }

            Map<String, String> localeMap = new HashMap<>();
            json.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                    localeMap.put(entry.getKey(), entry.getValue().getAsString());
                }
            });

            translations.put(locale, localeMap);
            logger.at(Level.INFO).log("[Lang] Loaded %s", locale);
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("[Lang] Failed to load %s, skipping", file.getName());
        }
    }

    // Translate with fallback chain
    public String translate(String key, String locale, Map<String, String> params) {
        if (key == null || key.isEmpty()) {
            logger.at(Level.FINE).log("[Lang] Translate called with empty key");
            return "";
        }
        
        if (locale == null || locale.isEmpty()) {
             locale = defaultLocale;
        }

        String translation = resolveTranslation(key, locale);
        
        if (params != null && !params.isEmpty()) {
            translation = replacePlaceholders(translation, params);
        }

        return translation;
    }

    // No-params convenience method
    public String translate(String key, String locale) {
        return translate(key, locale, null);
    }

    // Resolve with fallback chain
    private String resolveTranslation(String key, String locale) {
        // Try requested locale
        String translation = getTranslation(key, locale);
        if (translation != null) {
            return translation;
        }

        // Try default locale
        if (!defaultLocale.equals(locale)) {
            translation = getTranslation(key, defaultLocale);
            if (translation != null) {
                return translation;
            }
        }

        // Try fallback locale
        if (!FALLBACK_LOCALE.equals(locale) && !FALLBACK_LOCALE.equals(defaultLocale)) {
            translation = getTranslation(key, FALLBACK_LOCALE);
            if (translation != null) {
                return translation;
            }
        }

        // Return key itself
        return key;
    }

    // Get from specific locale
    private String getTranslation(String key, String locale) {
        Map<String, String> localeMap = translations.get(locale);
        if (localeMap == null) {
            // Diagnostic: Log strictly at debug level to avoid spam
            logger.at(Level.FINE).log("[Lang] Missing locale file for %s", locale);
            return null;
        }
        String val = localeMap.get(key);
        if (val == null) {
             logger.at(Level.FINE).log("[Lang] Missing key %s in %s", key, locale);
        }
        return val;
    }

    // Named placeholder replacement
    private String replacePlaceholders(String text, Map<String, String> params) {
        String result = text;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, entry.getValue());
            }
        }
        return result;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public Set<String> getAvailableLocales() {
        return Collections.unmodifiableSet(translations.keySet());
    }

    // Translate for specific player with locale fallback
    public String translateForPlayer(String key, java.util.UUID playerUuid, Map<String, String> params) {
        if (key == null || key.isEmpty()) {
            return key;
        }

        if (playerUuid == null) {
            return translate(key, defaultLocale, params);
        }

        // Resolution order: player locale -> default -> en_us -> key
        String playerLocale = null;

        if (storageManager != null) {
            // Check cache first
            if (localeCache.containsKey(playerUuid)) {
                java.util.Optional<String> cached = localeCache.get(playerUuid);
                if (cached.isPresent()) {
                    playerLocale = cached.get();
                }
            } else {
                // Read-through to DB
                java.util.Optional<String> localeOpt = storageManager.getPlayerLocale(playerUuid);
                localeCache.put(playerUuid, localeOpt);
                if (localeOpt.isPresent()) {
                    playerLocale = localeOpt.get();
                }
            }
        }

        // Use player locale if set
        String locale = (playerLocale != null && !playerLocale.isEmpty()) ? playerLocale : defaultLocale;
        
        return translate(key, locale, params);
    }

    // No-params convenience for player
    public String translateForPlayer(String key, java.util.UUID playerUuid) {
        return translateForPlayer(key, playerUuid, null);
    }

    public com.hypixel.hytale.server.core.Message translateToMessage(String key, java.util.UUID uuid, Map<String, String> params) {
        String text = translateForPlayer(key, uuid, params);
        return me.almana.moderationplus.utils.ColorUtils.parse(text);
    }

    public com.hypixel.hytale.server.core.Message translateToMessage(String key, java.util.UUID uuid) {
        return translateToMessage(key, uuid, null);
    }

    // Write-through caching with validation
    public void setPlayerLocale(java.util.UUID uuid, String locale) {
        if (uuid == null) return;
        
        // Validation: Normalize and check against available
        if (locale == null || locale.isBlank()) {
             logger.at(Level.INFO).log("[Lang] Attempted to set empty locale for %s", uuid);
             return; 
        }
        
        String normalized = locale.trim().toLowerCase(java.util.Locale.ROOT);
        
        if (!translations.containsKey(normalized)) {
             logger.at(Level.INFO).log("[Lang] Rejected invalid locale '%s' for %s", normalized, uuid);
             return;
        }

        if (storageManager != null) {
            storageManager.setPlayerLocale(uuid, normalized);
            localeCache.put(uuid, java.util.Optional.of(normalized));
        }
    }

    public void resetPlayerLocale(java.util.UUID uuid) {
        if (storageManager != null) {
            storageManager.setPlayerLocale(uuid, null);
            localeCache.put(uuid, java.util.Optional.empty());
        }
    }

    public void clearCache(java.util.UUID uuid) {
        localeCache.remove(uuid);
    }

    public void clearAllCaches() {
        localeCache.clear();
    }
    
    // Hot Reload
    public void reload() {
        logger.at(Level.INFO).log("[Lang] Reloading languages...");
        
        File langDir = new File(LANG_DIR);
        if (!langDir.exists()) {
             return;
        }

        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return;
        }

        Map<String, Map<String, String>> newTranslations = new ConcurrentHashMap<>();

        for (File file : files) {
            String locale = file.getName().replace(".json", "");
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    Map<String, String> localeMap = new HashMap<>();
                    json.entrySet().forEach(entry -> {
                        if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                            localeMap.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    });
                    newTranslations.put(locale, localeMap);
                    logger.at(Level.INFO).log("[Lang] Reloaded %s", locale);
                }
            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log("[Lang] Failed to reload %s", file.getName());
            }
        }
        
        // Atomic swap
        this.translations = newTranslations;
        // Optionally clear cache if keys drastically change, or keep strictly player preferences
        // We keep player cache as it stores "locale name preferences" (e.g. "fr"), not "translations".
        // If "fr" file is deleted, resolution logic handles fallback safely.
        
        logger.at(Level.INFO).log("[Lang] Reload complete.");
    }
}
