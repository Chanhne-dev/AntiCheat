package chanhne.AntiCheat.check.TrouserStreak;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.util.DetectionHelper;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phát hiện + chặn exploit "CrossbowMachineGun": client gửi thẳng gói dùng
 * vật phẩm (bắn) liên tục mỗi tick thay vì giữ chuột phải để nạp đạn đúng
 * thời gian rồi mới bắn, biến nỏ thành súng liên thanh.
 *
 * Cách phát hiện: Bukkit bắn EntityShootBowEvent mỗi lần 1 mũi tên THẬT SỰ
 * rời nỏ (không phải lúc bắt đầu nạp). Nỏ vanilla cần tối thiểu 25 tick để
 * nạp đầy (1.25s), mỗi cấp Quick Charge trừ 5 tick (QC1=20, QC2=15,
 * QC3=10...). Nếu khoảng cách giữa 2 phát bắn liên tiếp của cùng người chơi
 * (với cùng mức Quick Charge trên chính cây nỏ đó) ngắn hơn mức tối thiểu
 * cho phép (trừ hao sai số mạng qua tolerance-ratio) -> hủy phát bắn đó.
 *
 * Hủy phát bắn (event.setCancelled(true)) mỗi lần vi phạm. Ngoài ra kick
 * ngay lập tức qua ChanhOffAPI#kickPlayer khi phát hiện (không ban, không
 * lưu lịch sử vi phạm) - theo đúng yêu cầu chỉ kick + cancel, không ban.
 *
 * FOLIA: EntityShootBowEvent nổ ra trên region thread của entity bắn. Xử lý
 * hoàn toàn đồng bộ trong handler (không gọi API bất đồng bộ nào), nên
 * không cần scheduler đặc biệt nào cả - kể cả vòng lặp thông báo admin có
 * thể gọi trực tiếp vì đang chạy đúng trên thread hợp lệ của sự kiện.
 */
public class CrossbowMachineGunCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, Long> lastShotMillis = new ConcurrentHashMap<>();

    private static final int BASE_CHARGE_TICKS = 25;
    private static final int TICKS_PER_QUICK_CHARGE_LEVEL = 5;
    private static final int MIN_CHARGE_TICKS_FLOOR = 5; // đề phòng enchant cấp cao bất thường
    private static final long MILLIS_PER_TICK = 50L;
    // Multishot khiến EntityShootBowEvent bắn riêng cho từng mũi tên (thường 3
    // lần) của CÙNG 1 loạt bắn, gần như cùng lúc. Coi các lần gọi trong cửa sổ
    // này là 1 loạt bắn duy nhất, không tính cooldown giữa chúng với nhau.
    private static final long SAME_VOLLEY_WINDOW_MILLIS = 150L; // ~3 tick

    public CrossbowMachineGunCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isCrossbowCheckEnabled()) return;

        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.shouldBypass(player)) return;

        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.CROSSBOW) return;

        long now = System.currentTimeMillis();
        Long last = lastShotMillis.get(player.getUniqueId());

        // Cùng 1 loạt bắn (Multishot) -> bỏ qua hoàn toàn, không tính là lần
        // bắn mới, không cập nhật lại mốc thời gian.
        if (last != null && (now - last) < SAME_VOLLEY_WINDOW_MILLIS) {
            return;
        }

        int quickCharge = bow.getEnchantmentLevel(Enchantment.QUICK_CHARGE);
        int minChargeTicks = Math.max(MIN_CHARGE_TICKS_FLOOR, BASE_CHARGE_TICKS - quickCharge * TICKS_PER_QUICK_CHARGE_LEVEL);
        long minIntervalMillis = (long) (minChargeTicks * MILLIS_PER_TICK * cfg.getCrossbowToleranceRatio());

        if (last != null && (now - last) < minIntervalMillis) {
            event.setCancelled(true);
            flag(player, now - last, minIntervalMillis, cfg);
            return; // không cập nhật lastShotMillis cho phát bị hủy
        }

        lastShotMillis.put(player.getUniqueId(), now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastShotMillis.remove(event.getPlayer().getUniqueId());
    }

    private void flag(Player player, long actualIntervalMillis, long minIntervalMillis, ConfigManager cfg) {
        DetectionHelper.log( plugin, cfg, "CROSSBOW MACHINE GUN PHÁT HIỆN (đã hủy phát bắn)", "Người chơi: " + player.getName() + " (" + player.getUniqueId() + ")", ">> Khoảng cách 2 phát: " + actualIntervalMillis + "ms", ">> Tối thiểu cần: " + minIntervalMillis + "ms");
        DetectionHelper.notifyAdmins( plugin, cfg, "warning.crossbow-machinegun-detected", player.getName());
        DetectionHelper.discord( plugin, "CrossbowMachineGun phát hiện (đã hủy phát bắn)", "Người chơi: " + player.getName() + "\nKhoảng cách 2 phát: " + actualIntervalMillis + "ms"    + "\nTối thiểu cần: " + minIntervalMillis + "ms", 0xE67E22);
        DetectionHelper.kick( plugin, cfg, player.getUniqueId(), player.getName(), cfg.isCrossbowKickOnDetect(), cfg.getCrossbowKickReason(), "CrossbowMachineGun");
    }
}