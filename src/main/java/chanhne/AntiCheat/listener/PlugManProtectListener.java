package chanhne.AntiCheat.listener;

import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.config.ConfigManager;

/**
 * Chặn PlugMan (unload/reload/disable/restart...) khi nhắm vào chính plugin AntiCheat.
 * Áp dụng cho cả người chơi (PlayerCommandPreprocessEvent) lẫn console
 * (ServerCommandEvent, bao gồm cả RCON).
 */
public class PlugManProtectListener implements Listener {

    private static final String DENY_MESSAGE = "§cKhông thể dùng PlugMan để thao tác với plugin AntiCheat.";

    private final Mainplugin plugin;

    public PlugManProtectListener(Mainplugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!isPlugManTargetingSelf(event.getMessage())) return;

        event.setCancelled(true);
        deny(event.getPlayer(), event.getPlayer().getName(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (!isPlugManTargetingSelf(event.getCommand())) return;

        event.setCancelled(true);
        deny(event.getSender(), event.getSender().getName(), event.getCommand());
    }

    private void deny(CommandSender sender, String name, String command) {
        sender.sendMessage(DENY_MESSAGE);

        ConfigManager cfg = plugin.getConfigManager();
        if (cfg.isLogToConsole()) {
            plugin.getLogger().warning("Đã chặn lệnh PlugMan nhắm vào AntiCheat từ " + name + ": " + command);
        }
    }

    /**
     * Ví dụ bị chặn: "/plugman unload AntiCheat", "plm reload *", "/plugman:plugman restart all".
     */
    private boolean isPlugManTargetingSelf(String raw) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isPlugmanProtectEnabled()) return false;
        if (raw == null) return false;

        String[] args = raw.trim().split("\\s+");
        if (args.length < 3) return false;

        String label = args[0].toLowerCase(Locale.ROOT);
        if (label.startsWith("/")) {
            label = label.substring(1);
        }
        // Bỏ namespace: plugman:plugman -> plugman
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (!cfg.getPlugmanCommands().contains(label)) return false;

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!cfg.getPlugmanActions().contains(action)) return false;

        String self = plugin.getName();
        for (int i = 2; i < args.length; i++) {
            String target = args[i];
            if (target.equalsIgnoreCase(self) || target.equals("*") || target.equalsIgnoreCase("all")) {
                return true;
            }
        }
        return false;
    }
}
