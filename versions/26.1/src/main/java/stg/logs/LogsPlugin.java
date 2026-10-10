package stg.logs;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class LogsPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private LogHub hub;
    private LogTap tap;
    private LocalConsole console;
    private volatile boolean running;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        ensureColors();
        if (getCommand("logsstg") != null) {
            getCommand("logsstg").setExecutor(this);
            getCommand("logsstg").setTabCompleter(this);
        }
        if (!openConsole(true)) {
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        running = false;
        detachTap();
        stopConsole();
        hub = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("restart")) {
            if (!sender.hasPermission("logsstg.restart")) {
                sender.sendMessage("You can't reload LogsSTG.");
                return true;
            }
            sender.sendMessage(reloadConsole() ? "LogsSTG reloaded." : "LogsSTG could not reload. Check the console.");
            return true;
        }
        sender.sendMessage("Usage: /logsstg restart");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "restart".startsWith(args[0].toLowerCase(Locale.ROOT)) && sender.hasPermission("logsstg.restart")) {
            return List.of("restart");
        }
        return Collections.emptyList();
    }

    boolean running() {
        return running;
    }

    /**
     * @return false when the line is empty or the plugin is stopping
     */
    boolean runConsole(String raw) {
        LogHub target = hub;
        if (raw == null || raw.isEmpty() || target == null || !running) {
            return false;
        }
        String command = raw.charAt(0) == '/' ? raw.substring(1).trim() : raw.trim();
        if (command.isEmpty()) {
            return false;
        }
        target.publish("> " + command);
        try {
            getServer().getScheduler().runTask(this, () -> {
                if (!isEnabled()) {
                    return;
                }
                try {
                    getServer().dispatchCommand(getServer().getConsoleSender(), command);
                } catch (RuntimeException e) {
                    String message = e.getMessage();
                    target.publish("! " + (message == null || message.isBlank() ? "command failed" : message));
                }
            });
        } catch (RuntimeException e) {
            target.publish("! command failed");
            return false;
        }
        return true;
    }

    private boolean reloadConsole() {
        reloadConfig();
        ensureColors();
        return openConsole(false);
    }

    private void ensureColors() {
        if (getConfig().isSet("colors.info")) {
            return;
        }
        Path file = getDataFolder().toPath().resolve("config.yml");
        String block = """

                # Hex colors. #RGB and #RRGGBB both work.
                # Apply edits with /logsstg restart
                colors:
                  info: "#C8C8C8"
                  warn: "#E6B84D"
                  error: "#FF6B6B"
                  text: "#D0D0D0"
                  background: "#000000"
                """;
        try {
            Files.writeString(file, block.stripIndent(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            getLogger().warning("Could not add colors to config.yml. Defaults will be used.");
            return;
        }
        reloadConfig();
    }

    private boolean openConsole(boolean firstStart) {
        String host = getConfig().getString("host", "127.0.0.1");
        int port = getConfig().getInt("port", 8765);
        int history = Math.max(50, Math.min(5000, getConfig().getInt("history", 1000)));
        if (host == null || host.isBlank() || port < 1 || port > 65535) {
            getLogger().severe("Invalid host or port in config.yml");
            return false;
        }
        host = host.trim();
        PageColors colors = PageColors.from(getConfig(), getLogger());
        if (hub == null) {
            hub = new LogHub(history);
        } else {
            hub.setCapacity(history);
        }
        running = true;
        LocalConsole previous = console;
        console = null;
        if (previous != null) {
            previous.stop();
        }
        Exception last = null;
        for (int attempt = 0; attempt < 25; attempt++) {
            try {
                if (tap == null) {
                    tap = LogTap.attach(hub);
                }
                console = LocalConsole.start(this, hub, host, port, colors);
                last = null;
                break;
            } catch (Exception e) {
                last = e;
                if (firstStart || attempt == 24) {
                    break;
                }
                try {
                    Thread.sleep(40);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (console == null) {
            getLogger().log(Level.SEVERE, "Could not open the log port", last);
            if (firstStart) {
                running = false;
                detachTap();
            }
            return false;
        }
        warnIfExposed(host);
        getLogger().info("Live logs at http://" + host + ":" + port + "/");
        return true;
    }

    private void stopConsole() {
        if (console != null) {
            console.stop();
            console = null;
        }
    }

    private void detachTap() {
        if (tap != null) {
            tap.detach();
            tap = null;
        }
    }

    private void warnIfExposed(String host) {
        try {
            if (!InetAddress.getByName(host).isLoopbackAddress()) {
                getLogger().warning("Bound outside loopback. Anyone who can reach this port can run console commands.");
            }
        } catch (Exception e) {
            getLogger().warning("Bound address could not be checked. Treat this port as full console access.");
        }
    }
}
