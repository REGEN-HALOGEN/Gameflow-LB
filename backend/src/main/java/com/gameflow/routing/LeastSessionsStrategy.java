package com.gameflow.routing;

import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.ServerNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Routes to the server with the fewest active sessions relative to capacity. */
@Component
public class LeastSessionsStrategy implements RoutingStrategy {

    @Override
    public com.gameflow.model.RoutingStrategy getType() {
        return com.gameflow.model.RoutingStrategy.LEAST_SESSIONS;
    }

    @Override
    public CandidateScore evaluate(PlayerRequest request, ServerNode node,
                                   double playerLatencyMs, int candidateIndex, int candidateCount) {
        ServerMetrics m = node.getMetrics();
        double norm = node.getCapacity() == 0 ? 1.0
                : Math.max(0.0, Math.min(1.0, (double) m.getSessions() / node.getCapacity()));
        double score = WeightedGamingStrategy.round1(norm * 100.0);

        Map<String, Double> breakdown = zeroBreakdown();
        breakdown.put("sessions", score);

        return build(node, m, playerLatencyMs, score, breakdown);
    }

    static Map<String, Double> zeroBreakdown() {
        Map<String, Double> b = new LinkedHashMap<>();
        b.put("latency", 0.0);
        b.put("cpu", 0.0);
        b.put("gpu", 0.0);
        b.put("packetLoss", 0.0);
        b.put("sessions", 0.0);
        b.put("jitter", 0.0);
        b.put("penalty", 0.0);
        return b;
    }

    static CandidateScore build(ServerNode node, ServerMetrics m, double playerLatencyMs,
                                double score, Map<String, Double> breakdown) {
        String penaltyReason = Eligibility.check(node, m, playerLatencyMs);
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
