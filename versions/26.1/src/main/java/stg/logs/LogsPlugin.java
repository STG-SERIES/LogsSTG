package stg.logs;

import java.net.InetAddress;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class LogsPlugin extends JavaPlugin {
    private LogHub hub;
    private LogTap tap;
    private LocalConsole console;
    private volatile boolean running;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String host = getConfig().getString("host", "127.0.0.1");
        int port = getConfig().getInt("port", 8765);
        int history = Math.max(50, Math.min(5000, getConfig().getInt("history", 1000)));
        if (host == null || host.isBlank() || port < 1 || port > 65535) {
            getLogger().severe("Invalid host or port in config.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        hub = new LogHub(history);
        running = true;
        try {
            tap = LogTap.attach(hub);
            console = LocalConsole.start(this, hub, host.trim(), port);
        } catch (Exception e) {
            running = false;
            getLogger().log(Level.SEVERE, "Could not open the log port", e);
            detachTap();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        warnIfExposed(host.trim());
        getLogger().info("Live logs at http://" + host.trim() + ":" + port + "/");
    }

    @Override
    public void onDisable() {
        running = false;
        detachTap();
        if (console != null) {
            console.stop();
            console = null;
        }
        hub = null;
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
