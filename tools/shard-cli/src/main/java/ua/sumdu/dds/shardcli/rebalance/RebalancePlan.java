package ua.sumdu.dds.shardcli.rebalance;

import java.util.List;

public record RebalancePlan(
        int version,
        List<RebalanceMove> moves
) {}
