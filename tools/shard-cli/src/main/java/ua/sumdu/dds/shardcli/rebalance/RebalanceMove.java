package ua.sumdu.dds.shardcli.rebalance;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single shard move operation.
 * JSON format: { "db": "db_0", "from": "postgres_00", "to": "postgres_01" }
 */
public record RebalanceMove(
        @JsonProperty("db") String database,
        @JsonProperty("from") String fromNode,
        @JsonProperty("to") String toNode
) {
    /**
     * Extracts shard key (0-f) from database name (db_0 -> 0, db_a -> a)
     */
    public String shardKey() {
        if (database == null || !database.startsWith("db_")) {
            throw new IllegalStateException("Invalid database name: " + database);
        }
        return database.substring(3); // "db_0" -> "0"
    }
}
