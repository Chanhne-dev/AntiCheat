package chanhne.AntiCheat.check.IllegalItem;

public enum ViolationType {

    // Item bị cấm tuyệt đối
    BANNED_ITEM,

    // Bình thuốc vượt cấp
    ILLEGAL_POTION,

    // Enchant vượt giới hạn
    ENCHANT_LEVEL_TOO_HIGH,

    // Item không được phép có enchant
    INVALID_ENCHANT_ITEM,

    // Enchant không hợp lệ với item
    INVALID_ENCHANT_COMBO
}