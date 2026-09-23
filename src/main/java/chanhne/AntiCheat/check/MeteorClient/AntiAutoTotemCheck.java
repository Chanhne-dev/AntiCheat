package chanhne.AntiCheat.check.MeteorClient;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.util.DetectionHelper;
import chanhne.AntiCheat.util.ViolationTracker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Phát hiện module "AutoTotem" của Meteor Client và tương tự.
 *
 * LƯU Ý QUAN TRỌNG - TẠI SAO KHÔNG CHỈ DÙNG EntityResurrectEvent:
 * Chỉ nghe EntityResurrectEvent (đúng lúc totem cứu mạng) có 2 lỗ hổng:
 *   1. Không bắt được lúc AutoTotem CHỦ ĐỘNG trang bị totem trước khi bị nguy
 *      hiểm (máu thấp, đang bay elytra...) - tức là chưa từng có lần "chết
 *      hụt" nào, offhand tự nhiên có totem xuất hiện.
 *   2. Không phân biệt được việc offhand thay đổi có phải do THAO TÁC TAY THẬT
 *      (nhấn F, click/kéo-thả trong inventory) hay tự động không rõ nguyên nhân.
 *   3. Không quan trọng người chơi có 1 hay nhiều totem dự phòng - vấn đề nằm
 *      ở CÁCH totem xuất hiện trong offhand, không phải số lượng.
 *
 * => Cách làm: theo dõi offhand LIÊN TỤC MỖI TICK (tick-based, giống các check
 * khác trong plugin), đồng thời lắng nghe các sự kiện thao tác tay HỢP LỆ có
 * thể khiến offhand thay đổi (PlayerSwapHandItemsEvent - phím F ngoài GUI,
 * InventoryClickEvent, InventoryDragEvent - click/kéo-thả trong GUI) để đánh
 * dấu 1 "cửa sổ hợp lệ" ngắn ngay sau đó. Nếu totem xuất hiện trong offhand mà
 * KHÔNG nằm trong cửa sổ hợp lệ nào -> chắc chắn không phải do người chơi tự
 * thao tác -> nghi vấn "SilentTotemEquip".
 *
 * Song song đó vẫn giữ tín hiệu phản xạ thời gian sau EntityResurrectEvent
 * ("FastRefillAfterResurrect") làm lớp phòng thủ thứ 2 - phòng trường hợp cheat
 * cố tình giả 1 cú click packet để lách qua tín hiệu "im lặng" ở trên.
 */
public class AntiAutoTotemCheck implements Listener {

    private final Mainplugin plugin;
    private final ConfigManager config;
    private final ViolationTracker tracker = new ViolationTracker();
    private final Map<UUID, TotemState> states = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();

    private static final class TotemState {
        int tickCounter = 0;
        boolean hasLastOffhandType = false;
        Material lastOffhandType = Material.AIR;

        // Tick nội bộ (theo state.tickCounter, KHÔNG phải tick server toàn cục)
        // mà thao tác tay hợp lệ gần nhất còn "có hiệu lực" tới đó
        int allowedUntilLocalTick = -1;
        // Tick nội bộ lúc totem vừa cứu mạng gần nhất (-1 nếu chưa có)
        int lastResurrectLocalTick = -1;
    }

    public AntiAutoTotemCheck(Mainplugin plugin) {
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

    @EventHandler(ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        TotemState state = states.get(player.getUniqueId());
        if (state != null) {
            state.lastResurrectLocalTick = state.tickCounter;
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        markManualAction(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (who instanceof Player player) {
            markManualAction(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (who instanceof Player player) {
            markManualAction(player.getUniqueId());
        }
    }

    private void markManualAction(UUID uuid) {
        TotemState state = states.get(uuid);
        if (state == null) return;

        int graceTicks = config.getAntiAutoTotemManualActionGraceTicks();
        state.allowedUntilLocalTick = state.tickCounter + graceTicks;
    }

    private void startTracking(Player player) {
        UUID uuid = player.getUniqueId();

        ScheduledTask existing = tasks.remove(uuid);
        if (existing != null) existing.cancel();

        states.put(uuid, new TotemState());

        ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> tick(player),
                () -> stopTracking(uuid),
                2L,
                1L
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
        if (plugin.shouldBypass(player)) return;
        if (!config.isAntiAutoTotemEnabled()) return;

        UUID uuid = player.getUniqueId();
        TotemState state = states.get(uuid);
        if (state == null) return;

        state.tickCounter++;

        Material currentOffhand = player.getInventory().getItemInOffHand().getType();

        if (!state.hasLastOffhandType) {
            state.lastOffhandType = currentOffhand;
            state.hasLastOffhandType = true;
            return;
        }

        boolean justEquippedTotem = currentOffhand == Material.TOTEM_OF_UNDYING
                && state.lastOffhandType != Material.TOTEM_OF_UNDYING;

        if (justEquippedTotem) {
            boolean withinManualWindow = state.tickCounter <= state.allowedUntilLocalTick;

            int minRefillTicks = config.getAntiAutoTotemMinRefillTicks();
            boolean fastAfterResurrect = state.lastResurrectLocalTick >= 0
                    && (state.tickCounter - state.lastResurrectLocalTick) < minRefillTicks;

            if (config.isAntiAutoTotemDebug()) {
                plugin.getLogger().info("[AntiAutoTotem-DEBUG] " + player.getName() + " totem xuất hiện trong offhand | withinManualWindow=" + withinManualWindow + " | fastAfterResurrect=" + fastAfterResurrect);
            }

            if (!withinManualWindow) {
                raiseViolation(player, uuid, "SilentTotemEquip");
            } else if (fastAfterResurrect) {
                raiseViolation(player, uuid, "FastRefillAfterResurrect");
            }
        }

        state.lastOffhandType = currentOffhand;
    }

    private void raiseViolation(Player player, UUID uuid, String pattern) {
        int count = tracker.increase(uuid);
        int threshold = config.getAntiAutoTotemViolationThreshold();

        if (count >= threshold) {
            DetectionHelper.log(plugin, config, "AntiAutoTotem",
                    "Player: " + player.getName(),
                    "Pattern: " + pattern);

            DetectionHelper.notifyAdmins(plugin, config, "warning.antiautototem-detected", player.getName());

            DetectionHelper.kick(plugin, config,
                    uuid,
                    player.getName(),
                    config.isAntiAutoTotemKickOnDetect(),
                    config.getAntiAutoTotemKickReason(),
                    "AntiAutoTotem");

            DetectionHelper.ban(plugin, config,
                    uuid,
                    player.getName(),
                    config.isAntiAutoTotemBanEnabled(),
                    config.getAntiAutoTotemOffenseType(),
                    "AntiAutoTotem",
                    "warning.antiautototem-banned");

            tracker.reset(uuid);
        }
    }
}