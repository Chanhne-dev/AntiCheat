package chanhne.AntiCheat.check.TrouserStreak;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.util.DetectionHelper;
import chanhne.AntiCheat.util.ViolationTracker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

public class TPFlyCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final ConfigManager config;
    private final ViolationTracker tracker = new ViolationTracker();
    private final Map<UUID, Boolean> previousOnGround = new ConcurrentHashMap<>();

    public TPFlyCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.shouldBypass(player)) return;
        if (!config.isTpFlyCheckEnabled()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Chỉ xoay đầu -> bỏ qua
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        double dist3d = from.distance(to);
        double verticalDelta = to.getY() - from.getY();
        boolean onGround = player.isOnGround();
        boolean currentOnGround = player.isOnGround();
        boolean wasOnGround = previousOnGround.getOrDefault(player.getUniqueId(), currentOnGround);

        boolean suspect = false;

        // 1. Khoảng cách dịch chuyển vượt ngưỡng
        if (dist3d >= config.getTpFlyMaxTeleportDistance()) {
            suspect = true;
        }

        // 2. Đang trên mặt đất nhưng bị đẩy lên quá cao
        if (onGround && verticalDelta > config.getTpFlyMaxVerticalDelta()) {
            suspect = true;
        }

        // 3. Đang trên mặt đất nhưng bị tụt xuống quá nhanh (teleport xuống)
        if (onGround && verticalDelta < -config.getTpFlyMaxVerticalDelta()) {
            suspect = true;
        }

        // 4. Không trên mặt đất nhưng bị đẩy lên bất thường (bay lên từ giữa không trung)
        if (!onGround && verticalDelta > config.getTpFlyMaxVerticalDeltaAir()) {
            suspect = true;
        }

        // 5. Không trên mặt đất nhưng bị tụt xuống quá nhanh (teleport xuống)
        if (!wasOnGround && currentOnGround) {
            previousOnGround.put(player.getUniqueId(), currentOnGround);
            return; // bỏ qua toàn bộ, không tăng violation
        }

        previousOnGround.put(player.getUniqueId(), currentOnGround);
        if (!suspect) return;

        // Lọc trạng thái hợp lệ (bay, lượn, xe)
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) {
            return;
        }

        // Vận tốc gần 0 => không bị đẩy bởi piston, slime, tnt, ...
        Vector velocity = player.getVelocity();
        double minVel = config.getTpFlyMinVelocityToBypass();
        if (velocity.lengthSquared() > minVel * minVel) {
            return; // vận tốc đủ lớn → có khả năng là va chạm hợp lệ
        }

        // Phát hiện vi phạm
        int count = tracker.increase(player.getUniqueId());
        int threshold = config.getTpFlyViolationThreshold();

        if (count >= threshold) {
            // Log console
            DetectionHelper.log(plugin, config, "TPFly",
                    "Player: " + player.getName(),
                    "Dist: " + String.format("%.2f", dist3d),
                    "Vertical: " + String.format("%.2f", verticalDelta),
                    "OnGround: " + onGround,
                    "From: " + from,
                    "To: " + to);

            // Thông báo admin
            DetectionHelper.notifyAdmins(plugin, config, "anticheat.notify.tpfly", player.getName());

            // Kick
            DetectionHelper.kick(plugin, config,
                    player.getUniqueId(),
                    player.getName(),
                    config.isTpFlyKickOnDetect(),
                    config.getTpFlyKickReason(),
                    "TPFly");

            // Ban
            DetectionHelper.ban(plugin, config,
                    player.getUniqueId(),
                    player.getName(),
                    config.isTpFlyBanEnabled(),
                    config.getTpFlyOffenseType(),
                    "TPFly",
                    "anticheat.ban.tpfly");

            // Reset sau khi xử lý
            tracker.reset(player.getUniqueId());
        }
    }
}