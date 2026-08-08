package chanhne.AntiCheat.check.Fly;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.util.DetectionHelper;
import chanhne.AntiCheat.util.ViolationTracker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Phát hiện Fly hack kiểu module "Flight" của Meteor Client.
 *
 * QUAN TRỌNG - LÝ DO DÙNG TICK-BASED THAY VÌ PlayerMoveEvent:
 * PlayerMoveEvent chỉ fire khi client thực sự GỬI gói di chuyển lên server.
 * Khi Meteor Flight bật "Anti Kick: Packet", nó cố tình giữ Y gần như đứng
 * yên tuyệt đối và chỉ gửi "position reminder" theo chu kỳ (mặc định ~1
 * gói/giây) - khiến client gần như không gửi gói di chuyển, kéo theo
 * PlayerMoveEvent gần như không fire (thực tế đo được chỉ ~2 lần / 4 giây bay).
 * Nếu detect dựa vào SỐ LẦN EVENT BẮN RA thì thuật toán streak sẽ không bao
 * giờ đạt ngưỡng, dù người chơi vẫn đang bay suốt thời gian đó.
 *
 * => Thay vào đó, check này chạy một task lặp mỗi tick (giống hệt cách
 * vanilla tự đếm floatingTickCount), tự lấy vị trí/on-ground của người chơi
 * theo chu kỳ cố định, HOÀN TOÀN ĐỘC LẬP với việc client có gửi gói hay không.
 * Dùng Folia EntityScheduler (player.getScheduler()) để đảm bảo an toàn luồng.
 *
 * 4 pattern độc lập, chỉ cần 1 pattern đạt ngưỡng là coi như nghi vấn:
 *
 * 1. SustainedAscend - delta Y dương liên tục quá lâu (bay lên chủ động).
 * 2. Hover - |delta Y| gần như 0 liên tục khi đang lơ lửng giữa không trung.
 * 3. PacketAntiKick (MicroFall) - chuỗi delta Y ÂM, gần như giống hệt nhau
 *    tuyệt đối qua nhiều tick liên tiếp - dấu hiệu đặc trưng của Meteor Flight
 *    khi bật "Anti Kick: Packet".
 * 4. JoinHover - bắt trường hợp người chơi JOIN/REJOIN trong lúc đang lơ lửng
 *    giữa không trung. Có loại trừ dựa trên player.getFallDistance() (số liệu
 *    vanilla tự tính, giữ nguyên qua thoát/vào lại) để không oan người chơi
 *    đang rơi tự do hợp lệ rồi thoát/vào lại giữa chừng.
 *
 * (Chuyển từ MeteorFlyCheck.java cũ sang, giữ nguyên toàn bộ logic - chỉ đổi
 * package/tên lớp và không tự implement Listener nữa, được FlyCheck gọi vào thay.)
 */
class HoverFlyDetector {

    private final AntiCheatPlugin plugin;
    private final ConfigManager config;
    private final ViolationTracker tracker = new ViolationTracker();
    private final Map<UUID, FlyState> states = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();

    private static final class FlyState {
        int airTicks = 0;
        int ascendStreak = 0;
        int hoverStreak = 0;
        int microFallStreak = 0;
        boolean hasLastY = false;
        double lastY = 0.0;

        // JoinHover: theo dõi 1 cửa sổ ngắn ngay sau khi join/rejoin.
        int ticksSinceJoin = 0;
        double joinY = 0.0;
        boolean joinWindowClean = true;
        boolean joinWindowEvaluated = false;

        void resetAll() {
            airTicks = 0;
            ascendStreak = 0;
            hoverStreak = 0;
            microFallStreak = 0;
            hasLastY = false;
        }
    }

