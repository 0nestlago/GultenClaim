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
    }

    /**
     * QuickShop mağaza kurarken bir BlockBreakEvent simüle eder.
     * GultenClaim'in ProtectionListener'ı bunu zaten yakalar.
     * Bu handler ilave loglama veya mesaj özelleştirme için kullanılabilir.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockBreakMonitor(BlockBreakEvent event) {
        // Gelecekte QuickShop-Hikari API entegrasyonu için yer tutucu.
        // Şu an ProtectionListener yeterli korumayı sağlamaktadır.
    }
}
