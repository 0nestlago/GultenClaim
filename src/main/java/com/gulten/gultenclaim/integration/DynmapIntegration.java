package com.gulten.gultenclaim.integration;

import com.gulten.gultenclaim.GultenClaim;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.List;
import java.util.logging.Level;

public class DynmapIntegration {

    private final GultenClaim plugin;
    private DynmapAPI dynmapApi = null;
    private MarkerAPI markerApi = null;
    private MarkerSet markerSet = null;

    private boolean enabled = false;
    private String layerName = "GultenClaim";
    
    // Style configurations
    private int standardFillColor = 0x00FF00;
    private double standardFillOpacity = 0.35;
    private int standardLineColor = 0x00AA00;
    private double standardLineOpacity = 0.8;
    private int standardLineWeight = 2;

    private int outpostFillColor = 0xFF0000;
    private double outpostFillOpacity = 0.35;
    private int outpostLineColor = 0xAA0000;
    private double outpostLineOpacity = 0.8;
    private int outpostLineWeight = 2;

    public DynmapIntegration(GultenClaim plugin) {
        this.plugin = plugin;
        setupDynmap();
    }

    public void setupDynmap() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        if (!config.getBoolean("dynmap.enabled", true)) {
            plugin.getLogger().info("Dynmap integration is disabled in config.yml.");
            return;
        }

        Plugin dynPlugin = plugin.getServer().getPluginManager().getPlugin("dynmap");
        if (dynPlugin == null) {
            plugin.getLogger().info("Dynmap not found. Dynmap integration is disabled.");
            return;
        }

