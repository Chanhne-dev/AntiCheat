package chanhne.AntiCheat.check.TrouserStreak;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.messages.Message;
import chanhne.AntiCheat.util.DetectionHelper;
import chanhne.AntiCheat.util.ViolationTracker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phát hiện exploit "BoatNoclip": client tắt va chạm (noPhysics) của thuyền
 * đang cưỡi rồi tự ghi đè vận tốc để xuyên qua block. Không có quyền truy
 * cập gói tin thô ở tầng plugin Bukkit, nên không thể bắt trực tiếp
 * ServerboundMoveVehiclePacket bị làm giả (như gói "hạ 0.03130 block" để né
 * anti-fly-kick của vanilla). Thay vào đó, việc phát hiện dựa trên HẬU QUẢ
 * không thể chối cãi của noclip: thuyền kết thúc nằm chồng lên khối đặc —
 * điều vật lý bình thường (kể cả trên Folia) không bao giờ cho phép nếu
 * server còn xử lý va chạm cho vehicle di chuyển hợp lệ.
 *
 * Mỗi lần VehicleMoveEvent bắn cho 1 chiếc Boat có player điều khiển, lấy
 * mẫu vài điểm quanh thân thuyền và kiểm tra khối đặc. Yêu cầu "kẹt" liên
 * tục nhiều tick (stuck-ticks-required) trước khi flag, để tránh báo nhầm
 * do thuyền va chạm biên tạm thời (mép bờ, hàng rào, kính...).
 *
 * HẠN CHẾ CẦN BIẾT: đây là heuristic ở tầng plugin (không dùng NMS), lấy mẫu
 * điểm thay vì hình khối va chạm chính xác của từng loại block (bậc thang,
 * tường nửa khối...). Nên bật kick-on-detect trước, theo dõi log vài ngày,
 * rồi mới bật ban-enabled/mitigate nếu tỉ lệ báo nhầm chấp nhận được.
 *
 * FOLIA: VehicleMoveEvent vẫn nổ ra bình thường trên region thread của
 * entity. State dùng ConcurrentHashMap (nhiều vehicle ở các region khác
 * nhau có thể ghi đồng thời ở cấp map ngoài); việc đọc/ghi state riêng của
 * từng vehicle chỉ xảy ra trên thread xử lý sự kiện của chính vehicle đó.
 * Không dùng Bukkit.getScheduler() cho tác vụ nào tác động entity.
 *
 * ==========================================================================
 * TÍCH HỢP THÊM: "FreeBoatRide" - lợi dụng đúng cơ chế noclip ở trên (dịch
 * chuyển thuyền bằng gói ServerboundMoveVehiclePacket, bỏ qua va chạm) để
 * KÉO NGƯỜI CHƠI KHÁC (hành khách thứ 2 trở lên trong cùng thuyền) rơi thẳng
 * xuyên qua nền xuống hố/void, sau đó cho họ xuống xe (ép rời thuyền) ở dưới
 * đó rồi tự dịch chuyển bản thân về lại vị trí an toàn ban đầu.
 *
 * VehicleMoveEvent KHÔNG cancel được (Bukkit không có Cancellable cho event
 * này) nên không thể chặn trực tiếp cú dịch chuyển - phát hiện dựa trên:
 * thuyền có >=2 hành khách VÀ giảm Y đột ngột (nhiều block trong 1 lần
 * VehicleMoveEvent) - vật lý thuyền thật dưới trọng lực không bao giờ rơi
 * nhanh như vậy trong 1 lần cập nhật vị trí. Vì đây là hành vi có thể gây
 * hại trực tiếp tới người chơi khác (kéo xuống void), mặc định kick ngay +
 * ngưỡng ban thấp hơn nhiều so với BoatNoclip thường.
 * ==========================================================================
 */
