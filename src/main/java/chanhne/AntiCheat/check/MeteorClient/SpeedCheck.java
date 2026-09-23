// package chanhne.AntiCheat.check.MeteorClient;

// import chanhne.AntiCheat.Mainplugin;
// import chanhne.AntiCheat.config.ConfigManager;
// import org.bukkit.Location;
// import org.bukkit.Material;
// import org.bukkit.block.Block;
// import org.bukkit.enchantments.Enchantment;
// import org.bukkit.entity.Player;
// import org.bukkit.event.EventHandler;
// import org.bukkit.event.EventPriority;
// import org.bukkit.event.Listener;
// import org.bukkit.event.player.PlayerJoinEvent;
// import org.bukkit.event.player.PlayerMoveEvent;
// import org.bukkit.event.player.PlayerQuitEvent;
// import org.bukkit.event.player.PlayerTeleportEvent;
// import org.bukkit.event.vehicle.VehicleExitEvent;
// import org.bukkit.inventory.ItemStack;
// import org.bukkit.potion.PotionEffect;
// import org.bukkit.potion.PotionEffectType;
// import org.bukkit.util.Vector;

// import java.util.Map;
// import java.util.UUID;
// import java.util.concurrent.ConcurrentHashMap;

// public class SpeedCheck implements Listener {

//     private final Mainplugin plugin;
//     private final ConfigManager config;

//     private final Map<UUID, Long> frozenUntil = new ConcurrentHashMap<>();
//     private final Map<UUID, Location> frozenLocation = new ConcurrentHashMap<>();

//     private final Map<UUID, Long> lastMoveTime = new ConcurrentHashMap<>();
//     private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();
//     private final Map<UUID, Integer> violationCount = new ConcurrentHashMap<>();
//     private final Map<UUID, Double> lastSpeedBps = new ConcurrentHashMap<>();

//     private final Map<UUID, Long> groundGraceUntil = new ConcurrentHashMap<>();
//     private final Map<UUID, Long> waterGraceUntil = new ConcurrentHashMap<>();
//     private final Map<UUID, Long> soulSandGraceUntil = new ConcurrentHashMap<>();

//     private final Map<UUID, Boolean> wasOnGround = new ConcurrentHashMap<>();
//     private final Map<UUID, Boolean> wasInWater = new ConcurrentHashMap<>();
//     private final Map<UUID, Boolean> wasOnSoulSand = new ConcurrentHashMap<>();

//     public SpeedCheck(Mainplugin plugin) {
//         this.plugin = plugin;
//         this.config = plugin.getConfigManager();
//     }

//     @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
//     public void onPlayerMove(PlayerMoveEvent event) {
//         Player player = event.getPlayer();
//         if (plugin.shouldBypass(player)) return;
//         if (!config.isSpeedCheckEnabled()) return;

//         Location from = event.getFrom();
//         Location to = event.getTo();
//         if (to == null) return;

//         UUID uuid = player.getUniqueId();
//         long now = System.currentTimeMillis();

//         // ===== FREEZE =====
//         Long frozenTill = frozenUntil.get(uuid);
//         if (frozenTill != null && now < frozenTill) {
//             Location lock = frozenLocation.getOrDefault(uuid, from);
//             if (Math.abs(to.getX() - lock.getX()) > 0.01 || Math.abs(to.getZ() - lock.getZ()) > 0.01) {
//                 Location corrected = to.clone();
//                 corrected.setX(lock.getX());
//                 corrected.setZ(lock.getZ());
//                 event.setTo(corrected);
//             }
//             return;
//         } else {
//             frozenUntil.remove(uuid);
//             frozenLocation.remove(uuid);
//         }

//         if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
//             return;
//         }

//         // ===== THEO DÕI TRẠNG THÁI =====
//         boolean currentOnGround = player.isOnGround();
//         boolean prevOnGround = wasOnGround.getOrDefault(uuid, currentOnGround);
//         wasOnGround.put(uuid, currentOnGround);

//         boolean inWater = player.isInWater() || player.isInWaterOrBubbleColumn();
//         boolean wasInWaterPrev = wasInWater.getOrDefault(uuid, inWater);
//         wasInWater.put(uuid, inWater);

//         boolean onSoulSand = isOnSoulSand(player);
//         boolean wasOnSoulSandPrev = wasOnSoulSand.getOrDefault(uuid, onSoulSand);
//         wasOnSoulSand.put(uuid, onSoulSand);

//         // Grace period khi tiếp đất (500ms)
//         if (!prevOnGround && currentOnGround) {
//             groundGraceUntil.put(uuid, now + 500);
//         }

