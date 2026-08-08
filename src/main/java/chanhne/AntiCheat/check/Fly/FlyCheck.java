package chanhne.AntiCheat.check.Fly;

import chanhne.AntiCheat.AntiCheatPlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Module Fly - gộp 2 hướng phát hiện Fly hack, được tách thành các file riêng
 * trong cùng package để dễ bảo trì:
 *
 * - {@link TeleportFlyDetector}: bắt dịch chuyển/teleport đột ngột (dựa trên
 *   PlayerMoveEvent) - phù hợp với các cheat kiểu "chớp" vị trí (TPFly).
 *
 * - {@link HoverFlyDetector}: bắt hành vi bay giữ nguyên độ cao / bay lên liên
 *   tục kéo dài nhiều tick (tick-based, độc lập với PlayerMoveEvent) - phù hợp
 *   với cheat kiểu "negate gravity" (Meteor Client Flight module và tương tự).
 *
 * Lớp này chỉ đóng vai trò Listener + điều phối, KHÔNG chứa logic phát hiện -
 * toàn bộ logic nằm trong 2 detector ở trên để tách biệt rõ trách nhiệm.
 */
public class FlyCheck implements Listener {

    private final TeleportFlyDetector teleportDetector;
    private final HoverFlyDetector hoverDetector;

    public FlyCheck(AntiCheatPlugin plugin) {
        this.teleportDetector = new TeleportFlyDetector(plugin);
        this.hoverDetector = new HoverFlyDetector(plugin);
    }

    /**
     * Gọi hàm này trong onEnable() SAU KHI registerEvents, để HoverFlyDetector
     * bắt kịp những người chơi đã online sẵn (ví dụ khi /reload plugin trong
     * lúc server đang chạy).
     */
    public void startForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hoverDetector.onJoin(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        teleportDetector.onPlayerMove(event);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        hoverDetector.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        hoverDetector.onQuit(player.getUniqueId());
        teleportDetector.onQuit(player.getUniqueId());
    }
}