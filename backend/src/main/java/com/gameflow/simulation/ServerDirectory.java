package com.gameflow.simulation;

import com.gameflow.model.CircuitState;
import com.gameflow.model.RmiStatus;
import com.gameflow.model.ServerNode;
import com.gameflow.model.ServerState;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The load balancer's registry of known game-server nodes (balancer-side view).
 * Health, circuit and metric state mutate here; identity fields are fixed.
 */
@Component
public class ServerDirectory {

    private final Map<String, ServerNode> nodes = java.util.Collections.synchronizedMap(new LinkedHashMap<>());

    public ServerDirectory() {
        add(new ServerNode("GS-MUM-01", "MUMBAI", "Mumbai", 60, 2.50, "RTX_3080"));
        add(new ServerNode("GS-MUM-02", "MUMBAI", "Mumbai", 60, 2.50, "RTX_3080"));
        add(new ServerNode("GS-SIN-01", "SINGAPORE", "Singapore", 80, 5.00, "RTX_4090"));
        add(new ServerNode("GS-BLR-01", "BANGALORE", "Bangalore", 60, 2.50, "RTX_3080"));
    }

    public void add(ServerNode node) {
        nodes.put(node.getId(), node);
    }

    public void remove(String id) {
        nodes.remove(id);
    }

    public Collection<ServerNode> all() {
        synchronized (nodes) {
            return new java.util.ArrayList<>(nodes.values());
        }
    }

    public ServerNode get(String id) {
        return nodes.get(id);
    }

    /** Base self-measured latency per server, before player geography. */
    public static double baseLatencyFor(String serverId) {
        return switch (serverId) {
            case "GS-SIN-01" -> 12.0;
            case "GS-BLR-01" -> 10.0;
            default -> 15.0; // GS-MUM-*
        };
    }

    /** Full reset of balancer-side node state (used by simulation reset). */
    public void resetAll() {
        for (ServerNode node : nodes.values()) {
            node.setState(ServerState.HEALTHY);
            node.setCircuitState(CircuitState.CLOSED);
            node.setRmiStatus(RmiStatus.CONNECTED);
            node.setWeight(1.0);
            node.refreshEligibility();
        }
    }
}
