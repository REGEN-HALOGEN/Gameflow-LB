package com.gameflow.routing;

import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.ServerNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routes to the lowest-latency eligible server.
 *
 * Score = playerLatencyMs + jitterMs * 0.1 (lower is better).
 * Uses {@link Eligibility#checkNoLatencyCap} so no server is hard-blocked
 * solely because its latency exceeds 100 ms — when all options are far away
 * the strategy must still pick the best available one.
 */
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

        // Raw latency score (ms) — lower is better.
        // Add a small jitter component so co-located servers with identical
        // base latency are ranked by stability rather than list order.
        double score = round1(playerLatencyMs + m.getJitterMs() * 0.1);

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("latency",    round1(playerLatencyMs));
        breakdown.put("jitter",     round1(m.getJitterMs() * 0.1));
        breakdown.put("cpu",        0.0);
        breakdown.put("gpu",        0.0);
        breakdown.put("packetLoss", 0.0);
        breakdown.put("sessions",   0.0);
        breakdown.put("penalty",    0.0);

        // Use checkNoLatencyCap: this strategy's job is comparative ranking,
        // never outright rejection on latency.
        String penaltyReason = Eligibility.checkNoLatencyCap(node, m);
        double finalScore = score;
        if (penaltyReason != null) {
            finalScore += WeightedGamingStrategy.INELIGIBLE_PENALTY;
            breakdown.put("penalty", WeightedGamingStrategy.INELIGIBLE_PENALTY);
        }

        CandidateScore candidate = new CandidateScore();
        candidate.setServerId(node.getId());
        candidate.setLatencyMs(round1(playerLatencyMs));
        candidate.setCpu(m.getCpu());
        candidate.setGpu(m.getGpu());
        candidate.setPacketLoss(m.getPacketLoss());
        candidate.setSessions(m.getSessions());
        candidate.setJitterMs(m.getJitterMs());
        candidate.setScore(round1(finalScore));
        candidate.setScoreBreakdown(breakdown);
        candidate.setEligible(penaltyReason == null);
        candidate.setPenaltyReason(penaltyReason);
        return candidate;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}

