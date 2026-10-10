package stg.logs;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

final class LogTap extends AbstractAppender {
    static final String NAME = "LogsSTG";
    private static final int MAX_CHARS = 16_000;
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-?]*[ -/]*[@-~]");

    private static final PatternLayout PREFIXED = layout("[%d{HH:mm:ss} %level]: [%logger] %msg{nolookups}%n%xEx{full}");
    private static final PatternLayout PLAIN = layout("[%d{HH:mm:ss} %level]: %msg{nolookups}%n%xEx{full}");

    private final LogHub hub;

    private LogTap(LogHub hub) {
        super(NAME, null, PREFIXED, false, Property.EMPTY_ARRAY);
        this.hub = hub;
    }

    static LogTap attach(LogHub hub) {
        LogTap tap = new LogTap(hub);
        tap.start();
        LoggerContext context = context();
        Configuration configuration = context.getConfiguration();
        LoggerConfig root = root(configuration);
        root.removeAppender(NAME);
        configuration.addAppender(tap);
        root.addAppender(tap, Level.ALL, null);
        context.updateLoggers();
        return tap;
    }

    void detach() {
        LoggerContext context = context();
        Configuration configuration = context.getConfiguration();
        root(configuration).removeAppender(NAME);
        context.updateLoggers();
        stop();
    }

    @Override
    public void append(LogEvent event) {
        if (!isStarted() || event == null) {
            return;
        }
        PatternLayout layout = prefix(event.getLoggerName()) ? PREFIXED : PLAIN;
        Object rendered = layout.toSerializable(event);
        if (rendered == null) {
            return;
        }
        String text = stripSection(ANSI.matcher(rendered.toString()).replaceAll(""));
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '\n') {
            end--;
        }
        if (end == 0) {
            return;
        }
        if (end > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
        } else if (end != text.length()) {
            text = text.substring(0, end);
        }
        hub.publish(text);
    }

    private static PatternLayout layout(String pattern) {
        return PatternLayout.newBuilder()
                .withPattern(pattern)
                .withCharset(StandardCharsets.UTF_8)
                .build();
    }

    /**
     * Same names Paper's console leaves unprefixed: root, Minecraft, Mojang, and a few plugins
     * that log without their own name.
     */
    private static boolean prefix(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) {
            return false;
        }
        if (loggerName.equals("Minecraft")
                || loggerName.startsWith("Minecraft.")
                || loggerName.startsWith("net.minecraft.")
                || loggerName.startsWith("com.mojang.")
                || loggerName.startsWith("com.sk89q.")
                || loggerName.startsWith("ru.tehkode.")) {
            return false;
        }
        return true;
    }

    private static String stripSection(String text) {
        if (text.indexOf('§') < 0) {
            return text;
        }
        StringBuilder plain = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            plain.append(c);
        }
        return plain.toString();
    }

    private static LoggerContext context() {
        return (LoggerContext) LogManager.getContext(LogManager.class.getClassLoader(), false);
    }

    private static LoggerConfig root(Configuration configuration) {
        return configuration.getRootLogger();
    }
}
