package com.gameflow.routing;

import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.ServerNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Routes to the lowest-latency eligible server. */
@Component
public class LowestLatencyStrategy implements RoutingStrategy {

    @Override
    public com.gameflow.model.RoutingStrategy getType() {
        return com.gameflow.model.RoutingStrategy.LOWEST_LATENCY;
    }

    @Override
    public CandidateScore evaluate(PlayerRequest request, ServerNode node,
                                   double playerLatencyMs, int candidateIndex, int candidateCount) {
        ServerMetrics m = node.getMetrics();
        double norm = Math.max(0.0, Math.min(1.0, playerLatencyMs / 100.0));
        double score = round1(norm * 100.0);

        Map<String, Double> breakdown = LeastSessionsStrategy.zeroBreakdown();
        breakdown.put("latency", score);

        return LeastSessionsStrategy.build(node, m, playerLatencyMs, score, breakdown);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
