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

        // Chỉ teleport nếu player vừa bị kick/ban bởi anti-cheat
        if (plugin.hasPendingSafeTeleport(uuid)) {
            Location loc = player.getLocation();
            // Kiểm tra vị trí nguy hiểm (có thể luôn teleport để an toàn)
            if (loc.getY() < 0 || !loc.getBlock().getType().isSolid()) {
                Location safe = HighestSolidBlock.get(loc.getWorld(), loc.getBlockX(), loc.getBlockZ());
                player.teleportAsync(safe);
            }
            // Xóa flag sau khi đã xử lý
            plugin.removePendingSafeTeleport(uuid);
        }
    }
}