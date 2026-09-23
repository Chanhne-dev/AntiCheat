package chanhne.AntiCheat.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.messages.Message;

import java.util.*;

public class ConfigManager {

    private final Mainplugin plugin;
    private FileConfiguration config;

    // Cache
    private Set<String> bannedItems;
    private Map<Enchantment, Integer> maxEnchantLevels;
    private Set<String> invalidEnchantItems;
    private int maxPotionAmplifier;
    private int scanInterval;
    private int banDurationMinutes;
    private boolean itemBanEnabled;
    private int itemViolationThreshold;
    private boolean logToConsole;
    private boolean notifyAdmins;
    private String prefix;

    // Movement (AnHero pattern) check settings
    private boolean movementCheckEnabled;
    private double phantomAscentDelta;
    private int ascentTicksRequired;
    private double phantomDescentDelta;
    private int descentTicksRequired;
    private int linkWindowTicks;
    private int movementViolationThreshold;
    private boolean movementBanEnabled;
    private boolean movementKickOnDetect;
    private String movementKickReason;
    private String movementOffenseType;

    // MaceKill (fall-distance spoof / smash attack) check settings
    private boolean maceKillCheckEnabled;
    private double maceSmashFallThreshold;
    private double maceYoyoAscentDelta;
    private int maceYoyoWindowTicks;
    private double maceReturnTolerance;
    private int maceViolationThreshold;
    private boolean maceBanEnabled;
    private boolean maceMitigateDamage;
    private boolean maceKickOnDetect;
    private String maceKickReason;
    private String maceOffenseType;

    // BoatNoclip check settings
    private boolean boatNoclipCheckEnabled;
    private int boatStuckTicksRequired;
    private int boatViolationThreshold;
    private boolean boatBanEnabled;
    private boolean boatKickOnDetect;
    private String boatKickReason;
    private String boatOffenseType;

    // FreeBoatRide check settings (tích hợp trong BoatNoclipCheck)
    private boolean freeBoatRideCheckEnabled;
    private double freeBoatRideDropDelta;
    private int freeBoatRideDropStreakRequired;
    private boolean freeBoatRideKickOnDetect;
    private String freeBoatRideKickReason;
    private int freeBoatRideViolationThreshold;
    private boolean freeBoatRideBanEnabled;
    private String freeBoatRideOffenseType;
    private boolean freeBoatRideProtectPassengers;

    // CrossbowMachineGun check settings (cancel + kick, không ban)
    private boolean crossbowCheckEnabled;
    private double crossbowToleranceRatio;
    private boolean crossbowKickOnDetect;
    private String crossbowKickReason;

    // BetterScaffold check settings (cancel-only, không kick/ban)
    private boolean scaffoldCheckEnabled;
    private int scaffoldBurstCountThreshold;
    private long scaffoldBurstWindowMillis;
    private long scaffoldTowerMinIntervalMillis;
    private int scaffoldTowerStreakRequired;
    private boolean scaffoldKickOnBurst;
    private String scaffoldKickReason;

    // ClickTP (raycast teleport exploit) check settings (cancel-only, không kick/ban)
    // Lưu ý: ngưỡng phát hiện (cửa sổ thời gian, khoảng cách...) được suy ra từ vật lý
    // vanilla + dữ liệu tham chiếu thực tế, hardcode thẳng trong ClickTPCheck.java -
    // không cần expose ra config vì admin không cần (và không nên) tự chỉnh mấy giá trị đó.
    private boolean clickTpCheckEnabled;
    private int clickTpFreezeTicks;
    private boolean clickTpNotifyPlayer;

    // MovementLogger (công cụ ghi log RAW di chuyển để lấy dữ liệu tham chiếu, không phải check)
    private boolean movementLoggerEnabled;
    private String movementLoggerFileName;
    private double movementLoggerMinDistance;
    private int movementLoggerFlushIntervalSeconds;
    private int movementLoggerMaxQueueSize;

    // TPFly check settings
    private boolean flyCheckEnabled;
    private double tpFlyMaxTeleportDistance;
    private double tpFlyMaxVerticalDelta;          // onGround
    private double tpFlyMaxVerticalDeltaAir;       // không onGround (bay lên từ giữa không trung)
    private int tpFlyViolationThreshold;
    private boolean flyBanEnabled;
    private boolean flyKickOnDetect;
    private String tpFlyKickReason;
    private String tpFlyOffenseType;
    private double tpFlyMinVelocityToBypass;

