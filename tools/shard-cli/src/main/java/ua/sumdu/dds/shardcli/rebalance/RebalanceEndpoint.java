package ua.sumdu.dds.shardcli.rebalance;

public record RebalanceEndpoint(
        String node,
        String host,
        int port
) {}
