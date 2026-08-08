package chanhne.AntiCheat.check.MeteorClient;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.util.DetectionHelper;
import chanhne.AntiCheat.util.ViolationTracker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Phát hiện module "AntiVoid" (Meteor Client và tương tự): khi người chơi sắp
 * rơi xuống dưới ngưỡng void, cheat tự cấp vận tốc hất ngược lên (không phải
 * teleport phẳng) để tránh chết do sát thương void.
 *
 * DẤU HIỆU NHẬN DẠNG (rút ra từ dữ liệu movement-log thực tế):
 * Người chơi thật rơi vào vùng gần đáy world/void sẽ RƠI TIẾP rồi CHẾT do sát
 * thương void chỉ sau vài giây (không thể tự "nảy" lên lại). AntiVoid tạo ra
 * một chuỗi lặp lại kiểu PARABOL ngay tại ranh giới đó:
 *   - Rơi xuống: delta Y âm, độ lớn TĂNG DẦN đúng theo trọng lực
 *   - Đột ngột ĐẢO CHIỀU: delta Y chuyển dương với độ lớn nhảy vọt (không thể
 *     xảy ra tự nhiên khi đang lơ lửng giữa không trung - chỉ có thể do nhảy
 *     khi đứng đất, tiềm năng nổ/knockback 1 lần, hoặc hiệu ứng Levitation)
 *   - Bay lên rồi giảm tốc dần (delta dương giảm dần) - đúng vật lý parabol
 *     sau khi được "hất" 1 lực ban đầu, rồi lại rơi xuống -> LẶP LẠI chu kỳ
 *
 * Không dùng ngưỡng Y tuyệt đối cố định (mỗi world/map có đáy khác nhau) mà
 * tính TƯƠNG ĐỐI theo World#getMinHeight() để tự thích ứng mọi world.
 */
