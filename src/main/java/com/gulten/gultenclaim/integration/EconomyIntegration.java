package com.gulten.gultenclaim.integration;

import com.gulten.gultenclaim.GultenClaim;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

public class EconomyIntegration {

    private final GultenClaim plugin;
    private Economy economy = null;

    public EconomyIntegration(GultenClaim plugin) {
        this.plugin = plugin;
        // Delay by 1 tick so Vault and its economy plugins have time to register their services
        plugin.getServer().getScheduler().runTask(plugin, this::setupEconomy);
    }

    public void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().log(Level.WARNING, "[GultenClaim] Vault bulunamadi! Ekonomi ozellikleri devre disi.");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().log(Level.WARNING, "[GultenClaim] Vault ekonomi servisi kayitli degil! " +
                    "Bir ekonomi plugini yuklu oldugundan emin olun (EssentialsX, CMI, vb.)");
            return;
        }

        Economy candidate = rsp.getProvider();
        if (candidate == null) {
            plugin.getLogger().log(Level.WARNING, "[GultenClaim] Vault ekonomi saglayicisi null dondu!");
            return;
        }

        this.economy = candidate;
        plugin.getLogger().log(Level.INFO, "[GultenClaim] Vault Economy (" + economy.getName() + ") baglantisi kuruldu.");
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (!isEnabled()) return 0;
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (!isEnabled()) return true;
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isEnabled()) return true;
        if (amount <= 0) return true;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isEnabled()) return true;
        if (amount <= 0) return true;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        if (!isEnabled()) {
            return String.format("%.2f", amount);
        }
        return economy.format(amount);
    }
}
