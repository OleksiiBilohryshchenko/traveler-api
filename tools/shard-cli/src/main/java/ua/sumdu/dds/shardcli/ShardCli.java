package ua.sumdu.dds.shardcli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * CLI інструмент для застосування DDL/DML скриптів на всіх 16 шардах.
 *
 * Credentials читаються з:
 *   1. Environment variables: DB_USER, DB_PASSWORD
 *   2. .env файл в корені проєкту
 *
 * Використання:
 *   ./shard-cli.sh apply <script.sql> [--mapping mapping.json] [--env .env]
 *   ./shard-cli.sh status [--mapping mapping.json] [--env .env]
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

    private static void handleApply(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("ERROR: Script file required");
            System.err.println("Usage: apply <script.sql> [--mapping mapping.json] [--env .env]");
            System.exit(1);
        }

        String scriptPath = args[1];
        String mappingPath = extractOption(args, "--mapping", DEFAULT_MAPPING);
        String envPath = extractOption(args, "--env", DEFAULT_ENV);

        // Читаємо SQL скрипт
        String sql = Files.readString(Path.of(scriptPath));
        if (sql.isBlank()) {
            System.err.println("ERROR: Script file is empty");
            System.exit(1);
        }

        System.out.println("=== Shard CLI: Apply Script ===");
        System.out.println("Script: " + scriptPath);
        System.out.println("Mapping: " + mappingPath);
        System.out.println("Env: " + envPath);
        System.out.println();

        // Завантажуємо конфігурацію
        Credentials creds = loadCredentials(envPath);
        ShardConfig config = loadConfig(mappingPath, creds);

        System.out.println("User: " + creds.username);
        System.out.println();

        // Застосовуємо скрипт атомарно
        applyToAllShards(config, sql);
    }

    private static void handleStatus(String[] args) throws Exception {
        String mappingPath = extractOption(args, "--mapping", DEFAULT_MAPPING);
        String envPath = extractOption(args, "--env", DEFAULT_ENV);

        System.out.println("=== Shard CLI: Status ===");
        System.out.println("Mapping: " + mappingPath);
        System.out.println("Env: " + envPath);
        System.out.println();

        Credentials creds = loadCredentials(envPath);
        ShardConfig config = loadConfig(mappingPath, creds);

        System.out.println("User: " + creds.username);
        System.out.println();

        System.out.printf("%-8s %-15s %-6s %-10s %s%n", "SHARD", "HOST", "PORT", "DATABASE", "STATUS");
        System.out.println("-".repeat(60));

        for (ShardInfo shard : config.shards) {
            String status = checkConnection(shard, config) ? "✓ OK" : "✗ FAIL";
            System.out.printf("%-8s %-15s %-6d %-10s %s%n",
                    shard.key, shard.host, shard.port, shard.database, status);
        }
    }

    private static void applyToAllShards(ShardConfig config, String sql) throws Exception {
        List<Connection> connections = new ArrayList<>();
        List<String> successfulShards = new ArrayList<>();

        try {
            // Фаза 1: Відкриваємо з'єднання та транзакції на ВСІХ шардах
            System.out.println("Phase 1: Opening connections to all 16 shards...");

            for (ShardInfo shard : config.shards) {
                String url = String.format("jdbc:postgresql://%s:%d/%s",
                        shard.host, shard.port, shard.database);

                Connection conn = DriverManager.getConnection(url, config.username, config.password);
                conn.setAutoCommit(false); // Починаємо транзакцію
                connections.add(conn);

                System.out.printf("  [%s] Connected to %s%n", shard.key, shard.database);
            }

            System.out.println("  All connections established.\n");

            // Фаза 2: Виконуємо скрипт на кожному шарді
            System.out.println("Phase 2: Executing script on all shards...");

            for (int i = 0; i < config.shards.size(); i++) {
                ShardInfo shard = config.shards.get(i);
                Connection conn = connections.get(i);

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                    successfulShards.add(shard.key);
                    System.out.printf("  [%s] Script executed successfully%n", shard.key);
                } catch (Exception e) {
                    System.err.printf("  [%s] FAILED: %s%n", shard.key, e.getMessage());
                    throw new RuntimeException("Script failed on shard " + shard.key + ": " + e.getMessage(), e);
                }
            }

            System.out.println("  All shards executed successfully.\n");

            // Фаза 3: COMMIT на всіх шардах
            System.out.println("Phase 3: Committing transactions...");

            for (int i = 0; i < connections.size(); i++) {
                Connection conn = connections.get(i);
                ShardInfo shard = config.shards.get(i);
                conn.commit();
                System.out.printf("  [%s] Committed%n", shard.key);
            }

            System.out.println("\n=== SUCCESS: Script applied to all 16 shards ===");

        } catch (Exception e) {
            // ROLLBACK на всіх шардах при помилці
            System.err.println("\n!!! ERROR DETECTED - Rolling back all shards !!!");

            for (int i = 0; i < connections.size(); i++) {
                try {
                    Connection conn = connections.get(i);
                    if (conn != null && !conn.isClosed()) {
                        conn.rollback();
                        System.out.printf("  [%s] Rolled back%n", config.shards.get(i).key);
                    }
                } catch (Exception rollbackEx) {
                    System.err.printf("  [%s] Rollback failed: %s%n",
                            config.shards.get(i).key, rollbackEx.getMessage());
                }
            }

            throw e;

        } finally {
            // Закриваємо всі з'єднання
            for (Connection conn : connections) {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private static boolean checkConnection(ShardInfo shard, ShardConfig config) {
        String url = String.format("jdbc:postgresql://%s:%d/%s",
                shard.host, shard.port, shard.database);

        try (Connection conn = DriverManager.getConnection(url, config.username, config.password)) {
            return conn.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Завантажує credentials з ENV змінних або .env файлу
     */
    private static Credentials loadCredentials(String envPath) throws Exception {
        // Спочатку перевіряємо ENV змінні
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        // Якщо немає в ENV — читаємо з .env файлу
        if (user == null || password == null) {
            File envFile = new File(envPath);
            if (!envFile.exists()) {
                throw new RuntimeException("No DB_USER/DB_PASSWORD in environment and .env file not found: " + envPath);
            }

            Properties props = new Properties();
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Пропускаємо коментарі та пусті рядки
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    int idx = line.indexOf('=');
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        props.setProperty(key, value);
                    }
                }
            }

            user = props.getProperty("DB_USER", "postgres");
            password = props.getProperty("DB_PASSWORD");

            if (password == null) {
                throw new RuntimeException("DB_PASSWORD not found in .env file");
            }
        }

        return new Credentials(user, password);
    }

    private static ShardConfig loadConfig(String mappingPath, Credentials creds) throws Exception {
        File file = new File(mappingPath);
        if (!file.exists()) {
            throw new RuntimeException("Mapping file not found: " + mappingPath);
        }

        JsonNode root = mapper.readTree(file);
        JsonNode shardsNode = root.get("shards");

        ShardConfig config = new ShardConfig();
        config.username = creds.username;
        config.password = creds.password;
        config.shards = new ArrayList<>();

        // Сортуємо ключі: 0-9, a-f
        List<String> keys = new ArrayList<>();
        shardsNode.fieldNames().forEachRemaining(keys::add);
        keys.sort((a, b) -> {
            int aVal = Integer.parseInt(a, 16);
            int bVal = Integer.parseInt(b, 16);
            return Integer.compare(aVal, bVal);
        });

        for (String key : keys) {
            JsonNode shardNode = shardsNode.get(key);
            ShardInfo shard = new ShardInfo();
            shard.key = key;
            shard.host = shardNode.get("host").asText();
            shard.port = shardNode.get("port").asInt();
            shard.database = shardNode.get("database").asText();
            shard.node = shardNode.get("node").asText();
            config.shards.add(shard);
        }

        return config;
    }

    private static String extractOption(String[] args, String option, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(option)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    private static void printUsage() {
        System.out.println("""
            Shard CLI - Apply scripts to all database shards

            Usage:
              ./shard-cli.sh <command> [options]

            Commands:
              apply <script.sql>    Apply SQL script to all 16 shards atomically
              status                Check connection status of all shards
              help                  Show this help message

            Options:
              --mapping <file>      Path to mapping.json (default: src/main/resources/mapping.json)
              --env <file>          Path to .env file (default: .env)

            Credentials:
              Reads DB_USER and DB_PASSWORD from:
                1. Environment variables (if set)
                2. .env file in project root

            Examples:
              ./shard-cli.sh apply db/sharding/init-shard-schema.sql
              ./shard-cli.sh status
              ./shard-cli.sh apply schema.sql --env .env.production

            Notes:
              - The apply command opens transactions on ALL shards before executing
              - COMMIT happens only if ALL shards succeed
              - On any failure, ALL shards are rolled back
            """);
    }

    // Inner classes
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