//         // Grace period khi rời nước (5000ms)
//         if (wasInWaterPrev && !inWater) {
//             waterGraceUntil.put(uuid, now + 5000);
//         }

//         // Grace period khi rời soul sand (30000ms)
//         if (wasOnSoulSandPrev && !onSoulSand) {
//             soulSandGraceUntil.put(uuid, now + 30000);
//         }

//         // Kiểm tra grace periods
//         Long groundGrace = groundGraceUntil.get(uuid);
//         Long waterGrace = waterGraceUntil.get(uuid);
//         Long soulGrace = soulSandGraceUntil.get(uuid);

//         if ((groundGrace != null && now < groundGrace) ||
//             (waterGrace != null && now < waterGrace) ||
//             (soulGrace != null && now < soulGrace)) {
//             lastMoveTime.put(uuid, now);
//             lastLocation.put(uuid, to.clone());
//             violationCount.remove(uuid);
//             lastSpeedBps.remove(uuid);
//             return;
//         } else {
//             groundGraceUntil.remove(uuid);
//             waterGraceUntil.remove(uuid);
//             soulSandGraceUntil.remove(uuid);
//         }

//         // ===== TÍNH TỐC ĐỘ =====
//         Long lastTime = lastMoveTime.get(uuid);
//         Location lastLoc = lastLocation.get(uuid);

//         lastMoveTime.put(uuid, now);
//         lastLocation.put(uuid, to.clone());

//         if (lastTime == null || lastLoc == null) return;

//         long dt = now - lastTime;
//         if (dt <= 0) return;

//         double distance = lastLoc.distance(to);
//         double speedBps = (distance / dt) * 1000.0;

//         if (speedBps < config.getSpeedMinBps()) {
//             violationCount.remove(uuid);
//             lastSpeedBps.remove(uuid);
//             return;
//         }

//         if (player.isGliding() || player.isInsideVehicle() || player.isFlying()) {
//             violationCount.remove(uuid);
//             lastSpeedBps.remove(uuid);
//             return;
//         }

//         Vector velocity = player.getVelocity();
//         double minVel = config.getSpeedMinVelocityToBypass();
//         if (velocity.lengthSquared() > minVel * minVel) {
//             violationCount.remove(uuid);
//             lastSpeedBps.remove(uuid);
//             return;
//         }

//         double maxSpeed = getMaxAllowedSpeed(player);

//         Double lastSpeed = lastSpeedBps.get(uuid);
//         boolean suddenAccel = false;
//         if (lastSpeed != null && speedBps > 6.0) {
//             double diff = speedBps - lastSpeed;
//             if (diff > 3.5) {
//                 suddenAccel = true;
//             }
//         }

//         if (speedBps > maxSpeed || suddenAccel) {
//             int count = violationCount.getOrDefault(uuid, 0) + 1;
//             violationCount.put(uuid, count);

//             int threshold = config.getSpeedViolationThreshold();
//             if (count >= threshold) {
//                 int freezeTicks = config.getSpeedFreezeTicks();
//                 if (freezeTicks > 0) {
//                     frozenUntil.put(uuid, now + freezeTicks * 50L);
//                     frozenLocation.put(uuid, from.clone());
//                 }

//                 if (config.isLogToConsole()) {
//                     plugin.getLogger().warning(String.format(
//                             "[SpeedCheck] %s frozen (%.2f bps, max %.2f, sprint=%s, speed_amp=%d, onSoul=%s, soul_level=%d)",
//                             player.getName(),
//                             speedBps,
//                             maxSpeed,
//                             player.isSprinting(),
//                             getSpeedAmplifier(player),
//                             onSoulSand,
//                             getSoulSpeedLevel(player)
//                     ));
//                 }

//                 violationCount.remove(uuid);
//                 lastSpeedBps.remove(uuid);
//             }
//         } else {
//             violationCount.remove(uuid);
//         }

//         lastSpeedBps.put(uuid, speedBps);
//     }

//     // ===== HÀM TIỆN ÍCH =====
//     private int getSpeedAmplifier(Player player) {
//         PotionEffect effect = player.getPotionEffect(PotionEffectType.SPEED);
//         return effect != null ? effect.getAmplifier() : -1;
//     }

//     private boolean isOnSoulSand(Player player) {
//         Block block = player.getLocation().getBlock().getRelative(0, -1, 0);
//         Material type = block.getType();
//         return type == Material.SOUL_SAND || type == Material.SOUL_SOIL;
//     }