    // MeteorFly check settings (phát hiện Fly hack kiểu module Flight của Meteor Client)
    private boolean meteorFlyDebug;
    private double meteorFlyCheckOnlyBelowY;
    private int meteorFlyGraceTicks;
    private int meteorFlyAscendTicksThreshold;
    private double meteorFlyAscendMinDelta;
    private int meteorFlyHoverTicksThreshold;
    private double meteorFlyHoverMaxDelta;
    private int meteorFlyMicrofallTicksThreshold;
    private double meteorFlyMicrofallExpectedDelta;
    private double meteorFlyMicrofallTolerance;
    private double meteorFlyMinVelocityToBypass;
    private int meteorFlyMaceExemptTicks;
    private int meteorFlyJoinGraceTicks;
    private double meteorFlyJoinMaxFallDistance;
    private double meteorFlyJoinFallDistanceBypass;
    private int meteorFlyViolationThreshold;
    private String meteorFlyKickReason;
    private String meteorFlyOffenseType;

    // AntiVoid check settings (phát hiện module "nảy" lại tránh rơi xuống void)
    private boolean antiVoidCheckEnabled;
    private boolean antiVoidDebug;
    private double antiVoidZoneMargin;
    private double antiVoidBounceMinDelta;
    private double antiVoidBounceMinDeltaJump;
    private int antiVoidBounceWindowTicks;
    private int antiVoidBounceCountThreshold;
    private int antiVoidViolationThreshold;
    private boolean antiVoidBanEnabled;
    private boolean antiVoidKickOnDetect;
    private String antiVoidKickReason;
    private String antiVoidOffenseType;

    // AntiAutoTotem check settings (phát hiện refill totem nhanh bất thường sau khi cứu mạng)
    private boolean antiAutoTotemCheckEnabled;
    private boolean antiAutoTotemDebug;
    private int antiAutoTotemMinRefillTicks;
    private int antiAutoTotemManualActionGraceTicks;
    private int antiAutoTotemViolationThreshold;
    private boolean antiAutoTotemBanEnabled;
    private boolean antiAutoTotemKickOnDetect;
    private String antiAutoTotemKickReason;
    private String antiAutoTotemOffenseType;

    // AntiWindChargeJump check settings (phát hiện tự động nhảy sau khi ném Wind Charge)
    private boolean antiWindChargeJumpCheckEnabled;
    private boolean antiWindChargeJumpDebug;
    private double antiWindChargeJumpMinPitch;
    private double antiWindChargeJumpMaxHeightGain;
    private int antiWindChargeJumpMaxSessionTicks;
    private int antiWindChargeJumpViolationThreshold;
    private boolean antiWindChargeJumpBanEnabled;
    private boolean antiWindChargeJumpKickOnDetect;
    private String antiWindChargeJumpKickReason;
    private String antiWindChargeJumpOffenseType;

    // Speed check
    private boolean speedCheckEnabled;
    private int speedFreezeTicks;
    private double speedMinBps;
    private double speedMinVelocityToBypass;
    private int speedViolationThreshold;
    private boolean speedBanEnabled;
    private boolean speedKickOnDetect;
    private String speedKickReason;
    private String speedOffenseType;

    // block-command check
    private boolean blockCommandEnabled;
    private Set<String> blockedCommands;