public class BoatNoclipCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, StuckState> states = new ConcurrentHashMap<>();
    private final ViolationTracker tracker = new ViolationTracker();
    private final ViolationTracker freeBoatRideTracker = new ViolationTracker();

    // Kích thước lấy mẫu quanh tâm thuyền (boat vanilla rộng ~1.375, cao ~0.5625)
    private static final double SAMPLE_HALF_WIDTH = 0.65;
    private static final double SAMPLE_HALF_HEIGHT = 0.3;

    public BoatNoclipCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isBoatNoclipCheckEnabled()) return;

        Vehicle vehicle = event.getVehicle();
        if (!(vehicle instanceof Boat boat)) return;

        Player controller = findControllingPlayer(boat);
        if (controller == null) return;
        if (plugin.shouldBypass(controller)) return;

        boolean stuck = isEmbeddedInSolid(boat, event.getTo());
        StuckState state = states.computeIfAbsent(boat.getUniqueId(), k -> new StuckState());

        if (stuck) {
            state.streak++;
            if (state.streak >= cfg.getBoatStuckTicksRequired()) {
                flag(controller, boat, cfg);
                state.streak = 0; // tránh flag lại mỗi tick trong cùng 1 đợt kẹt
            }
        } else {
            state.streak = 0;
        }

        // --- FreeBoatRide: thuyền chở >=2 hành khách rơi Y đột ngột (kéo
        // người chơi khác xuống hố/void bằng cùng cơ chế noclip) ---
        if (cfg.isFreeBoatRideCheckEnabled()) {
            double toY = event.getTo().getY();
            if (!Double.isNaN(state.lastY)) {
                double dropDelta = state.lastY - toY; // dương = rơi xuống
                boolean multiPassenger = boat.getPassengers().size() >= 2;

                if (multiPassenger && dropDelta >= cfg.getFreeBoatRideDropDelta()) {
                    state.dropStreak++;
                    if (state.dropStreak >= cfg.getFreeBoatRideDropStreakRequired()) {
                        flagFreeBoatRide(controller, boat, cfg, dropDelta, event.getFrom());
                        state.dropStreak = 0;
                    }
                } else {
                    state.dropStreak = 0;
                }
            }
            state.lastY = toY;
        }
    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        states.remove(event.getVehicle().getUniqueId());
    }

    private Player findControllingPlayer(Boat boat) {
        for (Entity passenger : boat.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /**
     * Lấy mẫu vài điểm quanh thân thuyền (tâm + 4 góc ngang, ở cả mép trên/
     * dưới) và kiểm tra khối đặc. Yêu cầu ĐA SỐ điểm mẫu cùng nằm trong khối
     * đặc mới tính là "kẹt" (giảm false positive khi chỉ 1 góc chạm biên).
     */
    private boolean isEmbeddedInSolid(Boat boat, Location to) {
        if (to.getWorld() == null) return false;

        double[][] offsets = {
                {0, 0, 0},
                {SAMPLE_HALF_WIDTH, 0, 0}, {-SAMPLE_HALF_WIDTH, 0, 0},
                {0, 0, SAMPLE_HALF_WIDTH}, {0, 0, -SAMPLE_HALF_WIDTH},
                {0, SAMPLE_HALF_HEIGHT, 0}, {0, -SAMPLE_HALF_HEIGHT, 0}
        };

        int solidCount = 0;
        for (double[] off : offsets) {
            Block block = to.getWorld().getBlockAt(
                    (int) Math.floor(to.getX() + off[0]),
                    (int) Math.floor(to.getY() + off[1]),
                    (int) Math.floor(to.getZ() + off[2])
            );
            Material type = block.getType();
            if (type.isSolid() && !type.isAir()) {
                solidCount++;
            }
        }

        // Đa số điểm mẫu (>=5/7) nằm trong khối đặc -> gần như chắc chắn thân
        // thuyền đang chồng lấn khối đặc, không phải chỉ chạm biên.
        return solidCount >= 5;
    }

    private void flag(Player controller, Boat boat, ConfigManager cfg) {
        int violations = tracker.increase(controller.getUniqueId());

        DetectionHelper.log(plugin, cfg, "BOATNOCLIP PATTERN PHÁT HIỆN", "Người chơi: " + controller.getName() + " (" + controller.getUniqueId() + ")", ">> Vị trí thuyền: " + formatLocation(boat.getLocation()), ">> Vi phạm lần thứ " + violations);
        DetectionHelper.notifyAdmins(plugin, cfg, "warning.boatnoclip-detected", controller.getName());
        DetectionHelper.discord(plugin, "BoatNoclip pattern phát hiện", "Người chơi: " + controller.getName() + "\nVị trí: " + formatLocation(boat.getLocation()) + "\nVi phạm: " + violations, 0xE74C3C);
        DetectionHelper.kick(plugin, cfg, controller.getUniqueId(), controller.getName(), cfg.isBoatKickOnDetect(), cfg.getBoatKickReason(), "BoatNoclip");
        if (violations >= cfg.getBoatViolationThreshold()) {
            tracker.reset(controller.getUniqueId());
            DetectionHelper.ban(plugin, cfg, controller.getUniqueId(), controller.getName(), cfg.isBoatBanEnabled(), cfg.getBoatOffenseType(), "BoatNoclip", "warning.boatnoclip-banned");
        }
    }

    private void flagFreeBoatRide(Player controller, Boat boat, ConfigManager cfg, double dropDelta, Location safeLocation) {
        int violations = freeBoatRideTracker.increase(controller.getUniqueId());
        String passengers = listOtherPassengerNames(boat, controller);

        DetectionHelper.log(plugin, cfg, "FREEBOATRIDE PATTERN PHÁT HIỆN (nghi kéo người chơi khác xuống hố)",
                "Tài công: " + controller.getName() + " (" + controller.getUniqueId() + ")",
                ">> Hành khách khác trên thuyền: " + passengers,
                ">> Rơi " + String.format("%.1f", dropDelta) + " block trong 1 lần cập nhật vị trí",
                ">> Vi phạm lần thứ " + violations);
        DetectionHelper.notifyAdmins(plugin, cfg, "warning.freeboatride-detected", controller.getName());
        DetectionHelper.discord(plugin,
                "FreeBoatRide phát hiện (nghi kéo người chơi khác xuống hố)",
                "Tài công: " + controller.getName()
                        + "\nHành khách khác: " + passengers
                        + "\nRơi: " + String.format("%.1f", dropDelta) + " block"
                        + "\nVi phạm: " + violations,
                0xC0392B);

        if (cfg.isFreeBoatRideProtectPassengers()) {
            protectPassengers(boat, controller, safeLocation, cfg);
        }

        DetectionHelper.kick(plugin, cfg, controller.getUniqueId(), controller.getName(),
                cfg.isFreeBoatRideKickOnDetect(), cfg.getFreeBoatRideKickReason(), "FreeBoatRide");

        if (violations >= cfg.getFreeBoatRideViolationThreshold()) {
            freeBoatRideTracker.reset(controller.getUniqueId());
            DetectionHelper.ban(plugin, cfg, controller.getUniqueId(), controller.getName(),
                    cfg.isFreeBoatRideBanEnabled(), cfg.getFreeBoatRideOffenseType(), "FreeBoatRide", "warning.freeboatride-banned");
        }
    }

    /**
     * Tách các hành khách khác (không phải tài công/kẻ gian lận) khỏi
     * thuyền và dịch chuyển họ về đúng vị trí ngay TRƯỚC cú kéo bất thường
     * (event.getFrom() - vị trí đã được xác nhận hợp lệ ở lần cập nhật
     * trước đó, không thể đã bị kẹt trong khối đặc hay dưới void).
     *
     * Dùng Entity#teleportAsync thay vì Entity#teleport thông thường vì
     * đây là cách Folia-safe chính thức để dịch chuyển entity có thể liên
     * quan tới việc đổi region (điểm đến có thể thuộc region khác với
     * region hiện tại của nạn nhân).
     */
    private void protectPassengers(Boat boat, Player controller, Location safeLocation, ConfigManager cfg) {
        if (safeLocation == null || safeLocation.getWorld() == null) return;

        for (Entity passenger : boat.getPassengers()) {
            if (!(passenger instanceof Player victim)) continue;
            if (victim.getUniqueId().equals(controller.getUniqueId())) continue; // không "bảo vệ" chính kẻ gian lận

            boat.removePassenger(victim);

            victim.teleportAsync(safeLocation).thenAccept(success -> {
                if (Boolean.TRUE.equals(success)) {
                    victim.sendMessage(Message.component("warning.freeboatride-protected", "player", victim.getName()));
                    if (cfg.isLogToConsole()) {
                        plugin.getLogger().info("Đã bảo vệ " + victim.getName() + " khỏi FreeBoatRide (tài công: " + controller.getName() + ")");
                    }
                } else if (cfg.isLogToConsole()) {
                    plugin.getLogger().warning("Dịch chuyển bảo vệ THẤT BẠI cho " + victim.getName() + " (FreeBoatRide, tài công: " + controller.getName() + ")");
                }
            });
        }
    }

    private String listOtherPassengerNames(Boat boat, Player controller) {
        StringBuilder sb = new StringBuilder();
        for (Entity passenger : boat.getPassengers()) {
            if (passenger instanceof Player p && !p.getUniqueId().equals(controller.getUniqueId())) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p.getName());
            }
        }
        return sb.length() > 0 ? sb.toString() : "(không xác định)";
    }

    private String formatLocation(Location loc) {
        return String.format("%.1f, %.1f, %.1f (%s)", loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "?");
    }

    private static class StuckState {
        int streak = 0;
        double lastY = Double.NaN;
        int dropStreak = 0;
    }
}