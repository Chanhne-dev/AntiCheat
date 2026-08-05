package chanhne.AntiCheat.util;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.messages.Message;
import chanhne.offplugin.api.ChanhOffAPI;
import chanhne.offplugin.api.ChanhOffAPIProvider;
import net.kyori.adventure.text.Component;

public final class DetectionHelper {

    private DetectionHelper() {}

    public static void log(AntiCheatPlugin plugin, ConfigManager cfg, String title, String... lines) {
        if (!cfg.isLogToConsole()) return;

        plugin.getLogger().warning("=== " + title + " ===");
        for (String line : lines) {
            plugin.getLogger().warning(line);
        }
        plugin.getLogger().warning("==============================");
    }

    public static void notifyAdmins(AntiCheatPlugin plugin, ConfigManager cfg, String messageKey, String player) {
        if (!cfg.isNotifyAdmins()) return;

        Component component = Message.component(messageKey, "player", player);

        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission(AntiCheatPlugin.ADMIN_PERMISSION)) {
                    admin.sendMessage(component);
                }
            }
        });
    }

    public static void discord(AntiCheatPlugin plugin, String title, String description, int color) {
        plugin.getDiscordWebhook().send(title, description, color);
    }

    public static void kick(AntiCheatPlugin plugin, ConfigManager cfg, UUID uuid, String player, boolean enabled, String reason, String module) {
        if (!enabled) return;

        ChanhOffAPI api = ChanhOffAPIProvider.get();
        if (api == null) return;

        // Đánh dấu cần teleport an toàn khi join lại
        plugin.addPendingSafeTeleport(uuid);

        api.kickPlayer(uuid, reason, "AntiCheat").thenAccept(result -> {
                if (!result.success() && cfg.isLogToConsole()) {
                    plugin.getLogger().warning("Kick thất bại (" + module + ") cho " + player + ": " + result.message());
                }});
    }

    public static void ban(AntiCheatPlugin plugin, ConfigManager cfg, UUID uuid, String player, boolean enabled, String offenseType, String module, String adminMessageKey) {
        if (!enabled) return;

        ChanhOffAPI api = ChanhOffAPIProvider.get();
        if (api == null) return;

        // Đánh dấu cần teleport an toàn khi join lại
        plugin.addPendingSafeTeleport(uuid);

        api.banPlayer(uuid, player, offenseType, "AntiCheat").thenAccept(result -> {
                if (result.success()) {
                    plugin.getLogger().info(  "Đã ban " + player + " (" + module + "), lần " + result.strike());
                    notifyAdmins(plugin, cfg, adminMessageKey, player);
                } else if (cfg.isLogToConsole()) {
                    plugin.getLogger().warning( "Ban thất bại (" + module + "): " + result.message());
                }
        });
    }
}