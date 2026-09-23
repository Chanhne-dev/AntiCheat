package chanhne.AntiCheat.check.MeteorClient;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.messages.Message;

import io.papermc.paper.event.entity.EntityKnockbackEvent;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phát hiện exploit "ClickTP" (Meteor Client và các client tương tự).
 *
 * BẢN VÁ V11: chặn thêm module "Teleport" (pwn.noobs.trouserstreak) - khác
 * ClickTP ở chỗ dịch chuyển được TRẢI ĐỀU qua nhiều tick (2-13 tick tuỳ
 * khoảng cách, nội suy dần từng phần quãng đường) thay vì 1 gói tức thời,
 * kèm chỉnh "Timer" (tốc độ tick ảo) để cố né các ngưỡng thời gian. Phân
 * tích: dù trải qua nhiều tick, MỖI TICK vẫn di chuyển vài block (do số tick
 * bị giới hạn tối đa 13 dù khoảng cách bao xa), vượt xa vật lý hợp lệ. Lỗ
 * hổng THỰC SỰ: sustainedSpeedViolation (V7) trước đây CHỈ đo tốc độ NGANG -
 * nếu attacker để Timer=1 (tốc độ bình thường, né dt<30ms) và dùng module
 * để dịch chuyển CHỦ YẾU THEO CHIỀU DỌC (trèo lên cao, thoát hiểm...), né
 * được toàn bộ 3 lớp cũ (burst vì số tick quá thưa, speed-spike vì dt không
 * đủ nhỏ, sustained-speed vì chỉ xét ngang). Đã mở rộng sustained-speed đo
 * CẢ NGANG + LÊN (giữ nguyên loại trừ RƠI XUỐNG để không đụng free-fall hợp
 * lệ) - Wind Burst/knockback hợp lệ vẫn an toàn vì luôn bị chặn sớm bởi
 * nhánh miễn trừ velocity/EntityKnockbackEvent (V8-V10) trước khi chạy tới
 * bước tính sustained-speed này.
 *
 * BẢN VÁ V10: knockback vẫn không hoạt động sau V9 vì THỨ TỰ kiểm tra sai -
 * nhánh đóng băng (freeze) chạy TRƯỚC nhánh miễn trừ knockback, nên nếu
 * người chơi đang trong thời gian đóng băng (kể cả do báo nhầm trước đó) mà
 * bị đánh trúng, toạ độ X/Z bị khoá cứng ngay lập tức, triệt tiêu hoàn toàn
 * lực đẩy dù hợp lệ. Đã đảo thứ tự: kiểm tra + miễn trừ knockback LUÔN chạy
 * TRƯỚC, có hiệu lực bất kể đang đóng băng hay không. Đồng thời khi phát
 * hiện knockback hợp lệ trong lúc đang đóng băng, HUỶ LUÔN trạng thái đóng
 * băng đó (thay vì chỉ bỏ qua 1 tick) - nếu không, sau khi hết 800ms miễn
 * trừ, người chơi sẽ bị giật ngược về đúng điểm đóng băng cũ (đã lệch xa vị
 * trí thực do vừa bị knockback), trải nghiệm rất tệ.
 *
 * BẢN VÁ V9: V8 chỉ polling Player#getVelocity() mỗi tick di chuyển - có độ
 * trễ đồng bộ khiến vẫn còn báo nhầm khi dùng Wind Charge để bay lên hoặc
 * khi bị knockback. Giờ bắt TRỰC TIẾP io.papermc.paper.event.entity.
 * EntityKnockbackEvent - sự kiện Paper phát ra đúng thời điểm server quyết
 * định đẩy entity (đánh nhau, nổ, Wind Charge, Wind Burst...), làm nguồn tín
 * hiệu CHÍNH thay vì chỉ polling gián tiếp. Giữ velocity polling làm dự
 * phòng thứ 2. (Cân nhắc dùng thêm EntityDamageEvent nhưng loại bỏ vì rủi ro
 * bị lợi dụng: người chơi có thể cố ý tự gây sát thương môi trường - lửa,
 * rơi, đói... - để lấy "cửa sổ miễn trừ" rồi ClickTP trong lúc đó.)
 *
 * LƯU Ý QUAN TRỌNG: nếu sau bản vá này combo Mace (Wind Burst) vẫn bị chặn
 * ĐÚNG LÚC đòn đánh trúng, nhiều khả năng nguyên nhân KHÔNG PHẢI ClickTPCheck
 * mà là check "MaceKillCheck" (module khác, chưa có source để sửa trực tiếp) -
 * cụ thể là tính năng "mitigate-damage" trong mace-kill-check của config.yml,
 * được thiết kế để hủy đòn đánh khi phát hiện pattern "giả rơi để ăn Smash
 * Attack" (bay lên nhanh rồi quay lại gần độ cao cũ - "yo-yo pattern"). Vấn đề
 * là cơ chế Wind Burst HỢP LỆ của Mace (bay lên bằng Wind Charge KHÔNG CẦN
 * rơi thật rồi đập xuống) trông gần giống hệt pattern đó. Nếu vẫn gặp lỗi
 * này, hãy gửi MaceKillCheck.java để vá trực tiếp, hoặc tạm nới các giá trị
 * maceYoyoAscentDelta/maceReturnTolerance/maceYoyoWindowTicks trong config.yml.
 *
 * BẢN VÁ V8: sửa báo nhầm khi dùng Wind Charge (cầu gió) để bay lên, và khi
 * bị knockback/nổ "văng" đi - các cơ chế này bị chặn nhầm vì di chuyển đủ
 * nhanh/xa để chạm các ngưỡng burst/spike/sustained. Thêm 1 miễn trừ TỔNG
 * QUÁT: bất cứ khi nào Player#getVelocity() có độ lớn đáng kể, đó LUÔN LÀ
 * bằng chứng SERVER đang chủ động áp đặt vận tốc (Entity#setVelocity()) cho
 * knockback đánh nhau, nổ (TNT/Creeper/Wind Charge), Wind Burst, riptide...
 * - hoàn toàn khác với đi bộ/nhảy/ClickTP (client tự báo cáo vị trí trực
 * tiếp, velocity phía server luôn ~0, đã kiểm chứng ở các bản trước). Nhờ
 * vậy không cần dò từng cơ chế legit riêng lẻ (đã làm với Mace Wind Burst ở
 * V4) - chỉ cần 1 tín hiệu chung áp dụng cho MỌI hiệu ứng đẩy hợp lệ hiện
 * tại lẫn tương lai (kể cả các cơ chế mới của bản cập nhật sau này).
 *
 * BẢN VÁ V7 (2 sửa lỗi từ log thực tế):
 *   1) "Đi bộ tốc độ ma": phát hiện thêm click TP đều tay ở nhịp dt>=30ms
 *      (né được speed-spike vì dt hơi lớn, né được burst vì gói thưa) -
 *      mỗi lần chỉ nhảy 1-4 block ở CÙNG 1 độ cao Y cố định, nhưng nhịp đều
 *      đặn tạo tốc độ NGANG duy trì vượt xa sprint hợp lệ (~33-80 block/s
 *      so với tối đa hợp lệ ~12.5 block/s trong dữ liệu tham chiếu).
 *   2) Sửa cơ chế đóng băng: trước đây khoá CẢ TRỤC Y, nên nếu người chơi
 *      đang lơ lửng giữa không trung lúc bị đóng băng, họ bị "treo" không
 *      rơi trong suốt thời gian đóng băng -> vanilla tự kick "Flying is not
 *      enabled"/"floating too long" (đã xảy ra thực tế, xem log). Giờ CHỈ
 *      khoá X/Z, để trọng lực vẫn hoạt động bình thường theo chiều dọc -
 *      vẫn triệt tiêu hoàn toàn khả năng ClickTP ngang trong lúc đóng băng.
 *
 * BẢN VÁ V6: thêm phát hiện "TỐC ĐỘ TĂNG ĐỘT BIẾN" - dữ liệu tham chiếu mới
 * cho thấy vẫn lọt được các cú ClickTP TẦM NGẮN, ĐƠN LẺ (5-6 block, không lặp
 * lại đủ nhanh để tạo burst >4 gói/150ms nên bản V5 bỏ qua). Dấu hiệu: khoảng
 * cách di chuyển có ý nghĩa (>1.2 block) xảy ra trong thời gian NGẮN HƠN mức
 * 1 tick vanilla có thể tạo ra (dt<30ms) so với gói NGAY TRƯỚC ĐÓ của chính
 * người chơi - tương đương hàng nghìn block/giây, vật lý vanilla không có
 * cách nào tạo ra. Đã kiểm chứng: 0 báo nhầm trên dữ liệu hợp lệ (đi bộ, mace
 * combo, rơi tự do), bắt được cả 3 cú TP ngắn bị lọt lưới trong dữ liệu mới.
 *
 * BẢN VÁ V5:
 *   1) CHẶN SÂU HƠN (tại vị trí A, không phải B): các bản trước khi phát
 *      hiện vi phạm chỉ hủy gói CUỐI CÙNG và trả người chơi về "vị trí hợp
 *      lệ gần nhất" - nhưng vì thuật toán cần vài gói tích lũy trong cửa sổ
 *      mới đủ bằng chứng kết luận vi phạm (để tránh báo nhầm combo Mace Wind
 *      Burst - xem V4), các gói ĐẦU của chuỗi đã được chấp nhận là hợp lệ,
 *      khiến người chơi "trôi" gần tới B trước khi bị chặn. Bản V5 khi phát
 *      hiện vi phạm sẽ HỦY TOÀN BỘ CHUỖI trong cửa sổ, trả về đúng vị trí
 *      TRƯỚC KHI gói đầu tiên của chuỗi khả nghi xảy ra (A thật sự) - nhờ
 *      lưu lại vị trí "from" của từng gói trong cửa sổ thay vì chỉ lưu delta.
 *   2) LOG TỐC ĐỘ THỰC TẾ: tính trực tiếp từ khoảng cách di chuyển / thời
 *      gian thực giữa các gói (block/giây), KHÔNG dùng Player#getVelocity()
 *      vì ClickTP dịch chuyển bằng gói vị trí trực tiếp (không mô phỏng vật
 *      lý), nên velocity phía client gần như luôn hiển thị = 0 dù người chơi
 *      thực tế đang "nhảy" hàng chục block/giây.
 *
 * (Đã thử hạ số gói tối thiểu cần tích lũy để phát hiện nhanh hơn, nhưng
 * việc này làm báo nhầm combo Mace Wind Burst trở lại - xem dữ liệu tham
 * chiếu - nên vẫn giữ nguyên ngưỡng đó, chỉ đổi cách xử lý khi ĐÃ xác nhận
 * vi phạm để loại bỏ hoàn toàn phần "trôi" trước khi bị chặn.)
 *
 * Check này CHỈ HỦY (cancel) + đóng băng tại chỗ, không kick/ban.
 */
