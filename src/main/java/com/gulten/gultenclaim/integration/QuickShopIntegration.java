package com.gulten.gultenclaim.integration;

import com.gulten.gultenclaim.GultenClaim;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * QuickShop-Hikari ile GultenClaim entegrasyonu.
 *
 * QuickShop-Hikari mağaza kurarken dahili olarak BlockBreakEvent simüle eder.
 * Bu sayede GultenClaim'in mevcut ProtectionListener'ı mağaza kurma korumasını
 * zaten sağlar. Bu listener ek kontroller için açık bırakılmıştır.
 *
 * NOT: quickshop-api Maven bağımlılığı henüz çözülemediğinden event-bazlı
 * entegrasyon Bukkit'in standart event'leriyle sağlanmaktadır.
 */
public class QuickShopIntegration implements Listener {

    private final GultenClaim plugin;

    public QuickShopIntegration(GultenClaim plugin) {
        this.plugin = plugin;
        
        try {
            // QuickShop-Hikari ShopCreateEvent arayüzünü yansıma ile dinleyelim
            Class<? extends org.bukkit.event.Event> shopCreateEventClass = 
                    (Class<? extends org.bukkit.event.Event>) Class.forName("com.ghostchu.quickshop.api.event.ShopCreateEvent");
            
            plugin.getServer().getPluginManager().registerEvent(shopCreateEventClass, this, EventPriority.HIGH, 
                (listener, event) -> {
                    if (!shopCreateEventClass.isInstance(event)) return;
                    
                    try {
                        // event.getCreator() -> UUID
                        java.util.UUID creatorUuid = (java.util.UUID) shopCreateEventClass.getMethod("getCreator").invoke(event);
                        Player player = org.bukkit.Bukkit.getPlayer(creatorUuid);
                        if (player == null) return;
                        
                        // event.getLocation() -> Location
                        org.bukkit.Location loc = (org.bukkit.Location) shopCreateEventClass.getMethod("getLocation").invoke(event);
                        
                        // Check if player can build in this claim
                        if (!plugin.getClaimManager().canBuild(player, loc.getChunk())) {
                            // event.setCancelled(true)
                            shopCreateEventClass.getMethod("setCancelled", boolean.class).invoke(event, true);
                            player.sendMessage(plugin.getConfigManager().getMessage("quickshop-no-permission-create"));
                        }
                    } catch (Exception e) {
                        // ignore reflection errors during event
                    }
                }, plugin, true);
                
            plugin.getLogger().info("QuickShop-Hikari 'ShopCreateEvent' entegrasyonu aktif edildi.");
            
        } catch (Exception e) {
            plugin.getLogger().info("QuickShop-Hikari API bulunamadı veya uyumsuz sürüm.");
        }
    }
}
