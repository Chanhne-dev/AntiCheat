package chanhne.AntiCheat.listener;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.util.HighestSolidBlock;

public class AntiCheatListener implements Listener {

    private final AntiCheatPlugin plugin;

    public AntiCheatListener(AntiCheatPlugin plugin) {
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
}