package chanhne.AntiCheat.check;

import org.bukkit.inventory.ItemStack;

public class ViolationResult {

    private final ViolationType type;
    private final ItemStack item;
    private final int slot;
    private final String reason;
    private final String targetName;
    private final String enchantName;
    private final int level;

    public ViolationResult(ViolationType type, ItemStack item, int slot, String reason, String targetName,String enchantName, int level) {
        this.type = type;
        this.item = item;
        this.slot = slot;
        this.reason = reason;
        this.targetName = targetName;
        this.enchantName = enchantName;
        this.level = level;
    }

    public ViolationType getType() {
        return type;
    }

    public ItemStack getItem() {
        return item;
    }

    public int getSlot() {
        return slot;
    }

    public String getReason() {
        return reason;
    }

    public String getTargetName() {
        return targetName;
    }

    public int getLevel() {
        return level;
    }

    public String getEnchantName() { return enchantName; }

    @Override
    public String toString() {
        return "[" + type + "] Slot " + slot + ": " + reason;
    }
}