    public ConfigManager(Mainplugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    private void load() {
        config = plugin.getConfig();

        // Load banned items
        bannedItems = new HashSet<>();
        List<String> bannedList = config.getStringList("banned-items");
        for (String item : bannedList) {
            bannedItems.add(item.toUpperCase());
        }

        // Load max enchant levels
        maxEnchantLevels = new HashMap<>();
        if (config.isConfigurationSection("max-enchant-levels")) {
            for (String key : config.getConfigurationSection("max-enchant-levels").getKeys(false)) {
                int level = config.getInt("max-enchant-levels." + key, 0);
                try {
                    Enchantment ench = getEnchantmentByName(key);
                    if (ench != null) {
                        maxEnchantLevels.put(ench, level);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Enchantment không hợp lệ trong config: " + key);
                }
            }
        }

        // Load invalid enchant items
        invalidEnchantItems = new HashSet<>();
        List<String> invalidList = config.getStringList("invalid-enchant-items");
        for (String item : invalidList) {
            invalidEnchantItems.add(item.toUpperCase());
        }

        // Load block-command settings
        blockCommandEnabled = config.getBoolean("block-command.enabled", true);
        blockedCommands = new HashSet<>();

        for (String command : config.getStringList("block-command.commands")) {
            String normalized = command.trim().toLowerCase(Locale.ROOT);

            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }

            if (!normalized.isEmpty()) {
                blockedCommands.add(normalized);
            }
        }

        // Load settings
        maxPotionAmplifier = config.getInt("max-potion-levels.max-amplifier", 1);
        scanInterval = config.getInt("settings.scan-interval", 40);
        banDurationMinutes = config.getInt("settings.ban-duration-minutes", 1);
        itemBanEnabled = config.getBoolean("settings.item-ban-enabled", true);
        itemViolationThreshold = config.getInt("settings.item-violation-threshold", 1);
        logToConsole = config.getBoolean("settings.log-to-console", true);
        notifyAdmins = config.getBoolean("settings.notify-admins", true);
        prefix = Message.get("prefix");

        // Movement (AnHero pattern) check settings
        movementCheckEnabled = config.getBoolean("movement-check.enabled", true);
        phantomAscentDelta = config.getDouble("movement-check.phantom-ascent-delta", 3.0);
        ascentTicksRequired = config.getInt("movement-check.ascent-ticks-required", 3);
        phantomDescentDelta = config.getDouble("movement-check.phantom-descent-delta", -6.0);
        descentTicksRequired = config.getInt("movement-check.descent-ticks-required", 2);
        linkWindowTicks = config.getInt("movement-check.link-window-ticks", 20);
        movementViolationThreshold = config.getInt("movement-check.violation-threshold", 2);
        movementBanEnabled = config.getBoolean("movement-check.ban-enabled", true);
        movementKickOnDetect = config.getBoolean("movement-check.kick-on-detect", true);
        movementKickReason = config.getString("movement-check.kick-reason", "Phát hiện di chuyển bất thường (AnHero pattern)");
        movementOffenseType = config.getString("movement-check.offense-type", "AnHero");

        // MaceKill (fall-distance spoof / smash attack) check settings
        maceKillCheckEnabled = config.getBoolean("mace-kill-check.enabled", true);
        maceSmashFallThreshold = config.getDouble("mace-kill-check.smash-fall-distance-threshold", 1.5);
        maceYoyoAscentDelta = config.getDouble("mace-kill-check.yoyo-ascent-delta", 3.0);
        maceYoyoWindowTicks = config.getInt("mace-kill-check.yoyo-window-ticks", 6);
        maceReturnTolerance = config.getDouble("mace-kill-check.return-tolerance", 1.0);
        maceViolationThreshold = config.getInt("mace-kill-check.violation-threshold", 2);
        maceBanEnabled = config.getBoolean("mace-kill-check.ban-enabled", true);
        maceMitigateDamage = config.getBoolean("mace-kill-check.mitigate-damage", true);
        maceKickOnDetect = config.getBoolean("mace-kill-check.kick-on-detect", true);
        maceKickReason = config.getString("mace-kill-check.kick-reason", "Phát hiện MaceKill (spoof fall damage)");
        maceOffenseType = config.getString("mace-kill-check.offense-type", "MaceKill");

        // BoatNoclip check settings
        boatNoclipCheckEnabled = config.getBoolean("boat-noclip-check.enabled", true);
        boatStuckTicksRequired = config.getInt("boat-noclip-check.stuck-ticks-required", 3);
        boatViolationThreshold = config.getInt("boat-noclip-check.violation-threshold", 2);
        boatBanEnabled = config.getBoolean("boat-noclip-check.ban-enabled", true);
        boatKickOnDetect = config.getBoolean("boat-noclip-check.kick-on-detect", true);
        boatKickReason = config.getString("boat-noclip-check.kick-reason", "Phát hiện BoatNoclip (thuyền xuyên block)");
        boatOffenseType = config.getString("boat-noclip-check.offense-type", "BoatNoclip");

        // FreeBoatRide check settings (tích hợp trong BoatNoclipCheck)
        freeBoatRideCheckEnabled = config.getBoolean("boat-noclip-check.freeboatride.enabled", true);
        freeBoatRideDropDelta = config.getDouble("boat-noclip-check.freeboatride.passenger-drop-delta", 3.0);
        freeBoatRideDropStreakRequired = config.getInt("boat-noclip-check.freeboatride.passenger-drop-streak-required", 1);
        freeBoatRideKickOnDetect = config.getBoolean("boat-noclip-check.freeboatride.kick-on-detect", true);
        freeBoatRideKickReason = config.getString("boat-noclip-check.freeboatride.kick-reason", "Phát hiện FreeBoatRide (kéo người chơi khác xuống hố)");
        freeBoatRideViolationThreshold = config.getInt("boat-noclip-check.freeboatride.violation-threshold", 1);
        freeBoatRideBanEnabled = config.getBoolean("boat-noclip-check.freeboatride.ban-enabled", true);
        freeBoatRideOffenseType = config.getString("boat-noclip-check.freeboatride.offense-type", "FreeBoatRide");
        freeBoatRideProtectPassengers = config.getBoolean("boat-noclip-check.freeboatride.protect-passengers", true);

        // CrossbowMachineGun check settings (cancel + kick, không ban)
        crossbowCheckEnabled = config.getBoolean("crossbow-check.enabled", true);
        crossbowToleranceRatio = config.getDouble("crossbow-check.tolerance-ratio", 0.85);
        crossbowKickOnDetect = config.getBoolean("crossbow-check.kick-on-detect", true);
        crossbowKickReason = config.getString("crossbow-check.kick-reason", "Phát hiện bắn nỏ liên thanh bất thường (CrossbowMachineGun)");

        // BetterScaffold check settings (cancel-only, không kick/ban)
        scaffoldCheckEnabled = config.getBoolean("scaffold-check.enabled", true);
        scaffoldBurstCountThreshold = config.getInt("scaffold-check.burst-count-threshold", 3);
        scaffoldBurstWindowMillis = config.getLong("scaffold-check.burst-window-millis", 120);
        scaffoldTowerMinIntervalMillis = config.getLong("scaffold-check.tower-min-interval-millis", 250);
        scaffoldTowerStreakRequired = config.getInt("scaffold-check.tower-streak-required", 2);
        scaffoldKickOnBurst = config.getBoolean("scaffold-check.kick-on-burst", true);
        scaffoldKickReason = config.getString("scaffold-check.kick-reason", "Phát hiện đặt nhiều block cùng lúc bất thường (BetterScaffold)");

        // ClickTP (raycast teleport exploit) check settings (cancel-only, không kick/ban)
        clickTpCheckEnabled = config.getBoolean("click-tp-check.enabled", true);
        clickTpFreezeTicks = config.getInt("click-tp-check.freeze-ticks", 50);
        clickTpNotifyPlayer = config.getBoolean("click-tp-check.notify-player", true);

        // MovementLogger settings
        movementLoggerEnabled = config.getBoolean("movement-logger.enabled", false);
        movementLoggerFileName = config.getString("movement-logger.file-name", "movement-log.csv");
        movementLoggerMinDistance = config.getDouble("movement-logger.min-distance-to-log", 0.05);
        movementLoggerFlushIntervalSeconds = config.getInt("movement-logger.flush-interval-seconds", 2);
        movementLoggerMaxQueueSize = config.getInt("movement-logger.max-queue-size", 50000);

        // TPFly
        flyCheckEnabled = config.getBoolean("fly-check.enabled", true);
        tpFlyMaxTeleportDistance = config.getDouble("fly-check.teleport.max-teleport-distance", 2.5);
        tpFlyMaxVerticalDelta = config.getDouble("fly-check.teleport.max-vertical-delta", 1.0);
        tpFlyMaxVerticalDeltaAir = config.getDouble("fly-check.teleport.max-vertical-delta-air", 1.5);
        tpFlyViolationThreshold = config.getInt("fly-check.violation-threshold", 1);
        flyBanEnabled = config.getBoolean("fly-check.ban-enabled", true);
        flyKickOnDetect = config.getBoolean("fly-check.kick-on-detect", true);
        tpFlyKickReason = config.getString("fly-check.kick-reason", "Phát hiện Fly hack (dịch chuyển/bay bất thường)");
        tpFlyOffenseType = config.getString("fly-check.offense-type", "Fly");
        tpFlyMinVelocityToBypass = config.getDouble("fly-check.teleport.min-velocity-to-bypass", 0.2);

        // MeteorFly
        meteorFlyDebug = config.getBoolean("fly-check.hover.debug", false);
        meteorFlyCheckOnlyBelowY = config.getDouble("fly-check.hover.check-only-below-y", -2032.0);
        meteorFlyGraceTicks = config.getInt("fly-check.hover.grace-ticks", 10);
        meteorFlyAscendTicksThreshold = config.getInt("fly-check.hover.ascend-ticks-threshold", 8);
        meteorFlyAscendMinDelta = config.getDouble("fly-check.hover.ascend-min-delta", 0.02);
        meteorFlyHoverTicksThreshold = config.getInt("fly-check.hover.hover-ticks-threshold", 12);
        meteorFlyHoverMaxDelta = config.getDouble("fly-check.hover.hover-max-delta", 0.02);
        meteorFlyMicrofallTicksThreshold = config.getInt("fly-check.hover.microfall-ticks-threshold", 8);
        meteorFlyMicrofallExpectedDelta = config.getDouble("fly-check.hover.microfall-expected-delta", -0.03130);
        meteorFlyMicrofallTolerance = config.getDouble("fly-check.hover.microfall-tolerance", 0.0008);
        meteorFlyMinVelocityToBypass = config.getDouble("fly-check.hover.min-velocity-to-bypass", 0.2);
        meteorFlyMaceExemptTicks = config.getInt("fly-check.hover.mace-exempt-ticks", 30);
        meteorFlyJoinGraceTicks = config.getInt("fly-check.hover.join-grace-ticks", 10);
        meteorFlyJoinMaxFallDistance = config.getDouble("fly-check.hover.join-max-fall-distance", 1.0);
        meteorFlyJoinFallDistanceBypass = config.getDouble("fly-check.hover.join-fall-distance-bypass", 3.0);
        meteorFlyViolationThreshold = config.getInt("fly-check.violation-threshold", 1);
        meteorFlyKickReason = config.getString("fly-check.kick-reason", "Phát hiện Fly hack (dịch chuyển/bay bất thường)");
        meteorFlyOffenseType = config.getString("fly-check.offense-type", "Fly");

        // AntiVoid
        antiVoidCheckEnabled = config.getBoolean("antivoid-check.enabled", true);
        antiVoidDebug = config.getBoolean("antivoid-check.debug", false);
        antiVoidZoneMargin = config.getDouble("antivoid-check.void-zone-margin", 8.0);
        antiVoidBounceMinDelta = config.getDouble("antivoid-check.bounce-min-delta", 0.1);
        antiVoidBounceMinDeltaJump = config.getDouble("antivoid-check.bounce-min-delta-jump", 0.3);
        antiVoidBounceWindowTicks = config.getInt("antivoid-check.bounce-window-ticks", 100);
        antiVoidBounceCountThreshold = config.getInt("antivoid-check.bounce-count-threshold", 2);
        antiVoidViolationThreshold = config.getInt("antivoid-check.violation-threshold", 1);
        antiVoidBanEnabled = config.getBoolean("antivoid-check.ban-enabled", true);
        antiVoidKickOnDetect = config.getBoolean("antivoid-check.kick-on-detect", true);
        antiVoidKickReason = config.getString("antivoid-check.kick-reason", "Phát hiện AntiVoid (né sát thương void bất thường)");
        antiVoidOffenseType = config.getString("antivoid-check.offense-type", "AntiVoid");

        // AntiAutoTotem
        antiAutoTotemCheckEnabled = config.getBoolean("antiautototem-check.enabled", true);
        antiAutoTotemDebug = config.getBoolean("antiautototem-check.debug", false);
        antiAutoTotemMinRefillTicks = config.getInt("antiautototem-check.min-refill-ticks", 3);
        antiAutoTotemManualActionGraceTicks = config.getInt("antiautototem-check.manual-action-grace-ticks", 3);
        antiAutoTotemViolationThreshold = config.getInt("antiautototem-check.violation-threshold", 2);
        antiAutoTotemBanEnabled = config.getBoolean("antiautototem-check.ban-enabled", true);
        antiAutoTotemKickOnDetect = config.getBoolean("antiautototem-check.kick-on-detect", true);
        antiAutoTotemKickReason = config.getString("antiautototem-check.kick-reason", "Phát hiện AutoTotem (refill totem bất thường)");
        antiAutoTotemOffenseType = config.getString("antiautototem-check.offense-type", "AutoTotem");

        // AntiWindChargeJump
        antiWindChargeJumpCheckEnabled = config.getBoolean("antiwindchargejump-check.enabled", true);
        antiWindChargeJumpDebug = config.getBoolean("antiwindchargejump-check.debug", false);
        antiWindChargeJumpMinPitch = config.getDouble("antiwindchargejump-check.min-pitch", 50.0);
        // Độ cao tối đa hợp lý (block) cho 1 phiên bay tự đẩy bằng Wind Charge -
        // theo dữ liệu quan sát thực tế: người chơi thật đạt ~5 block, cheat đạt
        // tới ~12 block. Đặt ngưỡng cao hơn mức thật 1 chút để tránh oan người
        // chơi giỏi/may mắn, nhưng vẫn đủ thấp để bắt được mức bất thường.
        antiWindChargeJumpMaxHeightGain = config.getDouble("antiwindchargejump-check.max-height-gain", 8.0);
        // Số tick tối đa theo dõi 1 phiên bay trước khi bỏ qua (không kết luận
        // gì) nếu chưa chạm đất trở lại - phòng trường hợp rơi xuống hố sâu/bơi
        antiWindChargeJumpMaxSessionTicks = config.getInt("antiwindchargejump-check.max-session-ticks", 100);
        antiWindChargeJumpViolationThreshold = config.getInt("antiwindchargejump-check.violation-threshold", 1);
        antiWindChargeJumpBanEnabled = config.getBoolean("antiwindchargejump-check.ban-enabled", false);
        antiWindChargeJumpKickOnDetect = config.getBoolean("antiwindchargejump-check.kick-on-detect", false);
        antiWindChargeJumpKickReason = config.getString("antiwindchargejump-check.kick-reason", "Phát hiện WindChargeJump (tự động nhảy bất thường)");
        antiWindChargeJumpOffenseType = config.getString("antiwindchargejump-check.offense-type", "WindChargeJump");

        // Speed check
        speedCheckEnabled = config.getBoolean("speed-check.enabled", true);
        speedFreezeTicks = config.getInt("speed-check.freeze-ticks", 50);
        speedMinBps = config.getDouble("speed-check.min-bps", 1.0);
        speedMinVelocityToBypass = config.getDouble("speed-check.min-velocity-to-bypass", 0.2);
        speedViolationThreshold = config.getInt("speed-check.violation-threshold", 2);
        speedBanEnabled = config.getBoolean("speed-check.ban-enabled", true);
        speedKickOnDetect = config.getBoolean("speed-check.kick-on-detect", true);
        speedKickReason = config.getString("speed-check.kick-reason", "Phát hiện Speed hack");
        speedOffenseType = config.getString("speed-check.offense-type", "Speed");
    }

    private Enchantment getEnchantmentByName(String name) {
        // Thử theo tên Bukkit key
        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(name.toLowerCase());
            Enchantment ench = org.bukkit.Registry.ENCHANTMENT.get(key);
            if (ench != null) return ench;
        } catch (Exception ignored) {}

        // Fallback: dùng getByName (deprecated nhưng vẫn hoạt động)
        return Enchantment.getByName(name.toUpperCase());
    }