        try {
            this.dynmapApi = (DynmapAPI) dynPlugin;
            this.markerApi = dynmapApi.getMarkerAPI();
            if (this.markerApi == null) {
                plugin.getLogger().warning("Error loading Dynmap Marker API.");
                return;
            }
            this.enabled = true;
            loadConfigStyles(config);
            createMarkerSet();
            plugin.getLogger().info("Dynmap integration successfully initialized.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to hook into Dynmap API!", e);
        }
    }

    private void loadConfigStyles(FileConfiguration config) {
        this.layerName = config.getString("dynmap.layer-name", "GultenClaim");
        
        this.standardFillColor = parseColor(config.getString("dynmap.styles.standard.fill-color", "#00FF00"));
        this.standardFillOpacity = config.getDouble("dynmap.styles.standard.fill-opacity", 0.35);
        this.standardLineColor = parseColor(config.getString("dynmap.styles.standard.line-color", "#00AA00"));
        this.standardLineOpacity = config.getDouble("dynmap.styles.standard.line-opacity", 0.8);
        this.standardLineWeight = config.getInt("dynmap.styles.standard.line-weight", 2);

        this.outpostFillColor = parseColor(config.getString("dynmap.styles.outpost.fill-color", "#FF0000"));
        this.outpostFillOpacity = config.getDouble("dynmap.styles.outpost.fill-opacity", 0.35);
        this.outpostLineColor = parseColor(config.getString("dynmap.styles.outpost.line-color", "#AA0000"));
        this.outpostLineOpacity = config.getDouble("dynmap.styles.outpost.line-opacity", 0.8);
        this.outpostLineWeight = config.getInt("dynmap.styles.outpost.line-weight", 2);
    }

    private int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid hex color in config: " + hex + ". Defaulting to black.");
            return 0x000000;
        }
    }

    /** Darkens an RGB int color by ~30% for use as border line color. */
    private int darkenColor(int rgb) {
        int r = (int) (((rgb >> 16) & 0xFF) * 0.65);
        int g = (int) (((rgb >> 8) & 0xFF) * 0.65);
        int b = (int) ((rgb & 0xFF) * 0.65);
        return (r << 16) | (g << 8) | b;
    }

    private void createMarkerSet() {
        if (!enabled) return;
        markerSet = markerApi.getMarkerSet("gultenclaim.markerset");
        if (markerSet == null) {
            markerSet = markerApi.createMarkerSet("gultenclaim.markerset", layerName, null, false);
        } else {
            markerSet.setMarkerSetLabel(layerName);
        }
    }

    public boolean isEnabled() {
        return enabled && markerSet != null;
    }

    public void registerClaim(String worldName, int x, int z, String ownerName, boolean isOutpost, List<String> trustedNames) {
        registerClaim(worldName, x, z, ownerName, isOutpost, trustedNames, null);
    }

    /**
     * Registers a claim on dynmap.
     *
     * @param ownerColor Optional custom hex color set by a premium player (e.g. "#FF00FF"), or null to use defaults.
     */
    public void registerClaim(String worldName, int x, int z, String ownerName, boolean isOutpost, List<String> trustedNames, String ownerColor) {
        if (!isEnabled()) return;

        // Verify if world exists on the server
        if (Bukkit.getWorld(worldName) == null) {
            // World not loaded, cannot map yet
            return;
        }

        String markerId = "gultenclaim_" + worldName + "_" + x + "_" + z;
        String label = ownerName + "'s Claim " + (isOutpost ? "(Karakol)" : "");

        double[] xCoords = new double[] { x * 16.0, x * 16.0 + 16.0 };
        double[] zCoords = new double[] { z * 16.0, z * 16.0 + 16.0 };

        // Remove old marker if exists to prevent duplication
        AreaMarker marker = markerSet.findAreaMarker(markerId);
        if (marker != null) {
            marker.deleteMarker();
        }

        marker = markerSet.createAreaMarker(markerId, label, false, worldName, xCoords, zCoords, false);
        if (marker == null) {
            plugin.getLogger().warning("Failed to create Dynmap AreaMarker for chunk: " + x + ", " + z);
            return;
        }

        // Apply Styles — prefer custom owner color if provided
        if (ownerColor != null && !ownerColor.isEmpty()) {
            int customFill = parseColor(ownerColor);
            // Darken the custom color slightly for the border
            int customLine = darkenColor(customFill);
            if (isOutpost) {
                marker.setFillStyle(outpostFillOpacity, customFill);
                marker.setLineStyle(outpostLineWeight, outpostLineOpacity, customLine);
            } else {
                marker.setFillStyle(standardFillOpacity, customFill);
                marker.setLineStyle(standardLineWeight, standardLineOpacity, customLine);
            }
        } else if (isOutpost) {
            marker.setFillStyle(outpostFillOpacity, outpostFillColor);
            marker.setLineStyle(outpostLineWeight, outpostLineOpacity, outpostLineColor);
        } else {
            marker.setFillStyle(standardFillOpacity, standardFillColor);
            marker.setLineStyle(standardLineWeight, standardLineOpacity, standardLineColor);
        }

        // Apply Description (HTML Popup)
        String typeLabel = isOutpost ? "Karakol (Outpost)" : "Standart Claim";
        StringBuilder trustedBuilder = new StringBuilder();
        if (trustedNames == null || trustedNames.isEmpty()) {
            trustedBuilder.append("Hiçbiri");
        } else {
            for (int i = 0; i < trustedNames.size(); i++) {
                trustedBuilder.append(trustedNames.get(i));
                if (i < trustedNames.size() - 1) {
                    trustedBuilder.append(", ");
                }
            }
        }

        String desc = "<div style=\"padding: 7px;\">" +
                "<h3>GultenClaim Alanı</h3>" +
                "<b>Sahibi:</b> " + ownerName + "<br/>" +
                "<b>Tür:</b> " + typeLabel + "<br/>" +
                "<b>Koordinat:</b> Chunk (" + x + ", " + z + ")<br/>" +
                "<b>Güvenilenler:</b> " + trustedBuilder.toString() +
                "</div>";

        marker.setDescription(desc);
    }

    public void unregisterClaim(String worldName, int x, int z) {
        if (!isEnabled()) return;
        String markerId = "gultenclaim_" + worldName + "_" + x + "_" + z;
        AreaMarker marker = markerSet.findAreaMarker(markerId);
        if (marker != null) {
            marker.deleteMarker();
        }
    }

    public void clearAll() {
        if (!isEnabled()) return;
        for (AreaMarker marker : markerSet.getAreaMarkers()) {
            marker.deleteMarker();
        }
    }
}
