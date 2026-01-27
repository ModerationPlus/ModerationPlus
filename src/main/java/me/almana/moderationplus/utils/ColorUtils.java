package me.almana.moderationplus.utils;

import com.hypixel.hytale.server.core.Message;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {
    private static final Map<Character, Color> COLOR_MAP = new HashMap<>();
    private static final Pattern CODE_PATTERN = Pattern.compile("&([0-9a-fA-F])");

    static {
        COLOR_MAP.put('0', Color.BLACK);
        COLOR_MAP.put('1', new Color(0, 0, 170)); // Dark Blue
        COLOR_MAP.put('2', new Color(0, 170, 0)); // Dark Green
        COLOR_MAP.put('3', new Color(0, 170, 170)); // Dark Aqua
        COLOR_MAP.put('4', new Color(170, 0, 0)); // Dark Red
        COLOR_MAP.put('5', new Color(170, 0, 170)); // Dark Purple
        COLOR_MAP.put('6', new Color(255, 170, 0)); // Gold
        COLOR_MAP.put('7', new Color(170, 170, 170)); // Gray
        COLOR_MAP.put('8', new Color(85, 85, 85)); // Dark Gray
        COLOR_MAP.put('9', new Color(85, 85, 255)); // Blue
        COLOR_MAP.put('a', new Color(85, 255, 85)); // Green
        COLOR_MAP.put('b', new Color(85, 255, 255)); // Aqua
        COLOR_MAP.put('c', new Color(255, 85, 85)); // Red
        COLOR_MAP.put('d', new Color(255, 85, 255)); // Light Purple
        COLOR_MAP.put('e', new Color(255, 255, 85)); // Yellow
        COLOR_MAP.put('f', Color.WHITE);
    }

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})|<#([A-Fa-f0-9]{6})>");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    public static Message parse(String text) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }

        // 1. Check for Hex Colors (Last one wins approach)
        // Format: &#RRGGBB or <#RRGGBB>
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        Color finalColor = null;
        
        // Find the last hex code
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            if (hex == null) hex = hexMatcher.group(2);
            if (hex != null) {
                try {
                    finalColor = Color.decode("#" + hex);
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. Check for Legacy Colors (Last one wins, overriding hex if it appears later?)
        // To properly implement "Last One Wins" mixed, we should scan linearly, but Regex is easier for now.
        // We will assume if Hex is present, it's intended. If Legacy is present properly, it might override.
        // For simplicity and performance, this implementation prioritizes Hex if found, otherwise Legacy.
        // To be strictly correct per string position, we'd need a different parser. 
        // Given typically users use one color per line in this atomic system:
        
        if (finalColor == null) {
            Matcher legacyMatcher = LEGACY_PATTERN.matcher(text);
            while (legacyMatcher.find()) {
                char code = legacyMatcher.group(1).toLowerCase().charAt(0);
                if (COLOR_MAP.containsKey(code)) {
                    finalColor = COLOR_MAP.get(code);
                }
            }
        }

        // Strip codes
        String content = HEX_PATTERN.matcher(text).replaceAll("");
        content = LEGACY_PATTERN.matcher(content).replaceAll("");

        Message msg = Message.raw(content);
        if (finalColor != null) {
            msg.color(finalColor);
        }
        return msg;
    }
}
