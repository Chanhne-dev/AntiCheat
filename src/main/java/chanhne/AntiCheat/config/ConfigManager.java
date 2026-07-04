package chanhne.AntiCheat.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;

import chanhne.AntiCheat.AntiCheatPlugin;
import chanhne.AntiCheat.messages.Message;

import java.util.*;

public class ConfigManager {

    private final AntiCheatPlugin plugin;
    private FileConfiguration config;

    // Cache
    private Set<String> bannedItems;
    private Map<Enchantment, Integer> maxEnchantLevels;
    private Set<String> invalidEnchantItems;
    private int maxPotionAmplifier;
    private int scanInterval;
    private int banDurationMinutes;
    private boolean logToConsole;
    private boolean notifyAdmins;
    private String prefix;

    public ConfigManager(AntiCheatPlugin plugin) {
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

        // Load settings
        maxPotionAmplifier = config.getInt("max-potion-levels.max-amplifier", 1);
        scanInterval = config.getInt("settings.scan-interval", 40);
        banDurationMinutes = config.getInt("settings.ban-duration-minutes", 1);
        logToConsole = config.getBoolean("settings.log-to-console", true);
        notifyAdmins = config.getBoolean("settings.notify-admins", true);
        prefix = Message.get("prefix");
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
    public int getMaxPotionAmplifier() { return maxPotionAmplifier; }
    public int getScanInterval() { return scanInterval; }
    public int getBanDurationMinutes() { return banDurationMinutes; }
    public boolean isLogToConsole() { return logToConsole; }
    public boolean isNotifyAdmins() { return notifyAdmins; }
    public String getPrefix() { return prefix; }
}