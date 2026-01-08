package ua.sumdu.dds.travelerapi.sharding;

import java.util.UUID;

public final class ShardRouter {

    private ShardRouter() {
    }

    /**
     * Routes UUID to shard name db_0 .. db_f
     * Uses last hex character of UUID.
     */
    public static String route(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }

        String uuid = id.toString().replace("-", "");
        char lastChar = uuid.charAt(uuid.length() - 1);

        int shardIndex = Character.digit(lastChar, 16);
        if (shardIndex < 0) {
            throw new IllegalStateException("Invalid hex char in UUID: " + lastChar);
        }

        return "db_" + Integer.toHexString(shardIndex);
    }
}
