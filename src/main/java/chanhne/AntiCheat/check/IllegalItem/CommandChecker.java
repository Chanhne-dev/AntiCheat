package chanhne.AntiCheat.check.IllegalItem;

import java.util.Locale;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import chanhne.AntiCheat.Mainplugin;

public class CommandChecker implements Listener {

    private final Mainplugin plugin;

    public CommandChecker(Mainplugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.shouldBypass(event.getPlayer())) return;

        String msg = event.getMessage().toLowerCase(Locale.ROOT);
        if (!msg.startsWith("/setblock")
                && !msg.startsWith("/fill")
                && !msg.startsWith("/item")
                && !msg.startsWith("/give")) {
            return;
        }

        for (String banned : plugin.getConfigManager().getBannedItems()) {
            String material = banned.toLowerCase(Locale.ROOT);
            if (material.equals("spawner_egg")) {
                if (msg.contains("_spawn_egg")) {
                    event.setCancelled(true);
                    return;
                }
            } else if (msg.contains(material.toLowerCase(Locale.ROOT))) {
                event.setCancelled(true);
                return;
            }
        }
    }
}