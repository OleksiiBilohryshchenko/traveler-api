package ua.sumdu.dds.shardcli.rebalance;

import java.util.List;

/**
 * Rebalance plan containing list of shard moves.
 * JSON format: { "moves": [ ... ] }
 */
public record RebalancePlan(
        List<RebalanceMove> moves
) {
    public RebalancePlan {
        if (moves == null || moves.isEmpty()) {
            throw new IllegalArgumentException("Plan must contain at least one move");
        }
    }
}
