package com.gulten.gultenclaim.config;

import com.gulten.gultenclaim.GultenClaim;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ConfigManager {

    private final GultenClaim plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File messagesFile;
    private String prefix;

    public ConfigManager(GultenClaim plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        // Load config.yml
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Get language
        String lang = config.getString("language", "tr").toLowerCase();
        String messagesFileName = "messages_" + lang + ".yml";
        
        // Save default messages if they don't exist
        saveDefaultMessages(messagesFileName);

        // Load messages.yml
        this.messagesFile = new File(plugin.getDataFolder(), messagesFileName);
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Load default values from jar for messages
        InputStream defMessagesStream = plugin.getResource(messagesFileName);
        if (defMessagesStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defMessagesStream, StandardCharsets.UTF_8));
            this.messages.setDefaults(defConfig);
        }

        this.prefix = colorize(messages.getString("prefix", "&8[&6GultenClaim&8] "));
    }

    private void saveDefaultMessages(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Default message file not found in jar: " + fileName);
            }
        }
    }

    public String getMessage(String path) {
        String msg = messages.getString(path);
        if (msg == null) {
            return ChatColor.RED + "Message path missing: " + path;
        }
        return prefix + colorize(msg);
    }

    public String getRawMessage(String path) {
        String msg = messages.getString(path);
        if (msg == null) {
            return ChatColor.RED + "Message path missing: " + path;
        }
        return colorize(msg);
    }

    public List<String> getMessageList(String path) {
        List<String> rawList = messages.getStringList(path);
        List<String> colorizedList = new ArrayList<>();
        for (String line : rawList) {
            colorizedList.add(colorize(line));
        }
        return colorizedList;
    }

    public String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }
}
