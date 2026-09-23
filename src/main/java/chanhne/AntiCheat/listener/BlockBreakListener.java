package chanhne.AntiCheat.listener;

import java.util.EnumSet;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Vector;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.check.TrouserStreak.LineOfSightCheck;

public class BlockBreakListener implements Listener {

    private final Mainplugin plugin;

    private final LineOfSightCheck lineOfSightCheck;
    private static final double MAX_REACH = 5.2D;
    private static final double MIN_DOT = 0.94D;

    public BlockBreakListener(Mainplugin plugin) {
        this.plugin = plugin;
        this.lineOfSightCheck = new LineOfSightCheck(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (plugin.shouldBypass(player)) return;

        if (player.getGameMode() == GameMode.CREATIVE) return;

        if (!CHECK_BLOCKS.contains(block.getType())) return;

        if (!checkReach(player, block)) {
            event.setCancelled(true);
            return;
        }

        if (!lineOfSightCheck.check(player, block)) {
            event.setCancelled(true);
            return;
        }

        if (!checkRotation(player, block)) {
            event.setCancelled(true);
            return;
        }

        // TODO
        // RotationManager.startOrUpdate(player, block);
        // Sau này detector InstaMineNuker sẽ bắt đầu thu thập rotation tại đây.
    }

    private boolean checkReach(Player player, Block block) {
        Location eye = player.getEyeLocation();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        return eye.distanceSquared(center) <= MAX_REACH * MAX_REACH;
    }

    private boolean checkRotation(Player player, Block block) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector toBlock = block.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector()).normalize();

        return look.dot(toBlock) >= MIN_DOT;
    }

    private static final EnumSet<Material> CHECK_BLOCKS = EnumSet.of(
        Material.STONE,
        Material.COBBLESTONE,
        Material.DEEPSLATE,
        Material.COBBLED_DEEPSLATE,
        Material.DIRT,
        Material.GRASS_BLOCK,
        Material.SAND,
        Material.RED_SAND,
        Material.GRAVEL,
        Material.NETHERRACK,
        Material.END_STONE,

        Material.COAL_ORE,
        Material.IRON_ORE,
        Material.GOLD_ORE,
        Material.DIAMOND_ORE,
        Material.EMERALD_ORE,
        Material.REDSTONE_ORE,
        Material.LAPIS_ORE,
        Material.COPPER_ORE,

        Material.DEEPSLATE_COAL_ORE,
        Material.DEEPSLATE_IRON_ORE,
        Material.DEEPSLATE_GOLD_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
        Material.DEEPSLATE_EMERALD_ORE,
        Material.DEEPSLATE_REDSTONE_ORE,
        Material.DEEPSLATE_LAPIS_ORE,
        Material.DEEPSLATE_COPPER_ORE
    );
}