package com.gameflow.routing;

import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.ServerNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 3: Cost-Optimized Strategy
 * Finds all servers meeting a baseline SLA (latency < 60ms),
 * then picks the one with the lowest costPerHour.
 */
@Component
public class CostOptimizedStrategy implements RoutingStrategy {

    @Override
    public com.gameflow.model.RoutingStrategy getType() {
        return com.gameflow.model.RoutingStrategy.COST_OPTIMIZED;
    }

    @Override
    public CandidateScore evaluate(PlayerRequest request, ServerNode node, double playerLatencyMs, int candidateIndex, int candidateCount) {
        ServerMetrics m = node.getMetrics();
        
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("costPerHour", node.getCostPerHour());
        breakdown.put("latency", playerLatencyMs);
        
        String penaltyReason = Eligibility.check(node, m, playerLatencyMs);
        
        double score = node.getCostPerHour() + (playerLatencyMs / 1000.0);
        if (penaltyReason != null) {
            score += WeightedGamingStrategy.INELIGIBLE_PENALTY;
            breakdown.put("penalty", WeightedGamingStrategy.INELIGIBLE_PENALTY);
        } else if (playerLatencyMs >= 60.0) {
            // Soft SLA: prioritize servers under 60ms, but allow fallback if none available (<100ms ceiling)
            score += 15.0;
            breakdown.put("slaPenalty", 15.0);
        }

        CandidateScore candidate = new CandidateScore();
        candidate.setServerId(node.getId());
        candidate.setLatencyMs(WeightedGamingStrategy.round1(playerLatencyMs));
        candidate.setCpu(m.getCpu());
        candidate.setGpu(m.getGpu());
        candidate.setPacketLoss(m.getPacketLoss());
        candidate.setSessions(m.getSessions());
        candidate.setJitterMs(m.getJitterMs());
        candidate.setScore(WeightedGamingStrategy.round1(score));
        candidate.setScoreBreakdown(breakdown);
        candidate.setEligible(penaltyReason == null);
        candidate.setPenaltyReason(penaltyReason);
        return candidate;
    }
}
