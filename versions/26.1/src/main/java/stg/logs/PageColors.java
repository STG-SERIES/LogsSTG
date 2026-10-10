package stg.logs;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfiguration;

final class PageColors {
    private static final Pattern HEX = Pattern.compile("(?i)#(?:[0-9a-f]{6}|[0-9a-f]{3})");

    private final String info;
    private final String warn;
    private final String error;
    private final String text;
    private final String background;

    private PageColors(String info, String warn, String error, String text, String background) {
        this.info = info;
        this.warn = warn;
        this.error = error;
        this.text = text;
        this.background = background;
    }

    static PageColors from(FileConfiguration config, Logger logger) {
        return new PageColors(
                hex(config, "colors.info", "#C8C8C8", logger),
                hex(config, "colors.warn", "#E6B84D", logger),
                hex(config, "colors.error", "#FF6B6B", logger),
                hex(config, "colors.text", "#D0D0D0", logger),
                hex(config, "colors.background", "#000000", logger));
    }

    String json() {
        return "{\"info\":\"" + info
                + "\",\"warn\":\"" + warn
                + "\",\"error\":\"" + error
                + "\",\"text\":\"" + text
                + "\",\"background\":\"" + background
                + "\"}";
    }

    byte[] render(byte[] template) {
        String page = new String(template, StandardCharsets.UTF_8)
                .replace("__INFO__", info)
                .replace("__WARN__", warn)
                .replace("__ERROR__", error)
                .replace("__TEXT__", text)
                .replace("__BG__", background);
        return page.getBytes(StandardCharsets.UTF_8);
    }

    byte[] event() {
        return ("event: theme\ndata: " + json() + "\n\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String hex(FileConfiguration config, String path, String fallback, Logger logger) {
        String raw = config.getString(path, fallback);
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (!HEX.matcher(value).matches()) {
            logger.warning("Invalid hex for " + path + " (" + value + "). Using " + fallback + ".");
            return fallback;
        }
        if (value.length() == 4) {
            char red = value.charAt(1);
            char green = value.charAt(2);
            char blue = value.charAt(3);
            value = "#" + red + red + green + green + blue + blue;
        }
        return "#" + value.substring(1).toUpperCase(Locale.ROOT);
    }
}
