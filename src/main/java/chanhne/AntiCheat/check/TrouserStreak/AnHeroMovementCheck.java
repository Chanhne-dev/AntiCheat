package chanhne.AntiCheat.check.TrouserStreak;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.util.DetectionHelper;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phát hiện pattern "AnHero" (client cheat): spam di chuyển Y lên bất thường
 * (bypass vật lý thật, ví dụ move(0,+7,0) liên tục nhiều tick) ngay sau đó
 * ép vận tốc rơi cực nhanh (ví dụ setDeltaMovement(0.01,-10,0)) để tạo sát
 * thương rơi tức thời, thường nhằm né combat-log hoặc tự sát nhanh.
 *
 * Không dùng chung pipeline ViolationResult/EnforcementHandler vì pipeline
 * đó gắn chặt với vi phạm item (xóa inventory, message theo item/slot) —
 * không phù hợp ngữ nghĩa với vi phạm movement. Thay vào đó module này tự
 * xử lý log/notify/ban theo đúng phong cách (ConfigManager, Message,
 * DiscordWebhook, ChanhOffAPI) mà EnforcementHandler đang dùng.
 */
public class AnHeroMovementCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, PlayerMovementState> states = new HashMap<>();

    public AnHeroMovementCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isMovementCheckEnabled()) return;

        Player player = event.getPlayer();
        if (plugin.shouldBypass(player)) return;
        if (isExempt(player)) return;

        if (event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getZ() == event.getTo().getZ()) {
            return; // pure look-direction change, ignore
        }

        double deltaY = event.getTo().getY() - event.getFrom().getY();
        PlayerMovementState state = states.computeIfAbsent(player.getUniqueId(), k -> new PlayerMovementState());

        // --- Đợt bay lên bất thường (phantom ascent) ---
        if (deltaY >= cfg.getPhantomAscentDelta()) {
            state.ascentStreak++;
            if (state.ascentStreak >= cfg.getAscentTicksRequired()) {
                state.ascentBurstEndedAtTick = state.tick;
                state.ascentStreak = 0; // tránh flag lại nhiều lần trong cùng 1 đợt
            }
        } else {
            state.ascentStreak = 0;
        }

        // --- Đợt rơi bất thường (phantom descent), chỉ tính nếu vừa có đợt bay lên gần đây ---
        if (deltaY <= cfg.getPhantomDescentDelta()) {
            boolean withinLinkWindow = state.ascentBurstEndedAtTick >= 0
                    && (state.tick - state.ascentBurstEndedAtTick) <= cfg.getLinkWindowTicks();

            if (withinLinkWindow) {
                state.descentStreak++;
                if (state.descentStreak >= cfg.getDescentTicksRequired()) {
                    flag(player, state, cfg);
                    state.descentStreak = 0;
                    state.ascentBurstEndedAtTick = -1; // đã xử lý pattern này
                }
            } else {
                state.descentStreak = 0;
            }
        } else {
            state.descentStreak = 0;
        }

        state.tick++;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private boolean isExempt(Player player) {
        return player.isFlying()
                || player.isGliding() // elytra
                || player.getVehicle() != null
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
    }

    private void flag(Player player, PlayerMovementState state, ConfigManager cfg) {
        state.violations++;
        int violations = state.violations;

        DetectionHelper.log( plugin, cfg, "DI CHUYỂN BẤT THƯỜNG (AnHero pattern)", "Người chơi: " + player.getName() + " (" + player.getUniqueId() + ")", ">> Vi phạm lần thứ " + violations);
        DetectionHelper.notifyAdmins( plugin, cfg, "warning.movement-anomaly-detected", player.getName());
        DetectionHelper.discord( plugin, "AnHero pattern phát hiện", "Người chơi: " + player.getName() + "\nVi phạm: " + violations, 0xE74C3C);
        DetectionHelper.kick( plugin, cfg, player.getUniqueId(), player.getName(), cfg.isMovementKickOnDetect(), cfg.getMovementKickReason(), "AnHero");
        if (violations >= cfg.getMovementViolationThreshold()) {
            state.violations = 0;
            DetectionHelper.ban( plugin, cfg, player.getUniqueId(), player.getName(), cfg.isMovementBanEnabled(), cfg.getMovementOffenseType(), "AnHero", "warning.movement-anomaly-banned");
        }
    }

    private static class PlayerMovementState {
        int tick = 0;
        int ascentStreak = 0;
        int descentStreak = 0;
        int ascentBurstEndedAtTick = -1;
        int violations = 0;
    }
}
