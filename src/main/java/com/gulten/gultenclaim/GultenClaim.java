package com.gulten.gultenclaim;

import com.gulten.gultenclaim.command.ClaimCommand;
import com.gulten.gultenclaim.config.ConfigManager;
import com.gulten.gultenclaim.database.DatabaseManager;
import com.gulten.gultenclaim.integration.DynmapIntegration;
import com.gulten.gultenclaim.integration.EconomyIntegration;
import com.gulten.gultenclaim.integration.QuickShopIntegration;
import com.gulten.gultenclaim.listener.MovementListener;
import com.gulten.gultenclaim.listener.ProtectionListener;
import com.gulten.gultenclaim.manager.ClaimManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class GultenClaim extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private EconomyIntegration economyIntegration;
    private DynmapIntegration dynmapIntegration;
    private ClaimManager claimManager;

    @Override
    public void onEnable() {
        getLogger().log(Level.INFO, "GultenClaim plugin loading...");

        // 1. Config Manager
        this.configManager = new ConfigManager(this);

        // 2. Database Manager
        this.databaseManager = new DatabaseManager(this);

        // 3. Economy Hook (Vault)
        this.economyIntegration = new EconomyIntegration(this);

        // 4. Dynmap Hook
        this.dynmapIntegration = new DynmapIntegration(this);

        // 5. Core Claim Manager
        this.claimManager = new ClaimManager(this);

        // Register Listeners
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);

        // 6. QuickShop-Hikari Hook (opsiyonel)
        if (Bukkit.getPluginManager().getPlugin("QuickShop-Hikari") != null) {
            getServer().getPluginManager().registerEvents(new QuickShopIntegration(this), this);
            getLogger().info("QuickShop-Hikari entegrasyonu aktifleştirildi.");
        }

        // Register Command
        PluginCommand claimCommand = getCommand("claim");
        if (claimCommand != null) {
            ClaimCommand cmdExecutor = new ClaimCommand(this);
            claimCommand.setExecutor(cmdExecutor);
            claimCommand.setTabCompleter(cmdExecutor);
        } else {
            getLogger().log(Level.SEVERE, "Failed to register /claim command! Command description missing in plugin.yml");
        }

        getLogger().log(Level.INFO, "GultenClaim plugin version " + getDescription().getVersion() + " has been successfully loaded!");
    }

    @Override
    public void onDisable() {
        getLogger().log(Level.INFO, "GultenClaim plugin disabling...");

        // Disable flight for all flying players to prevent floating exploits after reload
        if (claimManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (claimManager.isFlying(player)) {
                    claimManager.disableFlight(player);
                }
            }
        }

        // Clear Dynmap markers
        if (dynmapIntegration != null) {
            dynmapIntegration.clearAll();
        }

        // Close database
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().log(Level.INFO, "GultenClaim has been disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EconomyIntegration getEconomyIntegration() {
        return economyIntegration;
    }

    public DynmapIntegration getDynmapIntegration() {
        return dynmapIntegration;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }
}