    // Getters
    public Set<String> getBannedItems() { return bannedItems; }
    public Map<Enchantment, Integer> getMaxEnchantLevels() { return maxEnchantLevels; }
    public Set<String> getInvalidEnchantItems() { return invalidEnchantItems; }
    public Set<String> getBlockedCommands() { return blockedCommands; }
    public int getMaxPotionAmplifier() { return maxPotionAmplifier; }
    public int getScanInterval() { return scanInterval; }
    public int getBanDurationMinutes() { return banDurationMinutes; }
    public boolean isLogToConsole() { return logToConsole; }
    public boolean isNotifyAdmins() { return notifyAdmins; }
    public boolean isBlockCommandEnabled() { return blockCommandEnabled; }
    public String getPrefix() { return prefix; }

    // Movement (AnHero pattern) check getters
    public boolean isMovementCheckEnabled() { return movementCheckEnabled; }
    public double getPhantomAscentDelta() { return phantomAscentDelta; }
    public int getAscentTicksRequired() { return ascentTicksRequired; }
    public double getPhantomDescentDelta() { return phantomDescentDelta; }
    public int getDescentTicksRequired() { return descentTicksRequired; }
    public int getLinkWindowTicks() { return linkWindowTicks; }
    public int getMovementViolationThreshold() { return movementViolationThreshold; }
    public boolean isMovementBanEnabled() { return movementBanEnabled; }
    public boolean isItemBanEnabled() { return itemBanEnabled; }
    public int getItemViolationThreshold() { return itemViolationThreshold; }
    public boolean isMovementKickOnDetect() { return movementKickOnDetect; }
    public String getMovementKickReason() { return movementKickReason; }
    public String getMovementOffenseType() { return movementOffenseType; }

