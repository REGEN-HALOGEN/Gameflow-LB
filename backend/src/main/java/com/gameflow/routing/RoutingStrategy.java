package com.gameflow.routing;

import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.ServerNode;

/**
 * Pluggable routing strategy. Implementations score one candidate server;
 * lower score is better. The {@link RoutingEngine} applies exclusion rules,
 * picks the lowest eligible score, and records the decision.
 */
public interface RoutingStrategy {

    /** Which strategy this is (the model enum serialized into JSON). */
    com.gameflow.model.RoutingStrategy getType();

    /**
     * Score a single candidate.
     *
     * @param request        the incoming player request
     * @param node           balancer-side view of the server (with latest metrics)
     * @param playerLatencyMs player-specific latency to this server (matrix + faults)
     * @param candidateIndex position of this server in the evaluation order
     * @param candidateCount total candidates evaluated for this decision
     */
    CandidateScore evaluate(PlayerRequest request, ServerNode node,
                           double playerLatencyMs, int candidateIndex, int candidateCount);

    /** Called after each decision; default no-op (used by round-robin cursor). */
    default void afterDecision() {
    }
}
