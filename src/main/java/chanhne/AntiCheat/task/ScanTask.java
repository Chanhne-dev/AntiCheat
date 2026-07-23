package chanhne.AntiCheat.task;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.check.ViolationResult;
import chanhne.AntiCheat.enforcement.EnforcementHandler;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ScanTask {

    private final AntiCheatPlugin plugin;
    private BukkitTask task;
    private final EnforcementHandler enforcementHandler;

    public ScanTask(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.enforcementHandler = new EnforcementHandler(plugin);
    }

    public void start() {
        int interval = plugin.getConfigManager().getScanInterval();
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(plugin, scheduledTask -> {
                    scanPlayer(player);
                }, null);
            }}, 0, interval * 50L, TimeUnit.MILLISECONDS
        );

        plugin.getLogger().info("Scan task khởi động, interval: " + interval + " tick");
    }


    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
    }

    private void scanPlayer(Player player) {
        List<ViolationResult> violations = plugin.getItemChecker().checkInventory(player);
        if (!violations.isEmpty()) {
            enforcementHandler.handleViolations(player, violations);
        }
    }
}