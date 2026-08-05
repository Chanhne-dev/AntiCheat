package chanhne.AntiCheat.debug;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.config.ConfigManager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Công cụ ghi log RAW toàn bộ dữ liệu di chuyển của người chơi ra file CSV,
 * phục vụ mục đích thu thập dữ liệu tham chiếu để hiệu chỉnh ngưỡng cho các
 * check di chuyển (ClickTP, AnHero...).
 *
 * ĐÂY KHÔNG PHẢI 1 CHECK: không hủy, không cảnh báo, không kick/ban, không
 * ảnh hưởng gì tới người chơi - chỉ đọc dữ liệu và ghi log.
 *
 * Đăng ký ở EventPriority.LOWEST để ghi lại đúng dữ liệu GỐC do client gửi
 * lên, TRƯỚC KHI bất kỳ check nào khác (vd ClickTPCheck chạy ở HIGH) có cơ
 * hội sửa event.setTo(...) - nhờ vậy log phản ánh chính xác hành vi client
 * gửi lên, kể cả các cú di chuyển sau đó bị check khác hủy.
 *
 * Ghi bất đồng bộ: dữ liệu được đẩy vào hàng đợi trong onMove (rất nhanh,
 * không chặn region thread), rồi 1 thread nền riêng gom lại và flush ra
 * file định kỳ - an toàn trên cả Folia.
 *
 * MẶC ĐỊNH TẮT (movement-logger.enabled: false trong config.yml) vì log
 * mọi di chuyển của mọi người chơi có thể sinh ra file rất lớn - chỉ nên
 * bật tạm thời trong lúc cần thu thập dữ liệu để hiệu chỉnh ngưỡng, rồi
 * tắt lại.
 *
 * CỘT TỐC ĐỘ (thêm theo yêu cầu): log CẢ HAI loại "tốc độ" vì chúng phản
 * ánh những thứ khác nhau:
 *   - velocity / velocity_horizontal: Player#getVelocity() - vector vận tốc
 *     phía SERVER áp đặt (knockback, nổ, elytra boost, mũi tên...). Đi bộ/
 *     chạy bình thường KHÔNG đi qua cơ chế velocity (client tự báo cáo vị
 *     trí trực tiếp), nên 2 cột này gần như luôn ~0 khi đi bộ lẫn khi dùng
 *     ClickTP - KHÔNG dùng để phân biệt được 2 trường hợp này.
 *   - speed_bps: tốc độ THỰC TẾ tự tính = khoảng cách 3D / thời gian thực
 *     giữa gói này và gói trước đó của CÙNG người chơi (block/giây). Đây
 *     mới là con số phản ánh đúng "người chơi đang di chuyển nhanh cỡ nào"
 *     bất kể cơ chế gây ra (đi bộ, knockback, hay ClickTP).
 */
public class MovementLogger implements Listener {

