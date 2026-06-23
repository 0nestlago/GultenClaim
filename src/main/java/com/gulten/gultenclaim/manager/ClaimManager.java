package com.gulten.gultenclaim.manager;

import com.gulten.gultenclaim.GultenClaim;
import com.gulten.gultenclaim.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.*;

public class ClaimManager {

    private final GultenClaim plugin;
    
    // In-memory cache of claimed chunks
    private final Map<ChunkCoords, ClaimedChunk> claims = new HashMap<>();
    
    // Cache of trust relations: ownerUuid -> (trustedUuid -> trustedName)
    private final Map<UUID, Map<UUID, String>> trustedPlayers = new HashMap<>();

    // Toggled settings
    private final Set<UUID> autoClaimers = new HashSet<>();
    private final Set<UUID> bypassPlayers = new HashSet<>();
    private final Set<UUID> flyingPlayers = new HashSet<>();

    // Spawn cache: ChunkCoords -> spawn Location
    private final Map<ChunkCoords, Location> claimSpawns = new HashMap<>();

    // Claim Colors for dynmap: UUID -> hex String
    private final Map<UUID, String> claimColors = new HashMap<>();

    public ClaimManager(GultenClaim plugin) {
        this.plugin = plugin;
        loadData();
    }

    private void loadData() {
        // Load claims from DB
        List<DatabaseManager.ClaimData> dbClaims = plugin.getDatabaseManager().loadClaims();
        for (DatabaseManager.ClaimData data : dbClaims) {
            ChunkCoords coords = new ChunkCoords(data.worldUuid, data.x, data.z);
            ClaimedChunk claim = new ClaimedChunk(
                    data.worldUuid,
                    data.x,
                    data.z,
                    UUID.fromString(data.ownerUuid),
                    data.ownerName,
                    data.isOutpost
            );
            claim.settingsFromJson(data.settings);
            claims.put(coords, claim);
        }

        // Load trusts from DB
        Map<UUID, Set<UUID>> dbTrusts = plugin.getDatabaseManager().loadTrusts();
        for (Map.Entry<UUID, Set<UUID>> entry : dbTrusts.entrySet()) {
            UUID ownerUuid = entry.getKey();
            Map<UUID, String> trustMap = this.trustedPlayers.computeIfAbsent(ownerUuid, k -> new HashMap<>());
            for (UUID trustedUuid : entry.getValue()) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(trustedUuid);
                String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Bilinmeyen";
                trustMap.put(trustedUuid, name);
            }
        }

        // Load spawn points from DB
        for (DatabaseManager.ClaimData data : dbClaims) {
            if (data.hasSpawn) {
                World world = Bukkit.getWorld(UUID.fromString(data.worldUuid));
                if (world != null) {
                    ChunkCoords coords = new ChunkCoords(data.worldUuid, data.x, data.z);
                    Location loc = new Location(world, data.spawnX, data.spawnY, data.spawnZ, data.spawnYaw, data.spawnPitch);
                    claimSpawns.put(coords, loc);
                }
            }
        }

        // Load colors
        claimColors.putAll(plugin.getDatabaseManager().loadColors());

