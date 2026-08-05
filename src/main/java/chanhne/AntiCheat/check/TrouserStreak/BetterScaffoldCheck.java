package chanhne.AntiCheat.check.TrouserStreak;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.util.DetectionHelper;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phát hiện + chặn exploit "BetterScaffold": tự động đặt block dưới chân
 * khi di chuyển/nhảy, có thể đặt nhiều block cùng lúc (horizontal/vertical
 * radius) và tự nhảy liên tục để xây tháp (fast-tower) nhanh hơn người thật.
 *
 * Hai tín hiệu độc lập, mỗi tín hiệu đủ để hủy 1 lượt đặt block:
 *
 * (A) "Burst" - đặt nhiều block trong 1 khoảng thời gian cực ngắn: 1 cú
 *     click chuột thật của vanilla chỉ có thể tạo ra 1 BlockPlaceEvent.
 *     BetterScaffold với horizontal-radius/vertical-radius > 1 gọi
 *     BlockUtils.place nhiều lần liên tiếp trong cùng 1 tick client, tạo ra
 *     nhiều BlockPlaceEvent gần như đồng thời cho cùng người chơi.
 *
 * (B) "Fast-tower" - đặt block ngay dưới chân (kiểu tự xây tháp) với nhịp
 *     độ nhanh bất thường và LẶP LẠI liên tục (fastTower tự set
 *     deltaMovement nhảy + hạ xuống ngay sau khi đặt, tạo nhịp rất đều và
 *     nhanh hơn nhiều so với chu kỳ nhảy-đặt-rơi thật của người chơi).
 *
 * Theo yêu cầu: (A) "burst" bị CANCEL + KICK ngay (qua ChanhOffAPI#kickPlayer,
 * không ban, không lưu lịch sử vi phạm) vì đây là bằng chứng gần như không
 * thể chối cãi (vanilla thật không thể tạo nhiều BlockPlaceEvent gần như
 * đồng thời từ 1 cú click). (B) "fast-tower" chỉ CANCEL, không kick, vì đây
 * là heuristic dựa trên nhịp độ nên rủi ro báo nhầm cao hơn.
 *
 * HẠN CHẾ: đây là heuristic dựa trên thời gian/tần suất ở tầng Bukkit
 * (không có gói tin thô), không phát hiện được rotation giả hay air-place
 * đơn lẻ không lặp lại. Nên theo dõi log false-positive trước khi áp dụng
 * cho server PvP tốc độ xây cao (ví dụ SkyWars/BedWars có thể có xây tháp
 * hợp lệ rất nhanh) - cân nhắc bật whitelist theo world/gamemode nếu cần.
 *
 * FOLIA: BlockPlaceEvent nổ ra trên region thread của player đặt block. Xử
 * lý hoàn toàn đồng bộ (không gọi API bất đồng bộ nào), không cần
 * scheduler đặc biệt.
 */
public class BetterScaffoldCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, Deque<Long>> recentPlacements = new ConcurrentHashMap<>();
    private final Map<UUID, TowerState> towerStates = new ConcurrentHashMap<>();

    public BetterScaffoldCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isScaffoldCheckEnabled()) return;

        Player player = event.getPlayer();
        if (plugin.shouldBypass(player)) return;

        long now = System.currentTimeMillis();

        // --- (A) Burst: nhiều block trong cửa sổ thời gian cực ngắn ---
        Deque<Long> history = recentPlacements.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        history.addLast(now);
        while (!history.isEmpty() && now - history.peekFirst() > cfg.getScaffoldBurstWindowMillis()) {
            history.removeFirst();
        }

        if (history.size() >= cfg.getScaffoldBurstCountThreshold()) {
            event.setCancelled(true);
            flag(player, "burst", cfg, true);
            return;
        }

        // --- (B) Fast-tower: đặt block ngay dưới chân, nhịp nhanh liên tục ---
        Block placed = event.getBlockPlaced();
        boolean isBelowFeet = placed.getX() == player.getLocation().getBlockX()
                && placed.getZ() == player.getLocation().getBlockZ()
                && placed.getY() == player.getLocation().getBlockY() - 1;

        if (isBelowFeet) {
            TowerState state = towerStates.computeIfAbsent(player.getUniqueId(), k -> new TowerState());

            if (state.lastMillis > 0 && (now - state.lastMillis) < cfg.getScaffoldTowerMinIntervalMillis()) {
                state.streak++;
                if (state.streak >= cfg.getScaffoldTowerStreakRequired()) {
                    event.setCancelled(true);
                    flag(player, "fast-tower", cfg, false);
                    state.streak = 0;
                    state.lastMillis = now;
                    return;
                }
            } else {
                state.streak = 0;
            }
            state.lastMillis = now;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        recentPlacements.remove(event.getPlayer().getUniqueId());
        towerStates.remove(event.getPlayer().getUniqueId());
    }

    private void flag(Player player, String signal, ConfigManager cfg, boolean kickOnBurst) {
        DetectionHelper.log( plugin, cfg, "BETTERSCAFFOLD PHÁT HIỆN (đã hủy đặt block)", "Người chơi: " + player.getName() + " (" + player.getUniqueId() + ")", ">> Tín hiệu: " + signal);
        DetectionHelper.notifyAdmins( plugin, cfg, "warning.scaffold-detected", player.getName());
        DetectionHelper.discord( plugin, "BetterScaffold phát hiện (đã hủy đặt block)", "Người chơi: " + player.getName() + "\nTín hiệu: " + signal, 0xE67E22);
        if (kickOnBurst) {
            DetectionHelper.kick( plugin, cfg, player.getUniqueId(), player.getName(), cfg.isScaffoldKickOnBurst(), cfg.getScaffoldKickReason(), "BetterScaffold");
        }
    }

    private static class TowerState {
        long lastMillis = 0;
        int streak = 0;
    }
}