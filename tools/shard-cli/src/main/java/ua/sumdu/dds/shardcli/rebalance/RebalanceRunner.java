package ua.sumdu.dds.shardcli.rebalance;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.sql.*;
import java.util.List;
import java.util.Properties;

public final class RebalanceRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RebalanceRunner() {}

    /* =========================
       ENV
       ========================= */

    static final class Env {
        final String dbUser;
        final String dbPassword;
        final String registryJdbc;

        private Env(String dbUser, String dbPassword, String registryJdbc) {
            this.dbUser = dbUser;
            this.dbPassword = dbPassword;
            this.registryJdbc = registryJdbc;
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
            String jdbc = env("SHARD_REGISTRY_JDBC", props, null);

            if (pass == null || pass.isBlank())
                throw new RuntimeException("DB_PASSWORD not set");
            if (jdbc == null || jdbc.isBlank())
                throw new RuntimeException("SHARD_REGISTRY_JDBC not set");

            return new Env(user, pass, jdbc);
        }

        private static String env(String key, Properties p, String def) {
            String v = System.getenv(key);
            if (v != null) return v;
            return p.getProperty(key, def);
        }
    }

    /* =========================
       PLAN
       ========================= */

    public static RebalancePlan loadPlan(String planPath) throws Exception {
        File f = new File(planPath);
        if (!f.exists())
            throw new IllegalArgumentException("Plan file not found: " + planPath);
        return MAPPER.readValue(f, RebalancePlan.class);
    }

    /* =========================
       RUN
       ========================= */

    public static void run(RebalancePlan plan, boolean dryRun) {
        if (plan.moves() == null || plan.moves().isEmpty())
            throw new IllegalArgumentException("Plan has no moves");

        Env env = Env.load();

        System.out.println("=== Rebalance ===");
        System.out.println("Plan version: " + plan.version());
        System.out.println("Moves: " + plan.moves().size());
        System.out.println("Dry-run: " + dryRun);
        System.out.println("Registry JDBC: " + env.registryJdbc);
        System.out.println();

        try (Connection c = DriverManager.getConnection(
                env.registryJdbc, env.dbUser, env.dbPassword)) {

            c.setAutoCommit(false);

            for (RebalanceMove m : plan.moves()) {
                applyMove(c, m, dryRun);
            }

            if (dryRun) {
                c.rollback();
                System.out.println("\nDRY-RUN завершено (ROLLBACK)");
            } else {
                c.commit();
                System.out.println("\nREBALANCE ЗАВЕРШЕНО (COMMIT)");
            }

        } catch (Exception e) {
            throw new RuntimeException("Rebalance failed", e);
        }
    }

    /* =========================
       MOVE
       ========================= */

    private static void applyMove(Connection c, RebalanceMove m, boolean dryRun) throws Exception {
        System.out.printf(
                "- shard=%s db=%s %s -> %s%n",
                m.shard(),
                m.database(),
                m.from().node(),
                m.to().node()
        );

        PreparedStatement ps = c.prepareStatement("""
            SELECT node, version
              FROM shard_registry
             WHERE shard = ?
             FOR UPDATE
        """);
        ps.setString(1, m.shard());

        ResultSet rs = ps.executeQuery();
        if (!rs.next())
            throw new IllegalStateException("Shard not found in registry: " + m.shard());

        String currentNode = rs.getString("node");
        int version = rs.getInt("version");

        if (!currentNode.equals(m.from().node())) {
            throw new IllegalStateException(
                    "Shard " + m.shard() + " expected on " +
                            m.from().node() + " but found on " + currentNode
            );
        }

        System.out.printf(
                "  registry: node=%s version=%d -> %s%n",
                currentNode, version, m.to().node()
        );

        if (!dryRun) {
            PreparedStatement upd = c.prepareStatement("""
                UPDATE shard_registry
                   SET node = ?,
                       host = ?,
                       port = ?,
                       database = ?,
                       version = ?
                 WHERE shard = ?
            """);

            upd.setString(1, m.to().node());
            upd.setString(2, m.to().host());
            upd.setInt(3, m.to().port());
            upd.setString(4, m.database());
            upd.setInt(5, version + 1);
            upd.setString(6, m.shard());

            upd.executeUpdate();
        }
    }
}