        // Register claims on Dynmap
        Bukkit.getScheduler().runTaskLater(plugin, this::registerAllOnDynmap, 40L);
    }

    public String getPlayerColor(UUID uuid) {
        return claimColors.get(uuid);
    }

    public void setPlayerColor(UUID uuid, String hex) {
        if (hex == null) {
            claimColors.remove(uuid);
            // We can add a delete method in DB, but for now we can just save null or empty
            plugin.getDatabaseManager().saveColor(uuid, "");
        } else {
            claimColors.put(uuid, hex);
            plugin.getDatabaseManager().saveColor(uuid, hex);
        }
        updateOwnerDynmap(uuid);
    }

    public void registerAllOnDynmap() {
        if (!plugin.getDynmapIntegration().isEnabled()) return;
        for (ClaimedChunk claim : claims.values()) {
            World world = Bukkit.getWorld(UUID.fromString(claim.worldUuid));
            String worldName = world != null ? world.getName() : claim.worldUuid;
            String ownerColor = getPlayerColor(claim.ownerUuid);
            plugin.getDynmapIntegration().registerClaim(
                    worldName,
                    claim.x,
                    claim.z,
                    claim.ownerName,
                    claim.isOutpost,
                    getTrustedNames(claim.ownerUuid),
                    ownerColor
            );
        }
    }

    // Adjacency Check: check if chunk shares a border with any existing claim of the same player
    public boolean isAdjacent(UUID playerUuid, String worldUuid, int x, int z) {
        // If player has no claims at all, the first claim doesn't need to be adjacent
        if (getClaimCount(playerUuid) == 0) {
            return true;
        }

        ChunkCoords[] directions = new ChunkCoords[]{
                new ChunkCoords(worldUuid, x + 1, z),
                new ChunkCoords(worldUuid, x - 1, z),
                new ChunkCoords(worldUuid, x, z + 1),
                new ChunkCoords(worldUuid, x, z - 1)
        };

        for (ChunkCoords dir : directions) {
            ClaimedChunk neighbor = claims.get(dir);
            if (neighbor != null && neighbor.ownerUuid.equals(playerUuid)) {
                return true;
            }
        }

        return false;
    }

    // Overloaded method for external plugins like GultenClan to claim chunks bypassing limits
    public ClaimResult claimChunk(UUID ownerUuid, String ownerName, World world, int x, int z, boolean isOutpost, boolean bypassEconomyAndLimits) {
        String worldUuid = world.getUID().toString();
        ChunkCoords coords = new ChunkCoords(worldUuid, x, z);

        if (claims.containsKey(coords)) {
            return ClaimResult.ALREADY_CLAIMED;
        }

        // Add to cache
        ClaimedChunk claim = new ClaimedChunk(worldUuid, x, z, ownerUuid, ownerName, isOutpost);
        claims.put(coords, claim);

        // Add to database
        plugin.getDatabaseManager().addClaim(worldUuid, x, z, ownerUuid, ownerName, isOutpost);

        // Update Dynmap
        if (plugin.getDynmapIntegration().isEnabled()) {
            plugin.getDynmapIntegration().registerClaim(
                    world.getName(),
                    x,
                    z,
                    ownerName,
                    isOutpost,
                    getTrustedNames(ownerUuid),
                    getPlayerColor(ownerUuid)
            );
        }

        return ClaimResult.SUCCESS;
    }

    // Try to claim a chunk
    public ClaimResult claimChunk(Player player, World world, int x, int z, boolean isOutpost) {
        String worldUuid = world.getUID().toString();
        ChunkCoords coords = new ChunkCoords(worldUuid, x, z);

        // Check if already claimed
        if (claims.containsKey(coords)) {
            return ClaimResult.ALREADY_CLAIMED;
        }

        UUID playerUuid = player.getUniqueId();

        // Admin override limits
        boolean isAdmin = player.hasPermission("gultenclaim.admin");

        if (!isAdmin) {
            // Check overall claim limit
            int maxClaims = getMaxClaims(player);
            int currentClaims = getClaimCount(playerUuid);
            if (currentClaims >= maxClaims) {
                return ClaimResult.LIMIT_REACHED;
            }

            if (isOutpost) {
                // Check outpost limit
                int maxOutposts = getMaxOutposts(player);
                int currentOutposts = getOutpostCount(playerUuid);
                if (currentOutposts >= maxOutposts) {
                    return ClaimResult.OUTPOST_LIMIT_REACHED;
                }
                // Outposts MUST be separate
                if (getClaimCount(playerUuid) > 0 && isAdjacent(playerUuid, worldUuid, x, z)) {
                    return ClaimResult.OUTPOST_CANNOT_BE_ADJACENT;
                }
            } else {
                // Standard claim must be adjacent (unless first claim)
                if (!isAdjacent(playerUuid, worldUuid, x, z)) {
                    return ClaimResult.NOT_ADJACENT;
                }
            }
        }

        // Get prices
        double price = isOutpost ?
                plugin.getConfigManager().getConfig().getDouble("outpost.price", 1000.0) :
                plugin.getConfigManager().getConfig().getDouble("claim.price", 100.0);

        // Economy transactions
        if (plugin.getEconomyIntegration().isEnabled() && !isAdmin) {
            if (!plugin.getEconomyIntegration().has(player, price)) {
                return ClaimResult.NO_MONEY;
            }
            plugin.getEconomyIntegration().withdraw(player, price);
        }

        // Add to cache
        ClaimedChunk claim = new ClaimedChunk(worldUuid, x, z, playerUuid, player.getName(), isOutpost);
        claims.put(coords, claim);

        // Add to database
        plugin.getDatabaseManager().addClaim(worldUuid, x, z, playerUuid, player.getName(), isOutpost);

        // Update Dynmap
        if (plugin.getDynmapIntegration().isEnabled()) {
            plugin.getDynmapIntegration().registerClaim(
                    world.getName(),
                    x,
                    z,
                    player.getName(),
                    isOutpost,
                    getTrustedNames(playerUuid),
                    getPlayerColor(playerUuid)
            );
        }

        return ClaimResult.SUCCESS;
    }

    // Unclaim a chunk
    public UnclaimResult unclaimChunk(Player player, World world, int x, int z, boolean forceAdmin) {
        String worldUuid = world.getUID().toString();
        ChunkCoords coords = new ChunkCoords(worldUuid, x, z);

        if (!claims.containsKey(coords)) {
            return UnclaimResult.NOT_CLAIMED;
        }

        ClaimedChunk claim = claims.get(coords);
        UUID playerUuid = player.getUniqueId();
        boolean isAdmin = player.hasPermission("gultenclaim.admin") && forceAdmin;

        if (!claim.ownerUuid.equals(playerUuid) && !isAdmin) {
            return UnclaimResult.NOT_OWNER;
        }

        // Calculate refund
        double price = claim.isOutpost ?
                plugin.getConfigManager().getConfig().getDouble("outpost.price", 1000.0) :
                plugin.getConfigManager().getConfig().getDouble("claim.price", 100.0);
        double refundPercent = claim.isOutpost ?
                plugin.getConfigManager().getConfig().getDouble("outpost.refund-percentage", 50.0) :
                plugin.getConfigManager().getConfig().getDouble("claim.refund-percentage", 50.0);
        double refund = price * (refundPercent / 100.0);

        // Process refund
        if (plugin.getEconomyIntegration().isEnabled() && refund > 0 && !isAdmin) {
            plugin.getEconomyIntegration().deposit(player, refund);
        }

        // Remove from cache
        claims.remove(coords);

        // Remove from database
        plugin.getDatabaseManager().removeClaim(worldUuid, x, z);

        // Remove spawn cache
        removeClaimSpawn(worldUuid, x, z);

        // Remove from Dynmap
        if (plugin.getDynmapIntegration().isEnabled()) {
            plugin.getDynmapIntegration().unregisterClaim(world.getName(), x, z);
        }

        // Flight check for players in this chunk
        checkFlightForChunk(world, x, z);

        return UnclaimResult.SUCCESS;
    }

    // Unclaim all claims of a player
    public double unclaimAll(Player player) {
        UUID playerUuid = player.getUniqueId();
        double totalRefund = 0;

        double claimPrice = plugin.getConfigManager().getConfig().getDouble("claim.price", 100.0);
        double claimRefundPercent = plugin.getConfigManager().getConfig().getDouble("claim.refund-percentage", 50.0);
        double claimRefund = claimPrice * (claimRefundPercent / 100.0);

        double outpostPrice = plugin.getConfigManager().getConfig().getDouble("outpost.price", 1000.0);
        double outpostRefundPercent = plugin.getConfigManager().getConfig().getDouble("outpost.refund-percentage", 50.0);
        double outpostRefund = outpostPrice * (outpostRefundPercent / 100.0);

        Iterator<Map.Entry<ChunkCoords, ClaimedChunk>> it = claims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ChunkCoords, ClaimedChunk> entry = it.next();
            ClaimedChunk claim = entry.getValue();

            if (claim.ownerUuid.equals(playerUuid)) {
                totalRefund += claim.isOutpost ? outpostRefund : claimRefund;
                
                // Remove from DB & Dynmap
                plugin.getDatabaseManager().removeClaim(claim.worldUuid, claim.x, claim.z);
                if (plugin.getDynmapIntegration().isEnabled()) {
                    World world = Bukkit.getWorld(UUID.fromString(claim.worldUuid));
                    if (world != null) {
                        plugin.getDynmapIntegration().unregisterClaim(world.getName(), claim.x, claim.z);
                    }
                }
                it.remove();
            }
        }

        if (plugin.getEconomyIntegration().isEnabled() && totalRefund > 0) {
            plugin.getEconomyIntegration().deposit(player, totalRefund);
        }

        // Flight check for the player
        disableFlight(player);

        return totalRefund;
    }

    // Trust Management
    public boolean addTrust(Player owner, OfflinePlayer trusted) {
        UUID ownerUuid = owner.getUniqueId();
        UUID trustedUuid = trusted.getUniqueId();

        if (ownerUuid.equals(trustedUuid)) {
            return false;
        }

        Map<UUID, String> trustMap = trustedPlayers.computeIfAbsent(ownerUuid, k -> new HashMap<>());
        if (trustMap.containsKey(trustedUuid)) {
            return false;
        }

        String name = trusted.getName() != null ? trusted.getName() : "Bilinmeyen";
        trustMap.put(trustedUuid, name);

        // Save to database
        plugin.getDatabaseManager().addTrust(ownerUuid, trustedUuid, name);

        // Update Dynmap markers for this owner's claims
        updateOwnerDynmap(ownerUuid);

        return true;
    }

    public boolean removeTrust(Player owner, OfflinePlayer trusted) {
        UUID ownerUuid = owner.getUniqueId();
        UUID trustedUuid = trusted.getUniqueId();

        Map<UUID, String> trustMap = trustedPlayers.get(ownerUuid);
        if (trustMap == null || !trustMap.containsKey(trustedUuid)) {
            return false;
        }

        trustMap.remove(trustedUuid);
        if (trustMap.isEmpty()) {
            trustedPlayers.remove(ownerUuid);
        }

        // Delete from database
        plugin.getDatabaseManager().removeTrust(ownerUuid, trustedUuid);

        // Update Dynmap markers
        updateOwnerDynmap(ownerUuid);

        // Disable flight for the untrusted player if they are currently inside this owner's claims
        Player onlineTrusted = trusted.getPlayer();
        if (onlineTrusted != null && flyingPlayers.contains(trustedUuid)) {
            ClaimedChunk currentClaim = getClaimAt(onlineTrusted.getLocation().getChunk());
            if (currentClaim != null && currentClaim.ownerUuid.equals(ownerUuid)) {
                disableFlight(onlineTrusted);
                onlineTrusted.sendMessage(plugin.getConfigManager().getMessage("fly-disabled-left-claim"));
            }
        }

        return true;
    }

    private void updateOwnerDynmap(UUID ownerUuid) {
        if (!plugin.getDynmapIntegration().isEnabled()) return;
        List<String> trustedNames = getTrustedNames(ownerUuid);
        String ownerColor = getPlayerColor(ownerUuid);
        for (ClaimedChunk claim : claims.values()) {
            if (claim.ownerUuid.equals(ownerUuid)) {
                World world = Bukkit.getWorld(UUID.fromString(claim.worldUuid));
                if (world != null) {
                    plugin.getDynmapIntegration().registerClaim(
                            world.getName(),
                            claim.x,
                            claim.z,
                            claim.ownerName,
                            claim.isOutpost,
                            trustedNames,
                            ownerColor
                    );
                }
            }
        }
    }

    // Helper to check permissions
    public boolean canBuild(Player player, Chunk chunk) {
        return canBuild(player, chunk, com.gulten.gultenclaim.event.ClaimPermissionCheckEvent.ActionType.GENERAL);
    }

    public boolean canBuild(Player player, Chunk chunk, com.gulten.gultenclaim.event.ClaimPermissionCheckEvent.ActionType actionType) {
        ClaimedChunk claim = getClaimAt(chunk);
        // Wilderness — claim yok, herkes düzenleyebilir
        if (claim == null) return true;

        // Public claim — herkes düzenleyebilir
        if (claim.settingPublic) return true;

        // Fire event for external plugins (like GultenClan)
        com.gulten.gultenclaim.event.ClaimPermissionCheckEvent event = new com.gulten.gultenclaim.event.ClaimPermissionCheckEvent(player, claim, chunk, actionType);
        Bukkit.getPluginManager().callEvent(event);

        if (event.getResult() == com.gulten.gultenclaim.event.ClaimPermissionCheckEvent.Result.ALLOW) return true;
        if (event.getResult() == com.gulten.gultenclaim.event.ClaimPermissionCheckEvent.Result.DENY) return false;

        UUID playerUuid = player.getUniqueId();
        if (claim.ownerUuid.equals(playerUuid)) return true;
        if (hasBypass(player)) return true;

        return isTrusted(claim.ownerUuid, playerUuid);
    }

    public boolean isTrusted(UUID ownerUuid, UUID playerUuid) {
        if (ownerUuid.equals(playerUuid)) return true;
        Map<UUID, String> trustMap = trustedPlayers.get(ownerUuid);
        return trustMap != null && trustMap.containsKey(playerUuid);
    }

    public List<String> getTrustedNames(UUID ownerUuid) {
        Map<UUID, String> trustMap = trustedPlayers.get(ownerUuid);
        if (trustMap == null || trustMap.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(trustMap.values());
    }

    public Map<UUID, String> getTrustedMap(UUID ownerUuid) {
        return trustedPlayers.getOrDefault(ownerUuid, Collections.emptyMap());
    }

    public ClaimedChunk getClaimAt(Chunk chunk) {
        String worldUuid = chunk.getWorld().getUID().toString();
        return claims.get(new ChunkCoords(worldUuid, chunk.getX(), chunk.getZ()));
    }

    public ClaimedChunk getClaimAt(String worldUuid, int x, int z) {
        return claims.get(new ChunkCoords(worldUuid, x, z));
    }

    public int getClaimCount(UUID ownerUuid) {
        int count = 0;
        for (ClaimedChunk claim : claims.values()) {
            if (claim.ownerUuid.equals(ownerUuid)) {
                count++;
            }
        }
        return count;
    }

    public int getOutpostCount(UUID ownerUuid) {
        int count = 0;
        for (ClaimedChunk claim : claims.values()) {
            if (claim.ownerUuid.equals(ownerUuid) && claim.isOutpost) {
                count++;
            }
        }
        return count;
    }

    // Limit calculations scanning player effective permissions
    public int getMaxClaims(Player player) {
        if (player.hasPermission("gultenclaim.admin")) {
            return Integer.MAX_VALUE;
        }

        int max = -1;
        for (PermissionAttachmentInfo attachmentInfo : player.getEffectivePermissions()) {
            String perm = attachmentInfo.getPermission().toLowerCase();
            if (perm.startsWith("gultenclaim.max.")) {
                try {
                    int amount = Integer.parseInt(perm.substring("gultenclaim.max.".length()));
                    if (amount > max) {
                        max = amount;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        int base = max != -1 ? max : plugin.getConfigManager().getConfig().getInt("claim.default-max", 10);

        // Bonus for trusted members: each member adds N extra claims
        int bonusPerMember = plugin.getConfigManager().getConfig().getInt("claim.bonus-per-member", 0);
        if (bonusPerMember > 0) {
            int memberCount = getTrustedMap(player.getUniqueId()).size();
            // Cap bonus using config: claim.member-bonus-max (0 = unlimited)
            int maxBonus = plugin.getConfigManager().getConfig().getInt("claim.member-bonus-max", 0);
            int bonus = memberCount * bonusPerMember;
            if (maxBonus > 0) bonus = Math.min(bonus, maxBonus);
            base += bonus;
        }

        return base;
    }

    public int getMaxOutposts(Player player) {
        if (player.hasPermission("gultenclaim.admin")) {
            return Integer.MAX_VALUE;
        }

        int max = -1;
        for (PermissionAttachmentInfo attachmentInfo : player.getEffectivePermissions()) {
            String perm = attachmentInfo.getPermission().toLowerCase();
            if (perm.startsWith("gultenclaim.maxoutpost.")) {
                try {
                    int amount = Integer.parseInt(perm.substring("gultenclaim.maxoutpost.".length()));
                    if (amount > max) {
                        max = amount;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        
        return max != -1 ? max : plugin.getConfigManager().getConfig().getInt("outpost.default-max", 2);
    }

    // Toggles and getters
    public boolean toggleAutoClaim(Player player) {
        UUID uuid = player.getUniqueId();
        if (autoClaimers.contains(uuid)) {
            autoClaimers.remove(uuid);
            return false;
        } else {
            autoClaimers.add(uuid);
            return true;
        }
    }

    public boolean isAutoClaiming(Player player) {
        return autoClaimers.contains(player.getUniqueId());
    }

    public void removeAutoClaimer(Player player) {
        autoClaimers.remove(player.getUniqueId());
    }

    public boolean toggleBypass(Player player) {
        UUID uuid = player.getUniqueId();
        if (bypassPlayers.contains(uuid)) {
            bypassPlayers.remove(uuid);
            return false;
        } else {
            bypassPlayers.add(uuid);
            return true;
        }
    }

    public boolean hasBypass(Player player) {
        // OP oyuncular otomatik bypass alır
        if (player.isOp()) return true;
        // gultenclaim.admin iznine sahip olup bypass modunu açmış olanlar
        return player.hasPermission("gultenclaim.admin") && bypassPlayers.contains(player.getUniqueId());
    }

    public void removeBypasser(Player player) {
        bypassPlayers.remove(player.getUniqueId());
    }

    // Flight management
    public boolean toggleFlight(Player player) {
        UUID uuid = player.getUniqueId();
        if (flyingPlayers.contains(uuid)) {
            disableFlight(player);
            return false;
        } else {
            enableFlight(player);
            return true;
        }
    }

    public void enableFlight(Player player) {
        flyingPlayers.add(player.getUniqueId());
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    public void disableFlight(Player player) {
        flyingPlayers.remove(player.getUniqueId());
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public boolean isFlying(Player player) {
        return flyingPlayers.contains(player.getUniqueId());
    }

    private void checkFlightForChunk(World world, int x, int z) {
        for (Player onlinePlayer : world.getPlayers()) {
            Chunk chunk = onlinePlayer.getLocation().getChunk();
            if (chunk.getX() == x && chunk.getZ() == z) {
                if (isFlying(onlinePlayer)) {
                    // Check if they still have rights
                    ClaimedChunk claim = getClaimAt(chunk);
                    if (claim == null || (!claim.ownerUuid.equals(onlinePlayer.getUniqueId()) && !hasBypass(onlinePlayer))) {
                        disableFlight(onlinePlayer);
                        onlinePlayer.sendMessage(plugin.getConfigManager().getMessage("fly-disabled-left-claim"));
                    }
                }
            }
        }
    }

    public Map<ChunkCoords, ClaimedChunk> getClaims() {
        return claims;
    }

    // Returns all claims owned by a specific player (sorted by world+coords)
    public List<ClaimedChunk> getClaimsByOwner(UUID ownerUuid) {
        List<ClaimedChunk> result = new ArrayList<>();
        for (ClaimedChunk claim : claims.values()) {
            if (claim.ownerUuid.equals(ownerUuid)) {
                result.add(claim);
            }
        }
        return result;
    }

    // Set the spawn point for a claim and persist to DB
    public void setClaimSpawn(ClaimedChunk claim, Location location) {
        ChunkCoords coords = new ChunkCoords(claim.worldUuid, claim.x, claim.z);
        claimSpawns.put(coords, location.clone());
        plugin.getDatabaseManager().setClaimSpawn(
                claim.worldUuid, claim.x, claim.z,
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()
        );
    }

    // Get the spawn point for a claim (null if not set)
    public Location getClaimSpawn(String worldUuid, int x, int z) {
        return claimSpawns.get(new ChunkCoords(worldUuid, x, z));
    }

    // Remove spawn entry when claim is deleted
    public void removeClaimSpawn(String worldUuid, int x, int z) {
        claimSpawns.remove(new ChunkCoords(worldUuid, x, z));
    }

    /**
     * /claim toggle komutu: Belirtilen ayarı toggle eder.
     * @param player Komutu kullanan oyuncu
     * @param settingKey pvp | fire | mob-spawning | explosions | public
     * @return 0=ayarsız claim yok, 1=yetkisiz, 2=ayar açıldı, 3=ayar kapatıldı, -1=geçersiz anahtar
     */
    public int toggleClaimSetting(Player player, String settingKey) {
        String[] valid = {"pvp", "fire", "mob-spawning", "explosions", "public"};
        boolean keyOk = false;
        for (String s : valid) if (s.equals(settingKey.toLowerCase())) { keyOk = true; break; }
        if (!keyOk) return -1;

        ClaimedChunk claim = getClaimAt(player.getLocation().getChunk());
        if (claim == null) return 0;
        if (!claim.ownerUuid.equals(player.getUniqueId()) && !hasBypass(player)) return 1;

        boolean newVal = claim.toggle(settingKey.toLowerCase());
        // Persist
        plugin.getDatabaseManager().saveClaimSettings(
                claim.worldUuid, claim.x, claim.z, claim.settingsToJson());
        return newVal ? 2 : 3;
    }

    // Inner classes
    public static class ChunkCoords {
        public final String worldUuid;
        public final int x;
        public final int z;

        public ChunkCoords(String worldUuid, int x, int z) {
            this.worldUuid = worldUuid;
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkCoords that = (ChunkCoords) o;
            return x == that.x && z == that.z && Objects.equals(worldUuid, that.worldUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldUuid, x, z);
        }
    }

    public static class ClaimedChunk {
        public final String worldUuid;
        public final int x;
        public final int z;
        public final UUID ownerUuid;
        public final String ownerName;
        public final boolean isOutpost;

        // Per-claim toggle settings
        public boolean settingPvp = false;          // PvP claim içinde
        public boolean settingFire = false;         // Ateş yayılımı
        public boolean settingMobSpawning = true;   // Mob spawning
        public boolean settingExplosions = false;   // Patlamalar claim'i etkilesin mi
        public boolean settingPublic = false;       // Herkes düzenleyebilsin mi

        public ClaimedChunk(String worldUuid, int x, int z, UUID ownerUuid, String ownerName, boolean isOutpost) {
            this.worldUuid = worldUuid;
            this.x = x;
            this.z = z;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.isOutpost = isOutpost;
        }

        /** Verilen ayarın değerini döndürür. */
        public boolean getSetting(String key) {
            switch (key.toLowerCase()) {
                case "pvp": return settingPvp;
                case "fire": return settingFire;
                case "mob-spawning": return settingMobSpawning;
                case "explosions": return settingExplosions;
                case "public": return settingPublic;
                default: return false;
            }
        }

        /** Verilen ayarı toggle eder ve yeni değeri döndürür. */
        public boolean toggle(String key) {
            switch (key.toLowerCase()) {
                case "pvp": settingPvp = !settingPvp; return settingPvp;
                case "fire": settingFire = !settingFire; return settingFire;
                case "mob-spawning": settingMobSpawning = !settingMobSpawning; return settingMobSpawning;
                case "explosions": settingExplosions = !settingExplosions; return settingExplosions;
                case "public": settingPublic = !settingPublic; return settingPublic;
                default: return false;
            }
        }

        /** Ayarları DB'ye kaydedilmek üzere JSON string'e çevirir. */
        public String settingsToJson() {
            return "{\"pvp\":" + settingPvp +
                   ",\"fire\":" + settingFire +
                   ",\"mob-spawning\":" + settingMobSpawning +
                   ",\"explosions\":" + settingExplosions +
                   ",\"public\":" + settingPublic + "}";
        }

        /** JSON string'den ayarları yükler (basit parse). */
        public void settingsFromJson(String json) {
            if (json == null || json.isEmpty()) return;
            settingPvp = json.contains("\"pvp\":true");
            settingFire = json.contains("\"fire\":true");
            settingMobSpawning = !json.contains("\"mob-spawning\":false");
            settingExplosions = json.contains("\"explosions\":true");
            settingPublic = json.contains("\"public\":true");
        }
    }

    public enum ClaimResult {
        SUCCESS,
        ALREADY_CLAIMED,
        NO_MONEY,
        LIMIT_REACHED,
        OUTPOST_LIMIT_REACHED,
        NOT_ADJACENT,
        OUTPOST_CANNOT_BE_ADJACENT
    }

    public enum UnclaimResult {
        SUCCESS,
        NOT_CLAIMED,
        NOT_OWNER
    }
}
