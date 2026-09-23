package chanhne.AntiCheat.check.IllegalItem;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.messages.Message;
import chanhne.AntiCheat.util.ViolationTracker;
import dev.chanhne.betterban.api.ChanhOffAPI;
import dev.chanhne.betterban.api.ChanhOffAPIProvider;
import net.kyori.adventure.text.Component;

import java.util.List;

public class EnforcementHandler {

    private final Mainplugin plugin;
    private final ViolationTracker tracker = new ViolationTracker();

    public EnforcementHandler(Mainplugin plugin) {
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
                if (admin == null) continue;
                if (admin.hasPermission("anticheat.admin")) {
                    admin.sendMessage(comp);
                    if (violations.size() > 1) {
                        admin.sendMessage(cfg.getPrefix() + "§e Tổng cộng §f" + violations.size() + "§e vi phạm!");
                    }
                }
            }
        }

        // 5. Ban player - chỉ khi bật trong config VÀ đã đạt ngưỡng vi phạm,
        // giống mọi module check khác (movement, mace, boat, fly...) - tránh
        // 1 false positive từ ItemChecker (heuristic, ví dụ so tên "strong")
        // dẫn tới ban ngay lập tức không thể tắt qua config.
        if (cfg.isItemBanEnabled()) {
            int count = tracker.increase(player.getUniqueId());
            if (count >= cfg.getItemViolationThreshold()) {
                ChanhOffAPI api = ChanhOffAPIProvider.get();
                if (api != null) {
                    api.banPlayer(player.getUniqueId(), player.getName(), "IllegalItem", "AntiCheat");
                }
                tracker.reset(player.getUniqueId());
            }
        }
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