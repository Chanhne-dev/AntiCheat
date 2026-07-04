package chanhne.AntiCheat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import chanhne.AntiCheat.check.CommandChecker;
import chanhne.AntiCheat.check.ItemChecker;
import chanhne.AntiCheat.command.CommandHandler;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.discord.DiscordWebhook;
import chanhne.AntiCheat.enforcement.EnforcementHandler;
import chanhne.AntiCheat.listener.PlayerInventoryListener;
import chanhne.AntiCheat.messages.Message;
import chanhne.AntiCheat.task.ScanTask;

public class AntiCheatPlugin extends JavaPlugin {

    public static final String ADMIN_PERMISSION = "anticheat.admin";
    private static AntiCheatPlugin MainPlugin;
    private ConfigManager configManager;
    private ItemChecker itemChecker;
    private ScanTask scanTask;
    private DiscordWebhook discordWebhook;
    private EnforcementHandler enforcementHandler;

    @Override
    public void onEnable() {
        MainPlugin = this;

        // Lưu config mặc định nếu chưa có
        saveDefaultConfig();
        Message.init(this);

        // Khởi tạo các manager
        configManager = new ConfigManager(this);
        itemChecker = new ItemChecker(this);
        CommandHandler commandHandler = new CommandHandler(this);
        enforcementHandler = new EnforcementHandler(this);
        discordWebhook = new DiscordWebhook(getConfig().getString("discord.webhook"));

        // Đăng ký listener
        Bukkit.getPluginManager().registerEvents(new PlayerInventoryListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CommandChecker(this), this);

        // Đăng ký lệnh
        getCommand("illegal").setExecutor(commandHandler);

        // Bắt đầu task quét định kỳ
        scanTask = new ScanTask(this);
        scanTask.start();

        getLogger().info("=================================");
        getLogger().info("  AntiCheatPlugin đã khởi động!");
        getLogger().info("  Phiên bản: " + getPluginMeta().getVersion());
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        if (scanTask != null) {
            scanTask.stop();
        }
        getLogger().info("AntiCheatPlugin đã tắt.");
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        itemChecker.reload();
        if (scanTask != null) {
            scanTask.stop();
            scanTask.start();
        }
    }

    public boolean shouldBypass(Player player) {
        return player != null && player.hasPermission(ADMIN_PERMISSION);
    }

    public EnforcementHandler getEnforcementHandler() {
        return enforcementHandler;
    }

    public static AntiCheatPlugin getMainPlugin() {
        return MainPlugin;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ItemChecker getItemChecker() {
        return itemChecker;
    }
    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }
}