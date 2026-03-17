package ua.grigo.frontiercontracts.util;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bukkit.ChatColor;

public final class TextUtil {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.##");

    private TextUtil() {
    }

    public static String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    public static List<String> colorize(List<String> lines) {
        List<String> output = new ArrayList<>(lines.size());
        for (String line : lines) {
            output.add(colorize(line));
        }
        return output;
    }

    public static String applyPlaceholders(String input, Map<String, String> placeholders) {
        String value = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        return value;
    }

    public static List<String> applyPlaceholders(List<String> input, Map<String, String> placeholders) {
        List<String> output = new ArrayList<>(input.size());
        for (String line : input) {
            output.add(applyPlaceholders(line, placeholders));
        }
        return output;
    }

    public static String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }

    public static List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("\n"));
    }

    public static String prettyToken(String raw) {
        String[] parts = raw.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    public static String formatDuration(long totalSeconds) {
        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return hours + "г " + minutes + "хв";
        }
        if (minutes > 0) {
            return minutes + "хв " + seconds + "с";
        }
        return seconds + "с";
    }

    public static String formatMoney(double money) {
        return MONEY_FORMAT.format(money);
    }

    public static String normalizePlain(String raw) {
        if (raw == null) {
            return "";
        }
        return ChatColor.stripColor(colorize(raw))
            .replaceAll("\\s+", "")
            .toLowerCase(java.util.Locale.ROOT)
            .trim();
    }
}
