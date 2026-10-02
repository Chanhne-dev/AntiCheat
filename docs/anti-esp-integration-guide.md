# Hướng dẫn tích hợp Entity-Culling Anti-ESP vào plugin AntiCheat có sẵn

Tài liệu này hướng dẫn cách gắn logic ẩn/hiện entity theo line-of-sight (LOS)
vào một plugin anticheat đã tồn tại, chạy trên Folia (build 26.x). Mục tiêu:
plugin AntiCheat của bạn gọi được API culling như một module con, không cần
viết lại toàn bộ hệ thống scheduler/event của bạn.

---

## 1. Nguyên lý cốt lõi (đọc trước khi tích hợp)

- Đây **không phải** một "detector" — không có sự kiện `EspDetectedEvent` nào
  cả. Nó ngăn ESP bằng cách server không gửi packet entity cho những gì
  player thật sự không nhìn thấy được, nên client không có dữ liệu để vẽ ESP.
- Vì vậy tích hợp vào AntiCheat của bạn theo hướng: **chạy song song, độc
  lập** với module phát hiện hành vi (aim, reach, killaura...). Nó không cần
  biết gì về violation-score hay punishment pipeline của bạn.
- Ràng buộc Folia bắt buộc: mọi thao tác đọc world quanh player và gọi
  `hideEntity`/`showEntity` phải chạy trên region thread sở hữu player đó
  tại thời điểm gọi. Không được cache thread hay gọi từ
  `Bukkit.getScheduler()`.

---

## 2. Cách đóng gói thành module độc lập

Khuyến nghị tách thành **addon/submodule riêng** thay vì nhét thẳng vào core
AntiCheat, vì lifecycle (start/stop theo dõi từng player) khác hẳn lifecycle
chấm điểm vi phạm:

```
your-anticheat/
├── core/                      (đã có: check manager, punishment, config...)
└── modules/
    └── esp-culling/
        ├── EntityVisibilityManager.java   (đã có, xem tin nhắn trước)
        └── EspCullingModule.java          (lớp cầu nối — xem mục 3)
```

Nếu core AntiCheat của bạn có sẵn khái niệm "Module"/"Check" (interface với
`enable()`/`disable()`), hãy implement lại `EspCullingModule` theo interface
đó thay vì tự quản lý lifecycle. Ví dụ interface giả định:

```java
public interface AntiCheatModule {
    void onEnable();
    void onDisable();
}
```

---

## 3. Lớp cầu nối (bridge) — điểm tích hợp chính

```java
public final class EspCullingModule implements AntiCheatModule {

    private final YourAntiCheatPlugin plugin;   // plugin core của bạn
    private final EntityVisibilityManager visibilityManager;

    public EspCullingModule(YourAntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.visibilityManager = new EntityVisibilityManager(plugin);
    }

    @Override
    public void onEnable() {
        // Hook vào listener PlayerJoin/Quit đã có sẵn trong core của bạn,
        // KHÔNG đăng ký listener trùng lặp nếu core đã có PlayerLifecycleService.
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            visibilityManager.startTracking(p);
        }
    }

    @Override
    public void onDisable() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            visibilityManager.stopTracking(p);
        }
    }

    // Gọi từ listener PlayerJoinEvent đã có sẵn trong core
    public void onPlayerJoin(Player player) {
        visibilityManager.startTracking(player);
    }

    // Gọi từ listener PlayerQuitEvent đã có sẵn trong core
    public void onPlayerQuit(Player player) {
        visibilityManager.stopTracking(player);
    }
}
```

Trong `onEnable()` của plugin AntiCheat chính:

```java
this.espCullingModule = new EspCullingModule(this);
this.espCullingModule.onEnable();
```

Và trong listener `PlayerJoinEvent`/`PlayerQuitEvent` **đã tồn tại sẵn trong
core**, chỉ cần thêm một dòng gọi sang module thay vì đăng ký listener mới —
tránh hai hệ thống tranh nhau xử lý cùng sự kiện.

