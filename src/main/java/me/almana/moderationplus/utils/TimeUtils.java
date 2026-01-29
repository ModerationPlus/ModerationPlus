package me.almana.moderationplus.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Locale;

public class TimeUtils {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([mhdw])");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    public static String formatTime(long timestamp, String locale) {
        return DATE_FORMAT.withLocale(Locale.forLanguageTag(locale)).format(Instant.ofEpochMilli(timestamp));
    }

    public static long parseDuration(String input) {
        Matcher matcher = DURATION_PATTERN.matcher(input.toLowerCase());
        long totalMillis = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "m" -> totalMillis += value * 60 * 1000;
                case "h" -> totalMillis += value * 60 * 60 * 1000;
                case "d" -> totalMillis += value * 24 * 60 * 60 * 1000;
                case "w" -> totalMillis += value * 7 * 24 * 60 * 60 * 1000;
            }
        }

        if (!found) {
            throw new IllegalArgumentException("Invalid duration format: " + input);
        }

        return totalMillis;
    }

    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0)
            sb.append(days).append("d ");
        if (hours % 24 > 0)
            sb.append(hours % 24).append("h ");
        if (minutes % 60 > 0)
            sb.append(minutes % 60).append("m ");
        if (seconds % 60 > 0 || sb.length() == 0)
            sb.append(seconds % 60).append("s");

        return sb.toString().trim();
    }
}