    HoverFlyDetector(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    void onJoin(Player player) {
        startTracking(player);
    }

    void onQuit(UUID uuid) {
        stopTracking(uuid);
    }

    private void startTracking(Player player) {
        UUID uuid = player.getUniqueId();

        // Dọn sạch task cũ nếu có (phòng trường hợp quit/rejoin quá nhanh, ví dụ
        // ngay sau khi bị kick - lệnh kick gọi API bất đồng bộ nên có thể tới trễ
        // vài tick, khiến entry cũ trong map chưa kịp dọn khi player join lại)
        ScheduledTask existing = tasks.remove(uuid);
        if (existing != null) existing.cancel();

        states.put(uuid, new FlyState());

        ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> tick(player),
                () -> stopTracking(uuid), // retired callback: entity không còn hợp lệ (đã rời server)
                2L,  // delay ban đầu
                2L   // chạy mỗi 2 tick
        );
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
        if (plugin.shouldBypass(player)) {
            if (config.isMeteorFlyDebug()) {
                plugin.getLogger().info("[MeteorFly-DEBUG] " + player.getName() + " bị shouldBypass() chặn (OP/permission anticheat.admin)");
            }
            return;
        }
        if (!config.isFlyCheckEnabled()) return;

        UUID uuid = player.getUniqueId();
        FlyState state = states.get(uuid);
        if (state == null) {
            if (config.isMeteorFlyDebug()) {
                plugin.getLogger().warning("[MeteorFly-DEBUG] " + player.getName() + " KHÔNG có FlyState trong map - task đang chạy nhưng chưa startTracking() đúng cách!");
            }
            return;
        }

        Location loc = player.getLocation();
        double currentY = loc.getY();

        // Bỏ qua vùng void kỹ thuật RẤT SÂU dưới ngưỡng này (nếu bạn có khu vực như
        // vậy trên map và không muốn check ở đó). Mặc định cực thấp (-2032, sát đáy
        // world Paper) nên KHÔNG loại trừ khu vực chơi bình thường nào cả - check áp
        // dụng ở TOÀN BỘ độ cao trừ khi bạn tự chỉnh lại giá trị này trong config.
        double skipBelowY = config.getMeteorFlyCheckOnlyBelowY();
        if (currentY < skipBelowY) {
            if (config.isMeteorFlyDebug()) {
                plugin.getLogger().info("[MeteorFly-DEBUG] " + player.getName()
                        + " bỏ qua vì Y=" + String.format("%.2f", currentY) + " < " + skipBelowY);
            }
            state.resetAll();
            state.lastY = currentY;
            state.hasLastY = true;
            return;
        }

        boolean onGroundOrExempt = player.isOnGround() || isExempt(player);

        if (config.isMeteorFlyDebug()) {
            plugin.getLogger().info(String.format(
                    "[MeteorFly-DEBUG] %s | Y=%.4f | onGround=%b | exempt=%b | joinTicks=%d | airTicks=%d | ascend=%d | hover=%d | microfall=%d",
                    player.getName(), currentY, player.isOnGround(), isExempt(player),
                    state.ticksSinceJoin, state.airTicks, state.ascendStreak, state.hoverStreak, state.microFallStreak));
        }

        // ===== JoinHover: đánh giá cửa sổ ngắn ngay sau khi join/rejoin =====
        if (!state.joinWindowEvaluated) {
            if (state.ticksSinceJoin == 0) {
                state.joinY = currentY; // mốc Y ngay tick đầu tiên sau khi bắt đầu track

                // Loại trừ trường hợp người chơi đang RƠI TỰ DO HỢP LỆ rồi thoát game
                // giữa chừng, sau đó vào lại: dùng FallDistance vanilla (giữ nguyên
                // qua NBT khi thoát/vào lại, độc lập với client) làm bằng chứng.
                double fallDistance = player.getFallDistance();
                double bypassThreshold = config.getMeteorFlyJoinFallDistanceBypass();
                if (fallDistance >= bypassThreshold) {
                    if (config.isMeteorFlyDebug()) {
                        plugin.getLogger().info("[MeteorFly-DEBUG] " + player.getName()
                                + " bỏ qua JoinHover do FallDistance=" + String.format("%.3f", fallDistance)
                                + " (đang rơi tự do hợp lệ trước khi vào/thoát game)");
                    }
                    state.joinWindowClean = false;
                    state.joinWindowEvaluated = true;
                }
            }

            if (!state.joinWindowEvaluated) {
                if (onGroundOrExempt) {
                    state.joinWindowClean = false;
                    state.joinWindowEvaluated = true;
                } else {
                    state.ticksSinceJoin++;
                    int joinGraceTicks = config.getMeteorFlyJoinGraceTicks();

                    if (state.ticksSinceJoin >= joinGraceTicks) {
                        double fallDistance = state.joinY - currentY; // dương nếu đã rơi xuống
                        double maxFall = config.getMeteorFlyJoinMaxFallDistance();

                        if (state.joinWindowClean && fallDistance < maxFall) {
                            raiseViolation(player, uuid, state, "JoinHover", 0.0, currentY);
                        }
                        state.joinWindowEvaluated = true;
                    }
                }
            }
        }

        // ===== 3 pattern streak thông thường (Ascend / Hover / PacketAntiKick) =====
        if (onGroundOrExempt) {
            state.resetAll();
            state.lastY = currentY;
            state.hasLastY = true;
            return;
        }

        if (!state.hasLastY) {
            state.lastY = currentY;
            state.hasLastY = true;
            state.airTicks = 1;
            return;
        }

        double verticalDelta = currentY - state.lastY;
        state.lastY = currentY;
        state.airTicks++;

        updateStreaks(state, verticalDelta);

        int graceTicks = config.getMeteorFlyGraceTicks();
        if (state.airTicks <= graceTicks) {
            return;
        }

        boolean suspect = false;
        StringBuilder pattern = new StringBuilder();

        if (state.ascendStreak >= config.getMeteorFlyAscendTicksThreshold()) {
            suspect = true;
            pattern.append("SustainedAscend");
        }
        if (state.hoverStreak >= config.getMeteorFlyHoverTicksThreshold()) {
            suspect = true;
            if (pattern.length() > 0) pattern.append("+");
            pattern.append("Hover");
        }
        if (state.microFallStreak >= config.getMeteorFlyMicrofallTicksThreshold()) {
            suspect = true;
            if (pattern.length() > 0) pattern.append("+");
            pattern.append("PacketAntiKick");
        }

        if (!suspect) return;

        raiseViolation(player, uuid, state, pattern.toString(), verticalDelta, currentY);
    }

