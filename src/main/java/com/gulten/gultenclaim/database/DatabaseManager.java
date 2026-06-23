package com.gulten.gultenclaim.database;

import com.gulten.gultenclaim.GultenClaim;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class DatabaseManager {

    private final GultenClaim plugin;
    private Connection connection;
    private final File dbFile;

    public DatabaseManager(GultenClaim plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "claims.db");
        initialize();
    }

    private synchronized void initialize() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
            migrateSpawnColumns();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite database connection failed!", e);
        }
    }

    private synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get SQLite database connection!", e);
        }
        return connection;
    }

    private void createTables() {
        // Spawn columns included from the start for fresh installs
        String createClaimsTable = "CREATE TABLE IF NOT EXISTS gultenclaim_claims (" +
                "world_uuid TEXT NOT NULL," +
                "x INTEGER NOT NULL," +
                "z INTEGER NOT NULL," +
                "owner_uuid TEXT NOT NULL," +
                "owner_name TEXT NOT NULL," +
                "is_outpost INTEGER DEFAULT 0," +
                "spawn_x REAL," +
                "spawn_y REAL," +
                "spawn_z REAL," +
                "spawn_yaw REAL," +
                "spawn_pitch REAL," +
                "settings TEXT DEFAULT '{}'," +
                "PRIMARY KEY (world_uuid, x, z)" +
                ");";


        String createTrustTable = "CREATE TABLE IF NOT EXISTS gultenclaim_trust (" +
                "owner_uuid TEXT NOT NULL," +
                "trusted_uuid TEXT NOT NULL," +
                "trusted_name TEXT NOT NULL," +
                "PRIMARY KEY (owner_uuid, trusted_uuid)" +
                ");";

        String createColorsTable = "CREATE TABLE IF NOT EXISTS gultenclaim_colors (" +
                "uuid TEXT PRIMARY KEY," +
                "hex_color TEXT NOT NULL" +
                ");";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(createClaimsTable);
            stmt.execute(createTrustTable);
            stmt.execute(createColorsTable);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create database tables!", e);
        }
    }

    // Migration: add spawn columns + settings column if upgrading from an older database
    private void migrateSpawnColumns() {
        String[] spawnCols = {"spawn_x", "spawn_y", "spawn_z", "spawn_yaw", "spawn_pitch"};
        for (String col : spawnCols) {
            try (Statement stmt = getConnection().createStatement()) {
                stmt.execute("ALTER TABLE gultenclaim_claims ADD COLUMN " + col + " REAL");
            } catch (SQLException ignored) {
                // Column already exists — safe to ignore
            }
        }
        // Add settings column for older databases
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("ALTER TABLE gultenclaim_claims ADD COLUMN settings TEXT DEFAULT '{}'");
        } catch (SQLException ignored) {
            // Already exists
        }
    }

    // Load all claims from database
    public List<ClaimData> loadClaims() {
        List<ClaimData> claims = new ArrayList<>();
        String query = "SELECT * FROM gultenclaim_claims";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                String worldUuid = rs.getString("world_uuid");
                int x = rs.getInt("x");
                int z = rs.getInt("z");
                String ownerUuid = rs.getString("owner_uuid");
                String ownerName = rs.getString("owner_name");
                boolean isOutpost = rs.getInt("is_outpost") == 1;

                // Spawn (may be null for older data)
                double spawnX = rs.getDouble("spawn_x");
                double spawnY = rs.getDouble("spawn_y");
                double spawnZ = rs.getDouble("spawn_z");
                float spawnYaw = (float) rs.getDouble("spawn_yaw");
                float spawnPitch = (float) rs.getDouble("spawn_pitch");
                boolean hasSpawn = !rs.wasNull();

                String settings = rs.getString("settings");

                claims.add(new ClaimData(worldUuid, x, z, ownerUuid, ownerName, isOutpost,
                        hasSpawn, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, settings));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load claims from database!", e);
        }
        return claims;
    }

    // Load all trust relations from database
    public Map<UUID, Set<UUID>> loadTrusts() {
        Map<UUID, Set<UUID>> trusts = new HashMap<>();
        String query = "SELECT * FROM gultenclaim_trust";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
                UUID trustedUuid = UUID.fromString(rs.getString("trusted_uuid"));
                trusts.computeIfAbsent(ownerUuid, k -> new HashSet<>()).add(trustedUuid);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load trust list from database!", e);
        }
        return trusts;
    }

    // Add a claim
    public void addClaim(String worldUuid, int x, int z, UUID ownerUuid, String ownerName, boolean isOutpost) {
        String sql = "INSERT OR REPLACE INTO gultenclaim_claims (world_uuid, x, z, owner_uuid, owner_name, is_outpost) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, worldUuid);
            pstmt.setInt(2, x);
            pstmt.setInt(3, z);
            pstmt.setString(4, ownerUuid.toString());
            pstmt.setString(5, ownerName);
            pstmt.setInt(6, isOutpost ? 1 : 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save claim to database!", e);
        }
    }

    // Set/update the spawn point for a claim
    public void setClaimSpawn(String worldUuid, int x, int z, double spawnX, double spawnY, double spawnZ, float yaw, float pitch) {
        String sql = "UPDATE gultenclaim_claims SET spawn_x=?, spawn_y=?, spawn_z=?, spawn_yaw=?, spawn_pitch=? " +
                "WHERE world_uuid=? AND x=? AND z=?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setDouble(1, spawnX);
            pstmt.setDouble(2, spawnY);
            pstmt.setDouble(3, spawnZ);
            pstmt.setDouble(4, yaw);
            pstmt.setDouble(5, pitch);
            pstmt.setString(6, worldUuid);
            pstmt.setInt(7, x);
            pstmt.setInt(8, z);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save claim spawn to database!", e);
        }
    }

    // Remove a claim
    public void removeClaim(String worldUuid, int x, int z) {
        String sql = "DELETE FROM gultenclaim_claims WHERE world_uuid = ? AND x = ? AND z = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, worldUuid);
            pstmt.setInt(2, x);
            pstmt.setInt(3, z);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete claim from database!", e);
        }
    }

    // Remove all claims by owner
    public void removeClaimsByOwner(UUID ownerUuid) {
        String sql = "DELETE FROM gultenclaim_claims WHERE owner_uuid = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, ownerUuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete all claims of owner from database!", e);
        }
    }

    // Add a trust relation
    public void addTrust(UUID ownerUuid, UUID trustedUuid, String trustedName) {
        String sql = "INSERT OR REPLACE INTO gultenclaim_trust (owner_uuid, trusted_uuid, trusted_name) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, ownerUuid.toString());
            pstmt.setString(2, trustedUuid.toString());
            pstmt.setString(3, trustedName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save trust relations to database!", e);
        }
    }

    // Remove a trust relation
    public void removeTrust(UUID ownerUuid, UUID trustedUuid) {
        String sql = "DELETE FROM gultenclaim_trust WHERE owner_uuid = ? AND trusted_uuid = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, ownerUuid.toString());
            pstmt.setString(2, trustedUuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete trust relation from database!", e);
        }
    }

    // Load all claim colors from database
    public Map<UUID, String> loadColors() {
        Map<UUID, String> colors = new HashMap<>();
        String query = "SELECT * FROM gultenclaim_colors";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String hexColor = rs.getString("hex_color");
                colors.put(uuid, hexColor);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load claim colors from database!", e);
        }
        return colors;
    }

    // Save or update per-claim toggle settings
    public void saveClaimSettings(String worldUuid, int x, int z, String settingsJson) {
        String sql = "UPDATE gultenclaim_claims SET settings=? WHERE world_uuid=? AND x=? AND z=?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, settingsJson);
            pstmt.setString(2, worldUuid);
            pstmt.setInt(3, x);
            pstmt.setInt(4, z);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save claim settings to database!", e);
        }
    }

    // Save or update a claim color
    public void saveColor(UUID uuid, String hexColor) {
        String sql = "INSERT OR REPLACE INTO gultenclaim_colors (uuid, hex_color) VALUES (?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, hexColor);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save claim color to database!", e);
        }
    }

    // Data class representing one SQLite row
    public static class ClaimData {
        public final String worldUuid;
        public final int x;
        public final int z;
        public final String ownerUuid;
        public final String ownerName;
        public final boolean isOutpost;
        // Spawn data
        public final boolean hasSpawn;
        public final double spawnX;
        public final double spawnY;
        public final double spawnZ;
        public final float spawnYaw;
        public final float spawnPitch;
        // Toggle settings
        public final String settings;

        public ClaimData(String worldUuid, int x, int z, String ownerUuid, String ownerName, boolean isOutpost,
                         boolean hasSpawn, double spawnX, double spawnY, double spawnZ, float spawnYaw, float spawnPitch,
                         String settings) {
            this.worldUuid = worldUuid;
            this.x = x;
            this.z = z;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.isOutpost = isOutpost;
            this.hasSpawn = hasSpawn;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
            this.spawnYaw = spawnYaw;
            this.spawnPitch = spawnPitch;
            this.settings = settings != null ? settings : "{}";
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error while closing database connection!", e);
        }
    }
}
