package ua.sumdu.dds.shardcli.rebalance;

public record RebalanceMove(
        String shard,
        String database,
        RebalanceEndpoint from,
        RebalanceEndpoint to
) {}
