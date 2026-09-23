package chanhne.AntiCheat.task;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.check.IllegalItem.EnforcementHandler;
import chanhne.AntiCheat.check.IllegalItem.ViolationResult;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ScanTask {

    private final Mainplugin plugin;
    private final EnforcementHandler enforcementHandler;
    private ScheduledTask task;

    public ScanTask(Mainplugin plugin) {
        this.plugin = plugin;
        this.enforcementHandler = new EnforcementHandler(plugin);
    }

    public void start() {
        int interval = plugin.getConfigManager().getScanInterval();
        long periodMs = interval * 50L;

        // Folia's AsyncScheduler throws IllegalArgumentException if
        // initialDelay is 0 (delay/period must be > 0) - use the same
        // period as the initial delay instead of 0.
        task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player == null) continue;
                player.getScheduler().run(plugin, entityTask -> {
                    scanPlayer(player);
                }, null);
            }
        }, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }


    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
    }

    private void scanPlayer(Player player) {
        List<ViolationResult> violations = plugin.getItemChecker().checkInventory(player);
        if (!violations.isEmpty()) {
            enforcementHandler.handleViolations(player, violations);
        }
    }
}