---

## 4. Config hóa các tham số

Đưa các hằng số trong `AntiEspPlugin` (`SCAN_RADIUS`, `MIN_CULL_DISTANCE`,
`SCAN_PERIOD_TICKS`) vào file config chung của AntiCheat, ví dụ
`config.yml`:

```yaml
modules:
  esp-culling:
    enabled: true
    scan-radius: 48.0        # block, nên <= view-distance * 16
    min-cull-distance: 6.0   # block, tránh flicker khi combat gần
    scan-period-ticks: 4     # 4 tick = 5 lần/giây mỗi player
```

Đọc config này khi khởi tạo `EntityVisibilityManager`, truyền vào qua
constructor thay vì dùng `static final` cứng, để admin chỉnh được mà không
cần build lại plugin.

---

## 5. Điểm cần rà soát khi ghép vào codebase có sẵn

| Rủi ro | Cách kiểm tra |
|---|---|
| Core AntiCheat đã tự quản lý `hideEntity`/`showEntity` (vd. cho tính năng vanish/spectator) | Phải hợp nhất logic — hai module cùng gọi `hideEntity` trên cùng entity sẽ ghi đè trạng thái lẫn nhau. Thêm một `VisibilityStateOwner` chung để tránh xung đột. |
| Core dùng `BukkitScheduler`/`BukkitRunnable` ở đâu đó cho task theo player | Không được để hai kiểu scheduler (Bukkit cũ và Folia EntityScheduler) cùng thao tác trên entity — sẽ gây exception khi chạy trên Folia. Audit toàn bộ `runTaskTimer`, `runTaskLater` liên quan đến player/entity. |
| Core có hệ thống "freeze/teleport-back" khi phát hiện vi phạm | Thao tác teleport/spawn packet đó cũng phải chạy qua `EntityScheduler` của entity liên quan, không phải scheduler global. |
| ProtocolLib injection (nếu core AntiCheat dùng packet listener để đọc/ghi gói tin thô) | Đảm bảo bản ProtocolLib hỗ trợ Folia (`getNewEntityTracker` cho Folia, có từ 5.1.0 trở lên), nếu không hai lớp (Bukkit API culling + ProtocolLib packet) có thể đá nhau về thứ tự gói tin. |

---

## 6. Checklist kiểm thử sau khi tích hợp

- [ ] Build thành công, plugin.yml của bạn có `folia-supported: true`.
- [ ] Hai player đứng hai bên tường dày → không thấy nhau; bước ra khỏi góc
      khuất → hiện lại trong < 1 giây.
- [ ] Combat cự ly gần (< `min-cull-distance`) không bị giật/flicker.
- [ ] Player teleport (lệnh `/tp`, plugin warp...) sang world/region khác
      không làm crash hoặc treo task (kiểm tra log không có
      `IllegalStateException: not owned by current region`).
- [ ] Reload plugin / `/antiCheat reload` không để lại entity bị ẩn vĩnh
      viễn (trạng thái phải được trả lại trước khi module tắt).
- [ ] Test với đúng chức năng vanish/spectator có sẵn trong AntiCheat — vẫn
      hoạt động đúng, không bị module ESP-culling ghi đè.
- [ ] Đo TPS/MSPT trước và sau khi bật module với ~50-100 player giả lập,
      xác nhận không gây tụt tick đáng kể (nếu có, tăng
      `scan-period-ticks` hoặc giảm `scan-radius`).

---

## 7. Mở rộng (tùy chọn)

Nếu core AntiCheat của bạn có bus sự kiện nội bộ, có thể bắn thêm sự kiện
nội bộ (không phải phát hiện cheat, chỉ là thông tin trạng thái) mỗi khi một
entity chuyển từ hidden ↔ visible, để các module khác (vd. HUD debug cho
staff, hoặc thống kê hiệu năng) có thể lắng nghe mà không cần đọc thẳng vào
`EntityVisibilityManager`.
