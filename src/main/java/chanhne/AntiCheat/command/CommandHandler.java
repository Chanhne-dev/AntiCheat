package chanhne.AntiCheat.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.messages.Message;
import chanhne.AntiCheat.task.ScanSession;


public class CommandHandler implements CommandExecutor {

    private final Mainplugin plugin;

    public CommandHandler(Mainplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Xử lý lệnh mới /anticheat (hoặc /at) với subcommands
        if (command.getName().equalsIgnoreCase("anticheat") || label.equalsIgnoreCase("at")) {
            if (args.length == 0) {
                // Gửi hướng dẫn sử dụng
                Message.send(sender, "plugin.usage", "label", label);
                return true;
            } else if (args.length == 3 && args[0].equalsIgnoreCase("check")) {
                Player target = Bukkit.getPlayer(args[1]);
                int seconds;

                if (target == null) {
                    sender.sendMessage("Player not found.");
                    return true;
                }

                try {
                    seconds = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("Invalid seconds.");
                    return true;
                }
                new ScanSession(plugin, target, seconds).start();
                sender.sendMessage("Started checking " + target.getName());

                return true;
            }

            String sub = args[0].toLowerCase();
            if (sub.equals("reload")) {
                return handleReload(sender);
            } else {
                Message.send(sender, "plugin.invalid-subcommand");
                return true;
            }
        }

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
}