    private static final String CSV_HEADER = "timestamp,player,uuid,world,from_x,from_y,from_z,to_x,to_y,to_z,"
            + "horizontal_dist,vertical_delta,dist_3d,dt_ms,speed_bps,velocity,velocity_horizontal,"
            + "on_ground,sprinting,sneaking,flying,gliding,in_vehicle,"
            + "speed_amplifier,jump_boost_amplifier,ping_ms";

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final AntiCheatPlugin plugin;
    private final ConfigManager cfg;

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    // Thời điểm (millis) của gói di chuyển gần nhất TỪNG người chơi, dùng để tính dt/speed_bps
    private final Map<UUID, Long> lastEventTime = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private BufferedWriter writer;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MovementLogger(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    /** Gọi trong onEnable() (và sau reload) để bắt đầu ghi log nếu được bật trong config. */
    public void start() {
        if (!cfg.isMovementLoggerEnabled()) return;
        if (!running.compareAndSet(false, true)) return;

        try {
            File dir = new File(plugin.getDataFolder(), "logs");
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().warning("[MovementLogger] Khong the tao thu muc logs/");
            }
            File file = new File(dir, cfg.getMovementLoggerFileName());
            boolean isNew = !file.exists();

            writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8, true));
            if (isNew) {
                writer.write(CSV_HEADER);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[MovementLogger] Khong the mo file log: " + e.getMessage());
            running.set(false);
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AntiCheat-MovementLogger");
            t.setDaemon(true);
            return t;
        });
        long intervalSeconds = Math.max(1, cfg.getMovementLoggerFlushIntervalSeconds());
        scheduler.scheduleAtFixedRate(this::flush, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        plugin.getLogger().info("[MovementLogger] Da bat dau ghi log di chuyen -> logs/" + cfg.getMovementLoggerFileName());
    }

    /** Gọi trong onDisable() (và trước khi reload) để flush hết dữ liệu còn lại và đóng file an toàn. */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        flush();
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {
            // Bỏ qua lỗi khi đóng file lúc tắt plugin
        }
        writer = null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    private void onMove(PlayerMoveEvent event) {
        if (!running.get()) return;

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() == null || to.getWorld() == null) return;

        double horizontalDist = Math.sqrt(square(to.getX() - from.getX()) + square(to.getZ() - from.getZ()));
        double verticalDelta = to.getY() - from.getY();
        double dist3d = Math.sqrt(square(horizontalDist) + square(verticalDelta));

        double minDist = cfg.getMovementLoggerMinDistance();
        // Bỏ qua các event chỉ xoay camera / gần như đứng yên để đỡ rác log
        if (horizontalDist < minDist && Math.abs(verticalDelta) < minDist) return;

        UUID id = player.getUniqueId();
        long nowMs = System.currentTimeMillis();
        Long prevTime = lastEventTime.put(id, nowMs);
        long dtMs = prevTime != null ? Math.max(1, nowMs - prevTime) : -1;
        // Tốc độ THỰC TẾ: khoảng cách / thời gian thực - không phải Player#getVelocity()
        double speedBps = dtMs > 0 ? dist3d / (dtMs / 1000.0) : 0.0;

        Vector velocity = player.getVelocity();
        double velocityLength = velocity.length();
        double velocityHorizontal = Math.sqrt(square(velocity.getX()) + square(velocity.getZ()));

        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP_BOOST);

        String line = String.join(",",
                LocalDateTime.now(ZONE).format(TIME),
                csvSafe(player.getName()),
                id.toString(),
                csvSafe(from.getWorld().getName()),
                fmt(from.getX()), fmt(from.getY()), fmt(from.getZ()),
                fmt(to.getX()), fmt(to.getY()), fmt(to.getZ()),
                fmt(horizontalDist), fmt(verticalDelta), fmt(dist3d),
                String.valueOf(dtMs),
                fmt(speedBps),
                fmt(velocityLength),
                fmt(velocityHorizontal),
                String.valueOf(player.isOnGround()),
                String.valueOf(player.isSprinting()),
                String.valueOf(player.isSneaking()),
                String.valueOf(player.isFlying()),
                String.valueOf(player.isGliding()),
                String.valueOf(player.isInsideVehicle()),
                String.valueOf(speed != null ? speed.getAmplifier() : -1),
                String.valueOf(jump != null ? jump.getAmplifier() : -1),
                String.valueOf(player.getPing())
        );

        queue.add(line);

        int maxQueue = cfg.getMovementLoggerMaxQueueSize();
        while (maxQueue > 0 && queue.size() > maxQueue) {
            queue.poll(); // Đĩa ghi không kịp -> rớt bớt dòng cũ nhất, tránh phình bộ nhớ
        }
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        lastEventTime.remove(event.getPlayer().getUniqueId());
    }

    private void flush() {
        if (writer == null) return;

        StringBuilder batch = new StringBuilder();
        int count = 0;
        String line;
        while ((line = queue.poll()) != null) {
            batch.append(line).append('\n');
            count++;
        }
        if (count == 0) return;

        try {
            synchronized (this) {
                writer.write(batch.toString());
                writer.flush();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[MovementLogger] Loi ghi file log: " + e.getMessage());
        }
    }

    private static String fmt(double v) {
        return String.format("%.4f", v);
    }

    private static String csvSafe(String s) {
        return s.replace(",", "_").replace("\n", " ").replace("\r", " ");
    }

    private static double square(double v) {
        return v * v;
    }
}