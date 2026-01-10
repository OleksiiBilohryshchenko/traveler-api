package ua.sumdu.dds.travelerapi.sharding;

import java.util.Map;

/**
 * Complete shard configuration snapshot.
 */
public record ShardConfig(
        Map<String, ShardInfo> shards,
        int version
) {
    /**
     * Get shard by hex key.
     * @throws IllegalArgumentException if shard not found
     */
    public ShardInfo getShard(String hexKey) {
        ShardInfo info = shards.get(hexKey);
        if (info == null) {
            throw new IllegalArgumentException("Unknown shard: " + hexKey);
        }
        return info;
    }

    /**
     * Get number of shards.
     */
    public int size() {
        return shards.size();
    }
}
