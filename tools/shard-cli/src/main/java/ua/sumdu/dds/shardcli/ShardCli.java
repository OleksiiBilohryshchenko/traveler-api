package ua.sumdu.dds.shardcli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ua.sumdu.dds.shardcli.rebalance.RebalancePlan;
import ua.sumdu.dds.shardcli.rebalance.RebalanceRunner;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * CLI інструмент для керування shard-базами:
 *  - apply   : застосування SQL на всі шарди атомарно
 *  - status  : перевірка доступності шардів
 *  - rebalance : перенесення shard-DB між postgres-вузлами
 */
public class ShardCli {

    private static final String DEFAULT_MAPPING = "src/main/resources/mapping.json";
    private static final String DEFAULT_ENV = ".env";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];

        try {
            switch (command) {
                case "apply" -> handleApply(args);
                case "status" -> handleStatus(args);
                case "rebalance" -> handleRebalance(args);
                case "help", "--help", "-h" -> printUsage();
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // =========================
    // REBALANCE
    // =========================

    private static void handleRebalance(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("ERROR: rebalance plan file required");
            System.err.println("Usage: rebalance <plan.json> [--dry-run] [--env .env]");
            System.exit(1);
        }

        String planPath = args[1];
        boolean dryRun = Arrays.asList(args).contains("--dry-run");

        String envPath = extractOption(args, "--env", ".env");
        System.setProperty("SHARDCLI_ENV", envPath);

        System.out.println("=== Shard CLI: Rebalance ===");
        System.out.println("Plan: " + planPath);
        System.out.println("Dry-run: " + dryRun);
        System.out.println("Env: " + envPath);
        System.out.println();

        RebalancePlan plan = RebalanceRunner.loadPlan(planPath);
        RebalanceRunner.run(plan, dryRun);
    }

    // =========================
    // APPLY
    // =========================

    private static void handleApply(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("ERROR: Script file required");
            System.err.println("Usage: apply <script.sql> [--mapping mapping.json] [--env .env]");
            System.exit(1);
        }

        String scriptPath = args[1];
        String mappingPath = extractOption(args, "--mapping", DEFAULT_MAPPING);
        String envPath = extractOption(args, "--env", DEFAULT_ENV);

        String sql = Files.readString(Path.of(scriptPath));
        if (sql.isBlank()) {
            throw new RuntimeException("Script file is empty");
        }

        System.out.println("=== Shard CLI: Apply Script ===");
        System.out.println("Script: " + scriptPath);
        System.out.println("Mapping: " + mappingPath);
        System.out.println("Env: " + envPath);
        System.out.println();

        Credentials creds = loadCredentials(envPath);
        ShardConfig config = loadConfig(mappingPath, creds);

        applyToAllShards(config, sql);
    }

    // =========================
    // STATUS
    // =========================

    private static void handleStatus(String[] args) throws Exception {
        String mappingPath = extractOption(args, "--mapping", DEFAULT_MAPPING);
        String envPath = extractOption(args, "--env", DEFAULT_ENV);

        System.out.println("=== Shard CLI: Status ===");
        System.out.println("Mapping: " + mappingPath);
        System.out.println("Env: " + envPath);
        System.out.println();

        Credentials creds = loadCredentials(envPath);
        ShardConfig config = loadConfig(mappingPath, creds);

        System.out.printf("%-8s %-15s %-6s %-10s %s%n",
                "SHARD", "HOST", "PORT", "DATABASE", "STATUS");
        System.out.println("-".repeat(60));

        for (ShardInfo shard : config.shards) {
            String status = checkConnection(shard, config) ? "✓ OK" : "✗ FAIL";
            System.out.printf("%-8s %-15s %-6d %-10s %s%n",
                    shard.key, shard.host, shard.port, shard.database, status);
        }
    }

    // =========================
    // CORE LOGIC
    // =========================

    private static void applyToAllShards(ShardConfig config, String sql) throws Exception {
        List<Connection> connections = new ArrayList<>();

        try {
            for (ShardInfo shard : config.shards) {
                String url = String.format("jdbc:postgresql://%s:%d/%s",
                        shard.host, shard.port, shard.database);
                Connection conn = DriverManager.getConnection(url, config.username, config.password);
                conn.setAutoCommit(false);
                connections.add(conn);
            }

            for (int i = 0; i < connections.size(); i++) {
                try (Statement stmt = connections.get(i).createStatement()) {
                    stmt.execute(sql);
                }
            }

            for (Connection conn : connections) {
                conn.commit();
            }

            System.out.println("SUCCESS: Script applied to all shards");

        } catch (Exception e) {
            for (Connection conn : connections) {
                try {
                    if (conn != null && !conn.isClosed()) conn.rollback();
                } catch (Exception ignored) {}
            }
            throw e;
        } finally {
            for (Connection conn : connections) {
                try {
                    if (conn != null && !conn.isClosed()) conn.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static boolean checkConnection(ShardInfo shard, ShardConfig config) {
        String url = String.format("jdbc:postgresql://%s:%d/%s",
                shard.host, shard.port, shard.database);
        try (Connection conn = DriverManager.getConnection(url, config.username, config.password)) {
            return conn.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }

    // =========================
    // CONFIG
    // =========================

    private static Credentials loadCredentials(String envPath) throws Exception {
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (user == null || password == null) {
            Properties props = new Properties();
            try (BufferedReader r = new BufferedReader(new FileReader(envPath))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("#") || line.isBlank()) continue;
                    int i = line.indexOf('=');
                    if (i > 0) props.setProperty(line.substring(0, i), line.substring(i + 1));
                }
            }
            user = props.getProperty("DB_USER", "postgres");
            password = props.getProperty("DB_PASSWORD");
        }

        return new Credentials(user, password);
    }

    private static ShardConfig loadConfig(String mappingPath, Credentials creds) throws Exception {
        JsonNode root = mapper.readTree(new File(mappingPath));
        JsonNode shardsNode = root.get("shards");

        List<String> keys = new ArrayList<>();
        shardsNode.fieldNames().forEachRemaining(keys::add);
        keys.sort(Comparator.comparingInt(k -> Integer.parseInt(k, 16)));

        ShardConfig cfg = new ShardConfig();
        cfg.username = creds.username;
        cfg.password = creds.password;
        cfg.shards = new ArrayList<>();

        for (String key : keys) {
            JsonNode n = shardsNode.get(key);
            ShardInfo s = new ShardInfo();
            s.key = key;
            s.host = n.get("host").asText();
            s.port = n.get("port").asInt();
            s.database = n.get("database").asText();
            s.node = n.get("node").asText();
            cfg.shards.add(s);
        }
        return cfg;
    }

    private static String extractOption(String[] args, String opt, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(opt)) return args[i + 1];
        }
        return def;
    }

    private static void printUsage() {
        System.out.println("""
            Usage:
              shard-cli apply <script.sql>
              shard-cli status
              shard-cli rebalance <plan.json> [--dry-run]
            """);
    }

    // =========================
    // DATA
    // =========================

    record Credentials(String username, String password) {}

    static class ShardConfig {
        String username;
        String password;
        List<ShardInfo> shards;
    }

    static class ShardInfo {
        String key;
        String host;
        int port;
        String database;
        String node;
    }
}
