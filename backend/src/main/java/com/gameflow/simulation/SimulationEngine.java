package com.gameflow.simulation;

import com.gameflow.events.EventBus;
import com.gameflow.health.CircuitBreakerRegistry;
import com.gameflow.health.HealthManager;
import com.gameflow.history.HistoryService;
import com.gameflow.model.RmiStatus;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.ServerNode;
import com.gameflow.model.ServerState;
import com.gameflow.model.SimulationState;
import com.gameflow.rmi.GameServerRegistry;
import com.gameflow.rmi.GameServerRemote;
import com.gameflow.routing.RoutingEngine;
import com.gameflow.session.SessionManager;
import com.gameflow.websocket.WsEventPublisher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Drives the whole simulation.
 *
 * - Metric tick (1s, ALWAYS running): pulls metrics from every server over
 *   RMI, records history, broadcasts METRIC_UPDATE, watches thresholds.
 * - Health tick (2s, ALWAYS running): RMI health checks.
 * - Traffic tick (1000/speed ms, only when RUNNING): scenarios + players.
 */
@Component
public class SimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);
    private static final Set<Double> ALLOWED_SPEEDS = Set.of(0.5, 1.0, 2.0, 5.0);

    private volatile SimulationState state = SimulationState.STOPPED;
    private volatile double speed = 1.0;

    private final ServerDirectory directory;
    private final GameServerRegistry registry;
    private final HealthManager healthManager;
    private final CircuitBreakerRegistry breakers;
    private final TrafficGenerator traffic;
    private final ScenarioEngine scenarios;
    private final SessionManager sessions;
    private final RoutingEngine routingEngine;
    private final HistoryService history;
    private final EventBus events;
    private final WsEventPublisher ws;

    private final Map<String, Boolean> gpuAlert = new LinkedHashMap<>();
    private final Map<String, Boolean> lossAlert = new LinkedHashMap<>();

    private final ScheduledExecutorService trafficExecutor;
    private ScheduledFuture<?> trafficFuture;

    public SimulationEngine(ServerDirectory directory,
                            GameServerRegistry registry,
                            HealthManager healthManager,
                            CircuitBreakerRegistry breakers,
                            TrafficGenerator traffic,
                            ScenarioEngine scenarios,
                            SessionManager sessions,
                            RoutingEngine routingEngine,
                            HistoryService history,
                            EventBus events,
                            WsEventPublisher ws) {
        this.directory = directory;
        this.registry = registry;
        this.healthManager = healthManager;
        this.breakers = breakers;
        this.traffic = traffic;
        this.scenarios = scenarios;
        this.sessions = sessions;
        this.routingEngine = routingEngine;
        this.history = history;
        this.events = events;
        this.ws = ws;

        ThreadFactory daemon = r -> {
            Thread t = new Thread(r, "gameflow-traffic");
            t.setDaemon(true);
            return t;
        };
        this.trafficExecutor = Executors.newSingleThreadScheduledExecutor(daemon);
        scheduleTrafficTick();
    }

    // ---------------- control API ----------------

    public synchronized void start() {
        if (state == SimulationState.RUNNING) {
            return;
        }
        state = SimulationState.RUNNING;
        if (traffic.getTargetSessions() <= 0 && scenarios.getActiveScenarioId() == null) {
            traffic.setTargetSessions(90); // sensible default load
        }
        events.info("SIMULATION_STARTED", "Simulation started at " + speed + "x", null);
        broadcastState();
    }

    public synchronized void pause() {
        if (state != SimulationState.RUNNING) {
            return;
        }
        state = SimulationState.PAUSED;
        events.info("SIMULATION_PAUSED", "Simulation paused", null);
        broadcastState();
    }

    public synchronized void resume() {
        if (state != SimulationState.PAUSED) {
            return;
        }
        state = SimulationState.RUNNING;
        events.info("SIMULATION_RESUMED", "Simulation resumed at " + speed + "x", null);
        broadcastState();
    }

    public synchronized void reset() {
        state = SimulationState.STOPPED;
        scenarios.stopAll();
        traffic.reset();
        sessions.clearAll();
        routingEngine.clearDecisions();
        history.clear();
        events.clear();
        healthManager.reset();
        breakers.resetAll();
        // Restore crashed servers and clear every injected fault.
        for (String id : registry.serverIds()) {
            try {
                ServerNode node = directory.get(id);
                ServerState oldState = node != null ? node.getState() : null;
                if (registry.isCrashed(id)) {
                    registry.restoreServer(id);
                }
                var impl = registry.getImpl(id);
                if (impl != null) {
                    impl.clearFaults();
                }
                // Broadcast state restoration so connected UIs update immediately
                // without waiting for a WS reconnect / re-seed.
                if (node != null && oldState != null && oldState != ServerState.HEALTHY) {
                    ws.publish("SERVER_STATE_CHANGED", Map.of(
                            "serverId", id,
                            "oldState", oldState.name(),
                            "newState", ServerState.HEALTHY.name()));
                }
            } catch (Exception e) {
                log.warn("Reset failed for {}: {}", id, e.getMessage());
            }
        }
        directory.resetAll();
        gpuAlert.clear();
        lossAlert.clear();
        events.info("SIMULATION_RESET",
                "Simulation reset: sessions, decisions, events, faults and scenarios cleared", null);
        broadcastState();
    }

    public synchronized void setSpeed(double speed) {
        if (!ALLOWED_SPEEDS.contains(speed)) {
            throw new IllegalArgumentException(
                    "Speed must be one of " + ALLOWED_SPEEDS + ", got " + speed);
        }
        this.speed = speed;
        scheduleTrafficTick();
        broadcastState();
    }

    public SimulationState getState() {
        return state;
    }

    public double getSpeed() {
        return speed;
    }

    private void broadcastState() {
        ws.publish("SIMULATION_STATE_CHANGED", Map.of(
                "state", state.name(),
                "speed", speed));
    }

    private synchronized void scheduleTrafficTick() {
        if (trafficFuture != null) {
            trafficFuture.cancel(false);
        }
        long periodMs = (long) (1000.0 / speed);
        trafficFuture = trafficExecutor.scheduleWithFixedDelay(
                this::trafficTick, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        trafficExecutor.shutdownNow();
    }

    // ---------------- ticks ----------------

    /** Player traffic + scenario timelines. Only advances while RUNNING. */
    private void trafficTick() {
        if (state != SimulationState.RUNNING) {
            return;
        }
        try {
            scenarios.tick();
            traffic.tick();
        } catch (Exception e) {
            log.warn("Traffic tick failed: {}", e.getMessage());
        }
    }

    /** RMI metric pull. Runs in every simulation state so the UI never looks dead. */
    @Scheduled(fixedRate = 1000)
    public void metricTick() {
        Map<String, ServerMetrics> latest = new LinkedHashMap<>();
        for (ServerNode node : directory.all()) {
            String id = node.getId();
            try {
                GameServerRemote stub = registry.lookupStub(id);
                if (stub == null) {
                    throw new java.rmi.RemoteException("No RMI stub bound for " + id);
                }
                ServerMetrics metrics = stub.getMetrics();
                node.setMetrics(metrics);
                node.setRmiStatus(RmiStatus.CONNECTED);
                // Refresh eligibility immediately so the UI badge reflects metric
                // threshold changes without waiting up to 2s for the health tick.
                node.refreshEligibility();
                latest.put(id, metrics);
                checkThresholds(node, metrics);
                ws.publish("METRIC_UPDATE", Map.of(
                        "serverId", id,
                        "metrics", metrics));
            } catch (Exception e) {
                node.setRmiStatus(RmiStatus.UNREACHABLE);
            }
        }
        if (!latest.isEmpty()) {
            history.recordTick(latest);
        }
    }

    private void checkThresholds(ServerNode node, ServerMetrics m) {
        String id = node.getId();
        boolean gpuHot = gpuAlert.getOrDefault(id, false);
        if (m.getGpu() > 90 && !gpuHot) {
            gpuAlert.put(id, true);
            node.setWeight(0.3);
            events.warn("GPU_THRESHOLD",
                    id + " GPU reached " + (int) m.getGpu() + "% — routing weight reduced", id);
        } else if (m.getGpu() <= 85 && gpuHot) {
            gpuAlert.put(id, false);
            node.setWeight(1.0);
            events.info("GPU_RECOVERED", id + " GPU back to " + (int) m.getGpu() + "%", id);
        }
        boolean lossHot = lossAlert.getOrDefault(id, false);
        if (m.getPacketLoss() > 5 && !lossHot) {
            lossAlert.put(id, true);
            events.warn("PACKET_LOSS_THRESHOLD",
                    id + " packet loss " + m.getPacketLoss() + "% exceeds 5%", id);
        } else if (m.getPacketLoss() <= 3 && lossHot) {
            lossAlert.put(id, false);
            events.info("PACKET_LOSS_RECOVERED",
                    id + " packet loss back to " + m.getPacketLoss() + "%", id);
        }
    }

    /** RMI health checks. Runs in every simulation state. */
    @Scheduled(fixedRate = 2000)
    public void healthTick() {
        healthManager.checkHealth();
    }
}