public class AntiVoidCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final ConfigManager config;
    private final ViolationTracker tracker = new ViolationTracker();
    private final Map<UUID, VoidState> states = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();

    private static final class VoidState {
        boolean hasLastY = false;
        double lastY = 0.0;
        boolean hasLastDelta = false;
        double lastDelta = 0.0;

        int bounceCount = 0;
        int ticksSinceFirstBounce = 0;

        void resetBounces() {
            bounceCount = 0;
            ticksSinceFirstBounce = 0;
        }
    }

    public AntiVoidCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    /**
     * Gọi trong onEnable() SAU KHI registerEvents, để bắt kịp người chơi đã
     * online sẵn (ví dụ khi /reload plugin trong lúc server đang chạy).
     */
    public void startForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            startTracking(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        startTracking(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopTracking(event.getPlayer().getUniqueId());
    }

    private void startTracking(Player player) {
        UUID uuid = player.getUniqueId();

        ScheduledTask existing = tasks.remove(uuid);
        if (existing != null) existing.cancel();

        states.put(uuid, new VoidState());

        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> tick(player), () -> stopTracking(uuid), 2L, 1L);
        tasks.put(uuid, task);
    }

    private void stopTracking(UUID uuid) {
        ScheduledTask task = tasks.remove(uuid);
        if (task != null) task.cancel();
        states.remove(uuid);
        tracker.reset(uuid);
    }

    private void tick(Player player) {
        if (!player.isOnline()) return;
        if (plugin.shouldBypass(player)) return;
        if (!config.isAntiVoidCheckEnabled()) return;

        UUID uuid = player.getUniqueId();
        VoidState state = states.get(uuid);
        if (state == null) return;

        World world = player.getWorld();
        double currentY = player.getLocation().getY();

        // Chỉ quan tâm khi Y nằm trong khoảng gần đáy world (vùng "gần void") -
        // tính tương đối theo min-height của world để tự thích ứng mọi world
        // (Overworld -64, Nether 0, world custom...) thay vì hardcode 1 số cố định
        double voidZoneTop = world.getMinHeight() + config.getAntiVoidZoneMargin();
        boolean inVoidZone = currentY <= voidZoneTop;

        if (!inVoidZone || player.isOnGround()) {
            state.hasLastY = true;
            state.lastY = currentY;
            state.hasLastDelta = false;
            state.resetBounces();
            return;
        }

        if (!state.hasLastY) {
            state.lastY = currentY;
            state.hasLastY = true;
            return;
        }

        double verticalDelta = currentY - state.lastY;
        state.lastY = currentY;

        // Loại trừ trạng thái hợp lệ có thể tạo ra chuyển hướng đột ngột thật sự:
        // Levitation (đẩy lên liên tục), SlowFalling (rơi rất chậm), đang bơi/ở
        // trong nước (lực đẩy nổi), lượn/bay/trong xe, hoặc đang bám leo trèo
        if (isExempt(player)) {
            state.hasLastDelta = false;
            state.resetBounces();
            return;
        }

        if (state.hasLastDelta) {
            double previousDelta = state.lastDelta;
            double bounceMinDelta = config.getAntiVoidBounceMinDelta();
            double bounceMinJump = config.getAntiVoidBounceMinDeltaJump();

            // Phát hiện 1 lần "nảy": đang rơi (delta âm) rồi đột ngột đảo chiều
            // sang dương với độ lớn đủ lớn - không thể xảy ra tự nhiên giữa
            // không trung nếu không có ngoại lực (mà đã loại trừ ở trên)
            boolean wasFalling = previousDelta < 0;
            boolean nowRising = verticalDelta >= bounceMinDelta;
            double deltaJump = verticalDelta - previousDelta;

            if (wasFalling && nowRising && deltaJump >= bounceMinJump) {
                state.bounceCount++;
                if (state.bounceCount == 1) {
                    state.ticksSinceFirstBounce = 0;
                }

                if (config.isAntiVoidDebug()) {
                    plugin.getLogger().info("[AntiVoid-DEBUG] " + player.getName() + " bounce #" + state.bounceCount+ " tại Y=" + String.format("%.3f", currentY)+ " (deltaJump=" + String.format("%.3f", deltaJump) + ")");
                }
            }
        }

        state.lastDelta = verticalDelta;
        state.hasLastDelta = true;

        if (state.bounceCount > 0) {
            state.ticksSinceFirstBounce++;

            int bounceWindowTicks = config.getAntiVoidBounceWindowTicks();
            if (state.ticksSinceFirstBounce > bounceWindowTicks) {
                // Quá thời hạn cửa sổ mà không đủ số lần nảy -> reset, tính lại từ đầu
                state.resetBounces();
            } else if (state.bounceCount >= config.getAntiVoidBounceCountThreshold()) {
                raiseViolation(player, uuid, state, currentY);
            }
        }
    }

    private void raiseViolation(Player player, UUID uuid, VoidState state, double currentY) {
        int count = tracker.increase(uuid);
        int threshold = config.getAntiVoidViolationThreshold();

        if (count >= threshold) {
            DetectionHelper.log(plugin, config, "AntiVoid", "Player: " + player.getName(), "World: " + player.getWorld().getName(), "BounceCount: " + state.bounceCount, "Y: " + String.format("%.5f", currentY));
            DetectionHelper.notifyAdmins(plugin, config, "warning.antivoid-detected", player.getName());
            DetectionHelper.kick(plugin, config, uuid, player.getName(), config.isAntiVoidKickOnDetect(), config.getAntiVoidKickReason(),"AntiVoid");
            DetectionHelper.ban(plugin, config, uuid, player.getName(), config.isAntiVoidBanEnabled(), config.getAntiVoidOffenseType(), "AntiVoid", "warning.antivoid-banned");

            tracker.reset(uuid);
            state.resetBounces();
        }
    }

    private boolean isExempt(Player player) {
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) return true;
        if (player.isSwimming() || player.isInWater()) return true;
        if (player.isRiptiding()) return true;
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) return true;
        return player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
    }
}