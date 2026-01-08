package ua.sumdu.dds.travelerapi.sharding;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

public final class ShardConfigLoader {

    private static final ShardConfig CONFIG = load();

    private ShardConfigLoader() {}

    private static ShardConfig load() {
        try (InputStream is =
                 ShardConfigLoader.class
                     .getClassLoader()
                     .getResourceAsStream("mapping.json")) {

            if (is == null) {
                throw new IllegalStateException("mapping.json not found in resources");
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(is, ShardConfig.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load shard mapping", e);
        }
    }

    public static ShardInfo getShard(String hexKey) {
        ShardInfo info = CONFIG.shards().get(hexKey);
        if (info == null) {
            throw new IllegalStateException("Unknown shard: " + hexKey);
        }
        return info;
    }
}
