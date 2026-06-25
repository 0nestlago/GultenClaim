package com.gulten.gultenclaim.util;

import com.gulten.gultenclaim.GultenClaim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Sunucu başladığında GitHub Releases API'den en son sürümü kontrol eder.
 * Yeni sürüm varsa online OP oyuncularına dil ayarına göre (TR/EN) bildirim gönderir.
 * Oyuncular giriş yaptığında da kontrol edilir.
 */
public class UpdateChecker {

    private final GultenClaim plugin;
    // GitHub API URL — GultenClaim repo releases
    private static final String API_URL =
            "https://api.github.com/repos/0nestlago/GultenClaim/releases/latest";

    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateChecker(GultenClaim plugin) {
        this.plugin = plugin;
    }

    /**
     * Asenkron olarak GitHub'dan en son sürümü kontrol eder.
     * Sonuç hazır olunca online OP'lara bildirir.
     */
    public void checkAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String current = plugin.getDescription().getVersion();
            String latest = fetchLatestVersion();
            if (latest == null) return;

            this.latestVersion = latest;
            if (!latest.equalsIgnoreCase(current) && !latest.equalsIgnoreCase("v" + current)) {
                this.updateAvailable = true;
                // Ana thread'de bildirim gönder
                Bukkit.getScheduler().runTask(plugin, () -> notifyOnlinePlayers());
            }
        });
    }

    /**
     * Girişte OP kontrolü — yeni sürüm bildirimini göster.
     */
    public void notifyPlayer(Player player) {
        if (!updateAvailable || !player.isOp()) return;
        sendUpdateMessage(player);
    }

    private void notifyOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                sendUpdateMessage(player);
            }
        }
    }

    private void sendUpdateMessage(Player player) {
        String lang = plugin.getConfigManager().getConfig().getString("language", "tr").toLowerCase();
        String current = plugin.getDescription().getVersion();

        if (lang.equals("tr")) {
            player.sendMessage(plugin.getConfigManager().colorize(
                    "&8[&6GultenClaim&8] &eYeni bir sürüm mevcut! &a" + latestVersion +
                    " &7(Mevcut: &c" + current + "&7)"));
            player.sendMessage(plugin.getConfigManager().colorize(
                    "&8[&6GultenClaim&8] &7İndirmek için: &bhttps://github.com/0nestlago/GultenClaim/releases/latest"));
        } else {
            player.sendMessage(plugin.getConfigManager().colorize(
                    "&8[&6GultenClaim&8] &eA new version is available! &a" + latestVersion +
                    " &7(Current: &c" + current + "&7)"));
            player.sendMessage(plugin.getConfigManager().colorize(
                    "&8[&6GultenClaim&8] &7Download at: &bhttps://github.com/0nestlago/GultenClaim/releases/latest"));
        }
    }

    /** GitHub releases API'den tag_name çeker (örn: "v1.3.0") */
    private String fetchLatestVersion() {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "GultenClaim-UpdateChecker");

            if (conn.getResponseCode() != 200) return null;

            try (Scanner scanner = new Scanner(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                while (scanner.hasNextLine()) sb.append(scanner.nextLine());
                String json = sb.toString();

                // Basit JSON parse — tag_name alanını çek
                int idx = json.indexOf("\"tag_name\"");
                if (idx == -1) return null;
                int start = json.indexOf("\"", idx + 10) + 1;
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("GultenClaim güncelleme kontrolü başarısız: " + e.getMessage());
            return null;
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
