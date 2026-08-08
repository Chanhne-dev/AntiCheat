package chanhne.AntiCheat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import chanhne.AntiCheat.check.TrouserStreak.AnHeroMovementCheck;
import chanhne.AntiCheat.check.TrouserStreak.BetterScaffoldCheck;
import chanhne.AntiCheat.check.TrouserStreak.MaceKillCheck;
// import chanhne.AntiCheat.check.TrouserStreak.TPFlyCheck;
import chanhne.AntiCheat.check.TrouserStreak.BoatNoclipCheck;
import chanhne.AntiCheat.check.TrouserStreak.CrossbowMachineGunCheck;
import chanhne.AntiCheat.check.MeteorClient.AntiVoidCheck;
import chanhne.AntiCheat.check.MeteorClient.ClickTPCheck;
// import chanhne.AntiCheat.check.MeteorClient.MeteorFlyCheck;
import chanhne.AntiCheat.check.Fly.FlyCheck;
// import chanhne.AntiCheat.check.MeteorClient.SpeedCheck;
import chanhne.AntiCheat.check.IllegalItem.CommandChecker;
import chanhne.AntiCheat.check.IllegalItem.EnforcementHandler;
import chanhne.AntiCheat.check.IllegalItem.ItemChecker;
import chanhne.AntiCheat.command.CommandHandler;
import chanhne.AntiCheat.config.ConfigManager;
import chanhne.AntiCheat.debug.MovementLogger;
import chanhne.AntiCheat.discord.DiscordWebhook;
import chanhne.AntiCheat.listener.AntiCheatListener;
import chanhne.AntiCheat.listener.BlockBreakListener;
import chanhne.AntiCheat.listener.BlockPlaceListener;
import chanhne.AntiCheat.listener.PlayerInventoryListener;
import chanhne.AntiCheat.messages.Message;
import chanhne.AntiCheat.task.ScanTask;

public class AntiCheatPlugin extends JavaPlugin {

    public static final String ADMIN_PERMISSION = "anticheat.admin";
    private static AntiCheatPlugin MainPlugin;
    private final Set<UUID> pendingSafeTeleport = ConcurrentHashMap.newKeySet();
    private ConfigManager configManager;
    private ItemChecker itemChecker;
    private ScanTask scanTask;
    private CommandHandler commandHandler;
    private DiscordWebhook discordWebhook;
    private EnforcementHandler enforcementHandler;
    private MovementLogger movementLogger;

    @Override
    public void onEnable() {
        MainPlugin = this;

        // Lưu config mặc định nếu chưa có
        saveDefaultConfig();
        Message.init(this);

        // Khởi tạo các manager
        configManager = new ConfigManager(this);
        itemChecker = new ItemChecker(this);
        commandHandler = new CommandHandler(this);
        discordWebhook = new DiscordWebhook(getConfig().getString("discord.webhook"));
        enforcementHandler = new EnforcementHandler(this);
        FlyCheck flyCheck = new FlyCheck(this);

        // Đăng ký listener
        Bukkit.getPluginManager().registerEvents(new BlockBreakListener(this),this);
        Bukkit.getPluginManager().registerEvents(new BlockPlaceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerInventoryListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AntiCheatListener(this), this);

        // Đăng ký các checkers
        Bukkit.getPluginManager().registerEvents(new CommandChecker(this), this);
        Bukkit.getPluginManager().registerEvents(new AnHeroMovementCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new BetterScaffoldCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new BoatNoclipCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new CrossbowMachineGunCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new ClickTPCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new MaceKillCheck(this), this);
        // Bukkit.getPluginManager().registerEvents(new TPFlyCheck(this), this);
        // Bukkit.getPluginManager().registerEvents(new MeteorFlyCheck(this), this);
        // Bukkit.getPluginManager().registerEvents(new SpeedCheck(this), this);
        Bukkit.getPluginManager().registerEvents(flyCheck, this);
        flyCheck.startForOnlinePlayers(); // bắt kịp người chơi đã online sẵn khi /reload

        AntiVoidCheck antiVoidCheck = new AntiVoidCheck(this);
        Bukkit.getPluginManager().registerEvents(antiVoidCheck, this);
        antiVoidCheck.startForOnlinePlayers();

        // MovementLogger: công cụ ghi log RAW di chuyển để lấy dữ liệu tham chiếu (mặc định
        // tắt trong config.yml, chỉ bật tạm thời khi cần thu thập dữ liệu hiệu chỉnh ngưỡng)
        movementLogger = new MovementLogger(this);
        Bukkit.getPluginManager().registerEvents(movementLogger, this);
        movementLogger.start();

        // Đăng ký lệnh
        getCommand("anticheat").setExecutor(commandHandler);

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

        if (movementLogger != null) {
            movementLogger.stop();
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

        if (movementLogger != null) {
            movementLogger.stop();
            movementLogger.start();
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

    public void addPendingSafeTeleport(UUID uuid) {
        pendingSafeTeleport.add(uuid);
    }

    public boolean hasPendingSafeTeleport(UUID uuid) {
        return pendingSafeTeleport.contains(uuid);
    }

    public void removePendingSafeTeleport(UUID uuid) {
        pendingSafeTeleport.remove(uuid);
    }
}