package ua.sumdu.dds.travelerapi.sharding;

import java.util.Map;

public record ShardConfig(
        Map<String, ShardInfo> shards,
        int version
) {}
