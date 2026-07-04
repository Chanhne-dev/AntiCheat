package chanhne.AntiCheat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

public class ColorUtil {

    public static String stripColor(String message) {
        return ChatColor.stripColor(message);
    }

    public static Component colorComponent(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}