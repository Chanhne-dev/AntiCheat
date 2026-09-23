package chanhne.AntiCheat.check.NoraTweaks;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.util.DetectionHelper;
import chanhne.AntiCheat.util.ViolationTracker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Phát hiện module "WindChargeJump" (NoraTweaks) và tương tự.
 *
 * LỊCH SỬ - TẠI SAO KHÔNG DÙNG "ĐỘ TRỄ NÉM->NHẢY ĐỀU ĐẶN" NỮA:
 * Bản trước đo độ trễ giữa lúc ném Wind Charge và lúc nhảy, nghi vấn nếu độ
 * trễ gần như giống hệt nhau qua nhiều lần liên tiếp. Thực tế test cho thấy
 * người chơi thật spam thủ công (đặc biệt nếu giữ phím nhảy - kỹ thuật "auto
 * jump" hợp lệ của chính client vanilla) cũng tạo ra độ trễ gần như không đổi,
 * vì tick game vốn đã lượng tử hoá theo bước 50ms - gây báo nhầm.
 *
 * CÁCH MỚI - GIỚI HẠN VẬT LÝ CỨNG VỀ ĐỘ CAO:
 * Thay vì đoán "có vẻ máy móc hay không", đo trực tiếp KẾT QUẢ VẬT LÝ: độ cao
 * thực sự đạt được sau khi ném Wind Charge xuống chân để tự đẩy lên. Wind
 * Charge thật (dù ném bằng tay hay bởi cheat) đều tuân theo cùng 1 công thức
 * nổ/knockback của vanilla - độ cao 1 cú đẩy hợp lệ bị giới hạn vật lý rõ ràng
 * (theo quan sát thực tế: ~5 block). Nếu đo được độ cao vượt xa mức đó (ví dụ
 * ~12 block) trong cùng 1 lần bay lên sau khi ném, đó không còn là do thao tác
 * tay nhanh/chậm nữa mà là dấu hiệu có thứ gì đó khuếch đại lực đẩy - không
 * quan tâm cheat làm điều đó bằng cách nào (spoof velocity, chồng nhiều vụ nổ
 * bất khả thi về mặt thao tác tay, v.v.).
 *
 * Cách đo: nghe {@link ProjectileLaunchEvent} với thực thể là {@link WindCharge}
 * do người chơi ném (chỉ tính nếu đang nhìn xuống đủ dốc - đúng kiểu ném tự đẩy,
 * không tính ném ngang vào kẻ địch). Khi đó bắt đầu 1 "phiên bay" - theo dõi Y
 * mỗi tick cho tới khi chạm đất, ghi nhận đỉnh cao nhất đạt được. Nhiều lần ném
 * liên tiếp trong cùng 1 phiên (chưa kịp chạm đất) đều tính gộp vào cùng 1 phiên,
 * vì cheat có thể chồng nhiều vụ nổ để đẩy cao hơn.
 */
public class AntiWindChargeJumpCheck implements Listener {

    private final Mainplugin plugin;
    private final ConfigManager config;
    private final ViolationTracker tracker = new ViolationTracker();
    private final Map<UUID, JumpSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();

    private static final class JumpSession {
        boolean active = false;
        double startY;
        double peakY;
        int ticksElapsed = 0;
    }

    public AntiWindChargeJumpCheck(Mainplugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        endSession(uuid);
        sessions.remove(uuid);
        tracker.reset(uuid);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof WindCharge windCharge)) return;

        ProjectileSource shooter = windCharge.getShooter();
        if (!(shooter instanceof Player thrower)) return;
        if (plugin.shouldBypass(thrower)) return;
        if (!config.isAntiWindChargeJumpEnabled()) return;

        Location throwerLoc = thrower.getLocation();

        // Bukkit pitch: dương = đang nhìn xuống, âm = nhìn lên (khớp quy ước xRot
        // vanilla mà WindChargeJump.java dùng) - chỉ tính nếu đủ dốc, đúng kiểu
        // ném tự đẩy lên, tránh tính oan lúc ném ngang vào kẻ địch bình thường
        float pitch = throwerLoc.getPitch();
        double minPitch = config.getAntiWindChargeJumpMinPitch();
        if (pitch < minPitch) return;

        UUID uuid = thrower.getUniqueId();
        JumpSession session = sessions.computeIfAbsent(uuid, k -> new JumpSession());

        if (!session.active) {
            // Bắt đầu 1 phiên bay mới
            double currentY = throwerLoc.getY();
            session.active = true;
            session.startY = currentY;
            session.peakY = currentY;
            session.ticksElapsed = 0;
            startSessionTask(thrower, uuid);
        }
        // Nếu phiên đang active (chưa kịp chạm đất) -> để nguyên, lần ném này
        // tự động được gộp vào cùng phiên qua task đang chạy, không cần làm gì thêm
    }

    private void startSessionTask(Player player, UUID uuid) {
        ScheduledTask existing = tasks.remove(uuid);
        if (existing != null) existing.cancel();

        ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> sessionTick(player, uuid),
                () -> endSession(uuid), // retired callback: entity không còn hợp lệ
                1L,
                1L
        );
        tasks.put(uuid, task);
    }

    private void sessionTick(Player player, UUID uuid) {
        JumpSession session = sessions.get(uuid);
        if (session == null || !session.active) {
            endSession(uuid);
            return;
        }

        if (!player.isOnline()) {
            endSession(uuid);
            return;
        }

        session.ticksElapsed++;
        Location loc = player.getLocation();
        double currentY = loc.getY();
        session.peakY = Math.max(session.peakY, currentY);

        if (player.isOnGround()) {
            // Đã chạm đất trở lại - kết thúc phiên, đánh giá độ cao đạt được
            double heightGained = session.peakY - session.startY;
            double maxLegitHeight = config.getAntiWindChargeJumpMaxHeightGain();

            if (config.isAntiWindChargeJumpDebug()) {
                plugin.getLogger().info("[AntiWindChargeJump-DEBUG] " + player.getName() + " kết thúc phiên - heightGained=" + String.format("%.2f", heightGained) + " (ngưỡng=" + maxLegitHeight + ")");
            }

            if (heightGained > maxLegitHeight) {
                raiseViolation(player, uuid, heightGained);
            }

            endSession(uuid);
            return;
        }

        int maxSessionTicks = config.getAntiWindChargeJumpMaxSessionTicks();
        if (session.ticksElapsed >= maxSessionTicks) {
            // Quá lâu chưa chạm đất (ví dụ đang bơi/rơi xuống hố sâu) - bỏ qua,
            // không đủ tin cậy để kết luận, không tính vi phạm
            if (config.isAntiWindChargeJumpDebug()) {
                plugin.getLogger().info("[AntiWindChargeJump-DEBUG] " + player.getName() + " phiên quá thời hạn (" + maxSessionTicks + " tick) - bỏ qua");
            }
            endSession(uuid);
        }
    }

    private void endSession(UUID uuid) {
        ScheduledTask task = tasks.remove(uuid);
        if (task != null) task.cancel();

        JumpSession session = sessions.get(uuid);
        if (session != null) {
            session.active = false;
        }
    }

    private void raiseViolation(Player player, UUID uuid, double heightGained) {
        int count = tracker.increase(uuid);
        int threshold = config.getAntiWindChargeJumpViolationThreshold();

        if (count >= threshold) {
            DetectionHelper.log(plugin, config, "AntiWindChargeJump",
                    "Player: " + player.getName(),
                    "HeightGained: " + String.format("%.2f", heightGained));

            DetectionHelper.notifyAdmins(plugin, config, "warning.antiwindchargejump-detected", player.getName());

            DetectionHelper.kick(plugin, config,
                    uuid,
                    player.getName(),
                    config.isAntiWindChargeJumpKickOnDetect(),
                    config.getAntiWindChargeJumpKickReason(),
                    "AntiWindChargeJump");

            DetectionHelper.ban(plugin, config,
                    uuid,
                    player.getName(),
                    config.isAntiWindChargeJumpBanEnabled(),
                    config.getAntiWindChargeJumpOffenseType(),
                    "AntiWindChargeJump",
                    "warning.antiwindchargejump-banned");

            tracker.reset(uuid);
        }
    }
}