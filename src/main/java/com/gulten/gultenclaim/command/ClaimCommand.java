package com.gulten.gultenclaim.command;

import com.gulten.gultenclaim.GultenClaim;
import com.gulten.gultenclaim.manager.ClaimManager;
import com.gulten.gultenclaim.util.ClaimVisualizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ClaimCommand implements CommandExecutor, TabCompleter {

    private final GultenClaim plugin;
    private final ClaimManager claimManager;
    private final ClaimVisualizer visualizer;

    public ClaimCommand(GultenClaim plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
        this.visualizer = new ClaimVisualizer(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            handleClaimStandard(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "outpost":
                handleClaimOutpost(player);
                break;
            case "clan":
                handleClaimClan(player);
                break;
            case "clantrust":
                handleClanTrust(player);
                break;
            case "unclaim":
                handleUnclaim(player, false);
                break;
            case "unclaimall":
                handleUnclaimAll(player);
                break;
            case "trust":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().colorize("&cUsage: /claim trust <player>"));
                    return true;
                }
                handleTrust(player, args[1]);
                break;
            case "untrust":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().colorize("&cUsage: /claim untrust <player>"));
                    return true;
                }
                handleUntrust(player, args[1]);
                break;
            case "list":
                handleList(player);
                break;
            case "info":
                handleInfo(player);
                break;
            case "show":
                visualizer.showBorders(player);
                break;
            case "auto":
                handleAuto(player);
                break;
            case "map":
                visualizer.printMap(player);
                break;
            case "fly":
                handleFly(player);
                break;
            case "setspawn":
                handleSetSpawn(player);
                break;
            case "color":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().colorize("&cKullanım: /claim color <#RRGGBB | reset>"));
                    return true;
                }
                handleColor(player, args[1]);
                break;
            case "tp":
                // Internal command used by clickable list: /claim tp <chunkX> <chunkZ> <worldUUID>
                if (args.length < 4) {
                    player.sendMessage(plugin.getConfigManager().colorize("&cUsage: /claim tp <chunkX> <chunkZ> <worldUUID>"));
                    return true;
                }
                handleTp(player, args[1], args[2], args[3]);
                break;
            case "bypass":
                handleBypass(player);
                break;
            case "adminunclaim":
                handleUnclaim(player, true);
                break;
            case "toggle":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().colorize("&cKullanım: /claim toggle <pvp|fire|mob-spawning|explosions|public>"));
                    return true;
                }
                handleToggle(player, args[1]);
                break;
            case "reload":
                handleReload(player);
                break;
            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void handleClaimStandard(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.ClaimResult result = claimManager.claimChunk(player, chunk.getWorld(), chunk.getX(), chunk.getZ(), false);

        switch (result) {
            case SUCCESS:
                double price = plugin.getConfigManager().getConfig().getDouble("claim.price", 100.0);
                String formattedPrice = plugin.getEconomyIntegration().format(price);
                player.sendMessage(plugin.getConfigManager().getMessage("claim-success").replace("%price%", formattedPrice));
                break;
            case ALREADY_CLAIMED:
                ClaimManager.ClaimedChunk claimAC = claimManager.getClaimAt(chunk);
                player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-already-claimed").replace("%owner%", claimAC != null ? claimAC.ownerName : "?"));
                break;
            case LIMIT_REACHED:
                player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-limit-reached").replace("%limit%", String.valueOf(claimManager.getMaxClaims(player))));
                break;
            case NO_MONEY:
                double priceNeeded = plugin.getConfigManager().getConfig().getDouble("claim.price", 100.0);
                player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-no-money").replace("%price%", plugin.getEconomyIntegration().format(priceNeeded)));
                break;
            case NOT_ADJACENT:
                player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-not-adjacent"));
                break;
            default:
                break;
        }
    }

    private void handleClaimOutpost(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.ClaimResult result = claimManager.claimChunk(player, chunk.getWorld(), chunk.getX(), chunk.getZ(), true);

        switch (result) {
            case SUCCESS:
                double price = plugin.getConfigManager().getConfig().getDouble("outpost.price", 1000.0);
                player.sendMessage(plugin.getConfigManager().getMessage("outpost-success").replace("%price%", plugin.getEconomyIntegration().format(price)));
                break;
            case ALREADY_CLAIMED:
                ClaimManager.ClaimedChunk claimAC = claimManager.getClaimAt(chunk);
                player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-already-claimed").replace("%owner%", claimAC != null ? claimAC.ownerName : "?"));
                break;
            case LIMIT_REACHED:
                player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-limit-reached").replace("%limit%", String.valueOf(claimManager.getMaxClaims(player))));
                break;
            case OUTPOST_LIMIT_REACHED:
                player.sendMessage(plugin.getConfigManager().getMessage("outpost-failed-limit-reached").replace("%limit%", String.valueOf(claimManager.getMaxOutposts(player))));
                break;
            case NO_MONEY:
                double priceNeeded = plugin.getConfigManager().getConfig().getDouble("outpost.price", 1000.0);
                player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-no-money").replace("%price%", plugin.getEconomyIntegration().format(priceNeeded)));
                break;
            case OUTPOST_CANNOT_BE_ADJACENT:
                player.sendMessage(plugin.getConfigManager().getMessage("outpost-failed-adjacent"));
                break;
            default:
                break;
        }
    }

    private void handleClaimClan(Player player) {
        com.gulten.gultenclaim.event.ClaimClanRequestEvent event = new com.gulten.gultenclaim.event.ClaimClanRequestEvent(player, player.getLocation().getChunk());
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isHandled()) {
            player.sendMessage(plugin.getConfigManager().colorize("&cKlan eklentisi bulunamadı veya bu özellik devre dışı."));
        }
    }

    private void handleClanTrust(Player player) {
        com.gulten.gultenclaim.event.ClaimClanTrustToggleEvent event = new com.gulten.gultenclaim.event.ClaimClanTrustToggleEvent(player);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isHandled()) {
            player.sendMessage(plugin.getConfigManager().colorize("&cKlan eklentisi bulunamadı veya bu özellik devre dışı."));
        }
    }

    private void handleUnclaim(Player player, boolean forceAdmin) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(chunk);

        if (claim == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("unclaim-failed-not-claimed"));
            return;
        }

        boolean isOutpost = claim.isOutpost;
        double price = isOutpost ?
                plugin.getConfigManager().getConfig().getDouble("outpost.price", 1000.0) :
                plugin.getConfigManager().getConfig().getDouble("claim.price", 100.0);
        double refundPercent = isOutpost ?
                plugin.getConfigManager().getConfig().getDouble("outpost.refund-percentage", 50.0) :
                plugin.getConfigManager().getConfig().getDouble("claim.refund-percentage", 50.0);
        double refund = price * (refundPercent / 100.0);

        ClaimManager.UnclaimResult result = claimManager.unclaimChunk(player, chunk.getWorld(), chunk.getX(), chunk.getZ(), forceAdmin);
        switch (result) {
            case SUCCESS:
                if (forceAdmin) {
                    player.sendMessage(plugin.getConfigManager().getMessage("admin-unclaim-success"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("unclaim-success").replace("%refund%", plugin.getEconomyIntegration().format(refund)));
                }
                break;
            case NOT_OWNER:
                player.sendMessage(plugin.getConfigManager().getMessage("unclaim-failed-not-owner"));
                break;
            default:
                break;
        }
    }

    private void handleUnclaimAll(Player player) {
        if (claimManager.getClaimCount(player.getUniqueId()) == 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-no-claims"));
            return;
        }
        double totalRefund = claimManager.unclaimAll(player);
        player.sendMessage(plugin.getConfigManager().getMessage("unclaim-all-success").replace("%refund%", plugin.getEconomyIntegration().format(totalRefund)));
    }

    private void handleTrust(Player player, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("trust-failed-self"));
            return;
        }
        boolean success = claimManager.addTrust(player, target);
        if (success) {
            player.sendMessage(plugin.getConfigManager().getMessage("trust-success").replace("%player%", target.getName() != null ? target.getName() : targetName));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("trust-failed-already-trusted"));
        }
    }

    private void handleUntrust(Player player, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            return;
        }
        boolean success = claimManager.removeTrust(player, target);
        if (success) {
            player.sendMessage(plugin.getConfigManager().getMessage("untrust-success").replace("%player%", target.getName() != null ? target.getName() : targetName));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("trust-failed-not-trusted"));
        }
    }

    /**
     * /claim list - Shows clickable list. Clicking a claim teleports to its spawn (or chunk center).
     */
    private void handleList(Player player) {
        List<ClaimManager.ClaimedChunk> ownedClaims = claimManager.getClaimsByOwner(player.getUniqueId());

        if (ownedClaims.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("claim-failed-no-claims"));
            return;
        }

        // Header — show current / effective max (includes trust bonus)
        int maxClaims = claimManager.getMaxClaims(player);
        int trustedCount = claimManager.getTrustedMap(player.getUniqueId()).size();
        int bonusPerMember = plugin.getConfigManager().getConfig().getInt("claim.bonus-per-member", 0);
        String bonusStr = bonusPerMember > 0 ? " &7(+" + (trustedCount * bonusPerMember) + " üye bonusu)" : "";
        player.sendMessage(plugin.getConfigManager().colorize("&e======== &6Claimleriniz (" + ownedClaims.size() + "/" + maxClaims + bonusStr + ") &e========"));

        for (ClaimManager.ClaimedChunk claim : ownedClaims) {
            World world = Bukkit.getWorld(UUID.fromString(claim.worldUuid));
            String worldName = world != null ? world.getName() : "?";
            String typeLabel = claim.isOutpost
                    ? plugin.getConfigManager().getRawMessage("claim-info-type-outpost")
                    : plugin.getConfigManager().getRawMessage("claim-info-type-standard");

            // Check if spawn is set
            Location spawn = claimManager.getClaimSpawn(claim.worldUuid, claim.x, claim.z);
            String spawnHint = spawn != null
                    ? String.format("Spawn: %.1f, %.1f, %.1f", spawn.getX(), spawn.getY(), spawn.getZ())
                    : "Spawn ayarlanmamış — chunk merkezine ışınlanır";

            // Build clickable component: clicking runs /claim tp <chunkX> <chunkZ> <worldUUID>
            String tpCommand = "/claim tp " + claim.x + " " + claim.z + " " + claim.worldUuid;
            int blockX = claim.x * 16 + 8;
            int blockZ = claim.z * 16 + 8;

            Component line = Component.text(" ▶ ", NamedTextColor.GOLD)
                    .append(Component.text(worldName, NamedTextColor.YELLOW))
                    .append(Component.text(" [" + blockX + ", " + blockZ + "]", NamedTextColor.GREEN))
                    .append(Component.text(" (" + typeLabel + ")", NamedTextColor.GRAY))
                    .append(Component.text(" [TP]", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand(tpCommand))
                            .hoverEvent(HoverEvent.showText(Component.text("Tıkla: " + spawnHint, NamedTextColor.YELLOW))));

            player.sendMessage(line);
        }

        player.sendMessage(plugin.getConfigManager().colorize("&e====================================="));
    }

    private void handleInfo(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(chunk);

        if (claim == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("unclaim-failed-not-claimed"));
            return;
        }

        String typeLabel = claim.isOutpost
                ? plugin.getConfigManager().getRawMessage("claim-info-type-outpost")
                : plugin.getConfigManager().getRawMessage("claim-info-type-standard");

        List<String> trustedNames = claimManager.getTrustedNames(claim.ownerUuid);
        String trustedString = trustedNames.isEmpty()
                ? plugin.getConfigManager().getRawMessage("claim-info-none")
                : String.join(", ", trustedNames);

        Location spawn = claimManager.getClaimSpawn(claim.worldUuid, claim.x, claim.z);
        String spawnStr = spawn != null
                ? String.format("%.1f, %.1f, %.1f", spawn.getX(), spawn.getY(), spawn.getZ())
                : plugin.getConfigManager().getRawMessage("claim-info-none");

        player.sendMessage(plugin.getConfigManager().getMessage("claim-info-title"));
        player.sendMessage(plugin.getConfigManager().getMessage("claim-info-owner").replace("%owner%", claim.ownerName));
        player.sendMessage(plugin.getConfigManager().getMessage("claim-info-type").replace("%type%", typeLabel));
        player.sendMessage(plugin.getConfigManager().getMessage("claim-info-trusted").replace("%trusted%", trustedString));
        player.sendMessage(plugin.getConfigManager().getMessage("claim-info-spawn").replace("%spawn%", spawnStr));
        player.sendMessage(plugin.getConfigManager().colorize("&e========================="));
    }

    /**
     * /claim setspawn — sets the spawn point of the current claim to the player's location.
     * Only the claim owner can set its spawn.
     */
    private void handleSetSpawn(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(chunk);

        if (claim == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("unclaim-failed-not-claimed"));
            return;
        }

        if (!claim.ownerUuid.equals(player.getUniqueId()) && !player.hasPermission("gultenclaim.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("unclaim-failed-not-owner"));
            return;
        }

        claimManager.setClaimSpawn(claim, player.getLocation());
        player.sendMessage(plugin.getConfigManager().getMessage("setspawn-success"));
    }

    /**
     * /claim tp <chunkX> <chunkZ> <worldUUID> — internal command triggered by clicking the /claim list.
     * Teleports player to the claim's spawn or the chunk center if no spawn is set.
     * Allowed for owner and trusted players.
     */
    private void handleTp(Player player, String chunkXStr, String chunkZStr, String worldUuidStr) {
        int chunkX, chunkZ;
        try {
            chunkX = Integer.parseInt(chunkXStr);
            chunkZ = Integer.parseInt(chunkZStr);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().colorize("&cGeçersiz chunk koordinatları."));
            return;
        }

        World world = Bukkit.getWorld(UUID.fromString(worldUuidStr));
        if (world == null) {
            player.sendMessage(plugin.getConfigManager().colorize("&cDünya bulunamadı."));
            return;
        }

        ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(worldUuidStr, chunkX, chunkZ);
        if (claim == null) {
            player.sendMessage(plugin.getConfigManager().colorize("&cBu claim artık mevcut değil."));
            return;
        }

        // Check permission: owner or trusted
        if (!claim.ownerUuid.equals(player.getUniqueId())
                && !claimManager.isTrusted(claim.ownerUuid, player.getUniqueId())
                && !player.hasPermission("gultenclaim.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        // Teleport to spawn or chunk center
        Location spawn = claimManager.getClaimSpawn(worldUuidStr, chunkX, chunkZ);
        Location destination;
        if (spawn != null) {
            destination = spawn.clone();
        } else {
            // Chunk center, safe Y
            int blockX = chunkX * 16 + 8;
            int blockZ = chunkZ * 16 + 8;
            int blockY = world.getHighestBlockYAt(blockX, blockZ) + 1;
            destination = new Location(world, blockX + 0.5, blockY, blockZ + 0.5);
        }

        player.teleport(destination);
        player.sendMessage(plugin.getConfigManager().getMessage("tp-success")
                .replace("%world%", world.getName())
                .replace("%x%", String.valueOf((int) destination.getX()))
                .replace("%z%", String.valueOf((int) destination.getZ())));
    }

    private void handleAuto(Player player) {
        boolean auto = claimManager.toggleAutoClaim(player);
        player.sendMessage(plugin.getConfigManager().getMessage(auto ? "auto-claim-enabled" : "auto-claim-disabled"));
    }

    private void handleFly(Player player) {
        if (!player.hasPermission("gultenclaim.fly")) {
            player.sendMessage(plugin.getConfigManager().getMessage("fly-failed-no-perm"));
            return;
        }
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.ClaimedChunk claim = claimManager.getClaimAt(chunk);
        // Fly only works on the player's OWN claim (not trusted claims)
        if (claim == null || (!claim.ownerUuid.equals(player.getUniqueId()) && !claimManager.hasBypass(player))) {
            player.sendMessage(plugin.getConfigManager().getMessage("fly-failed-no-claim"));
            return;
        }
        boolean flying = claimManager.toggleFlight(player);
        player.sendMessage(plugin.getConfigManager().getMessage(flying ? "fly-enabled" : "fly-disabled"));
    }

    /**
     * /claim color <#RRGGBB | reset> — premium players can set a custom dynmap color for their claims.
     * Requires gultenclaim.color permission.
     */
    private void handleColor(Player player, String arg) {
        if (!player.hasPermission("gultenclaim.color")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (arg.equalsIgnoreCase("reset")) {
            claimManager.setPlayerColor(player.getUniqueId(), null);
            player.sendMessage(plugin.getConfigManager().getMessage("color-reset"));
            return;
        }

        // Validate hex colour: must be #RRGGBB
        if (!arg.matches("#[0-9A-Fa-f]{6}")) {
            player.sendMessage(plugin.getConfigManager().getMessage("color-invalid"));
            return;
        }

        claimManager.setPlayerColor(player.getUniqueId(), arg.toUpperCase());
        player.sendMessage(plugin.getConfigManager().getMessage("color-success").replace("%color%", arg.toUpperCase()));
    }

    private void handleToggle(Player player, String key) {
        int result = claimManager.toggleClaimSetting(player, key.toLowerCase());
        switch (result) {
            case -1:
                player.sendMessage(plugin.getConfigManager().getMessage("toggle-unknown"));
                break;
            case 0:
                player.sendMessage(plugin.getConfigManager().getMessage("unclaim-failed-not-claimed"));
                break;
            case 1:
                player.sendMessage(plugin.getConfigManager().getMessage("unclaim-failed-not-owner"));
                break;
            case 2:
                player.sendMessage(plugin.getConfigManager().getMessage("toggle-enabled").replace("%setting%", key));
                break;
            case 3:
                player.sendMessage(plugin.getConfigManager().getMessage("toggle-disabled").replace("%setting%", key));
                break;
        }
    }

    private void handleBypass(Player player) {
        if (!player.hasPermission("gultenclaim.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        boolean bypass = claimManager.toggleBypass(player);
        player.sendMessage(plugin.getConfigManager().getMessage(bypass ? "admin-bypass-enabled" : "admin-bypass-disabled"));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("gultenclaim.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        plugin.getConfigManager().reload();
        claimManager.registerAllOnDynmap();
        player.sendMessage(plugin.getConfigManager().getMessage("config-reloaded"));
    }

    private void showHelp(Player player) {
        for (String line : plugin.getConfigManager().getMessageList("command-help")) {
            player.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList(
                    "outpost", "unclaim", "unclaimall",
                    "trust", "untrust", "list", "info",
                    "show", "auto", "map", "fly", "setspawn",
                    "toggle", "clan", "clantrust"
            ));
            if (player.hasPermission("gultenclaim.color")) {
                list.add("color");
            }
            if (player.hasPermission("gultenclaim.admin")) {
                list.add("bypass");
                list.add("adminunclaim");
                list.add("reload");
            }
            return list.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("trust") || sub.equals("untrust")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("toggle")) {
                return Arrays.asList("pvp", "fire", "mob-spawning", "explosions", "public")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
