package chanhne.AntiCheat.listener;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.util.DetectionHelper;
import chanhne.AntiCheat.util.HighestSolidBlock;

public class AntiCheatListener implements Listener {

    private final Mainplugin plugin;

    public AntiCheatListener(Mainplugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.hasPendingSafeTeleport(uuid)) return;
        plugin.removePendingSafeTeleport(uuid);
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            Location loc = player.getLocation();

            if (loc.getY() < 0 || !loc.getBlock().getType().isSolid()) {
                Location safe = HighestSolidBlock.get(loc.getWorld(), loc.getBlockX(), loc.getBlockZ());
                player.teleportAsync(safe);
            }
        }, null, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!event.getPlayer().isOp()) return;
        handleDeop(event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        handleDeop(event.getCommand());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedCommand(PlayerCommandPreprocessEvent event) {

        if (!plugin.getConfigManager().isBlockCommandEnabled()) return;
        String message = event.getMessage().trim();

        if (message.isEmpty()) return;
        String[] args = message.split("\\s+");
        String command = args[0].toLowerCase(Locale.ROOT);

        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (plugin.getConfigManager().getBlockedCommands().contains(command)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cLệnh này đã bị chặn.");
        }
    }

    private void handleDeop(String command) {
        String[] args = command.trim().split("\\s+");

        if (args.length < 2) return;

        // Hỗ trợ cả:
        // /deop Player
        // deop Player
        if (args[0].startsWith("/")) {
            args[0] = args[0].substring(1);
        }

        if (!args[0].equalsIgnoreCase("deop")) return;

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) return;

        // Folia: dùng scheduler của player
        target.getScheduler().runDelayed(plugin, task -> {
            if (!target.isOnline()) return;

            // Chỉ kick nếu DEOP thực sự thành công
            if (!target.isOp()) {
                DetectionHelper.kick(plugin, plugin.getConfigManager(), target.getUniqueId(), target.getName(), true, "§cBạn vừa bị DEOP!\n§eVui lòng đăng nhập lại để kích hoạt AntiCheat.", "DEOP-RELOG");
            }

        }, null, 1L);
    }
}