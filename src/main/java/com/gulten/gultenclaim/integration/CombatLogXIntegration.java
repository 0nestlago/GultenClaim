package com.gulten.gultenclaim.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * CombatLogX entegrasyonu — Reflection tabanlı.
 * Derleme zamanı bağımlılığı yoktur; CombatLogX yüklüyse çalışır, yoksa devre dışı kalır.
 */
public class CombatLogXIntegration {

    private final boolean enabled;
    private Object combatManager; // ICombatManager — reflection ile erişilir
    private Method isInCombatMethod;

    public CombatLogXIntegration(JavaPlugin plugin) {
        boolean tempEnabled = false;

        try {
            Plugin clxPlugin = plugin.getServer().getPluginManager().getPlugin("CombatLogX");
            if (clxPlugin == null) {
                plugin.getLogger().info("CombatLogX bulunamadı, entegrasyon devre dışı.");
            } else {
                Class<?> iCombatLogX = null;
                String[] possiblePackages = {
                        "com.sirblobman.combatlogx.api.ICombatLogX",
                        "com.github.sirblobman.combatlogx.api.ICombatLogX",
                        "com.gambino.combatlogx.api.ICombatLogX"
                };

                for (String pkg : possiblePackages) {
                    try {
                        iCombatLogX = Class.forName(pkg);
                        break;
                    } catch (ClassNotFoundException ignored) {}
                }

                if (iCombatLogX != null && iCombatLogX.isInstance(clxPlugin)) {
                    Method getCombatManager = iCombatLogX.getMethod("getCombatManager");
                    this.combatManager = getCombatManager.invoke(clxPlugin);

                    Class<?> iCombatManager = null;
                    String[] managerPackages = {
                            "com.sirblobman.combatlogx.api.manager.ICombatManager",
                            "com.github.sirblobman.combatlogx.api.manager.ICombatManager",
                            "com.gambino.combatlogx.api.manager.ICombatManager"
                    };

                    for (String pkg : managerPackages) {
                        try {
                            iCombatManager = Class.forName(pkg);
                            break;
                        } catch (ClassNotFoundException ignored) {}
                    }

                    if (iCombatManager != null) {
                        this.isInCombatMethod = iCombatManager.getMethod("isInCombat", Player.class);
                        tempEnabled = true;
                        plugin.getLogger().info("CombatLogX entegrasyonu (reflection) başarıyla aktif edildi.");
                    }
                } else {
                    plugin.getLogger().warning("CombatLogX bulundu ancak API arayüzü eşleşmedi.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("CombatLogX entegrasyonu yüklenemedi: " + e.getMessage());
        }

        this.enabled = tempEnabled;
    }

    /** CombatLogX entegrasyonu aktif mi? */
    public boolean isEnabled() {
        return enabled;
    }

    /** Oyuncu combat'ta mı? */
    public boolean isInCombat(Player player) {
        if (!enabled || combatManager == null || isInCombatMethod == null) return false;
        try {
            Object result = isInCombatMethod.invoke(combatManager, player);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}
