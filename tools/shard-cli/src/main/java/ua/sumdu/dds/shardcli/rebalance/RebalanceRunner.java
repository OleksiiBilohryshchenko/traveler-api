package ua.sumdu.dds.shardcli.rebalance;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.sql.*;
import java.util.Properties;

/**
 * Executes shard rebalancing using PostgreSQL logical replication.
 *
 * Algorithm (per task requirements):
 * 1. Create PUBLICATION on source node
 * 2. Create SUBSCRIPTION on target node
 * 3. Wait for sync (poll pg_stat_subscription)
 * 4. LOCK TABLE IN ACCESS EXCLUSIVE MODE on source
 * 5. Drop subscription (target becomes master)
 * 6. Update shard_registry
 * 7. REVOKE SELECT on source tables (signal to app)
 */
public final class RebalanceRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] TABLES = {"travel_plans", "locations"};
    private static final int SYNC_POLL_INTERVAL_MS = 1000;
    private static final int SYNC_TIMEOUT_MS = 300_000; // 5 minutes

    private RebalanceRunner() {}

    // =========================
    // NODE CONFIG
    // =========================

    /**
     * Maps node name to connection details.
     * In production, this would come from registry or config.
     */
    static class NodeConfig {
        final String host;
        final int port;

        NodeConfig(String host, int port) {
            this.host = host;
            this.port = port;
        }

        static NodeConfig forNode(String nodeName) {
            // Docker internal ports (all containers use 5432 internally)
            // External ports: postgres_00=5440, postgres_01=5441, etc.
            return switch (nodeName) {
                case "postgres_00" -> new NodeConfig("postgres_00", 5432);
                case "postgres_01" -> new NodeConfig("postgres_01", 5432);
                case "postgres_02" -> new NodeConfig("postgres_02", 5432);
                case "postgres_03" -> new NodeConfig("postgres_03", 5432);
                default -> throw new IllegalArgumentException("Unknown node: " + nodeName);
            };
        }

        static NodeConfig forNodeExternal(String nodeName) {
            // External ports for CLI running outside Docker
            return switch (nodeName) {
                case "postgres_00" -> new NodeConfig("localhost", 5440);
                case "postgres_01" -> new NodeConfig("localhost", 5441);
                case "postgres_02" -> new NodeConfig("localhost", 5442);
                case "postgres_03" -> new NodeConfig("localhost", 5443);
                default -> throw new IllegalArgumentException("Unknown node: " + nodeName);
            };
        }
    }

    // =========================
    // ENV
    // =========================

    static final class Env {
        final String dbUser;
        final String dbPassword;
        final String registryJdbc;
        final boolean useExternalPorts;

        private Env(String dbUser, String dbPassword, String registryJdbc, boolean useExternalPorts) {
            this.dbUser = dbUser;
            this.dbPassword = dbPassword;
            this.registryJdbc = registryJdbc;
            this.useExternalPorts = useExternalPorts;
        }

        static Env load() {
            Properties props = new Properties();

            String envPath = System.getProperty("SHARDCLI_ENV", ".env");
            File f = new File(envPath);
            if (f.exists()) {
                try (var r = new java.io.FileReader(f)) {
                    props.load(r);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read .env", e);
                }
            }

            String user = env("DB_USER", props, "postgres");
            String pass = env("DB_PASSWORD", props, null);
            String jdbc = env("SHARD_REGISTRY_JDBC", props, "jdbc:postgresql://localhost:5450/dds_registry");
            boolean external = Boolean.parseBoolean(env("USE_EXTERNAL_PORTS", props, "true"));

            if (pass == null || pass.isBlank())
                throw new RuntimeException("DB_PASSWORD not set");

            return new Env(user, pass, jdbc, external);
        }

        private static String env(String key, Properties p, String def) {
            String v = System.getenv(key);
            if (v != null) return v;
            return p.getProperty(key, def);
        }

        NodeConfig getNodeConfig(String nodeName) {
            return useExternalPorts
                    ? NodeConfig.forNodeExternal(nodeName)
                    : NodeConfig.forNode(nodeName);
        }
    }

    // =========================
    // PLAN LOADING
    // =========================

    public static RebalancePlan loadPlan(String planPath) throws Exception {
        File f = new File(planPath);
        if (!f.exists())
            throw new IllegalArgumentException("Plan file not found: " + planPath);
        return MAPPER.readValue(f, RebalancePlan.class);
    }

    // =========================
    // MAIN EXECUTION
    // =========================

    public static void run(RebalancePlan plan, boolean dryRun) {
        Env env = Env.load();

        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║       SHARD REBALANCE STARTED          ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println("Moves: " + plan.moves().size());
        System.out.println("Dry-run: " + dryRun);
        System.out.println("Registry: " + env.registryJdbc);
        System.out.println();

        int success = 0;
        int failed = 0;

        for (RebalanceMove move : plan.moves()) {
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.printf("Moving: %s (%s → %s)%n",
                    move.database(), move.fromNode(), move.toNode());
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            try {
                executeMove(env, move, dryRun);
                success++;
                System.out.println("✓ Move completed successfully\n");
            } catch (Exception e) {
                failed++;
                System.err.println("✗ Move failed: " + e.getMessage());
                e.printStackTrace();
                System.out.println();

                if (!dryRun) {
                    System.err.println("Stopping due to failure. Manual cleanup may be required.");
                    break;
                }
            }
        }

        System.out.println("╔════════════════════════════════════════╗");
        System.out.printf("║  COMPLETED: %d success, %d failed       ║%n", success, failed);
        System.out.println("╚════════════════════════════════════════╝");
    }

    // =========================
    // SINGLE MOVE EXECUTION
    // =========================

    private static void executeMove(Env env, RebalanceMove move, boolean dryRun) throws Exception {
        NodeConfig source = env.getNodeConfig(move.fromNode());
        NodeConfig target = env.getNodeConfig(move.toNode());

        // Connect to postgres DB for admin operations (create database)
        String targetAdminJdbc = String.format("jdbc:postgresql://%s:%d/postgres",
                target.host, target.port);
        String sourceJdbc = String.format("jdbc:postgresql://%s:%d/%s",
                source.host, source.port, move.database());
        String targetJdbc = String.format("jdbc:postgresql://%s:%d/%s",
                target.host, target.port, move.database());

        String pubName = "pub_" + move.shardKey();
        String subName = "sub_" + move.shardKey();

        // Step 0: Create database and schema on target (if not exists)
        System.out.println("  [0/7] Creating database on target (if not exists)...");
        if (!dryRun) {
            createDatabaseIfNotExists(targetAdminJdbc, move.database(), env);
            // Create schema on target
            try (Connection tgt = DriverManager.getConnection(targetJdbc, env.dbUser, env.dbPassword)) {
                createSchemaOnTarget(tgt);
            }
        }

        // Step 1: Create publication on source
        System.out.println("  [1/7] Creating publication on source...");
        if (!dryRun) {
            try (Connection src = DriverManager.getConnection(sourceJdbc, env.dbUser, env.dbPassword)) {
                createPublication(src, pubName);
            }
        }

        // Step 2: Create subscription on target
        System.out.println("  [2/7] Creating subscription on target...");
        String connString = buildConnectionString(move.fromNode(), move.database(), env);
        if (!dryRun) {
            try (Connection tgt = DriverManager.getConnection(targetJdbc, env.dbUser, env.dbPassword)) {
                createSubscription(tgt, subName, pubName, connString);
            }
        }

        // Step 3: Wait for synchronization
        System.out.println("  [3/7] Waiting for sync...");
        if (!dryRun) {
            try (Connection tgt = DriverManager.getConnection(targetJdbc, env.dbUser, env.dbPassword)) {
                waitForSync(tgt, subName);
            }
        }

        // Step 4: Lock tables on source
        System.out.println("  [4/7] Locking tables on source...");
        Connection srcLocked = null;
        if (!dryRun) {
            srcLocked = DriverManager.getConnection(sourceJdbc, env.dbUser, env.dbPassword);
            srcLocked.setAutoCommit(false);
            lockTables(srcLocked);
        }

        try {
            // Step 5: Drop subscription (target becomes standalone)
            System.out.println("  [5/7] Dropping subscription (promoting target)...");
            if (!dryRun) {
                try (Connection tgt = DriverManager.getConnection(targetJdbc, env.dbUser, env.dbPassword)) {
                    dropSubscription(tgt, subName);
                }
            }

            // Step 6: Update registry
            System.out.println("  [6/7] Updating shard registry...");
            if (!dryRun) {
                try (Connection reg = DriverManager.getConnection(env.registryJdbc, env.dbUser, env.dbPassword)) {
                    updateRegistry(reg, move, target);
                }
            }

            // Step 7: Revoke access on source
            System.out.println("  [7/7] Revoking access on source...");
            if (!dryRun && srcLocked != null) {
                revokeAccess(srcLocked, env.dbUser);
                srcLocked.commit();
            }

        } finally {
            // Cleanup: drop publication on source
            if (!dryRun) {
                try (Connection src = DriverManager.getConnection(sourceJdbc, env.dbUser, env.dbPassword)) {
                    dropPublication(src, pubName);
                } catch (Exception e) {
                    System.err.println("  Warning: Failed to drop publication: " + e.getMessage());
                }
            }

            if (srcLocked != null) {
                try { srcLocked.close(); } catch (Exception ignored) {}
            }
        }
    }

    // =========================
    // REPLICATION OPERATIONS
    // =========================

    private static void createPublication(Connection conn, String pubName) throws SQLException {
        // Drop if exists (idempotent)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP PUBLICATION IF EXISTS " + pubName);
        }

        // Create publication for all tables
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE PUBLICATION " + pubName + " FOR ALL TABLES");
        }
        System.out.println("    Created publication: " + pubName);
    }

    private static void createSubscription(Connection conn, String subName, String pubName, String connString)
            throws SQLException {
        // Drop if exists
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SUBSCRIPTION IF EXISTS " + subName);
        } catch (SQLException e) {
            // Ignore if doesn't exist
        }

        // Create subscription
        String sql = String.format(
                "CREATE SUBSCRIPTION %s CONNECTION '%s' PUBLICATION %s WITH (copy_data = true)",
                subName, connString, pubName
        );
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
        System.out.println("    Created subscription: " + subName);
    }

    private static void waitForSync(Connection conn, String subName) throws SQLException, InterruptedException {
        long startTime = System.currentTimeMillis();

        String sql = """
            SELECT srsubstate 
            FROM pg_subscription_rel psr
            JOIN pg_subscription ps ON ps.oid = psr.srsubid
            WHERE ps.subname = ?
        """;

        while (true) {
            boolean allReady = true;
            int tableCount = 0;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, subName);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    tableCount++;
                    String state = rs.getString("srsubstate");
                    // States: i=init, d=data copy, s=sync, r=ready
                    if (!"r".equals(state)) {
                        allReady = false;
                    }
                }
            }

            if (tableCount > 0 && allReady) {
                System.out.println("    Sync complete for " + tableCount + " tables");
                return;
            }

            if (System.currentTimeMillis() - startTime > SYNC_TIMEOUT_MS) {
                throw new SQLException("Sync timeout after " + SYNC_TIMEOUT_MS + "ms");
            }

            System.out.print(".");
            Thread.sleep(SYNC_POLL_INTERVAL_MS);
        }
    }

    private static void lockTables(Connection conn) throws SQLException {
        for (String table : TABLES) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("LOCK TABLE " + table + " IN ACCESS EXCLUSIVE MODE");
            }
        }
        System.out.println("    Locked tables: " + String.join(", ", TABLES));
    }

    private static void dropSubscription(Connection conn, String subName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Disable first to stop replication
            stmt.execute("ALTER SUBSCRIPTION " + subName + " DISABLE");
        }

        try (Statement stmt = conn.createStatement()) {
            // Set slot_name to NONE to avoid slot issues
            stmt.execute("ALTER SUBSCRIPTION " + subName + " SET (slot_name = NONE)");
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SUBSCRIPTION " + subName);
        }
        System.out.println("    Dropped subscription: " + subName);
    }

    private static void dropPublication(Connection conn, String pubName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP PUBLICATION IF EXISTS " + pubName);
        }
        System.out.println("    Dropped publication: " + pubName);
    }

    private static void revokeAccess(Connection conn, String user) throws SQLException {
        for (String table : TABLES) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("REVOKE SELECT, INSERT, UPDATE, DELETE ON " + table + " FROM " + user);
            }
        }
        System.out.println("    Revoked access on tables");
    }

    // =========================
    // REGISTRY UPDATE
    // =========================

    private static void updateRegistry(Connection conn, RebalanceMove move, NodeConfig target)
            throws SQLException {
        conn.setAutoCommit(false);

        try {
            // Lock row for update
            String selectSql = "SELECT version FROM shard_registry WHERE shard = ? FOR UPDATE";
            int currentVersion;

            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, move.shardKey());
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new SQLException("Shard not found in registry: " + move.shardKey());
                }
                currentVersion = rs.getInt("version");
            }

            // Update with new location
            String updateSql = """
                UPDATE shard_registry 
                SET node = ?, host = ?, port = ?, version = ?
                WHERE shard = ?
            """;

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, move.toNode());
                ps.setString(2, target.host);
                ps.setInt(3, target.port);
                ps.setInt(4, currentVersion + 1);
                ps.setString(5, move.shardKey());
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("    Registry updated: version " + currentVersion + " → " + (currentVersion + 1));

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }

    // =========================
    // DATABASE SETUP
    // =========================

    private static void createDatabaseIfNotExists(String adminJdbc, String dbName, Env env) throws SQLException {
        try (Connection conn = DriverManager.getConnection(adminJdbc, env.dbUser, env.dbPassword)) {
            // Check if database exists
            String checkSql = "SELECT 1 FROM pg_database WHERE datname = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, dbName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    System.out.println("    Database " + dbName + " already exists on target");
                    return;
                }
            }

            // Create database (cannot be in transaction)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE " + dbName);
            }
            System.out.println("    Created database: " + dbName);
        }
    }

    private static void createSchemaOnTarget(Connection conn) throws SQLException {
        // Create extension
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        }

        // Create travel_plans table
        String createTravelPlans = """
            CREATE TABLE IF NOT EXISTS travel_plans (
                id UUID PRIMARY KEY,
                title VARCHAR(200) NOT NULL CHECK (LENGTH(TRIM(title)) > 0),
                description TEXT,
                start_date DATE,
                end_date DATE,
                budget DECIMAL(10,2),
                currency VARCHAR(3) DEFAULT 'USD' CHECK (LENGTH(currency) = 3),
                is_public BOOLEAN DEFAULT FALSE,
                version INTEGER NOT NULL DEFAULT 1,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                CONSTRAINT check_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
                CONSTRAINT check_budget CHECK (budget IS NULL OR budget >= 0)
            )
        """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTravelPlans);
        }

        // Create locations table
        String createLocations = """
            CREATE TABLE IF NOT EXISTS locations (
                id UUID PRIMARY KEY,
                travel_plan_id UUID NOT NULL REFERENCES travel_plans(id) ON DELETE CASCADE,
                name VARCHAR(200) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
                address TEXT,
                latitude DECIMAL(10, 6),
                longitude DECIMAL(11, 6),
                visit_order INTEGER CHECK (visit_order > 0),
                arrival_date TIMESTAMP WITH TIME ZONE,
                departure_date TIMESTAMP WITH TIME ZONE,
                budget DECIMAL(10,2),
                notes TEXT,
                version INTEGER NOT NULL DEFAULT 1,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                CONSTRAINT check_coordinates_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
                CONSTRAINT check_coordinates_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
                CONSTRAINT check_location_dates CHECK (departure_date IS NULL OR arrival_date IS NULL OR departure_date >= arrival_date),
                CONSTRAINT check_location_budget CHECK (budget IS NULL OR budget >= 0),
                CONSTRAINT unique_plan_order UNIQUE (travel_plan_id, visit_order)
            )
        """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createLocations);
        }

        // Create index
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_locations_plan_order ON locations(travel_plan_id, visit_order)");
        }

        System.out.println("    Schema created on target");
    }

    // =========================
    // HELPERS
    // =========================

    private static String buildConnectionString(String sourceNodeName, String database, Env env) {
        // Subscription runs INSIDE Docker container, so it needs Docker internal hostname
        // regardless of whether CLI uses external ports
        // postgres_00 -> postgres_00:5432 (Docker internal network)
        return String.format(
                "host=%s port=%d dbname=%s user=%s password=%s",
                sourceNodeName, 5432, database, env.dbUser, env.dbPassword
        );
    }
}