public class ClickTPCheck implements Listener {

    // ==== Hằng số thuật toán (suy ra từ vật lý vanilla + dữ liệu tham chiếu thực tế) ====

    private static final long WINDOW_MS = 150;
    private static final int MAX_PACKETS_IN_WINDOW = 4;
    private static final double MIN_VIOLATION_DISTANCE = 4.0;
    private static final double ABS_DIST_CAP = 8.0;
    private static final long GRACE_MS = 250;
    // Phát hiện "tốc độ tăng đột biến": khoảng cách đủ lớn xảy ra trong thời gian
    // NGẮN HƠN mức 1 tick vanilla có thể tạo ra - bắt được cả TP tầm ngắn 1 gói đơn
    // lẻ (không tạo đủ burst 5 gói để bị bắt bởi MAX_PACKETS_IN_WINDOW). Dữ liệu
    // tham chiếu: click-tp ngắn thực tế cho dt=1-2ms với khoảng cách 3.6-6.1 block
    // (tương đương 2700-6100 block/giây); dữ liệu hợp lệ (đi bộ/mace combo/rơi)
    // không có trường hợp nào dt<30ms mà khoảng cách >1.2 block.
    private static final long SPEED_SPIKE_MAX_DT_MS = 30;
    private static final double SPEED_SPIKE_MIN_DIST = 1.2;
    // Phát hiện "đi bộ tốc độ ma": bypass bằng cách click TP đều tay ở nhịp ~1 tick
    // (dt hơi lớn hơn SPEED_SPIKE_MAX_DT_MS nên né được speed-spike, và khoảng cách
    // giữa các gói đủ thưa nên né được burst) - mỗi lần chỉ nhảy 1-4 block NHƯNG với
    // tần suất đều đặn, tạo ra tốc độ DUY TRÌ liên tục vượt xa sprint hợp lệ. Chỉ xét
    // khi dt>=SPEED_SPIKE_MAX_DT_MS (bổ khuyết đúng khoảng speed-spike bỏ sót, tránh
    // đè lên vùng dt cực nhỏ dễ nhiễu). Dữ liệu tham chiếu: tốc độ ngang hợp lệ cao
    // nhất quan sát được là ~12.5 block/s (dt>=30ms); ClickTP nhịp đều cho 20-80 block/s.
    private static final double SUSTAINED_SPEED_MAX_BPS = 15.0;
    // Miễn trừ khi bị SERVER chủ động đẩy đi (knockback đánh nhau, nổ, Wind Charge,
    // Wind Burst...) - các cơ chế này áp đặt vector vận tốc qua Entity#setVelocity(),
    // KHÁC HẲN đi bộ/nhảy/ClickTP (client tự báo cáo vị trí, velocity phía server luôn
    // ~0). Bất kỳ velocity đáng kể nào cũng là bằng chứng server đang chủ động đẩy -
    // dùng làm tín hiệu miễn trừ tổng quát thay vì dò từng cơ chế riêng lẻ.
    private static final double KNOCKBACK_VELOCITY_THRESHOLD = 0.15;
    // Giữ miễn trừ thêm 1 khoảng sau cú đẩy cuối cùng, vì vận tốc giảm dần theo từng
    // tick (do drag) nhưng người chơi vẫn đang bay tiếp trên quỹ đạo hợp lệ đó. Áp
    // dụng cho cả 2 nguồn tín hiệu: EntityKnockbackEvent (bắt tại đúng thời điểm
    // server áp đặt lực) và Player#getVelocity() (dự phòng, tick sau).
    private static final long KNOCKBACK_GRACE_MS = 800;

