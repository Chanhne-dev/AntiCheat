package chanhne.AntiCheat.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.check.ViolationResult;
import chanhne.AntiCheat.check.ViolationType;
import chanhne.AntiCheat.enforcement.EnforcementHandler;
import chanhne.AntiCheat.messages.Message;

import java.util.List;
import java.util.Locale;

public class PlayerInventoryListener implements Listener {

    private final AntiCheatPlugin plugin;
    private final EnforcementHandler enforcementHandler;

    public PlayerInventoryListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.enforcementHandler = new EnforcementHandler(plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.shouldBypass(player)) return;

        String[] args = event.getMessage().toUpperCase(Locale.ROOT).split("\\s+");
        for (String arg : args) {
            if (!plugin.getItemChecker().isIllegalMaterial(arg)) continue;

            event.setCancelled(true);
            ViolationResult violation = new ViolationResult(
                    ViolationType.BANNED_ITEM,
                    new ItemStack(Material.BARRIER),
                    -1,
                    "Illegal command: " + event.getMessage(),
                    arg,
                    null,
                    0
            );

            plugin.getEnforcementHandler().handleViolations(player, List.of(violation));

            return;
        }
    }

    // Kiểm tra khi player tham gia server
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.shouldBypass(player)) {
            return;
        }

        // Delay 20 tick để inventory load xong
        // không hỗ trợ Folia
        // plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
        //     List<ViolationResult> violations = plugin.getItemChecker().checkInventory(player);
        //     if (!violations.isEmpty()) {
        //         enforcementHandler.handleViolations(player, violations);
        //     }
        // }, 20L);

        // Hỗ trợ Folia
        player.getScheduler().runDelayed(plugin, task -> {
            List<ViolationResult> violations = plugin.getItemChecker().checkInventory(player);
            if (!violations.isEmpty()) {
                enforcementHandler.handleViolations(player, violations);
            }
        }, null, 20L);
    }

    // Kiểm tra khi player nhặt item
    @EventHandler(priority = EventPriority.HIGH)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.shouldBypass(player)) return;

        ItemStack item = event.getItem().getItemStack();
        List<ViolationResult> violations = plugin.getItemChecker().checkItem(item, -1);

        if (!violations.isEmpty()) {
            event.setCancelled(true);
            // Thông báo nhưng không ban khi nhặt (chỉ ngăn nhặt)
            for (ViolationResult v : violations) {
                Message.send(player, "warning.illegal-item-pickup", "item", v.getTargetName());
            }
        }
    }

    // Kiểm tra khi player click trong inventory
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.shouldBypass(player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // Kiểm tra item đang được di chuyển
        if (cursor != null) {
            List<ViolationResult> violations = plugin.getItemChecker().checkItem(cursor, event.getSlot());
            if (!violations.isEmpty()) {
                event.setCancelled(true);
                enforcementHandler.handleViolations(player, violations);
            }
        }

        if (current != null) {
            List<ViolationResult> violations = plugin.getItemChecker().checkItem(current, event.getSlot());
            if (!violations.isEmpty()) {
                event.setCancelled(true);
                enforcementHandler.handleViolations(player, violations);
            }
        }
    }

    // Kiểm tra khi player chuyển item ra tay
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (plugin.shouldBypass(player)) return;

        ItemStack offHandItem = event.getOffHandItem();
        ItemStack mainHandItem = event.getMainHandItem();

        List<ViolationResult> violations = new java.util.ArrayList<>();
        if (offHandItem != null) violations.addAll(plugin.getItemChecker().checkItem(offHandItem, -1));
        if (mainHandItem != null) violations.addAll(plugin.getItemChecker().checkItem(mainHandItem, -1));

        if (!violations.isEmpty()) {
            event.setCancelled(true);
            enforcementHandler.handleViolations(player, violations);
        }
    }

    // Kiểm tra khi đóng inventory (có thể nhận item từ chest)
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (plugin.shouldBypass(player)) return;

        // Delay 1 tick để inventory cập nhật
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            List<ViolationResult> violations = plugin.getItemChecker().checkInventory(player);
            if (!violations.isEmpty()) {
                enforcementHandler.handleViolations(player, violations);
            }
        }, null, 1L);
    }
}