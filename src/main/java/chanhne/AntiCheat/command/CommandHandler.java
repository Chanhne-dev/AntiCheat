package chanhne.AntiCheat.command;

// import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
// import org.bukkit.entity.Player;

import chanhne.AntiCheat.AntiCheatPlugin;
// import chanhne.AntiCheat.check.ViolationResult;
import chanhne.AntiCheat.messages.Message;

// import java.util.List;

public class CommandHandler implements CommandExecutor {

    private final AntiCheatPlugin plugin;

    public CommandHandler(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // String prefix = plugin.getConfigManager().getPrefix();

        // Xử lý lệnh mới /illegal (hoặc /ill) với subcommands
        if (command.getName().equalsIgnoreCase("illegal") || label.equalsIgnoreCase("ill")) {
            if (args.length == 0) {
                // Gửi hướng dẫn sử dụng
                Message.send(sender, "plugin.usage", "label", label);
                return true;
            }

            String sub = args[0].toLowerCase();
            if (sub.equals("reload")) {
                return handleReload(sender);
            // } else if (sub.equals("check")) {
            //     // Lấy các đối số còn lại (tên player)
            //     String[] checkArgs = new String[args.length - 1];
            //     System.arraycopy(args, 1, checkArgs, 0, checkArgs.length);
            //     return handleCheck(sender, checkArgs, prefix);
            } else {
                Message.send(sender, "plugin.invalid-subcommand");
                return true;
            }
        }

        // (Tùy chọn) Hỗ trợ lệnh cũ nếu vẫn còn đăng ký trong plugin.yml
        // Bạn có thể bỏ qua phần này nếu đã xóa hoàn toàn lệnh cũ
        if (label.equalsIgnoreCase("illegalreload")) {
            return handleReload(sender);
        }
        // if (label.equalsIgnoreCase("illegalcheck")) {
        //     return handleCheck(sender, args, prefix);
        // }

        return false;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("anticheat.admin")) {
            Message.send(sender, "plugin.no-permission");
            return true;
        }

        plugin.reload();
        Message.reload();
        Message.send(sender, "plugin.reload-success");
        return true;
    }

    // private boolean handleCheck(CommandSender sender, String[] args, String prefix) {
    //     if (!sender.hasPermission("anticheat.admin")) {
    //         Message.send(sender, "plugin.no-permission");
    //         return true;
    //     }

    //     if (args.length == 0) {
    //         // Kiểm tra tất cả người chơi online
    //         Message.send(sender, "check.checking-all");
    //         int count = 0;
    //         for (Player p : Bukkit.getOnlinePlayers()) {
    //             List<ViolationResult> violations = plugin.getItemChecker().checkInventory(p);
    //             if (!violations.isEmpty()) {
    //                 Message.send(sender, "check.violations-found",
    //                     "player", p.getName(),
    //                     "count", violations.size()
    //                 );
    //                 for (ViolationResult v : violations) {
    //                     Message.send(sender, "check.violation-detail",
    //                         "reason", v.getReason()
    //                     );
    //                     count++;
    //                 }
    //             }

    //             if (count == 0) {
    //             Message.send(sender, "check.no-violations");
    //         }}
    //     } else {
    //         // Kiểm tra một player cụ thể
    //         Player target = Bukkit.getPlayer(args[0]);
    //         if (target == null) {
    //             Message.send(sender, "check.player-not-found",
    //                 "player", args[0]
    //             );
    //             return true;
    //         }

    //         List<ViolationResult> violations = plugin.getItemChecker().checkInventory(target);
    //         if (violations.isEmpty()) {
    //             Message.send(sender, "check.player-clean",
    //                 "player", target.getName()
    //             );
    //         } else {
    //             Message.send(sender, "check.player-violations-header",
    //                 "count", violations.size(),
    //                 "player", target.getName()
    //             );
    //             for (ViolationResult v : violations) {
    //                 Message.send(sender, "check.violation-slot",
    //                     "slot", v.getSlot(),
    //                     "reason", v.getReason()
    //                 );
    //             }
    //         }
    //     }
    //     return true;
    // }
}