//     private int getSoulSpeedLevel(Player player) {
//         ItemStack boots = player.getInventory().getBoots();
//         if (boots == null) return 0;
//         Enchantment soulSpeed = Enchantment.SOUL_SPEED;
//         if (soulSpeed != null && boots.containsEnchantment(soulSpeed)) {
//             return boots.getEnchantmentLevel(soulSpeed);
//         }
//         return 0;
//     }

//     private double getMaxAllowedSpeed(Player player) {
//         boolean sprinting = player.isSprinting();
//         int speedAmp = getSpeedAmplifier(player);
//         boolean onSoul = isOnSoulSand(player);
//         int soulLevel = getSoulSpeedLevel(player);

//         // Base speed và dung sai mới
//         double baseSpeed = sprinting ? 6.0 : 5.0;
//         double tolerance = 1.0;

//         // Hiệu ứng Speed
//         double speedBonus = 0.0;
//         if (speedAmp >= 0) {
//             if (sprinting) {
//                 switch (speedAmp) {
//                     case 0 -> speedBonus = 1.0;
//                     case 1 -> speedBonus = 2.2;
//                     default -> speedBonus = 2.2 + (speedAmp - 1) * 1.0;
//                 }
//             } else {
//                 speedBonus = (speedAmp + 1) * 0.8;
//             }
//         }

//         // Soul Speed (chỉ khi đứng trên soul sand)
//         double soulBonus = 0.0;
//         if (onSoul && player.isOnGround() && sprinting) {
//             switch (soulLevel) {
//                 case 1 -> soulBonus = 2.2;
//                 case 2 -> soulBonus = 2.8;
//                 case 3 -> soulBonus = 3.4;
//                 default -> soulBonus = 1.5;
//             }
//             if (speedAmp == 0) soulBonus += 1.0;
//             else if (speedAmp == 1) soulBonus += 2.2;
//         }

//         double maxSpeed = baseSpeed + speedBonus + soulBonus + tolerance;
//         return Math.min(maxSpeed, 14.0);
//     }

//     // ===== CÁC SỰ KIỆN DỌN DẸP =====
//     @EventHandler(priority = EventPriority.MONITOR)
//     public void onTeleport(PlayerTeleportEvent event) {
//         if (event.isCancelled()) return;
//         Player player = event.getPlayer();
//         UUID id = player.getUniqueId();
//         frozenUntil.remove(id);
//         frozenLocation.remove(id);
//         violationCount.remove(id);
//         lastSpeedBps.remove(id);
//         lastMoveTime.remove(id);
//         lastLocation.remove(id);
//         groundGraceUntil.remove(id);
//         waterGraceUntil.remove(id);
//         soulSandGraceUntil.remove(id);
//         wasOnGround.remove(id);
//         wasInWater.remove(id);
//         wasOnSoulSand.remove(id);
//     }

//     @EventHandler
//     public void onJoin(PlayerJoinEvent event) {
//         Player player = event.getPlayer();
//         UUID id = player.getUniqueId();
//         lastLocation.put(id, player.getLocation());
//         lastMoveTime.put(id, System.currentTimeMillis());
//         wasOnGround.put(id, player.isOnGround());
//         wasInWater.put(id, player.isInWater());
//         wasOnSoulSand.put(id, isOnSoulSand(player));
//     }

//     @EventHandler
//     public void onQuit(PlayerQuitEvent event) {
//         UUID id = event.getPlayer().getUniqueId();
//         frozenUntil.remove(id);
//         frozenLocation.remove(id);
//         violationCount.remove(id);
//         lastSpeedBps.remove(id);
//         lastMoveTime.remove(id);
//         lastLocation.remove(id);
//         groundGraceUntil.remove(id);
//         waterGraceUntil.remove(id);
//         soulSandGraceUntil.remove(id);
//         wasOnGround.remove(id);
//         wasInWater.remove(id);
//         wasOnSoulSand.remove(id);
//     }

//     @EventHandler(priority = EventPriority.MONITOR)
//     public void onVehicleExit(VehicleExitEvent event) {
//         if (!(event.getExited() instanceof Player player)) return;
//         UUID id = player.getUniqueId();
//         frozenUntil.remove(id);
//         frozenLocation.remove(id);
//         violationCount.remove(id);
//         lastSpeedBps.remove(id);
//         groundGraceUntil.remove(id);
//         waterGraceUntil.remove(id);
//         soulSandGraceUntil.remove(id);
//         wasOnGround.remove(id);
//         wasInWater.remove(id);
//         wasOnSoulSand.remove(id);
//     }
// }