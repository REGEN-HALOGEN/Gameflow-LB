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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

        // BUG-2 fix: collect completed-drain IDs first, remove after iteration
        // to avoid mutating pendingRemoval while iterating over it.
        List<String> completedDrains = new ArrayList<>();

        for (ServerNode node : directory.all()) {
            String id = node.getId();

            // If this node is pending removal, check if it's safe to destroy
            if (pendingRemoval.contains(id)) {
                if (node.getMetrics() != null && node.getMetrics().getSessions() == 0) {
                    completedDrains.add(id);
                } else {
                    // Count DRAINING capacity so its remaining sessions don't falsely
                    // deflate utilization and trigger another cascading drain.
                    totalCapacity += node.getCapacity();
                    if (node.getMetrics() != null) {
                        totalSessions += node.getMetrics().getSessions();
                    }
                }
                continue;
            }

            if (node.getState() == ServerState.HEALTHY || node.getState() == ServerState.DEGRADED) {
                totalCapacity += node.getCapacity();
            }
            if (node.getMetrics() != null) {
                totalSessions += node.getMetrics().getSessions();
            }
        }

        // Safe to mutate pendingRemoval now that iteration is complete.
        for (String id : completedDrains) {
            directory.remove(id);
            registry.unregisterServer(id);
            breakers.unregister(id);
            pendingRemoval.remove(id);
            events.info("SERVER_REMOVED", "Scale down complete, removed " + id, id);
            ws.publish("SERVER_REMOVED", Map.of("serverId", id));
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

        // Only drain when truly under-utilized: <20% util sustained for 10s.
        // Guard against cascade: count only HEALTHY/DEGRADED servers (i.e. those
        // actually routing traffic), not ones already DRAINING or pending removal.
        long activeServers = directory.all().stream()
                .filter(n -> !pendingRemoval.contains(n.getId()))
                .filter(n -> n.getState() == ServerState.HEALTHY
                          || n.getState() == ServerState.DEGRADED)
                .count();
        if (util < 0.20 && activeServers > 6) {
            scaleDownTicks++;
            if (scaleDownTicks >= 5) { // 10 seconds sustained low load
                scaleDownTicks = 0;
                scaleDown();
            }
        } else {
            scaleDownTicks = 0;
        }
    }

    private static final String[] SCALE_TIERS = {"RTX_3050", "RTX_3070", "RTX_3080", "RTX_4090", "RTX_4090_TI"};
    private static final double[] SCALE_COSTS = { 0.80,       1.80,       2.50,       4.50,        8.00 };
    private static final int[]    SCALE_CAPS  = { 100,        80,         60,         60,           40  };

    private void scaleUp() {
        int idx  = nextId.getAndIncrement();
        String id = "GS-MUM-A" + idx;
        String tier = SCALE_TIERS[idx % SCALE_TIERS.length];
        double cost = SCALE_COSTS[idx % SCALE_COSTS.length];
        int    cap  = SCALE_CAPS [idx % SCALE_CAPS.length];
        ServerNode newNode = new ServerNode(id, "MUMBAI", "Mumbai", cap, cost, tier);
        directory.add(newNode);
        registry.registerNewServer(id);
        breakers.register(id, events, ws);
        events.info("SERVER_ADDED", "Scale up: added " + id + " (" + tier + " @$" + cost + "/hr)", id);
        ws.publish("SERVER_ADDED", Map.of("server", newNode));
    }

    private void scaleDown() {
        // Prefer draining the most expensive server that has the fewest sessions.
        // This makes cost-optimized routing visibly reduce burn rate: as new sessions
        // land on cheap servers, expensive ones go idle and get drained first.
        ServerNode candidate = null;
        for (ServerNode node : directory.all()) {
            if (pendingRemoval.contains(node.getId())) continue;
            if (node.getState() != ServerState.HEALTHY) continue;

            if (candidate == null) {
                candidate = node;
            } else {
                int nodeSess   = node.getMetrics()     != null ? node.getMetrics().getSessions()      : 0;
                int candSess   = candidate.getMetrics() != null ? candidate.getMetrics().getSessions() : 0;
                double nodeCost = node.getCostPerHour();
                double candCost = candidate.getCostPerHour();

                // Pick by: highest cost first, fewest sessions as tiebreaker.
                if (nodeCost > candCost || (nodeCost == candCost && nodeSess < candSess)) {
                    candidate = node;
                }
            }
        }

        if (candidate != null) {
            pendingRemoval.add(candidate.getId());
            candidate.setState(ServerState.DRAINING);
            candidate.setWeight(0);
            candidate.refreshEligibility();
            events.info("SERVER_DRAINING", "Scale down triggered, draining " + candidate.getId(), candidate.getId());
            ws.publish("SERVER_STATE_CHANGED", Map.of(
                    "serverId", candidate.getId(),
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
