package com.gameflow.simulation;

import com.gameflow.events.EventBus;
import com.gameflow.health.CircuitBreakerRegistry;
import com.gameflow.model.ServerNode;
import com.gameflow.model.ServerState;
import com.gameflow.model.SimulationState;
import com.gameflow.rmi.GameServerRegistry;
import com.gameflow.websocket.WsEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Monitors cluster load and dynamically adds or removes servers.
 */
@Component
public class AutoScaler {

    private static final Logger log = LoggerFactory.getLogger(AutoScaler.class);

    private final ServerDirectory directory;
    private final GameServerRegistry registry;
    private final CircuitBreakerRegistry breakers;
    private final EventBus events;
    private final WsEventPublisher ws;

    private int scaleUpTicks = 0;
    private int scaleDownTicks = 0;
    private final AtomicInteger nextId = new AtomicInteger(1);
    
    // Track servers that are currently being drained and are pending removal.
    private final Set<String> pendingRemoval = new HashSet<>();

    public AutoScaler(ServerDirectory directory,
                      GameServerRegistry registry,
                      CircuitBreakerRegistry breakers,
                      EventBus events,
                      WsEventPublisher ws) {
        this.directory = directory;
        this.registry = registry;
        this.breakers = breakers;
        this.events = events;
        this.ws = ws;
    }

    public void evaluate(SimulationState state) {
        if (state != SimulationState.RUNNING) {
            scaleUpTicks = 0;
            scaleDownTicks = 0;
            return;
        }

        long totalCapacity = 0;
        long totalSessions = 0;

        for (ServerNode node : directory.all()) {
            String id = node.getId();
            
            // If this node is pending removal, check if it's safe to destroy
            if (pendingRemoval.contains(id)) {
                if (node.getMetrics() != null && node.getMetrics().getSessions() == 0) {
                    directory.remove(id);
                    registry.unregisterServer(id);
                    breakers.unregister(id);
                    pendingRemoval.remove(id);
                    events.info("SERVER_REMOVED", "Scale down complete, removed " + id, id);
                    ws.publish("SERVER_REMOVED", Map.of("serverId", id));
                }
                continue; // don't count its capacity
            }

            if (node.getState() == ServerState.HEALTHY || node.getState() == ServerState.DEGRADED) {
                totalCapacity += node.getCapacity();
            }
            if (node.getMetrics() != null) {
                totalSessions += node.getMetrics().getSessions();
            }
        }

        if (totalCapacity == 0) return;

        double util = (double) totalSessions / totalCapacity;

        if (util > 0.85) {
            scaleUpTicks++;
            if (scaleUpTicks >= 3) { // 6 seconds sustained high load
                scaleUpTicks = 0;
                scaleUp();
            }
        } else {
            scaleUpTicks = 0;
        }

        if (util < 0.30 && directory.all().size() > 4) { // keep at least 4 servers
            scaleDownTicks++;
            if (scaleDownTicks >= 3) {
                scaleDownTicks = 0;
                scaleDown();
            }
        } else {
            scaleDownTicks = 0;
        }
    }

    private void scaleUp() {
        int idx = 2 + nextId.getAndIncrement();
        String id = "GS-MUM-0" + idx; // e.g. GS-MUM-03
        boolean need4090 = (idx % 2 == 1);
        String tier = need4090 ? "RTX_4090" : "RTX_3080";
        double cost = need4090 ? 4.50 : 2.50;
        ServerNode newNode = new ServerNode(id, "MUMBAI", "Mumbai", 60, cost, tier);
        directory.add(newNode);
        registry.registerNewServer(id);
        breakers.register(id, events, ws);
        events.info("SERVER_ADDED", "Scale up triggered, added " + id + " (" + tier + ")", id);
        ws.publish("SERVER_ADDED", Map.of("server", newNode));
    }

    private void scaleDown() {
        ServerNode leastUsed = null;
        for (ServerNode node : directory.all()) {
            if (pendingRemoval.contains(node.getId())) continue;
            
            if (node.getState() == ServerState.HEALTHY) {
                if (leastUsed == null) {
                    leastUsed = node;
                } else if (node.getMetrics() != null && leastUsed.getMetrics() != null) {
                    if (node.getMetrics().getSessions() < leastUsed.getMetrics().getSessions()) {
                        leastUsed = node;
                    }
                }
            }
        }
        
        if (leastUsed != null) {
            pendingRemoval.add(leastUsed.getId());
            leastUsed.setState(ServerState.DRAINING);
            leastUsed.setWeight(0);
            leastUsed.refreshEligibility();
            events.info("SERVER_DRAINING", "Scale down triggered, draining " + leastUsed.getId(), leastUsed.getId());
            ws.publish("SERVER_STATE_CHANGED", Map.of(
                    "serverId", leastUsed.getId(),
                    "oldState", "HEALTHY",
                    "newState", "DRAINING"));
        }
    }
    
    public void reset() {
        pendingRemoval.clear();
        scaleUpTicks = 0;
        scaleDownTicks = 0;
    }
}