    // MaceKill (fall-distance spoof / smash attack) check getters
    public boolean isMaceKillCheckEnabled() { return maceKillCheckEnabled; }
    public double getMaceSmashFallThreshold() { return maceSmashFallThreshold; }
    public double getMaceYoyoAscentDelta() { return maceYoyoAscentDelta; }
    public int getMaceYoyoWindowTicks() { return maceYoyoWindowTicks; }
    public double getMaceReturnTolerance() { return maceReturnTolerance; }
    public int getMaceViolationThreshold() { return maceViolationThreshold; }
    public boolean isMaceBanEnabled() { return maceBanEnabled; }
    public boolean isMaceMitigateDamage() { return maceMitigateDamage; }
    public boolean isMaceKickOnDetect() { return maceKickOnDetect; }
    public String getMaceKickReason() { return maceKickReason; }
    public String getMaceOffenseType() { return maceOffenseType; }

    // BoatNoclip check getters
    public boolean isBoatNoclipCheckEnabled() { return boatNoclipCheckEnabled; }
    public int getBoatStuckTicksRequired() { return boatStuckTicksRequired; }
    public int getBoatViolationThreshold() { return boatViolationThreshold; }
    public boolean isBoatBanEnabled() { return boatBanEnabled; }
    public boolean isBoatKickOnDetect() { return boatKickOnDetect; }
    public String getBoatKickReason() { return boatKickReason; }
    public String getBoatOffenseType() { return boatOffenseType; }

