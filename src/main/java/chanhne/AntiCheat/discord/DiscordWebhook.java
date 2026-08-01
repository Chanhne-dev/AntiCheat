package chanhne.AntiCheat.discord;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.bukkit.Bukkit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class DiscordWebhook {

    private final String webhook;

    public DiscordWebhook(String webhook) {
        this.webhook = webhook;
    }

    public void send(String title, String description, int color) {

        if (webhook == null || webhook.isBlank()) return;
        HttpURLConnection connection = null;

        try {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", title);
            embed.addProperty("description", description);
            embed.addProperty("color", color);

            JsonObject payload = new JsonObject();
            payload.add("embeds", new JsonArray());
            payload.getAsJsonArray("embeds").add(embed);

            connection = (HttpURLConnection) URI.create(webhook).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();

            if (code == 204) return;

            Bukkit.getLogger().warning("[AntiCheat] Discord webhook trả về HTTP " + code);
            InputStream error = connection.getErrorStream() != null?connection.getErrorStream():connection.getInputStream();

            if (error != null) {
                String body = new String(error.readAllBytes(), StandardCharsets.UTF_8);
                Bukkit.getLogger().warning("[AntiCheat] Discord response: " + body);
            }

        } catch (java.net.ConnectException e) {
            Bukkit.getLogger().warning("[AntiCheat] Không thể kết nối tới Discord.");

        } catch (java.net.SocketTimeoutException e) {
            Bukkit.getLogger().warning("[AntiCheat] Kết nối Discord bị timeout.");

        } catch (java.net.UnknownHostException e) {
            Bukkit.getLogger().warning("[AntiCheat] Không phân giải được máy chủ discord.com.");

        } catch (Exception e) {
            Bukkit.getLogger().severe("[AntiCheat] Gửi Discord webhook thất bại.");
            e.printStackTrace();

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}