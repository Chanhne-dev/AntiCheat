package chanhne.AntiCheat.check.TrouserStreak;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.util.DetectionHelper;
import chanhne.AntiCheat.util.ViolationTracker;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phát hiện exploit "MaceKill": client hủy gói tấn công thật, gửi các gói
 * di chuyển giả để dịch chuyển lên cao rồi về lại chỗ cũ quanh 1 đòn đánh
 * bằng Mace, khiến server tính "Smash Attack" bonus damage (dựa trên
 * fallDistance) dù người chơi chưa thực sự rơi.
 *
 * Cách phát hiện: mỗi player được theo dõi 1 lịch sử vị trí Y gần đây
 * (ring buffer nhỏ, key theo UUID). Khi có đòn đánh bằng Mace khiến
 * fallDistance vượt ngưỡng kích hoạt smash bonus (mặc định 1.5, đúng
 * ngưỡng vanilla), đối chiếu với lịch sử Y thật:
 *   (A) "Yo-yo": vừa có 1 đợt tăng Y bất thường trong vài tick gần nhất,
 *       rồi quay lại gần đúng Y ban đầu ngay trước đòn đánh.
 *   (B) "Mismatch": độ rơi thực tế quan sát được (từ lịch sử) nhỏ hơn
 *       nhiều so với fallDistance mà server đang ghi nhận cho player đó.
 * Một trong hai dấu hiệu là đủ nghi ngờ cao vì một cú rơi thật không thể
 * vừa lớn (đủ để trigger smash) vừa "biến mất" ngay sau đó.
 *
 * FOLIA: các Bukkit event (PlayerMoveEvent, EntityDamageByEntityEvent...)
 * vẫn được gọi bình thường trên region thread tương ứng của entity. Class
 * này không dùng Bukkit.getScheduler() cho bất kỳ tác vụ nào tác động lên
 * entity player (không teleport/set velocity ở đây) nên không cần
 * player.getScheduler(). State dùng ConcurrentHashMap vì nhiều player ở
 * các region khác nhau có thể ghi đồng thời vào map cấp cao nhất; việc
 * đọc/ghi Deque riêng của từng player chỉ xảy ra trên thread xử lý sự
 * kiện của chính player đó nên không cần đồng bộ thêm.
 */
public class MaceKillCheck implements Listener {

    private final Mainplugin plugin;
    private final Map<UUID, Deque<Sample>> histories = new ConcurrentHashMap<>();
    private final ViolationTracker tracker = new ViolationTracker();

    private long globalTick = 0L;

    public MaceKillCheck(Mainplugin plugin) {
        this.plugin = plugin;
    }

    // --- Theo dõi lịch sử Y ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isMaceKillCheckEnabled()) return;

        Player player = event.getPlayer();
        if (plugin.shouldBypass(player)) return;

        record(player, event.getTo().getY());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        // Teleport hợp lệ (ender pearl, chorus fruit, /tp, warp...) không
        // phải là "fall" thật -> xóa lịch sử để tránh false positive.
        histories.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        histories.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        histories.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        histories.remove(event.getPlayer().getUniqueId());
        tracker.reset(event.getPlayer().getUniqueId());
    }

    private void record(Player player, double y) {
        ConfigManager cfg = plugin.getConfigManager();
        Deque<Sample> history = histories.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        history.addLast(new Sample(globalTick++, y));

        int maxWindow = cfg.getMaceYoyoWindowTicks() + 2;
        while (history.size() > maxWindow) {
            history.removeFirst();
        }
    }

    // --- Kiểm tra tại thời điểm đánh trúng ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isMaceKillCheckEnabled()) return;

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (plugin.shouldBypass(attacker)) return;

        if (attacker.getInventory().getItemInMainHand().getType() != Material.MACE) return;

        double fallDistance = attacker.getFallDistance();
        if (fallDistance < cfg.getMaceSmashFallThreshold()) return; // không phải smash attack, bỏ qua

        Deque<Sample> history = histories.get(attacker.getUniqueId());
        // Không đủ dữ liệu lịch sử (vừa join/teleport/respawn) -> không đủ
        // bằng chứng, bỏ qua thay vì tự động coi là nghi vấn.
        if (history == null || history.size() < 2) return;

        double oldestY = history.peekFirst().y;
        Location loc = attacker.getLocation();
        double currentY = loc.getY();
        double peakY = oldestY;
        for (Sample s : history) {
            if (s.y > peakY) peakY = s.y;
        }

        // Tín hiệu DUY NHẤT: vừa có 1 đợt tăng Y bất thường trong cửa sổ
        // ngắn gần nhất, rồi quay lại gần đúng Y ban đầu ngay trước đòn
        // đánh ("yo-yo"). Đây mới là chữ ký đặc trưng của MaceKill: exploit
        // gửi gói lên rồi gói xuống lại gần đúng vị trí cũ TRƯỚC KHI gửi
        // gói tấn công, tất cả trong vài tick. Một cú rơi thật không bao
        // giờ "bật ngược" lại gần đúng độ cao ban đầu như vậy.
        //
        // (Đã bỏ tín hiệu "mismatch" so sánh fallDistance với độ rơi quan
        // sát trong cửa sổ ngắn: với rơi thật cao (nhiều chục block), thời
        // gian rơi thực tế dài hơn cửa sổ theo dõi rất nhiều nên luôn bị
        // coi là "thiếu độ rơi" -> false positive. Tín hiệu này cũng không
        // bắt exploit tốt hơn yoyo vì gói giả của exploit làm fallDistance
        // khớp gần đúng với độ rơi giả trong cùng cửa sổ ngắn đó.)
        double ascent = peakY - Math.min(oldestY, currentY);
        boolean yoyo = ascent >= cfg.getMaceYoyoAscentDelta() && Math.abs(currentY - oldestY) <= cfg.getMaceReturnTolerance();

        if (yoyo) {
            flag(attacker, event, cfg, "yoyo");
        }
    }

    private void flag(Player attacker, EntityDamageByEntityEvent event, ConfigManager cfg, String signal) {

        int violations = tracker.increase(attacker.getUniqueId());

        DetectionHelper.log(plugin, cfg, "MACEKILL PATTERN PHÁT HIỆN", "Người chơi: " + attacker.getName() + " (" + attacker.getUniqueId() + ")", ">> Tín hiệu: " + signal, ">> fallDistance: " + attacker.getFallDistance(), ">> Vi phạm lần thứ " + violations);
        DetectionHelper.notifyAdmins(plugin, cfg, "warning.macekill-detected", attacker.getName());
        DetectionHelper.discord(plugin, "MaceKill pattern phát hiện", "Người chơi: " + attacker.getName() + "\nTín hiệu: " + signal + "\nfallDistance: " + attacker.getFallDistance() + "\nVi phạm: " + violations, 0xE74C3C);
        if (cfg.isMaceMitigateDamage()) {
            event.setCancelled(true);
        }

        DetectionHelper.kick(plugin, cfg, attacker.getUniqueId(), attacker.getName(), cfg.isMaceKickOnDetect(), cfg.getMaceKickReason(), "MaceKill");
        if (violations >= cfg.getMaceViolationThreshold()) {
            tracker.reset(attacker.getUniqueId());
            DetectionHelper.ban(plugin, cfg, attacker.getUniqueId(), attacker.getName(), cfg.isMaceBanEnabled(), cfg.getMaceOffenseType(), "MaceKill", "warning.macekill-banned");
        }
    }

    private static class Sample {
        final long tick;
        final double y;

        Sample(long tick, double y) {
            this.tick = tick;
            this.y = y;
        }
    }
}
