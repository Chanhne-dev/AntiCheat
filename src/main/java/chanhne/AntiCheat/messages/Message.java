package chanhne.AntiCheat.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import chanhne.AntiCheat.Mainplugin;
import chanhne.AntiCheat.util.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class Message {
    private static Mainplugin plugin;
    private static final Map<String, Object> messages = new HashMap<>();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static void init(Mainplugin MainPlugin) {
        plugin = MainPlugin;
        loadMessages();
    }

    private static void loadMessages() {
        messages.clear();

        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {plugin.saveResource("messages.yml", false);}
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        InputStream stream = plugin.getResource("messages.yml");
        if (stream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

            boolean changed = false;

            for (String key : defaults.getKeys(true)) {
                if (!cfg.contains(key)) {
                    cfg.set(key, defaults.get(key));
                    changed = true;
                }
            }

            if (changed) {
                try {
                    cfg.save(file);
                } catch (Exception ex) {
                    plugin.getLogger().warning(ex.getMessage());
                }
            }
        }

        for (String key : cfg.getKeys(true)) {
            if (cfg.isString(key)) {
                messages.put(key, cfg.getString(key, ""));
            } else if (cfg.isList(key)) {
                messages.put(key, cfg.getStringList(key));
            }
        }
    }

    public static Component component(String key, Object... args) {
        Object value = messages.get(key);

        if (!(value instanceof String msg)) {
            return Component.text("Missing message: " + key);
        }

        if (args.length > 0) {
            msg = applyPlaceholders(msg, args);
        }

        return LEGACY.deserialize(msg).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> componentList(String key, Object... args) {
        Object value = messages.get(key);

    if (!(value instanceof List<?> rawList)) {
        return List.of(
                LEGACY.deserialize(
                        get("missing-list", "key", key)
                ).decoration(TextDecoration.ITALIC, false)
        );
    }

        return rawList.stream().map(String.class::cast).map(line -> args.length > 0
            ? applyPlaceholders(line, args)
            : line).map(LEGACY::deserialize).map(component -> component.decoration(TextDecoration.ITALIC,false)).collect(Collectors.toList());
    }

    public static Component getComponent(String key, Object... args) {
        return component(key, args);
    }

    public static String get(String key, Object... args) {
        Object value = messages.get(key);

        if (!(value instanceof String msg)) {
            Object missing = messages.get("missing");

            if (missing instanceof String missingMsg) {
                return applyPlaceholders(
                        missingMsg,
                        "key",
                        key
                );
            }

            return "Missing message: " + key;
        }

        return args.length > 0
                ? applyPlaceholders(msg, args)
                : msg;
    }

    public static List<String> getList(String key, Object... args) {
        Object value = messages.get(key);

        if (!(value instanceof List<?> rawList)) {
            return Collections.emptyList();
        }

        return rawList.stream().map(String.class::cast).map(line -> args.length > 0
            ? applyPlaceholders(line, args)
            : line).collect(Collectors.toList());
    }

    public static void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(component(key, args));
    }

    public static void sendList(CommandSender sender, String key, Object... args) {
        componentList(key, args)
                .forEach(sender::sendMessage);
    }

    private static String applyPlaceholders(String message,Object... replacements) {
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String key = String.valueOf(replacements[i]);
            String value = String.valueOf(replacements[i + 1]);

            message = message.replace("{" + key + "}", value);
        }

        return message;
    }

    public static void reload() {
        loadMessages();
    }

    public static Component colored(String key, Object... args) {
        return ColorUtil.colorComponent(get(key, args));
    }

    public static List<Component> coloredList(String key, Object... args) {
        return getList(key, args).stream()
                .map(ColorUtil::colorComponent)
                .collect(Collectors.toList());
    }
}