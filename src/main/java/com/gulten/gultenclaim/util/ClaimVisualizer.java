package com.gulten.gultenclaim.util;

import com.gulten.gultenclaim.GultenClaim;
import com.gulten.gultenclaim.manager.ClaimManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class ClaimVisualizer {

    private final GultenClaim plugin;
    private final ClaimManager claimManager;

    public ClaimVisualizer(GultenClaim plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
    }

    // Show chunk borders with particles for 10 seconds
    public void showBorders(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        World world = chunk.getWorld();
        
        int minX = chunk.getX() * 16;
        int minZ = chunk.getZ() * 16;
        int maxX = minX + 16;
        int maxZ = minZ + 16;

        player.sendMessage(plugin.getConfigManager().getMessage("visualizer-shown"));

        new BukkitRunnable() {
            int seconds = 0;

            @Override
            public void run() {
                if (!player.isOnline() || seconds >= 10) {
                    cancel();
                    return;
                }

                double y = player.getLocation().getY() + 0.5;

                // Draw perimeter (horizontal lines)
                // Spawn particles with a spacing of 1 block
                for (int x = minX; x <= maxX; x++) {
                    spawnBorderParticle(player, world, x, y, minZ);
                    spawnBorderParticle(player, world, x, y, maxZ);
                }
                for (int z = minZ + 1; z < maxZ; z++) {
                    spawnBorderParticle(player, world, minX, y, z);
                    spawnBorderParticle(player, world, maxX, y, z);
                }

                seconds++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnBorderParticle(Player player, World world, double x, double y, double z) {
        // Particle.FLAME is supported across all versions and is very visible.
        player.spawnParticle(Particle.FLAME, new Location(world, x, y, z), 1, 0, 0, 0, 0);
    }

    // Print a 2D chat map of claims around player
    public void printMap(Player player) {
        Chunk centerChunk = player.getLocation().getChunk();
        World world = centerChunk.getWorld();
        UUID playerUuid = player.getUniqueId();
        
        int centerChunkX = centerChunk.getX();
        int centerChunkZ = centerChunk.getZ();

        player.sendMessage(" ");
        player.sendMessage(plugin.getConfigManager().getMessage("map-title"));
        
        // Show 9x9 grid around the player (Z goes north to south, X goes west to east)
        // In Minecraft, -Z is North, +Z is South. -X is West, +X is East.
        for (int dz = -4; dz <= 4; dz++) {
            StringBuilder line = new StringBuilder();
            int z = centerChunkZ + dz;
            
            for (int dx = -4; dx <= 4; dx++) {
                int x = centerChunkX + dx;
                
                // Add separator space
                if (dx > -4) {
                    line.append(" ");
                }

                // If center chunk, highlight the player
                if (dx == 0 && dz == 0) {
                    ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(world.getUID().toString(), x, z);
                    if (claim == null) {
                        line.append("&b+"); // Player standing in wildlands
                    } else if (claim.ownerUuid.equals(playerUuid)) {
                        line.append(claim.isOutpost ? "&eK" : "&bH"); // Player standing in own claim
                    } else {
                        line.append("&c#"); // Player standing in someone else's claim
                    }
                    continue;
                }

                ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(world.getUID().toString(), x, z);
                if (claim == null) {
                    line.append("&7/"); // Unclaimed
                } else if (claim.ownerUuid.equals(playerUuid)) {
                    line.append(claim.isOutpost ? "&eK" : "&bH"); // Own standard (H) or outpost (K)
                } else {
                    line.append("&c#"); // Claimed by someone else
                }
            }
            player.sendMessage(plugin.getConfigManager().colorize(line.toString()));
        }
        
        player.sendMessage(plugin.getConfigManager().getMessage("map-legend"));
        player.sendMessage(" ");
    }
}
