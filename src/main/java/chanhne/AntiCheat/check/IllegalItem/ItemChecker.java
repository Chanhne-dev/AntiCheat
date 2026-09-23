package chanhne.AntiCheat.check.IllegalItem;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ItemChecker {

    private final Mainplugin plugin;
    private ConfigManager cfg;

    public ItemChecker(Mainplugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        reload();
    }

    public void reload() {
        this.cfg = plugin.getConfigManager();
    }

    public boolean isIllegalMaterial(String material) {
        material = material.toUpperCase(Locale.ROOT);
        if (material.startsWith("MINECRAFT:")) {
            material = material.substring("MINECRAFT:".length());
        }
        return isBannedItem(material);
    }

    public List<ViolationResult> checkInventory(Player player) {
        if (plugin.shouldBypass(player)) return new ArrayList<>();

        List<ViolationResult> violations = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR) continue;

            List<ViolationResult> itemViolations = checkItem(item, slot);
            violations.addAll(itemViolations);
        }

        return violations;
    }

    /**
     * Kiểm tra một item cụ thể
     */
    public List<ViolationResult> checkItem(ItemStack item, int slot) {
        List<ViolationResult> violations = new ArrayList<>();
        if (item == null || item.getType() == Material.AIR) return violations;

        String typeName = item.getType().name();

        // 1. Kiểm tra item bị cấm tuyệt đối
        if (isBannedItem(typeName)) {
            violations.add(new ViolationResult(
                    ViolationType.BANNED_ITEM,
                    item,
                    slot,
                    "Item bị cấm: " + typeName,
                    item.getType().name(),
                    null,
                    0
            ));
            return violations; // Không cần kiểm tra thêm
        }

        // 2. Kiểm tra bình thuốc (Potion)
        if (isPotion(item.getType())) {
            violations.addAll(checkPotion(item, slot));
        }

        // 3. Kiểm tra enchantment bất hợp pháp
        violations.addAll(checkEnchantments(item, slot));

        return violations;
    }

    /**
     * Kiểm tra item có trong danh sách cấm không
     */
    private boolean isBannedItem(String typeName) {
        // Kiểm tra chính xác
        if (cfg.getBannedItems().contains(typeName)) return true;

        // Kiểm tra spawn egg (kết thúc bằng _SPAWN_EGG)
        if (typeName.endsWith("_SPAWN_EGG")) return true;

        // Kiểm tra các pattern khác trong danh sách cấm
        for (String banned : cfg.getBannedItems()) {
            if (banned.startsWith("_") && typeName.endsWith(banned)) return true;
        }

        return false;
    }

    /**
     * Kiểm tra bình thuốc có cấp độ hợp lệ không
     */
    private List<ViolationResult> checkPotion(ItemStack item, int slot) {
        List<ViolationResult> violations = new ArrayList<>();
        if (!(item.getItemMeta() instanceof PotionMeta potionMeta)) return violations;

        int maxAmp = cfg.getMaxPotionAmplifier(); // 1 = amplifier 1 = Level II

        // Kiểm tra các effect tùy chỉnh
        for (PotionEffect effect : potionMeta.getCustomEffects()) {
            int amplifier = effect.getAmplifier(); // 0 = Level I, 1 = Level II, 2 = Level III
            if (amplifier > maxAmp) {
                String effectName = effect.getType().getKey().getKey();
                int displayLevel = amplifier + 1;
                violations.add(new ViolationResult(
                        ViolationType.ILLEGAL_POTION,
                        item,
                        slot,
                        "Potion effect " + effectName + " cấp độ " + displayLevel + " (tối đa " + (maxAmp + 1) + ")",
                        item.getType().name(),
                        null,
                        0
                ));
            }
        }

        // Kiểm tra base potion type nếu có
        try {
            org.bukkit.potion.PotionType baseType = potionMeta.getBasePotionType();
            if (baseType != null) {
                // Kiểm tra strong (level 2) vs regular potion - cần check thêm
                // Tên chứa "strong" hoặc "long" là bình được mod
                String itemName = item.getType().name().toLowerCase();
                if (itemName.contains("strong") && maxAmp < 1) {
                    violations.add(new ViolationResult(
                            ViolationType.ILLEGAL_POTION,
                            item,
                            slot,
                            "Strong potion không được phép",
                            item.getType().name(),
                            null,
                            0
                    ));
                }
            }
        } catch (Exception ignored) {}

        return violations;
    }

    /**
     * Kiểm tra enchantment trên item
     */
    private List<ViolationResult> checkEnchantments(ItemStack item, int slot) {
        List<ViolationResult> violations = new ArrayList<>();
        String typeName = item.getType().name();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return violations;

        Map<Enchantment, Integer> enchants;

        // Lấy danh sách enchant (kể cả enchanted book)
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchants = storageMeta.getStoredEnchants();
        } else {
            enchants = item.getEnchantments();
        }

        if (enchants.isEmpty()) return violations;

        // Kiểm tra nếu item không nên có enchant (như khối cỏ, đất, v.v.)
        if (cfg.getInvalidEnchantItems().contains(typeName) && !enchants.isEmpty()) {
            violations.add(new ViolationResult(
                    ViolationType.INVALID_ENCHANT_ITEM,
                    item,
                    slot,
                    "Item " + typeName + " không được phép có enchantment",
                    item.getType().name(),
                    null,
                    0
                ));
            return violations;
        }

        // Kiểm tra cấp độ enchant
        Map<Enchantment, Integer> maxLevels = cfg.getMaxEnchantLevels();
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int level = entry.getValue();

            // Kiểm tra enchant có hợp lệ với loại item không
            if (!isEnchantValidForItem(ench, item.getType())) {
                violations.add(new ViolationResult(
                    ViolationType.INVALID_ENCHANT_COMBO,
                    item,
                    slot,
                    "Enchant " + getEnchantName(ench) + " không hợp lệ cho " + typeName,
                    item.getType().name(),
                    getEnchantName(ench),
                    level
                ));
                continue;
            }

            // Kiểm tra cấp độ tối đa
            if (maxLevels.containsKey(ench)) {
                int maxLevel = maxLevels.get(ench);
                if (level > maxLevel) {
                    violations.add(new ViolationResult(
                        ViolationType.INVALID_ENCHANT_COMBO,
                        item,
                        slot,
                        "Enchant " + getEnchantName(ench) + " không hợp lệ cho " + typeName,
                        item.getType().name(),
                        getEnchantName(ench),
                        level
                    ));
                }
            } else {
                // Enchant không có trong config -> kiểm tra max level vanilla
                int vanillaMax = ench.getMaxLevel();
                if (level > vanillaMax) {
                    violations.add(new ViolationResult(
                        ViolationType.INVALID_ENCHANT_COMBO,
                        item,
                        slot,
                        "Enchant " + getEnchantName(ench) + " không hợp lệ cho " + typeName,
                        item.getType().name(),
                        getEnchantName(ench),
                        level
                    ));
                }
            }
        }

        return violations;
    }

    /**
     * Kiểm tra xem enchant có hợp lệ với loại item không
     */
    private boolean isEnchantValidForItem(Enchantment ench, Material material) {
        // Tạo item tạm để kiểm tra
        try {
            if (material == Material.ENCHANTED_BOOK || material == Material.BOOK) {
                return true;
            }
            ItemStack testItem = new ItemStack(material);
            return ench.canEnchantItem(testItem);
        } catch (Exception e) {
            return true; // Nếu không kiểm tra được, cho qua
        }
    }

    /**
     * Lấy tên thân thiện của enchantment
     */
    private String getEnchantName(Enchantment ench) {
        try {
            return ench.getKey().getKey().replace("_", " ").toUpperCase();
        } catch (Exception e) {
            return ench.getKey().getKey();
        }
    }

    /**
     * Kiểm tra xem Material có phải potion không
     */
    private boolean isPotion(Material material) {
        return material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION
                || material == Material.TIPPED_ARROW;
    }
}