    // FreeBoatRide check getters (tích hợp trong BoatNoclipCheck)
    public boolean isFreeBoatRideCheckEnabled() { return freeBoatRideCheckEnabled; }
    public double getFreeBoatRideDropDelta() { return freeBoatRideDropDelta; }
    public int getFreeBoatRideDropStreakRequired() { return freeBoatRideDropStreakRequired; }
    public boolean isFreeBoatRideKickOnDetect() { return freeBoatRideKickOnDetect; }
    public String getFreeBoatRideKickReason() { return freeBoatRideKickReason; }
    public int getFreeBoatRideViolationThreshold() { return freeBoatRideViolationThreshold; }
    public boolean isFreeBoatRideBanEnabled() { return freeBoatRideBanEnabled; }
    public String getFreeBoatRideOffenseType() { return freeBoatRideOffenseType; }
    public boolean isFreeBoatRideProtectPassengers() { return freeBoatRideProtectPassengers; }

    // CrossbowMachineGun check getters (cancel + kick, không ban)
    public boolean isCrossbowCheckEnabled() { return crossbowCheckEnabled; }
    public double getCrossbowToleranceRatio() { return crossbowToleranceRatio; }
    public boolean isCrossbowKickOnDetect() { return crossbowKickOnDetect; }
    public String getCrossbowKickReason() { return crossbowKickReason; }