    private final Mainplugin plugin;
    private final ConfigManager cfg;

    private final Map<UUID, Location> lastValidLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Long> teleportGraceUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> frozenUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Location> frozenLocation = new ConcurrentHashMap<>();
    // Mốc thời gian (millis) người chơi được miễn check do vừa bị knockback/nổ/Wind
    // Charge hợp lệ (server áp đặt velocity)
    private final Map<UUID, Long> knockbackGraceUntil = new ConcurrentHashMap<>();
    // Lịch sử đầy đủ (thời điểm, VỊ TRÍ TRƯỚC gói đó, khoảng cách ngang, khoảng cách đi lên)
    // trong cửa sổ gần đây - lưu cả vị trí (không chỉ delta) để có thể rollback về đúng
    // điểm bắt đầu chuỗi khi phát hiện vi phạm.
    private final Map<UUID, Deque<MoveRecord>> recentMoves = new ConcurrentHashMap<>();

    public ClickTPCheck(Mainplugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onMove(PlayerMoveEvent event) {
        if (!cfg.isClickTpCheckEnabled()) return;

        Player player = event.getPlayer();
        if (plugin.shouldBypass(player)) return;

        UUID id = player.getUniqueId();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || from.getWorld() == null || to.getWorld() == null) return;

        // Đang bị SERVER chủ động đẩy đi (knockback đánh nhau, nổ, Wind Charge, Wind
        // Burst combo Mace...) hoặc vừa mới bị đẩy gần đây -> BỎ QUA HOÀN TOÀN, kể cả
        // khi đang trong thời gian đóng băng. Đặt kiểm tra này TRƯỚC nhánh đóng băng vì
        // lực đẩy hợp lệ từ server phải luôn có hiệu lực - nếu để nhánh đóng băng chạy
        // trước, toạ độ X/Z sẽ bị khoá cứng ngay khi bị đánh trúng, triệt tiêu hoàn toàn
        // hiệu ứng knockback dù đó là knockback hợp lệ (đã xảy ra thực tế).
        long nowMsForKnockback = System.currentTimeMillis();
        double velocityLen = player.getVelocity().length();
        Long kbGraceUntil = knockbackGraceUntil.get(id);
        boolean inKnockbackGrace = kbGraceUntil != null && nowMsForKnockback < kbGraceUntil;
        if (velocityLen > KNOCKBACK_VELOCITY_THRESHOLD || inKnockbackGrace) {
            knockbackGraceUntil.put(id, nowMsForKnockback + KNOCKBACK_GRACE_MS);
            // Huỷ luôn trạng thái đóng băng cũ (nếu có) - nếu không, sau khi hết 800ms
            // miễn trừ này, người chơi sẽ bị giật ngược về đúng điểm đóng băng cũ (giờ
            // đã lệch xa vị trí thực do vừa bị knockback hợp lệ), trải nghiệm rất tệ
            frozenUntil.remove(id);
            frozenLocation.remove(id);
            lastValidLocation.put(id, to);
            recentMoves.remove(id);
            return;
        }

        // Nếu người chơi đang bị đóng băng do vi phạm trước đó -> khoá NGANG (X/Z)
        Long frozenTill = frozenUntil.get(id);
        if (frozenTill != null) {
            if (System.currentTimeMillis() < frozenTill) {
                Location lock = frozenLocation.getOrDefault(id, from);
                // CHỈ khoá X/Z, để trục Y (trọng lực) vẫn hoạt động bình thường - nếu
                // khoá cả Y trong lúc người chơi đang lơ lửng giữa không trung, vanilla
                // sẽ tự kick "Flying is not enabled"/"floating too long" sau vài giây vì
                // tưởng nhầm là bay lụi (đã xảy ra thực tế - xem log). Chặn ngang vẫn đủ
                // để triệt tiêu hoàn toàn khả năng ClickTP tiếp tục trong lúc đóng băng.
                boolean driftedXZ = Math.abs(to.getX() - lock.getX()) > 0.01
                        || Math.abs(to.getZ() - lock.getZ()) > 0.01;
                if (driftedXZ) {
                    Location corrected = to.clone();
                    corrected.setX(lock.getX());
                    corrected.setZ(lock.getZ());
                    event.setTo(corrected);
                }
                return;
            } else {
                frozenUntil.remove(id);
                frozenLocation.remove(id);
            }
        }

        // Đổi world không phải mục tiêu của check này
        if (!from.getWorld().equals(to.getWorld())) {
            lastValidLocation.put(id, to);
            recentMoves.remove(id);
            return;
        }

        // Vừa teleport/respawn/thoát phương tiện hợp lệ gần đây -> bỏ qua tick này
        Long graceUntil = teleportGraceUntil.get(id);
        if (graceUntil != null && System.currentTimeMillis() < graceUntil) {
            lastValidLocation.put(id, to);
            return;
        }

        // Các trạng thái hợp lệ có thể gây tốc độ/dịch chuyển cao - bỏ qua hoàn toàn
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()
                || player.getVehicle() != null || isImmuneByEffect(player)) {
            lastValidLocation.put(id, to);
            recentMoves.remove(id);
            return;
        }

