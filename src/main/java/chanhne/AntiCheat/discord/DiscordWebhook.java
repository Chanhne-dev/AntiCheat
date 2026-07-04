package chanhne.AntiCheat.discord;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.bukkit.Bukkit;

import com.google.gson.JsonObject;

public class DiscordWebhook {

    private final String webhook;

    public DiscordWebhook(String webhook) {
        this.webhook = webhook;
    }

    public void send(String title, String description, int color) {

        if (webhook == null || webhook.isBlank()) {
            return;
        }

        try {

            JsonObject embed = new JsonObject();
            embed.addProperty("title", title);
            embed.addProperty("description", description);
            embed.addProperty("color", color);

            JsonObject payload = new JsonObject();
            payload.add("embeds", new com.google.gson.JsonArray());
            payload.getAsJsonArray("embeds").add(embed);

            HttpURLConnection connection = (HttpURLConnection) URI.create(webhook).toURL().openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            System.out.println("Discord Response = " + code);

            if (code != 204) throw new RuntimeException("Discord returned HTTP " + code);
        } catch (Exception e) {
            Bukkit.getLogger().severe("Failed to send Discord webhook");
            e.printStackTrace();
        }
    }
}