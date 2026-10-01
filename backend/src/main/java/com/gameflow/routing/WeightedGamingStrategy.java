package com.gameflow.routing;

import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.ServerNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default strategy: composite gaming score.
 *
 * <pre>
 * score = norm(latency) * 0.35 + norm(cpu) * 0.20 + norm(gpu) * 0.20
 *       + norm(packetLoss) * 0.10 + norm(sessions) * 0.10 + norm(jitter) * 0.05
 * </pre>
 * scaled to 0–100, lower is better. Latency dominates because cloud
 * gaming is latency-sensitive. Ineligible servers take a +25 penalty.
 */
@Component
public class WeightedGamingStrategy implements RoutingStrategy {

    // Must be strictly greater than the maximum possible legitimate score (100.0),
    // so ineligible candidates always visually rank below any eligible server in the UI.
    static final double INELIGIBLE_PENALTY = 200.0;

    @Override
    public com.gameflow.model.RoutingStrategy getType() {
        return com.gameflow.model.RoutingStrategy.WEIGHTED_GAMING;
    }

    @Override
    public CandidateScore evaluate(PlayerRequest request, ServerNode node,
                                   double playerLatencyMs, int candidateIndex, int candidateCount) {
        ServerMetrics m = node.getMetrics();

        double lat = cap(playerLatencyMs / 100.0);
        double cpu = cap(m.getCpu() / 100.0);
        double gpu = cap(m.getGpu() / 100.0);
        double loss = cap(m.getPacketLoss() / 5.0);
        double sess = node.getCapacity() == 0 ? 1.0
                : cap((double) m.getSessions() / node.getCapacity());
        double jit = cap(m.getJitterMs() / 20.0);

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("latency", round1(lat * 35.0));
        breakdown.put("cpu", round1(cpu * 20.0));
        breakdown.put("gpu", round1(gpu * 20.0));
        breakdown.put("packetLoss", round1(loss * 10.0));
        breakdown.put("sessions", round1(sess * 10.0));
        breakdown.put("jitter", round1(jit * 5.0));

        String penaltyReason = Eligibility.check(node, m, playerLatencyMs);
        double penalty = 0.0;
        if (penaltyReason != null) {
            penalty = INELIGIBLE_PENALTY;
        }
        breakdown.put("penalty", penalty);

        double score = breakdown.values().stream().mapToDouble(Double::doubleValue).sum();

        CandidateScore candidate = new CandidateScore();
        candidate.setServerId(node.getId());
        candidate.setLatencyMs(round1(playerLatencyMs));
        candidate.setCpu(m.getCpu());
        candidate.setGpu(m.getGpu());
        candidate.setPacketLoss(m.getPacketLoss());
        candidate.setSessions(m.getSessions());
        candidate.setJitterMs(m.getJitterMs());
        candidate.setScore(round1(score));
        candidate.setScoreBreakdown(breakdown);
        candidate.setEligible(penaltyReason == null);
        candidate.setPenaltyReason(penaltyReason);
        return candidate;
    }

    private static double cap(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