        double horizontalDist = Math.sqrt(square(to.getX() - from.getX()) + square(to.getZ() - from.getZ()));
        double verticalDelta = to.getY() - from.getY();
        double ascendDist = Math.max(0, verticalDelta);
        double dist3d = Math.sqrt(square(horizontalDist) + square(verticalDelta));

        long nowMs = System.currentTimeMillis();
        Deque<MoveRecord> moves = recentMoves.computeIfAbsent(id, k -> new ArrayDeque<>());

        MoveRecord previous = moves.peekLast(); // gói ngay trước gói hiện tại (để tính tốc độ tức thời)
        moves.addLast(new MoveRecord(nowMs, from.clone(), horizontalDist, ascendDist));
        while (!moves.isEmpty() && nowMs - moves.peekFirst().timeMs > WINDOW_MS) {
            moves.pollFirst();
        }

        double windowDistance = 0;
        int eventCount = 0;
        for (MoveRecord m : moves) {
            windowDistance += m.horizontalDist + m.ascendDist;
            eventCount++;
        }

        boolean burstViolation = eventCount > MAX_PACKETS_IN_WINDOW && windowDistance > MIN_VIOLATION_DISTANCE;
        boolean singleEventViolation = dist3d > ABS_DIST_CAP;

        // Tốc độ tức thời so với gói NGAY TRƯỚC đó của chính người chơi này - dùng để
        // bắt các cú TP tầm ngắn đơn lẻ (không đủ tạo burst) nhưng dt quá nhỏ so với
        // khoảng cách đã đi (vật lý vanilla không thể tạo ra tỉ lệ này)
        long instantDtMs = previous != null ? Math.max(1, nowMs - previous.timeMs) : Long.MAX_VALUE;
        boolean speedSpikeViolation = instantDtMs < SPEED_SPIKE_MAX_DT_MS && dist3d > SPEED_SPIKE_MIN_DIST;