    private void raiseViolation(Player player, UUID uuid, FlyState state, String pattern, double verticalDelta, double currentY) {
        int count = tracker.increase(uuid);
        int threshold = config.getMeteorFlyViolationThreshold();

        if (count >= threshold) {
            DetectionHelper.log(plugin, config, "MeteorFly",
                    "Player: " + player.getName(),
                    "Pattern: " + pattern,
                    "VerticalDelta: " + String.format("%.5f", verticalDelta),
                    "AirTicks: " + state.airTicks,
                    "AscendStreak: " + state.ascendStreak,
                    "HoverStreak: " + state.hoverStreak,
                    "MicroFallStreak: " + state.microFallStreak,
                    "Y: " + String.format("%.5f", currentY));

            DetectionHelper.notifyAdmins(plugin, config, "warning.meteorfly-detected", player.getName());
            DetectionHelper.kick(plugin, config, uuid, player.getName(), config.isFlyKickOnDetect(), config.getMeteorFlyKickReason(), "MeteorFly");
            DetectionHelper.ban(plugin, config, uuid, player.getName(), config.isFlyBanEnabled(), config.getMeteorFlyOffenseType(), "MeteorFly", "warning.meteorfly-banned");

            tracker.reset(uuid);
            state.resetAll();
            state.lastY = currentY;
            state.hasLastY = true;
        }
    }

    private void updateStreaks(FlyState state, double verticalDelta) {
        double ascendMinDelta = config.getMeteorFlyAscendMinDelta();
        if (verticalDelta >= ascendMinDelta) {
            state.ascendStreak++;
        } else {
            state.ascendStreak = 0;
        }

        double hoverMaxDelta = config.getMeteorFlyHoverMaxDelta();
        if (Math.abs(verticalDelta) <= hoverMaxDelta) {
            state.hoverStreak++;
        } else {
            state.hoverStreak = 0;
        }

        double expected = config.getMeteorFlyMicrofallExpectedDelta();
        double tolerance = config.getMeteorFlyMicrofallTolerance();
        boolean matchesExpectedStep = Math.abs(verticalDelta - expected) <= tolerance;

        if (matchesExpectedStep) {
            state.microFallStreak++;
        } else {
            state.microFallStreak = 0;
        }
    }

    private boolean isExempt(Player player) {
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) return true;
        if (player.isSwimming() || player.isInWater()) return true;
        if (player.isRiptiding()) return true;
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) return true;
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;
        return isOnClimbable(player);

        // LƯU Ý: KHÔNG dùng velocity để miễn trừ (đã gỡ bỏ) - vận tốc Y server tự
        // tích luỹ theo trọng lực trong lúc rơi/lơ lửng kéo dài CHỈ TĂNG chứ không
        // giảm, nên một khi vượt ngưỡng 1 lần sẽ vượt mãi mãi -> khiến exempt=true
        // vĩnh viễn, vô hiệu hoá toàn bộ detect (bug đã xác nhận qua debug log thực tế).
    }

    private boolean isOnClimbable(Player player) {
        Material type = player.getLocation().getBlock().getType();
        switch (type) {
            case LADDER:
            case VINE:
            case TWISTING_VINES:
            case TWISTING_VINES_PLANT:
            case WEEPING_VINES:
            case WEEPING_VINES_PLANT:
            case CAVE_VINES:
            case CAVE_VINES_PLANT:
            case SCAFFOLDING:
                return true;
            default:
                return false;
        }
    }
}