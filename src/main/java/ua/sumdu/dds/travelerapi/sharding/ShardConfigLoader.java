package ua.sumdu.dds.travelerapi.sharding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Loads shard configuration from central registry database.
 *
 * Features:
 * - Reads from shard_registry table instead of local file
 * - Caches configuration with version tracking
 * - Reloads on version mismatch or access errors
 * - Thread-safe with read-write locking
 */
public final class ShardConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ShardConfigLoader.class);

    // Registry connection settings (from environment or defaults)
    private static final String REGISTRY_JDBC = System.getenv().getOrDefault(
            "SHARD_REGISTRY_JDBC", "jdbc:postgresql://localhost:5450/dds_registry");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "postgres");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    // Cache
    private static final Map<String, ShardInfo> SHARD_CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger CACHED_VERSION = new AtomicInteger(-1);
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    // Reload control
    private static volatile long lastReloadAttempt = 0;
    private static final long RELOAD_COOLDOWN_MS = 1000; // Prevent reload spam

    private ShardConfigLoader() {}

    /**
     * Get shard info by hex key (0-f).
     * Automatically reloads from registry if cache is empty.
     */
    public static ShardInfo getShard(String hexKey) {
        LOCK.readLock().lock();
        try {
            if (SHARD_CACHE.isEmpty()) {
                LOCK.readLock().unlock();
                reloadFromRegistry();
                LOCK.readLock().lock();
            }

            ShardInfo info = SHARD_CACHE.get(hexKey);
            if (info == null) {
                throw new IllegalStateException("Unknown shard: " + hexKey);
            }
            return info;
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * Get current cached config version.
     */
    public static int getVersion() {
        return CACHED_VERSION.get();
    }

    /**
     * Force reload configuration from registry.
     * Called when application detects access error (REVOKE signal).
     */
    public static void reloadFromRegistry() {
        // Cooldown to prevent reload spam
        long now = System.currentTimeMillis();
        if (now - lastReloadAttempt < RELOAD_COOLDOWN_MS) {
            log.debug("Reload cooldown active, skipping");
            return;
        }

        LOCK.writeLock().lock();
        try {
            lastReloadAttempt = now;
            log.info("Reloading shard configuration from registry: {}", REGISTRY_JDBC);

            Map<String, ShardInfo> newCache = new ConcurrentHashMap<>();
            int newVersion = -1;

            try (Connection conn = DriverManager.getConnection(REGISTRY_JDBC, DB_USER, DB_PASSWORD)) {
                String sql = "SELECT shard, node, host, port, database, version FROM shard_registry";

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    while (rs.next()) {
                        String shardKey = rs.getString("shard").trim();
                        int version = rs.getInt("version");

                        ShardInfo info = new ShardInfo(
                                rs.getString("host"),
                                rs.getInt("port"),
                                rs.getString("database"),
                                DB_USER,
                                DB_PASSWORD
                        );

                        newCache.put(shardKey, info);
                        newVersion = Math.max(newVersion, version);
                    }
                }
            }

            if (newCache.isEmpty()) {
                throw new IllegalStateException("No shards found in registry");
            }

            // Atomic swap
            SHARD_CACHE.clear();
            SHARD_CACHE.putAll(newCache);

            int oldVersion = CACHED_VERSION.getAndSet(newVersion);
            log.info("Shard config reloaded: {} shards, version {} -> {}",
                    newCache.size(), oldVersion, newVersion);

        } catch (SQLException e) {
            log.error("Failed to reload shard config from registry", e);
            throw new RuntimeException("Failed to load shard configuration", e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Check if reload is needed based on registry version.
     * Can be called periodically or on-demand.
     */
    public static boolean checkAndReloadIfNeeded() {
        try (Connection conn = DriverManager.getConnection(REGISTRY_JDBC, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT MAX(version) as max_version FROM shard_registry";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                if (rs.next()) {
                    int registryVersion = rs.getInt("max_version");
                    if (registryVersion > CACHED_VERSION.get()) {
                        log.info("Registry version changed: {} -> {}, reloading...",
                                CACHED_VERSION.get(), registryVersion);
                        reloadFromRegistry();
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to check registry version", e);
        }
        return false;
    }

    /**
     * Handle access error - triggers config reload.
     * Call this when SQLException indicates permission denied (REVOKE signal).
     *
     * @param e the SQL exception to check
     * @return true if this was likely a REVOKE signal and reload was triggered
     */
    public static boolean handleAccessError(SQLException e) {
        String state = e.getSQLState();
        String message = e.getMessage().toLowerCase();

        // PostgreSQL error codes for permission denied
        // 42501 = INSUFFICIENT PRIVILEGE
        // 42000 = syntax/access error class
        boolean isAccessError = "42501".equals(state)
                || message.contains("permission denied")
                || message.contains("revoke");

        if (isAccessError) {
            log.warn("Access error detected, triggering config reload: {}", e.getMessage());
            reloadFromRegistry();
            return true;
        }

        return false;
    }

    /**
     * Get full config snapshot for debugging/monitoring.
     */
    public static ShardConfig getFullConfig() {
        LOCK.readLock().lock();
        try {
            if (SHARD_CACHE.isEmpty()) {
                LOCK.readLock().unlock();
                reloadFromRegistry();
                LOCK.readLock().lock();
            }
            return new ShardConfig(Map.copyOf(SHARD_CACHE), CACHED_VERSION.get());
        } finally {
            LOCK.readLock().unlock();
        }
    }
}