        // "Đi bộ tốc độ ma" / "trèo tốc độ ma": click đều tay ở dt>=30ms nhưng tốc độ
        // NGANG + LÊN (không tính rơi) duy trì vượt ngưỡng hợp lệ - bổ khuyết vùng dt mà
        // speedSpikeViolation không xét tới. Trước đây chỉ đo tốc độ NGANG, để lọt module
        // "Teleport" (dịch chuyển trải qua nhiều tick) khi dùng CHỦ YẾU THEO CHIỀU DỌC ở
        // tốc độ tick bình thường (không tăng Timer) - né được cả burst (số tick quá thưa)
        // lẫn speed-spike (dt không đủ nhỏ). Wind Burst/knockback hợp lệ luôn được nhánh
        // miễn trừ velocity ở TRÊN xử lý và return sớm trước khi chạy tới đây, nên không
        // bị ảnh hưởng bởi việc mở rộng này.
        double instantUpwardDist = horizontalDist + ascendDist;
        double instantSustainedSpeedBps = (previous != null && instantDtMs < Long.MAX_VALUE)
                ? instantUpwardDist / (instantDtMs / 1000.0) : 0;
        boolean sustainedSpeedViolation = instantDtMs >= SPEED_SPIKE_MAX_DT_MS
                && instantSustainedSpeedBps > SUSTAINED_SPEED_MAX_BPS;

