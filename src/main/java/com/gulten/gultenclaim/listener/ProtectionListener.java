package com.gulten.gultenclaim.listener;

import com.gulten.gultenclaim.GultenClaim;
import com.gulten.gultenclaim.manager.ClaimManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.UUID;

public class ProtectionListener implements Listener {

    private final GultenClaim plugin;
    private final ClaimManager claimManager;

    public ProtectionListener(GultenClaim plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();

        // Claim içine giren düşman canlıları her 3 saniyede bir temizle
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanHostileMobsFromClaims, 100L, 60L);
    }

    /**
     * mob-spawning kapalı olan claim'lerdeki tüm düşman (Monster) canlıları kaldırır.
     * Her 3 saniyede bir çalışır.
     */
    private void cleanHostileMobsFromClaims() {
        for (ClaimManager.ClaimedChunk claim : claimManager.getClaims().values()) {
            // Mob spawning açıksa temizleme
            if (claim.settingMobSpawning) continue;

            World world = Bukkit.getWorld(UUID.fromString(claim.worldUuid));
            if (world == null) continue;

            // Yüklü değilse atla (performans için)
            if (!world.isChunkLoaded(claim.x, claim.z)) continue;

            Chunk chunk = world.getChunkAt(claim.x, claim.z);
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Monster) {
                    entity.remove();
                }
            }
        }
    }

    // Block Break Protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-block-break", true)) {
            if (!claimManager.canBuild(player, block.getChunk())) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            }
        }
    }

    // Block Place Protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-block-place", true)) {
            if (!claimManager.canBuild(player, block.getChunk())) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            }
        }
    }

    // Player Interaction Protection (Chests, Doors, Buttons, Farmland Trample)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        Chunk chunk = block.getChunk();

        // 1. Farmland Trampling check
        if (event.getAction() == Action.PHYSICAL && block.getType() == Material.FARMLAND) {
            if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-crop-trample", true)) {
                if (claimManager.getClaimAt(chunk) != null) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // 2. Click block interaction check
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-interact", true)) {
                if (!claimManager.canBuild(player, chunk)) {
                    Material type = block.getType();
                    
                    // Check if block is interactable (container, door, gate, button, lever, bed, etc.)
                    if (isInteractable(type)) {
                        event.setCancelled(true);
                        player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    }
                }
            }
        }
    }

    // Entity Interact Protection (farmland trampling by mobs)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.FARMLAND) {
            if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-crop-trample", true)) {
                if (claimManager.getClaimAt(block.getChunk()) != null) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // Entity Damage Protection (Animals, Villagers, Frames, Monsters) + Player PvP toggle
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        Entity damagerEntity = event.getDamager();

        // If damager is projectile (arrow, etc.), get shooter
        Player player = null;
        if (damagerEntity instanceof Player) {
            player = (Player) damagerEntity;
        } else if (damagerEntity instanceof Projectile) {
            Projectile proj = (Projectile) damagerEntity;
            if (proj.getShooter() instanceof Player) {
                player = (Player) proj.getShooter();
            }
        }

        if (player == null) return;

        // --- Player vs Player PvP toggle ---
        if (target instanceof Player) {
            ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(target.getLocation().getChunk());
            if (claim != null && !claim.settingPvp && !claimManager.hasBypass(player)) {
                // CombatLogX: combat'tayken claim'de PvP'ye devam et (config'den)
                boolean combatPvpAllowed = plugin.getConfigManager().getConfig()
                        .getBoolean("combatlogx.allow-pvp-continue-in-claim", true);
                var clx = plugin.getCombatLogX();
                if (combatPvpAllowed && clx != null && clx.isEnabled()
                        && clx.isInCombat(player) && clx.isInCombat((Player) target)) {
                    // İkisi de combat'taysa PvP'ye izin ver
                    return;
                }
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("pvp-disabled-in-claim"));
            }
            return;
        }

        if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-entity-damage", true)) {
            // Tüm canlıları koru: hayvanlar, köylüler, zombie/skeleton dahil tüm mob'lar, çerçeveler
            if (isProtectedEntity(target)) {
                if (!claimManager.canBuild(player, target.getLocation().getChunk())) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
        }
    }

    // Oyuncu kova ile lav/su döktüğünde claim koruması
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!claimManager.canBuild(player, block.getChunk())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
        }
    }

    // Oyuncu kova ile lav/su aldığında claim koruması
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!claimManager.canBuild(player, block.getChunk())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
        }
    }

    // Oyuncu entity'yi ateşe verdiğinde claim koruması (EntityCombustByEntityEvent)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustByEntityEvent event) {
        Entity combuster = event.getCombuster();
        Player player = null;
        if (combuster instanceof Player) {
            player = (Player) combuster;
        } else if (combuster instanceof Projectile) {
            Projectile proj = (Projectile) combuster;
            if (proj.getShooter() instanceof Player) {
                player = (Player) proj.getShooter();
            }
        }
        if (player == null) return;
        Entity target = event.getEntity();
        if (isProtectedEntity(target)) {
            if (!claimManager.canBuild(player, target.getLocation().getChunk())) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            }
        }
    }

    // Fire Spread Toggle
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != Material.FIRE) return;
        ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(event.getBlock().getChunk());
        if (claim != null && !claim.settingFire) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(event.getBlock().getChunk());
        if (claim == null) return;

        BlockIgniteEvent.IgniteCause cause = event.getCause();

        // Doğal/çevresel ateş yayılımı — fire toggle'a bağlı
        if (cause == BlockIgniteEvent.IgniteCause.SPREAD
                || cause == BlockIgniteEvent.IgniteCause.LIGHTNING
                || cause == BlockIgniteEvent.IgniteCause.LAVA) {
            if (!claim.settingFire) {
                event.setCancelled(true);
            }
            return;
        }

        // Oyuncu kaynaklı ateş — sadece claim sahibi/güvenilir kişi yakabilir
        Player igniter = event.getPlayer();
        if (igniter != null) {
            if (!claimManager.canBuild(igniter, event.getBlock().getChunk())) {
                event.setCancelled(true);
                igniter.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            }
        }
    }

    // Mob Spawning Toggle
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();

        // Sadece doğal/otomatik spawn türlerini etkile (doğal, spawner, pekiştirme)
        // Plugin tetikli, egg, yumurtlama, evcilleştirme, özel spawn vb. etkileme
        boolean isNaturalSpawn = reason == CreatureSpawnEvent.SpawnReason.NATURAL
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER
                || reason == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS;
        if (!isNaturalSpawn) return;

        ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(event.getLocation().getChunk());
        if (claim != null && !claim.settingMobSpawning) {
            event.setCancelled(true);
        }
    }


    // Armor Stand Manipulation Protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        ArmorStand armorStand = event.getRightClicked();

        if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-entity-damage", true)) {
            if (!claimManager.canBuild(player, armorStand.getLocation().getChunk())) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            }
        }
    }

    // Player Interact Entity (Item Frame, Item Display, Animals)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getRightClicked();

        if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-entity-damage", true)) {
            if (target instanceof ItemFrame || target instanceof ArmorStand || target instanceof Painting) {
                if (!claimManager.canBuild(player, target.getLocation().getChunk())) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
        }
    }

    // Hanging Break Protection (Paintings, Item Frames by player)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player) {
            Player player = (Player) event.getRemover();
            if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-entity-damage", true)) {
                if (!claimManager.canBuild(player, event.getEntity().getLocation().getChunk())) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
        }
    }

    // Hanging Place Protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-block-place", true)) {
                if (!claimManager.canBuild(player, event.getEntity().getLocation().getChunk())) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
        }
    }

    // Explosion Protection: filter blocks destroyed inside claims (respects settingExplosions toggle)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-explosions", true)) {
            event.blockList().removeIf(block -> {
                ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(block.getChunk());
                return claim != null && !claim.settingExplosions;
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-explosions", true)) {
            event.blockList().removeIf(block -> {
                ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(block.getChunk());
                return claim != null && !claim.settingExplosions;
            });
        }
    }

    // Piston Griefing Protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-piston-grief", true)) {
            Block pistonBlock = event.getBlock();
            BlockFace direction = event.getDirection();
            ClaimManager.ClaimedChunk pistonClaim = claimManager.getClaimAt(pistonBlock.getChunk());

            // Check blocks pushed
            for (Block block : event.getBlocks()) {
                if (checkPistonMove(pistonClaim, block, direction)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (plugin.getConfigManager().getConfig().getBoolean("protection.prevent-piston-grief", true)) {
            Block pistonBlock = event.getBlock();
            BlockFace direction = event.getDirection();
            ClaimManager.ClaimedChunk pistonClaim = claimManager.getClaimAt(pistonBlock.getChunk());

            // For retracting, block is pulled towards the piston
            for (Block block : event.getBlocks()) {
                if (checkPistonMove(pistonClaim, block, direction)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean checkPistonMove(ClaimManager.ClaimedChunk pistonClaim, Block block, BlockFace direction) {
        ClaimManager.ClaimedChunk currentBlockClaim = claimManager.getClaimAt(block.getChunk());
        ClaimManager.ClaimedChunk destBlockClaim = claimManager.getClaimAt(block.getRelative(direction).getChunk());

        // Check if block moves across boundaries
        if (!isSameOwner(pistonClaim, currentBlockClaim) || !isSameOwner(pistonClaim, destBlockClaim)) {
            return true; // Cancel movement
        }
        return false;
    }

    private boolean isSameOwner(ClaimManager.ClaimedChunk claim1, ClaimManager.ClaimedChunk claim2) {
        if (claim1 == null && claim2 == null) return true;
        if (claim1 != null && claim2 != null) {
            return claim1.ownerUuid.equals(claim2.ownerUuid);
        }
        return false;
    }

    // Utility to determine if a block is interactable
    private boolean isInteractable(Material material) {
        String name = material.name();
        return name.contains("CHEST") ||
               name.contains("SHULKER_BOX") ||
               name.contains("BARREL") ||
               name.contains("FURNACE") ||
               name.contains("DISPENSER") ||
               name.contains("DROPPER") ||
               name.contains("HOPPER") ||
               name.contains("DOOR") ||
               name.contains("GATE") ||
               name.contains("BUTTON") ||
               name.contains("LEVER") ||
               name.contains("PLATE") || // Pressure plates
               name.contains("TRAPDOOR") ||
               name.contains("BED") ||
               material == Material.ANVIL ||
               material == Material.CHIPPED_ANVIL ||
               material == Material.DAMAGED_ANVIL ||
               material == Material.BREWING_STAND ||
               material == Material.BEACON ||
               material == Material.REPEATING_COMMAND_BLOCK ||
               material == Material.COMMAND_BLOCK ||
               material == Material.CHAIN_COMMAND_BLOCK ||
               material == Material.JUKEBOX ||
               material == Material.NOTE_BLOCK ||
               material == Material.LECTERN ||
               material == Material.DAYLIGHT_DETECTOR ||
               material == Material.BLAST_FURNACE ||
               material == Material.SMOKER ||
               material == Material.LOOM ||
               material == Material.CARTOGRAPHY_TABLE ||
               material == Material.GRINDSTONE ||
               material == Material.STONECUTTER ||
               material == Material.SMITHING_TABLE ||
               material == Material.CRAFTING_TABLE ||
               material == Material.ENCHANTING_TABLE ||
               material == Material.RESPAWN_ANCHOR ||
               material == Material.CHISELED_BOOKSHELF ||
               material == Material.COMPOSTER ||
               material == Material.FLOWER_POT ||
               material == Material.SWEET_BERRY_BUSH ||
               material == Material.CAKE;
    }

    // Utility to determine if an entity is protected
    private boolean isProtectedEntity(Entity entity) {
        // Pasif hayvanlar, köylüler, çerçeveler, resimler
        if (entity instanceof Animals ||
            entity instanceof Villager ||
            entity instanceof ArmorStand ||
            entity instanceof ItemFrame ||
            entity instanceof Painting ||
            entity instanceof WanderingTrader ||
            entity instanceof AbstractVillager) {
            return true;
        }
        // Tüm düşman canlılar (zombie, skeleton, creeper vb.) da korunuyor
        if (entity instanceof Monster) {
            return true;
        }
        // Golem'ler
        if (entity instanceof Golem) {
            return true;
        }
        return false;
    }
}
