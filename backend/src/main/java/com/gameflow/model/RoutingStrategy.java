package com.gameflow.model;

/**
 * Routing strategies available in the UI. Serialized as-is into JSON
 * (e.g. "WEIGHTED_GAMING").
 */
public enum RoutingStrategy {
    WEIGHTED_GAMING,
    LEAST_SESSIONS,
    LOWEST_LATENCY,
    ROUND_ROBIN,
    COST_OPTIMIZED
}
