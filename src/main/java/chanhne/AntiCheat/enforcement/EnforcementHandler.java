package chanhne.AntiCheat.enforcement;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.check.ViolationResult;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.messages.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import io.papermc.paper.ban.BanListType;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class EnforcementHandler {

    private final AntiCheatPlugin plugin;

    public EnforcementHandler(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Xử lý toàn bộ vi phạm: cảnh báo, xóa item, ban
     */
    public void handleViolations(Player player, List<ViolationResult> violations) {
        if (player == null || plugin.shouldBypass(player)) {
            return;
        }

        if (violations.isEmpty()) {
            return;
        }

        ConfigManager cfg = plugin.getConfigManager();

        // 1. Thông báo cho player về từng vi phạm
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDiscordWebhook().send(
                "🚨 Anti Cheat Ban",
                """
                **Player:** %s
                **UUID:** %s
                **Reason:** Illegal Item
                **Duration:** %d minute(s)
                """
                .formatted(
                    player.getName(),
                    player.getUniqueId(),
                    cfg.getBanDurationMinutes()
                ),
                0xFF0000
            )
        );
        for (ViolationResult violation : violations) {
            String message = buildViolationMessage(violation, cfg);
            player.sendMessage(message);
        }

        // 2. Xóa toàn bộ inventory
        clearInventory(player);
        Message.send(player, "warning.inventory-cleared");

        // 3. Log ra console
        if (cfg.isLogToConsole()) {
            plugin.getLogger().warning("=== VI PHẠM PHÁT HIỆN ===");
            plugin.getLogger().warning("Người chơi: " + player.getName() + " (" + player.getUniqueId() + ")");
            for (ViolationResult v : violations) {
                plugin.getLogger().warning("  >> " + v.toString());
            }
            plugin.getLogger().warning("========================");
        }

        // 4. Thông báo cho admin
        if (cfg.isNotifyAdmins()) {
            ViolationResult first = violations.get(0);

            Component comp = Message.component("warning.admin-notify",
                "player", player.getName(),
                "item", first.getTargetName());
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("anticheat.admin")) {
                    admin.sendMessage(comp);
                    if (violations.size() > 1) {
                        admin.sendMessage(cfg.getPrefix() + "§e Tổng cộng §f" + violations.size() + "§e vi phạm!");
                    }
                }
            }
        }

        // 5. Ban player
        banPlayer(player, cfg.getBanDurationMinutes());
    }

    /**
     * Xóa toàn bộ inventory của player
     */
    public void clearInventory(Player player) {
        if (plugin.shouldBypass(player)) {
            return;
        }
        player.getInventory().clear();
        // Xóa cả armor và offhand
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    /**
     * Ban player trong một khoảng thời gian
     */
    private void banPlayer(Player player, int minutes) {
        ConfigManager cfg = plugin.getConfigManager();
        Date expiry = Date.from(Instant.now().plus(Duration.ofMinutes(minutes)));

        Bukkit.getBanList(BanListType.PROFILE).addBan(
            player.getName(),
            "§cItem bất hợp pháp! Ban " + minutes + " phút.",
            expiry,
            "Anti Cheat"
        );

        // Kick player với thông báo
        String kickMsg = Message.get("ban.player-banned","player", player.getName(),"duration", String.valueOf(minutes));
        player.kick(LegacyComponentSerializer.legacyAmpersand().deserialize(kickMsg));

        // 2. 📢 Broadcast toàn server
        Component broadcast = Message.component("ban.player-banned-broadcast",
            "player", player.getName());
        Bukkit.broadcast(broadcast);

        // Kick phải chạy trên main thread
        Bukkit.getScheduler().runTask(plugin, () -> player.kick(Component.text(kickMsg)));

        if (cfg.isLogToConsole()) {
            plugin.getLogger().warning("BAN: " + player.getName() + " bị ban " + minutes + " phút do item bất hợp pháp.");
        }
    }

    /**
     * Tạo thông báo vi phạm dựa trên loại
     */
    private String buildViolationMessage(ViolationResult violation, ConfigManager cfg) {

        switch (violation.getType()) {
            case BANNED_ITEM:
            case INVALID_ENCHANT_ITEM:
                return Message.get("illegal-item-found",
                    "item", violation.getTargetName(),
                    "slot", String.valueOf(violation.getSlot()));
            case ILLEGAL_POTION:
                return Message.get("illegal-potion",
                    "item", violation.getTargetName(),
                    "slot", String.valueOf(violation.getSlot()),
                    "level", String.valueOf(violation.getLevel()));
            case ENCHANT_LEVEL_TOO_HIGH:
            case INVALID_ENCHANT_COMBO:
                return Message.get("illegal-enchant",
                    "item", violation.getTargetName(),
                    "slot", String.valueOf(violation.getSlot()),
                    "level", String.valueOf(violation.getLevel()),
                    "enchant", violation.getEnchantName() != null ? violation.getEnchantName() : "?");
            default:
                return Message.get("illegal-item-found",
                    "item", violation.getTargetName(),
                    "slot", String.valueOf(violation.getSlot()));
        }
    }
}