        if (burstViolation || singleEventViolation || speedSpikeViolation || sustainedSpeedViolation) {
            // CHẶN TẠI VỊ TRÍ A: nếu là vi phạm dạng dồn gói (burst), lấy vị trí TRƯỚC gói
            // ĐẦU TIÊN còn trong cửa sổ (tức trước khi cả chuỗi bắt đầu) - không phải chỉ
            // trước gói cuối cùng, để huỷ toàn bộ đoạn "trôi" chứ không chỉ chặn phần đuôi.
            // Nếu là vi phạm dạng 1 gói đơn lẻ (nhảy quá xa hoặc tốc độ đột biến),
            // "from" của chính gói đó đã là A rồi.
            Location safe = (burstViolation ? moves.peekFirst().from : from).clone();

            // Tính tốc độ THỰC TẾ từ khoảng cách/thời gian thực - không dùng
            // Player#getVelocity() vì client hiển thị gần như 0 trong lúc ClickTP hoạt động
            double instantSpeedBps = dist3d / (Math.min(instantDtMs, 50_000L) / 1000.0);
            long windowSpanMs = Math.max(1, nowMs - moves.peekFirst().timeMs);
            double windowSpeedBps = windowDistance / (windowSpanMs / 1000.0);
            double correctedDistance = safe.distance(to);

            event.setTo(safe);
            player.teleportAsync(safe);
            moves.clear();

            int freezeTicks = cfg.getClickTpFreezeTicks();
            if (freezeTicks > 0) {
                frozenUntil.put(id, nowMs + freezeTicks * 50L);
                frozenLocation.put(id, safe);
            }

            String violationType = singleEventViolation ? "single-jump"
                    : (speedSpikeViolation ? "speed-spike"
                    : (sustainedSpeedViolation ? "sustained-speed" : "packet-burst"));

            if (cfg.isLogToConsole()) {
                plugin.getLogger().warning("[ClickTP] Da chan tai vi tri goc cua " + player.getName()
                        + " (goi trong " + WINDOW_MS + "ms=" + eventCount
                        + ", tong khoang cach=" + fmt(windowDistance)
                        + ", dist3d 1 goi=" + fmt(dist3d)
                        + ", toc do tuc thoi=" + fmt(instantSpeedBps) + " block/s"
                        + ", toc do trung binh(" + WINDOW_MS + "ms)=" + fmt(windowSpeedBps) + " block/s"
                        + ", da huy=" + fmt(correctedDistance) + " block"
                        + ", loai=" + violationType
                        + ", dong bang=" + freezeTicks + " tick)");
            }

            if (cfg.isClickTpNotifyPlayer()) {
                Message.send(player, "warning.clicktp-detected");
            }
            return;
        }

