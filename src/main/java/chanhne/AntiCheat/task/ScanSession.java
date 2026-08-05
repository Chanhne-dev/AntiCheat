package chanhne.AntiCheat.task;

import org.bukkit.entity.Player;

import chanhne.AntiCheat.AntiCheatPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class ScanSession {

    private final Player player;
    private final AntiCheatPlugin plugin;

    private ScheduledTask task;

    private int currentTick = 0;
    private final int maxTicks;
    private Float lastYaw;
    private Float lastPitch;
    private final int seconds;

    public ScanSession(AntiCheatPlugin plugin, Player player, int seconds) {
        this.plugin = plugin;
        this.player = player;
        this.seconds = seconds;
        this.maxTicks = seconds * 20;
    }

    public void start() {
        plugin.getLogger().info("Start checking " + player.getName() + " for " + seconds + " seconds.");

        task = player.getScheduler().runAtFixedRate(plugin,
            scheduledTask -> {
                if (!player.isOnline()) {
                    stop();
                    return;
                }

                float yaw = player.getLocation().getYaw();
                float pitch = player.getLocation().getPitch();
                if (lastYaw == null || yaw != lastYaw || pitch != lastPitch) {
                    plugin.getLogger().info(String.format( "[%d] %.1fs %s yaw=%.2f pitch=%.2f", System.currentTimeMillis(), currentTick / 20.0, player.getName(), yaw, pitch));
                    lastYaw = yaw;
                    lastPitch = pitch;
                }
                currentTick++;
                if (currentTick >= maxTicks) stop();
            }, () -> stop(), 1L, 1L
        );
    }

    public void stop() {

        if (task != null) {
            task.cancel();
            task = null;
        }

        plugin.getLogger().info("Finished checking " + player.getName());
    }
}