    // BetterScaffold check getters (cancel-only, không kick/ban)
    public boolean isScaffoldCheckEnabled() { return scaffoldCheckEnabled; }
    public int getScaffoldBurstCountThreshold() { return scaffoldBurstCountThreshold; }
    public long getScaffoldBurstWindowMillis() { return scaffoldBurstWindowMillis; }
    public long getScaffoldTowerMinIntervalMillis() { return scaffoldTowerMinIntervalMillis; }
    public int getScaffoldTowerStreakRequired() { return scaffoldTowerStreakRequired; }
    public boolean isScaffoldKickOnBurst() { return scaffoldKickOnBurst; }
    public String getScaffoldKickReason() { return scaffoldKickReason; }

    // ClickTP check getters (cancel-only, không kick/ban)
    public boolean isClickTpCheckEnabled() { return clickTpCheckEnabled; }
    public int getClickTpFreezeTicks() { return clickTpFreezeTicks; }
    public boolean isClickTpNotifyPlayer() { return clickTpNotifyPlayer; }

    // MovementLogger getters
    public boolean isMovementLoggerEnabled() { return movementLoggerEnabled; }
    public String getMovementLoggerFileName() { return movementLoggerFileName; }
    public double getMovementLoggerMinDistance() { return movementLoggerMinDistance; }
    public int getMovementLoggerFlushIntervalSeconds() { return movementLoggerFlushIntervalSeconds; }
    public int getMovementLoggerMaxQueueSize() { return movementLoggerMaxQueueSize; }

    // TPFly check getters
    public boolean isFlyCheckEnabled() { return flyCheckEnabled; }
    public double getTpFlyMaxTeleportDistance() { return tpFlyMaxTeleportDistance; }
    public double getTpFlyMaxVerticalDelta() { return tpFlyMaxVerticalDelta; }
    public double getTpFlyMaxVerticalDeltaAir() { return tpFlyMaxVerticalDeltaAir; }
    public int getTpFlyViolationThreshold() { return tpFlyViolationThreshold; }
    public boolean isFlyBanEnabled() { return flyBanEnabled; }
    public boolean isFlyKickOnDetect() { return flyKickOnDetect; }
    public String getTpFlyKickReason() { return tpFlyKickReason; }
    public String getTpFlyOffenseType() { return tpFlyOffenseType; }
    public double getTpFlyMinVelocityToBypass() { return tpFlyMinVelocityToBypass; }

    // MeteorFly check getters
    public boolean isMeteorFlyDebug() { return meteorFlyDebug; }
    public double getMeteorFlyCheckOnlyBelowY() { return meteorFlyCheckOnlyBelowY; }
    public int getMeteorFlyGraceTicks() { return meteorFlyGraceTicks; }
    public int getMeteorFlyAscendTicksThreshold() { return meteorFlyAscendTicksThreshold; }
    public double getMeteorFlyAscendMinDelta() { return meteorFlyAscendMinDelta; }
    public int getMeteorFlyHoverTicksThreshold() { return meteorFlyHoverTicksThreshold; }
    public double getMeteorFlyHoverMaxDelta() { return meteorFlyHoverMaxDelta; }
    public int getMeteorFlyMicrofallTicksThreshold() { return meteorFlyMicrofallTicksThreshold; }
    public double getMeteorFlyMicrofallExpectedDelta() { return meteorFlyMicrofallExpectedDelta; }
    public double getMeteorFlyMicrofallTolerance() { return meteorFlyMicrofallTolerance; }
    public double getMeteorFlyMinVelocityToBypass() { return meteorFlyMinVelocityToBypass; }
    public int getMeteorFlyMaceExemptTicks() { return meteorFlyMaceExemptTicks; }
    public int getMeteorFlyJoinGraceTicks() { return meteorFlyJoinGraceTicks; }
    public double getMeteorFlyJoinMaxFallDistance() { return meteorFlyJoinMaxFallDistance; }
    public double getMeteorFlyJoinFallDistanceBypass() { return meteorFlyJoinFallDistanceBypass; }
    public int getMeteorFlyViolationThreshold() { return meteorFlyViolationThreshold; }
    public String getMeteorFlyKickReason() { return meteorFlyKickReason; }
    public String getMeteorFlyOffenseType() { return meteorFlyOffenseType; }

