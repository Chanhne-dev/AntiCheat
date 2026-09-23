package chanhne.AntiCheat.check.TrouserStreak;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import chanhne.AntiCheat.Mainplugin;

public class LineOfSightCheck {

    private final Mainplugin plugin;

    // Nên đồng bộ với ReachCheck
    private static final double MAX_DISTANCE = 5.2D;

    public LineOfSightCheck(Mainplugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Kiểm tra block đầu tiên người chơi nhìn thấy
     * có đúng là block đang đào hay không.
     */
    public boolean check(Player player, Block targetBlock) {

        if (player == null || targetBlock == null) return false;

        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), MAX_DISTANCE, FluidCollisionMode.NEVER, true);

        if (result == null) {
            plugin.getLogger().fine("[LOS] RayTrace returned null.");
            return false;
        }

        Block hitBlock = result.getHitBlock();

        if (hitBlock == null) {
            plugin.getLogger().fine("[LOS] No block hit.");
            return false;
        }

        if (!hitBlock.equals(targetBlock)) {
            plugin.getLogger().fine(String.format("[LOS] %s expected=%s hit=%s", player.getName(), targetBlock.getType(), hitBlock.getType()));
            return false;
        }

        return true;
    }
}