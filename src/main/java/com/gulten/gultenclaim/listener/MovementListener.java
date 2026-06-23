package com.gulten.gultenclaim.listener;

import com.gulten.gultenclaim.GultenClaim;
import com.gulten.gultenclaim.manager.ClaimManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MovementListener implements Listener {

    private final GultenClaim plugin;
    private final ClaimManager claimManager;
    private final Set<UUID> fallProtected = new HashSet<>();

    public MovementListener(GultenClaim plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // Ensure they actually crossed a chunk border
        if (from.getBlockX() >> 4 == to.getBlockX() >> 4 && from.getBlockZ() >> 4 == to.getBlockZ() >> 4) {
            return;
        }

        Player player = event.getPlayer();
        Chunk toChunk = to.getChunk();
        Chunk fromChunk = from.getChunk();

        ClaimManager.ClaimedChunk fromClaim = claimManager.getClaimAt(fromChunk);
        ClaimManager.ClaimedChunk toClaim = claimManager.getClaimAt(toChunk);

        // 1. Boundary Crossing Titles
        checkBoundaryCrossing(player, fromClaim, toClaim);

        // 2. Auto Claiming Logic
        if (claimManager.isAutoClaiming(player)) {
            handleAutoClaim(player, toChunk);
        }

        // 3. Flight Management Check
        if (claimManager.isFlying(player)) {
            handleFlightChecking(player, toChunk);
        }
    }

    private void checkBoundaryCrossing(Player player, ClaimManager.ClaimedChunk fromClaim, ClaimManager.ClaimedChunk toClaim) {
        UUID fromOwner = fromClaim != null ? fromClaim.ownerUuid : null;
        UUID toOwner = toClaim != null ? toClaim.ownerUuid : null;

        // Owner has changed
        if (fromOwner != toOwner || (fromClaim != null && toClaim != null && fromClaim.isOutpost != toClaim.isOutpost)) {
            if (toClaim == null) {
                // Entered Wildlands
                String title = plugin.getConfigManager().getRawMessage("entering-wildlands-title");
                String subtitle = plugin.getConfigManager().getRawMessage("entering-wildlands-subtitle");
                player.sendTitle(title, subtitle, 10, 40, 10);
            } else {
                // Entered Claim
                String ownerName = toClaim.ownerName;
                String title = plugin.getConfigManager().getRawMessage("entering-claim-title").replace("%owner%", ownerName);
                String subtitle = plugin.getConfigManager().getRawMessage("entering-claim-subtitle").replace("%owner%", ownerName);
                player.sendTitle(title, subtitle, 10, 40, 10);
            }
        }
    }

    private void handleAutoClaim(Player player, Chunk chunk) {
        World world = chunk.getWorld();
        int x = chunk.getX();
        int z = chunk.getZ();

        ClaimManager.ClaimedChunk existing = claimManager.getClaimAt(chunk);
        if (existing != null) {
            // Already claimed
            return;
        }

        // Auto claim is standard claim (always contiguous)
        ClaimManager.ClaimResult result = claimManager.claimChunk(player, world, x, z, false);
        if (result == ClaimManager.ClaimResult.SUCCESS) {
            double price = plugin.getConfigManager().getConfig().getDouble("claim.price", 100.0);
            String formattedPrice = plugin.getEconomyIntegration().format(price);
            player.sendMessage(plugin.getConfigManager().getMessage("claim-success").replace("%price%", formattedPrice));
        } else {
            // Disable autoclaim on failure and send why
            claimManager.toggleAutoClaim(player);
            player.sendMessage(plugin.getConfigManager().getMessage("auto-claim-disabled"));
            
            switch (result) {
                case LIMIT_REACHED:
                    int maxClaims = claimManager.getMaxClaims(player);
                    player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-limit-reached").replace("%limit%", String.valueOf(maxClaims)));
                    break;
                case NO_MONEY:
                    double price = plugin.getConfigManager().getConfig().getDouble("claim.price", 100.0);
                    String formattedPrice = plugin.getEconomyIntegration().format(price);
                    player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-no-money").replace("%price%", formattedPrice));
                    break;
                case NOT_ADJACENT:
                    player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-not-adjacent"));
                    break;
                default:
                    break;
            }
        }
    }

    private void handleFlightChecking(Player player, Chunk chunk) {
        ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(chunk);
        if (claim == null || (!claim.ownerUuid.equals(player.getUniqueId()) && !claimManager.hasBypass(player))) {
            // Disable fly
            claimManager.disableFlight(player);
            player.sendMessage(plugin.getConfigManager().getMessage("fly-disabled-left-claim"));

            // Give fall damage protection
            UUID uuid = player.getUniqueId();
            fallProtected.add(uuid);
            
            int duration = plugin.getConfigManager().getConfig().getInt("flight.fall-protection-seconds", 3);
            
            // Remove fall protection after saniye
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                fallProtected.remove(uuid);
            }, duration * 20L);
        }
    }

    // Fall damage protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Player player = (Player) event.getEntity();
            if (fallProtected.contains(player.getUniqueId())) {
                event.setCancelled(true);
                fallProtected.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Clean toggles
        claimManager.removeAutoClaimer(player);
        claimManager.removeBypasser(player);
        
        if (claimManager.isFlying(player)) {
            claimManager.disableFlight(player);
        }
        
        fallProtected.remove(uuid);
    }
}
