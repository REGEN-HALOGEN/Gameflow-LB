package com.gameflow.routing;

import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.ServerNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classic round robin over the candidate list. The cursor advances after
 * every decision; the server at the cursor scores 0, the next 5, etc.
 * Ineligible servers are still skipped by the engine.
 */
@Component
public class RoundRobinStrategy implements RoutingStrategy {

    private final AtomicInteger cursor = new AtomicInteger(0);

    @Override
    public com.gameflow.model.RoutingStrategy getType() {
        return com.gameflow.model.RoutingStrategy.ROUND_ROBIN;
    }

    @Override
    public CandidateScore evaluate(PlayerRequest request, ServerNode node,
                                   double playerLatencyMs, int candidateIndex, int candidateCount) {
        ServerMetrics m = node.getMetrics();
        int n = Math.max(1, candidateCount);
        int rank = Math.floorMod(candidateIndex - cursor.get(), n);
        double score = WeightedGamingStrategy.round1(rank * 5.0);

        Map<String, Double> breakdown = LeastSessionsStrategy.zeroBreakdown();
        breakdown.put("rotation", score);

        return LeastSessionsStrategy.build(node, m, playerLatencyMs, score, breakdown);
    }

    @Override
    public void afterDecision() {
        cursor.incrementAndGet();
    }

    /** Visible for tests. */
    int getCursor() {
        return cursor.get();
    }
}
