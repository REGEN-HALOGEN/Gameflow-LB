package com.gameflow.simulation;

import java.util.List;

/**
 * A user-defined scenario: an ordered list of steps executed on a wall-clock
 * timeline by {@link ScenarioEngine}. Unlike the built-in demo scenarios,
 * custom scenarios are created at runtime via the REST API and can target
 * any server, set traffic, switch routing strategy, and pace themselves
 * with WAIT steps.
 */
public record CustomScenario(
        String id,
        String name,
        String description,
        List<Step> steps,
        long createdAt) {

    public enum StepType {
        /** Pause the timeline for {@link Step#seconds}. */
        WAIT,
        /** Set the traffic generator's target session count. */
        SET_TRAFFIC,
        /** Inject a fault on a server. */
        FAULT,
        /** Recover a server (clear faults, restore RMI, start health probes). */
        RECOVER,
        /** Switch the routing engine's active strategy. */
        STRATEGY
    }

    /**
     * One timeline step. Only the fields relevant to {@link Step#type} are
     * required; the rest must be null:
     * <ul>
     *   <li>WAIT — {@code seconds} (1..600)</li>
     *   <li>SET_TRAFFIC — {@code targetSessions} (0..1000)</li>
     *   <li>FAULT — {@code serverId} + {@code faultType}</li>
     *   <li>RECOVER — {@code serverId}</li>
     *   <li>STRATEGY — {@code strategy}</li>
     * </ul>
     */
    public record Step(
            StepType type,
            Double seconds,
            Integer targetSessions,
            String serverId,
            com.gameflow.model.FaultType faultType,
            com.gameflow.model.RoutingStrategy strategy) {
    }
}
