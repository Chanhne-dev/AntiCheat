package chanhne.AntiCheat.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import chanhne.AntiCheat.AntiCheatPlugin;

public class BlockPlaceListener implements Listener {

    private static final double MAX_REACH = 5.2D;
    private final AntiCheatPlugin plugin;

    public BlockPlaceListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {

        Player player = event.getPlayer();
        Block against = event.getBlockAgainst();

        // Kiểm tra khoảng cách tới block được click
        if (!checkReach(player, against)) {
            event.setCancelled(true);
            return;
        }

        // TODO:
        // Sau này thêm các check Scaffold tại đây
        // Ví dụ:
        // - Góc nhìn tới BlockFace
        // - Eagle
        // - SafeWalk
        // - Tower
    }

    /**
     * Kiểm tra khoảng cách từ mắt người chơi tới block được click.
     */
    private boolean checkReach(Player player, Block block) {

        if (player == null || block == null) return false;

        Location eye = player.getEyeLocation();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        double distanceSquared = eye.distanceSquared(center);

        if (distanceSquared > MAX_REACH * MAX_REACH) {
            plugin.getLogger().fine(String.format("[Place-Reach] %s %.2f blocks", player.getName(), Math.sqrt(distanceSquared)));
            return false;
        }

        return true;
    }
}