        lastValidLocation.put(id, to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onKnockback(EntityKnockbackEvent event) {
        // Bắt TRỰC TIẾP tại đúng thời điểm server quyết định đẩy người chơi - đáng tin
        // cậy hơn nhiều so với chỉ polling Player#getVelocity() mỗi tick di chuyển, vì
        // không phụ thuộc độ trễ đồng bộ giữa lúc lực được áp đặt và lúc tick di chuyển
        // tiếp theo mới đọc được giá trị đó. Bắt được MỌI nguồn: đánh nhau (kể cả Wind
        // Burst của Mace), nổ, Wind Charge, và các cơ chế đẩy khác trong tương lai.
        if (!(event.getEntity() instanceof Player player)) return;
        knockbackGraceUntil.put(player.getUniqueId(), System.currentTimeMillis() + KNOCKBACK_GRACE_MS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        teleportGraceUntil.put(id, System.currentTimeMillis() + GRACE_MS);
        frozenUntil.remove(id);
        frozenLocation.remove(id);
        knockbackGraceUntil.remove(id);
        recentMoves.remove(id);
        if (event.getTo() != null) {
            lastValidLocation.put(id, event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        teleportGraceUntil.put(id, System.currentTimeMillis() + GRACE_MS);
        lastValidLocation.put(id, event.getRespawnLocation());
        recentMoves.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;
        teleportGraceUntil.put(player.getUniqueId(), System.currentTimeMillis() + GRACE_MS);
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lastValidLocation.put(player.getUniqueId(), player.getLocation());
        teleportGraceUntil.put(player.getUniqueId(), System.currentTimeMillis() + GRACE_MS);
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastValidLocation.remove(id);
        teleportGraceUntil.remove(id);
        frozenUntil.remove(id);
        frozenLocation.remove(id);
        knockbackGraceUntil.remove(id);
        recentMoves.remove(id);
    }

    private boolean isImmuneByEffect(Player player) {
        return player.hasPotionEffect(PotionEffectType.LEVITATION);
    }

    private static double square(double v) {
        return v * v;
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    /** Bản ghi 1 gói di chuyển: thời điểm, vị trí TRƯỚC gói đó, và khoảng cách đã đi. */
    private static final class MoveRecord {
        final long timeMs;
        final Location from;
        final double horizontalDist;
        final double ascendDist;

        MoveRecord(long timeMs, Location from, double horizontalDist, double ascendDist) {
            this.timeMs = timeMs;
            this.from = from;
            this.horizontalDist = horizontalDist;
            this.ascendDist = ascendDist;
        }
    }
}