    // AntiVoid check getters
    public boolean isAntiVoidCheckEnabled() { return antiVoidCheckEnabled; }
    public boolean isAntiVoidDebug() { return antiVoidDebug; }
    public double getAntiVoidZoneMargin() { return antiVoidZoneMargin; }
    public double getAntiVoidBounceMinDelta() { return antiVoidBounceMinDelta; }
    public double getAntiVoidBounceMinDeltaJump() { return antiVoidBounceMinDeltaJump; }
    public int getAntiVoidBounceWindowTicks() { return antiVoidBounceWindowTicks; }
    public int getAntiVoidBounceCountThreshold() { return antiVoidBounceCountThreshold; }
    public int getAntiVoidViolationThreshold() { return antiVoidViolationThreshold; }
    public boolean isAntiVoidBanEnabled() { return antiVoidBanEnabled; }
    public boolean isAntiVoidKickOnDetect() { return antiVoidKickOnDetect; }
    public String getAntiVoidKickReason() { return antiVoidKickReason; }
    public String getAntiVoidOffenseType() { return antiVoidOffenseType; }

    // AntiAutoTotem check getters
    public boolean isAntiAutoTotemEnabled() { return antiAutoTotemCheckEnabled; }
    public boolean isAntiAutoTotemDebug() { return antiAutoTotemDebug; }
    public int getAntiAutoTotemMinRefillTicks() { return antiAutoTotemMinRefillTicks; }
    public int getAntiAutoTotemManualActionGraceTicks() { return antiAutoTotemManualActionGraceTicks; }
    public int getAntiAutoTotemViolationThreshold() { return antiAutoTotemViolationThreshold; }
    public boolean isAntiAutoTotemBanEnabled() { return antiAutoTotemBanEnabled; }
    public boolean isAntiAutoTotemKickOnDetect() { return antiAutoTotemKickOnDetect; }
    public String getAntiAutoTotemKickReason() { return antiAutoTotemKickReason; }
    public String getAntiAutoTotemOffenseType() { return antiAutoTotemOffenseType; }

    // AntiWindChargeJump check getters
    public boolean isAntiWindChargeJumpEnabled() { return antiWindChargeJumpCheckEnabled; }
    public boolean isAntiWindChargeJumpDebug() { return antiWindChargeJumpDebug; }
    public double getAntiWindChargeJumpMinPitch() { return antiWindChargeJumpMinPitch; }
    public double getAntiWindChargeJumpMaxHeightGain() { return antiWindChargeJumpMaxHeightGain; }
    public int getAntiWindChargeJumpMaxSessionTicks() { return antiWindChargeJumpMaxSessionTicks; }
    public int getAntiWindChargeJumpViolationThreshold() { return antiWindChargeJumpViolationThreshold; }
    public boolean isAntiWindChargeJumpBanEnabled() { return antiWindChargeJumpBanEnabled; }
    public boolean isAntiWindChargeJumpKickOnDetect() { return antiWindChargeJumpKickOnDetect; }
    public String getAntiWindChargeJumpKickReason() { return antiWindChargeJumpKickReason; }
    public String getAntiWindChargeJumpOffenseType() { return antiWindChargeJumpOffenseType; }

    // Speed check getters
    public boolean isSpeedCheckEnabled() { return speedCheckEnabled; }
    public int getSpeedFreezeTicks() { return speedFreezeTicks; }
    public double getSpeedMinBps() { return speedMinBps; }
    public double getSpeedMinVelocityToBypass() { return speedMinVelocityToBypass; }
    public int getSpeedViolationThreshold() { return speedViolationThreshold; }
    public boolean isSpeedBanEnabled() { return speedBanEnabled; }
    public boolean isSpeedKickOnDetect() { return speedKickOnDetect; }
    public String getSpeedKickReason() { return speedKickReason; }
    public String getSpeedOffenseType() { return speedOffenseType; }
}