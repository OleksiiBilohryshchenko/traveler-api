package ua.sumdu.dds.travelerapi.sharding;

/**
 * Connection info for a single shard.
 */
public record ShardInfo(
        String host,
        int port,
        String database,
        String user,
        String password
) {
    /**
     * Build JDBC URL for this shard.
     */
    public String jdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }
}
