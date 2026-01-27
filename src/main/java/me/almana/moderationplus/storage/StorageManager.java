package me.almana.moderationplus.storage;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.UUID;
import java.util.logging.Level;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public class StorageManager {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private static final String DB_PATH = "mods/data/moderation.db";
    private static final int CURRENT_DB_VERSION = 2;

    private Connection connection;

    public void init() {
        try {
            ensureDataFolderExists();
            connect();
            setupDatabase();
            runMigrations();
            logger.at(Level.INFO).log("StorageManager initialized successfully.");
        } catch (SQLException e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to initialize StorageManager");
            throw new IllegalStateException("Failed to initialize database", e);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.at(Level.INFO).log("Database connection closed.");
            } catch (SQLException e) {
                logger.at(Level.SEVERE).withCause(e).log("Failed to close database connection");
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }

    private void ensureDataFolderExists() {
        File dataFolder = new File("mods/data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    private void connect() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found", e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        connection.setAutoCommit(true);
    }

    private void setupDatabase() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");

            stmt.execute("CREATE TABLE IF NOT EXISTS migrations (" +
                    "version INTEGER PRIMARY KEY, " +
                    "applied_at INTEGER NOT NULL" +
                    ");");
        }
    }

    private void runMigrations() throws SQLException {
        int currentVersion = getAppliedMigrationVersion();

        if (currentVersion < 1) {
            applyMigration1();
        }
        if (currentVersion < 2) {
            applyMigration2();
        }
        if (currentVersion < 3) {
            applyMigration3();
        }
        if (currentVersion < 4) {
            applyMigration4();
        }
        if (currentVersion < 5) {
            applyMigration5();
        }
        if (currentVersion < 6) {
            applyMigration6();
        }
        if (currentVersion < 7) {
            applyMigration7();
        }
        if (currentVersion < 8) {
            applyMigration8();
        }
    }

    private int getAppliedMigrationVersion() throws SQLException {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM migrations")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    private void applyMigration1() throws SQLException {
        logger.at(Level.INFO).log("Applying migration 1...");

        try (Statement stmt = connection.createStatement()) {

            stmt.execute("CREATE TABLE players (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "uuid TEXT UNIQUE NOT NULL, " +
                    "username TEXT, " +
                    "first_seen INTEGER, " +
                    "last_seen INTEGER" +
                    ");");

            try (PreparedStatement recordStmt = connection.prepareStatement(
                    "INSERT INTO migrations (version, applied_at) VALUES (?, ?)")) {
                recordStmt.setInt(1, 1);
                recordStmt.setLong(2, System.currentTimeMillis());
                recordStmt.executeUpdate();
            }
        }

        logger.at(Level.INFO).log("Migration 1 applied successfully.");
    }

    private void applyMigration2() throws SQLException {
        logger.at(Level.INFO).log("Applying migration 2...");

        try (Statement stmt = connection.createStatement()) {

            stmt.execute("CREATE TABLE punishment_types (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT UNIQUE NOT NULL" +
                    ");");

            stmt.execute("INSERT INTO punishment_types (name) VALUES ('BAN'), ('KICK'), ('MUTE'), ('WARN');");

            stmt.execute("CREATE TABLE punishments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player_id INTEGER NOT NULL, " +
                    "type_id INTEGER NOT NULL, " +
                    "issuer_uuid TEXT, " +
                    "reason TEXT, " +
                    "created_at INTEGER NOT NULL, " +
                    "expires_at INTEGER, " +
                    "active INTEGER NOT NULL, " +
                    "extra_data TEXT, " +
                    "FOREIGN KEY(player_id) REFERENCES players(id), " +
                    "FOREIGN KEY(type_id) REFERENCES punishment_types(id)" +
                    ");");

            try (PreparedStatement recordStmt = connection.prepareStatement(
                    "INSERT INTO migrations (version, applied_at) VALUES (?, ?)")) {
                recordStmt.setInt(1, 2);
                recordStmt.setLong(2, System.currentTimeMillis());
                recordStmt.executeUpdate();
            }
        }

        logger.at(Level.INFO).log("Migration 2 applied successfully.");
    }

    private void applyMigration3() throws SQLException {
        logger.at(Level.INFO).log("Applying migration 3...");

        try (Statement stmt = connection.createStatement()) {

            stmt.execute("CREATE TABLE staff_notes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player_id INTEGER NOT NULL, " +
                    "issuer_uuid TEXT NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "FOREIGN KEY(player_id) REFERENCES players(id)" +
                    ");");

            try (PreparedStatement recordStmt = connection.prepareStatement(
                    "INSERT INTO migrations (version, applied_at) VALUES (?, ?)")) {
                recordStmt.setInt(1, 3);
                recordStmt.setLong(2, System.currentTimeMillis());
                recordStmt.executeUpdate();
            }
        }

        logger.at(Level.INFO).log("Migration 3 applied successfully.");
    }

    private void applyMigration4() throws SQLException {
        logger.at(Level.INFO).log("Applying migration 4...");

        try (Statement stmt = connection.createStatement()) {

            stmt.execute("INSERT INTO punishment_types (name) VALUES ('JAIL');");

            try (PreparedStatement recordStmt = connection.prepareStatement(
                    "INSERT INTO migrations (version, applied_at) VALUES (?, ?)")) {
                recordStmt.setInt(1, 4);
                recordStmt.setLong(2, System.currentTimeMillis());
                recordStmt.executeUpdate();
            }
        }

        logger.at(Level.INFO).log("Migration 4 applied successfully.");
    }

    private void applyMigration5() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE server_identity (" +
                    "id INTEGER PRIMARY KEY CHECK (id = 1), " +
                    "server_id TEXT NOT NULL, " +
                    "server_secret TEXT NOT NULL" +
                    ");");

            try (PreparedStatement recordStmt = connection.prepareStatement(
                    "INSERT INTO migrations (version, applied_at) VALUES (?, ?)")) {
                recordStmt.setInt(1, 5);
                recordStmt.setLong(2, System.currentTimeMillis());
                recordStmt.executeUpdate();
            }
        }
    }

    private void applyMigration6() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE server_identity ADD COLUMN is_claimed INTEGER DEFAULT 0;");
            stmt.execute("ALTER TABLE server_identity ADD COLUMN claim_token TEXT;");

            try (PreparedStatement recordStmt = connection.prepareStatement(
                    "INSERT INTO migrations (version, applied_at) VALUES (?, ?)")) {
                recordStmt.setInt(1, 6);
                recordStmt.setLong(2, System.currentTimeMillis());
                recordStmt.executeUpdate();
            }
        }
    }

    public UUID getUuidByUsername(String username) {
        String query = "SELECT uuid FROM players WHERE username = ? COLLATE NOCASE";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to getUuidByUsername for %s", username);
        }
        return null;
    }

    public PlayerData getOrCreatePlayer(UUID uuid, String username) {
        try {
            PlayerData existing = getPlayerByUUID(uuid);
            if (existing != null) {
                logger.at(Level.INFO).log("Player found (reuse): %s (%s)", username, uuid);
                updatePlayerLastSeen(uuid, username);
                return getPlayerByUUID(uuid);
            } else {
                logger.at(Level.INFO).log("Creating new player: %s (%s)", username, uuid);
                return createPlayer(uuid, username);
            }
        } catch (SQLException e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to getOrCreatePlayer for %s", uuid);
            throw new RuntimeException("Database error in getOrCreatePlayer", e);
        }
    }

    public PlayerData getPlayerByUUID(UUID uuid) throws SQLException {
        String query = "SELECT id, uuid, username, first_seen, last_seen, locale FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String locale = rs.getString("locale");
                    return new PlayerData(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("username"),
                            rs.getLong("first_seen"),
                            rs.getLong("last_seen"),
                            locale != null && !locale.isEmpty() ? java.util.Optional.of(locale) : java.util.Optional.empty());
                }
            }
        }
        return null;
    }

    public PlayerData createPlayer(UUID uuid, String username) throws SQLException {
        String insert = "INSERT INTO players (uuid, username, first_seen, last_seen) VALUES (?, ?, ?, ?)";
        long now = System.currentTimeMillis();

        try (PreparedStatement stmt = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.setLong(3, now);
            stmt.setLong(4, now);
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    return new PlayerData(id, uuid, username, now, now, java.util.Optional.empty());
                } else {
                    throw new SQLException("Creating player failed, no ID obtained.");
                }
            }
        }
    }

    private void updatePlayerLastSeen(UUID uuid, String username) throws SQLException {
        String update = "UPDATE players SET username = ?, last_seen = ? WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(update)) {
            stmt.setString(1, username);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, uuid.toString());
            stmt.executeUpdate();
        }
    }

    // PlayerData with optional locale
    public record PlayerData(int id, UUID uuid, String username, long firstSeen, long lastSeen, java.util.Optional<String> locale) {
    }

    public void createPunishment(Punishment punishment) throws SQLException {
        String query = "INSERT INTO punishments (player_id, type_id, issuer_uuid, reason, created_at, expires_at, active, extra_data) "
                +
                "VALUES (?, (SELECT id FROM punishment_types WHERE name = ?), ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, punishment.playerId());
            stmt.setString(2, punishment.type());
            stmt.setString(3, punishment.issuerUuid());
            stmt.setString(4, punishment.reason());
            stmt.setLong(5, punishment.createdAt());
            if (punishment.expiresAt() > 0) {
                stmt.setLong(6, punishment.expiresAt());
            } else {
                stmt.setObject(6, null);
            }
            stmt.setInt(7, punishment.active() ? 1 : 0);
            stmt.setString(8, punishment.extraData());
            stmt.executeUpdate();
        }
    }

    public java.util.List<Punishment> getPunishmentsForPlayer(int playerId) throws SQLException {
        return getPunishments(playerId, false, null);
    }

    public java.util.List<Punishment> getActivePunishments(int playerId) throws SQLException {
        return getPunishments(playerId, true, null);
    }

    public java.util.List<Punishment> getActivePunishmentsByType(int playerId, String type) throws SQLException {
        return getPunishments(playerId, true, type);
    }

    public void deactivatePunishment(int id) throws SQLException {
        String query = "UPDATE punishments SET active = 0 WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    private java.util.List<Punishment> getPunishments(int playerId, boolean activeOnly, String typeFilter)
            throws SQLException {
        StringBuilder query = new StringBuilder(
                "SELECT p.id, p.player_id, pt.name as type, p.issuer_uuid, p.reason, p.created_at, p.expires_at, p.active, p.extra_data "
                        +
                        "FROM punishments p " +
                        "JOIN punishment_types pt ON p.type_id = pt.id " +
                        "WHERE p.player_id = ?");

        if (activeOnly) {
            query.append(" AND p.active = 1");
        }
        if (typeFilter != null) {
            query.append(" AND pt.name = ?");
        }

        java.util.List<Punishment> results = new java.util.ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query.toString())) {
            stmt.setInt(1, playerId);
            if (typeFilter != null) {
                stmt.setString(2, typeFilter);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new Punishment(
                            rs.getInt("id"),
                            rs.getInt("player_id"),
                            rs.getString("type"),
                            rs.getString("issuer_uuid"),
                            rs.getString("reason"),
                            rs.getLong("created_at"),
                            rs.getLong("expires_at"),
                            rs.getInt("active") == 1,
                            rs.getString("extra_data")));
                }
            }
        }
        return results;
    }

    public void createStaffNote(StaffNote note) throws SQLException {
        String query = "INSERT INTO staff_notes (player_id, issuer_uuid, message, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, note.playerId());
            stmt.setString(2, note.issuerUuid());
            stmt.setString(3, note.message());
            stmt.setLong(4, note.createdAt());
            stmt.executeUpdate();
        }
    }

    public java.util.List<StaffNote> getStaffNotes(int playerId) throws SQLException {
        String query = "SELECT id, player_id, issuer_uuid, message, created_at FROM staff_notes " +
                "WHERE player_id = ? ORDER BY created_at DESC";
        java.util.List<StaffNote> results = new java.util.ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new StaffNote(
                            rs.getInt("id"),
                            rs.getInt("player_id"),
                            rs.getString("issuer_uuid"),
                            rs.getString("message"),
                            rs.getLong("created_at")));
                }
            }
        }
        return results;
    }

    public void insertPunishment(Punishment punishment) throws SQLException {
        String query = "INSERT INTO punishments (player_id, type_id, issuer_uuid, reason, created_at, expires_at, active, extra_data) "
                +
                "SELECT ?, id, ?, ?, ?, ?, ?, ? FROM punishment_types WHERE name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, punishment.playerId());
            stmt.setString(2, punishment.issuerUuid());
            stmt.setString(3, punishment.reason());
            stmt.setLong(4, punishment.createdAt());
            if (punishment.expiresAt() > 0) {
                stmt.setLong(5, punishment.expiresAt());
            } else {
                stmt.setNull(5, Types.BIGINT);
            }
            stmt.setInt(6, punishment.active() ? 1 : 0);
            stmt.setString(7, punishment.extraData());
            stmt.setString(8, punishment.type());
            stmt.executeUpdate();
        }
    }

    public int deactivatePunishmentsByType(int playerId, String type) throws SQLException {
        String query = "UPDATE punishments SET active = 0 " +
                "WHERE player_id = ? AND type_id = (SELECT id FROM punishment_types WHERE name = ?) AND active = 1";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setString(2, type);
            return stmt.executeUpdate();
        }
    }

    public void flush() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA wal_checkpoint(FULL);");
            logger.at(Level.INFO).log("Database flushed (checkpointed).");
        } catch (SQLException e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to flush database");
        }
    }

    public record ServerIdentity(String serverId, String serverSecret, boolean isClaimed, String claimToken) {
    }

    public ServerIdentity getOrGenerateServerIdentity() throws SQLException {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT server_id, server_secret, is_claimed, claim_token FROM server_identity WHERE id = 1")) {
            if (rs.next()) {
                return new ServerIdentity(
                        rs.getString("server_id"),
                        rs.getString("server_secret"),
                        rs.getInt("is_claimed") == 1,
                        rs.getString("claim_token"));
            }
        }

        String sid = UUID.randomUUID().toString();
        byte[] b = new byte[32];
        new java.security.SecureRandom().nextBytes(b);
        String sec = java.util.Base64.getEncoder().encodeToString(b);

        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO server_identity (id, server_id, server_secret, is_claimed) VALUES (1, ?, ?, 0)")) {
            stmt.setString(1, sid);
            stmt.setString(2, sec);
            stmt.executeUpdate();
        }
        return new ServerIdentity(sid, sec, false, null);
    }

    public String getOrGenerateClaimToken() throws SQLException {

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT claim_token, is_claimed FROM server_identity WHERE id = 1")) {
            if (rs.next()) {
                if (rs.getInt("is_claimed") == 1) {
                    return null;
                }
                String existing = rs.getString("claim_token");
                if (existing != null && !existing.isEmpty()) {
                    return existing;
                }
            }
        }


        byte[] b = new byte[16];
        new java.security.SecureRandom().nextBytes(b);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);

        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE server_identity SET claim_token = ? WHERE id = 1")) {
            stmt.setString(1, token);
            stmt.executeUpdate();
        }
        return token;
    }

    public boolean completeClaim(String token) throws SQLException {
        boolean success = false;
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE server_identity SET is_claimed = 1, claim_token = NULL WHERE id = 1 AND claim_token = ? AND is_claimed = 0")) {
            stmt.setString(1, token);
            int rows = stmt.executeUpdate();
            success = rows > 0;
        }
        return success;
    }

    private void applyMigration7() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS web_commands_log (" +
                    "id TEXT PRIMARY KEY, " +
                    "processed_at INTEGER NOT NULL" +
                    ");");

            try (PreparedStatement recordStmt = connection.prepareStatement(
                    "INSERT INTO migrations (version, applied_at) VALUES (?, ?)")) {
                recordStmt.setInt(1, 7);
                recordStmt.setLong(2, System.currentTimeMillis());
                recordStmt.executeUpdate();
            }
        }
    }

    public boolean hasWebCommandProcessed(String commandId) {
        String query = "SELECT 1 FROM web_commands_log WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, commandId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to check processed web command: %s", commandId);
            return false;
        }
    }

    public void markWebCommandProcessed(String commandId) {
        String query = "INSERT OR IGNORE INTO web_commands_log (id, processed_at) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, commandId);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to mark web command processed: %s", commandId);
        }
    }

    // Add locale column to players table
    private void applyMigration8() throws SQLException {
        logger.at(Level.INFO).log("Applying migration 8...");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE players ADD COLUMN locale TEXT;");

            try (PreparedStatement recordStmt = connection.prepareStatement(
                    "INSERT INTO migrations (version, applied_at) VALUES (?, ?)")) {
                recordStmt.setInt(1, 8);
                recordStmt.setLong(2, System.currentTimeMillis());
                recordStmt.executeUpdate();
            }
        }

        logger.at(Level.INFO).log("Migration 8 applied successfully.");
    }

    // Get player locale by UUID
    public java.util.Optional<String> getPlayerLocale(UUID uuid) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT locale FROM players WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String locale = rs.getString("locale");
                    return locale != null && !locale.isEmpty() ? java.util.Optional.of(locale) : java.util.Optional.empty();
                }
            }
        } catch (SQLException e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to get locale for %s", uuid);
        }
        return java.util.Optional.empty();
    }



    // Set player locale by UUID
    // @deprecated Use LanguageManager.setPlayerLocale for caching support
    public void setPlayerLocale(UUID uuid, String locale) {
        try (PreparedStatement stmt = connection.prepareStatement("UPDATE players SET locale = ? WHERE uuid = ?")) {
            stmt.setObject(1, locale);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to set locale for %s", uuid);
        }
    }
}
