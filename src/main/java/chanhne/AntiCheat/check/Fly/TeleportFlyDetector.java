package chanhne.AntiCheat.check.Fly;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.util.DetectionHelper;
import chanhne.AntiCheat.util.ViolationTracker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * Bắt dịch chuyển/teleport đột ngột dựa trên PlayerMoveEvent - phù hợp với các
 * cheat kiểu "chớp" vị trí (TPFly): dịch chuyển 3D vượt ngưỡng, hoặc đẩy lên/
 * xuống bất thường theo trục Y so với trạng thái on-ground.
 *
 * (Chuyển từ TPFlyCheck.java cũ sang, giữ nguyên toàn bộ logic - chỉ đổi package
 * và không tự implement Listener nữa, được FlyCheck gọi vào thay.)
 */
class TeleportFlyDetector {

    private final AntiCheatPlugin plugin;
    private final ConfigManager config;
    private final ViolationTracker tracker = new ViolationTracker();
    private final Map<UUID, Boolean> previousOnGround = new ConcurrentHashMap<>();

    TeleportFlyDetector(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.shouldBypass(player)) return;
        if (!config.isFlyCheckEnabled()) return;

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

        // 5. Vừa tiếp đất sau khi rơi/bay -> bỏ qua toàn bộ tick này, không tăng violation
        // (tránh báo nhầm lúc rơi tự do chạm đất)
        if (!wasOnGround && currentOnGround) {
            previousOnGround.put(player.getUniqueId(), currentOnGround);
            return;
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
            DetectionHelper.notifyAdmins(plugin, config, "warning.tpfly-detected", player.getName());

            // Kick
            DetectionHelper.kick(plugin, config,
                    player.getUniqueId(),
                    player.getName(),
                    config.isFlyKickOnDetect(),
                    config.getTpFlyKickReason(),
                    "TPFly");

            // Ban
            DetectionHelper.ban(plugin, config,
                    player.getUniqueId(),
                    player.getName(),
                    config.isFlyBanEnabled(),
                    config.getTpFlyOffenseType(),
                    "TPFly",
                    "warning.tpfly-banned");

            // Reset sau khi xử lý
            tracker.reset(player.getUniqueId());
        }
    }

    /**
     * Dọn state khi player rời server - trước đây TPFlyCheck không có bước này,
     * khiến map previousOnGround/tracker rò rỉ dần theo thời gian (mỗi UUID từng
     * join sẽ ở lại map mãi mãi). Nhân dịp gộp module, sửa luôn.
     */
    void onQuit(UUID uuid) {
        previousOnGround.remove(uuid);
        tracker.reset(uuid);
    }
}