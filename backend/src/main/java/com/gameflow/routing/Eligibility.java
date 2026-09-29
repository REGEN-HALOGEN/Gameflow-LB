package com.gameflow.routing;

import com.gameflow.model.CircuitState;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.ServerNode;
import com.gameflow.model.ServerState;

/**
 * Shared exclusion rules: conditions that remove a server from routing
 * eligibility entirely, with a human-readable penalty reason.
 */
public final class Eligibility {

    private Eligibility() {
    }

    /**
     * @return penalty reason if the server must be excluded, null if eligible.
     */
    public static String check(ServerNode node, ServerMetrics metrics, double playerLatencyMs) {
        if (node.getCircuitState() == CircuitState.OPEN) {
            return "CIRCUIT OPEN";
        }
        ServerState state = node.getState();
        if (state == ServerState.OFFLINE) {
            return "SERVER OFFLINE";
        }
        if (state == ServerState.UNHEALTHY) {
            return "SERVER UNHEALTHY";
        }
        if (state == ServerState.RECOVERING) {
            return "SERVER RECOVERING";
        }
        if (metrics.getGpu() > 90) {
            return "GPU SATURATION (" + (int) metrics.getGpu() + "%)";
        }
        if (metrics.getPacketLoss() > 5) {
            return String.format("PACKET LOSS %.1f%% > 5%%", metrics.getPacketLoss());
        }
        if (playerLatencyMs > 100) {
            return "LATENCY " + (int) playerLatencyMs + "ms > 100ms";
        }
        return null;
    }
}
