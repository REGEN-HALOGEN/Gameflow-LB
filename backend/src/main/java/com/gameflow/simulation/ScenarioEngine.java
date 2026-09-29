package com.gameflow.simulation;

import com.gameflow.events.EventBus;
import com.gameflow.health.HealthManager;
import com.gameflow.model.EventSeverity;
import com.gameflow.model.ServerState;
import com.gameflow.rmi.GameServerImpl;
import com.gameflow.rmi.GameServerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic demo scenarios, each a scripted timeline over wall-clock
 * time. Scenarios manipulate real simulation state (fault overrides,
 * traffic targets, crashed servers) — the routing engine, health checks
 * and circuit breakers then react on their own.
 */
@Component
public class ScenarioEngine {

    private static final Logger log = LoggerFactory.getLogger(ScenarioEngine.class);
    private static final String FAULT_TARGET = "GS-MUM-02";

    public record ScenarioDescriptor(String id, String name, String description,
                                     boolean active, String targetServerId) {
    }

    private record ScenarioDef(String id, String name, String description, String targetServerId) {
    }

    private static final List<ScenarioDef> DEFS = List.of(
            new ScenarioDef("normal-traffic", "Normal Traffic",
                    "Gradually introduces players. Shows balanced routing across servers.", null),
            new ScenarioDef("gpu-saturation", "GPU Saturation",
                    "Ramps GS-MUM-02 GPU 70% → 97%. Routing should shed load.", FAULT_TARGET),
            new ScenarioDef("latency-spike", "Latency Spike",
                    "Pushes GS-MUM-02 latency 20ms → 180ms. Traffic moves to lower-latency servers.", FAULT_TARGET),
            new ScenarioDef("packet-loss", "Packet Loss",
                    "Raises GS-MUM-02 packet loss 0.1% → 12%. Server becomes unsuitable for cloud gaming.", FAULT_TARGET),
            new ScenarioDef("server-failure", "Server Failure",
                    "Crashes GS-MUM-02 (RMI goes dark). Circuit opens, sessions migrate.", FAULT_TARGET),
            new ScenarioDef("server-recovery", "Server Recovery",
                    "Restores the failed server: OFFLINE → RECOVERING → HALF_OPEN → HEALTHY → CLOSED.", FAULT_TARGET),
            new ScenarioDef("traffic-surge", "Traffic Surge",
                    "Ramps total sessions 20 → 50 → 100 → 200. Watch utilization climb.", null)
    );

    private final GameServerRegistry registry;
    private final TrafficGenerator traffic;
    private final HealthManager healthManager;
    private final ServerDirectory directory;
    private final EventBus events;

    private volatile String activeScenarioId = null;
    private volatile long phaseStartMs = 0;
    private volatile int phase = 0;

    public ScenarioEngine(GameServerRegistry registry,
                          TrafficGenerator traffic,
                          HealthManager healthManager,
                          ServerDirectory directory,
                          EventBus events) {
        this.registry = registry;
        this.traffic = traffic;
        this.healthManager = healthManager;
        this.directory = directory;
        this.events = events;
    }

    public List<ScenarioDescriptor> getDescriptors() {
        List<ScenarioDescriptor> out = new ArrayList<>();
        for (ScenarioDef def : DEFS) {
            out.add(new ScenarioDescriptor(def.id(), def.name(), def.description(),
                    def.id().equals(activeScenarioId), def.targetServerId()));
        }
        return out;
    }

    public String getActiveScenarioId() {
        return activeScenarioId;
    }

    public synchronized void start(String scenarioId) {
        ScenarioDef def = DEFS.stream()
                .filter(d -> d.id().equals(scenarioId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + scenarioId));
        stopAll();
        activeScenarioId = scenarioId;
        phase = 0;
        phaseStartMs = System.currentTimeMillis();
        applyPhase();
        events.info("SCENARIO_STARTED", "Scenario started: " + def.name(), def.targetServerId());
    }

    public synchronized void stop(String scenarioId) {
        if (scenarioId.equals(activeScenarioId)) {
            stopActive();
        }
    }

    public synchronized void stopAll() {
        if (activeScenarioId != null) {
            stopActive();
        }
    }

    private void stopActive() {
        clearFaultOverrides(FAULT_TARGET);
        events.info("SCENARIO_STOPPED", "Scenario stopped: " + activeScenarioId, null);
        activeScenarioId = null;
        phase = 0;
    }

    /** Called on every traffic tick while RUNNING. Advances scripted phases. */
    public synchronized void tick() {
        if (activeScenarioId == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - phaseStartMs;
        switch (activeScenarioId) {
            case "gpu-saturation" -> {
                double[] steps = {70, 80, 88, 92, 97};
                advancePhases(steps.length, elapsed, 12_000, s -> {
                    impl(FAULT_TARGET).setGpuTargetOverride(steps[s]);
                    events.warn("SCENARIO_PHASE",
                            "GPU target on " + FAULT_TARGET + " → " + (int) steps[s] + "%", FAULT_TARGET);
                });
            }
            case "latency-spike" -> {
                double[] steps = {20, 35, 70, 120, 180};
                advancePhases(steps.length, elapsed, 12_000, s -> {
                    impl(FAULT_TARGET).setLatencyOffsetMs(steps[s]);
                    events.warn("SCENARIO_PHASE",
                            "Latency offset on " + FAULT_TARGET + " → +" + (int) steps[s] + "ms", FAULT_TARGET);
                });
            }
            case "packet-loss" -> {
                double[] steps = {0.1, 1, 3, 7, 12};
                advancePhases(steps.length, elapsed, 12_000, s -> {
                    impl(FAULT_TARGET).setPacketLossOverride(steps[s]);
                    events.warn("SCENARIO_PHASE",
                            "Packet loss on " + FAULT_TARGET + " → " + steps[s] + "%", FAULT_TARGET);
                });
            }
            case "server-failure" -> {
                // Crash happens once at start; nothing more to script.
            }
            case "server-recovery" -> {
                // phase 0: crashed (done at start). phase 1 at +6s: restore.
                if (phase == 0 && elapsed >= 6_000) {
                    phase = 1;
                    restoreForRecovery();
                }
            }
            case "traffic-surge" -> {
                int[] steps = {20, 50, 100, 200};
                advancePhases(steps.length, elapsed, 20_000, s -> {
                    traffic.setTargetSessions(steps[s]);
                    events.info("SCENARIO_PHASE",
                            "Traffic target → " + steps[s] + " sessions", null);
                });
            }
            case "normal-traffic" -> {
                // Steady moderate load; nothing scripted.
            }
            default -> {
            }
        }
    }

    /** Apply the scenario's initial state at start(). */
    private void applyPhase() {
        switch (activeScenarioId) {
            case "normal-traffic" -> traffic.setTargetSessions(90);
            case "gpu-saturation" -> {
                impl(FAULT_TARGET).setGpuTargetOverride(70.0);
                traffic.setTargetSessions(90);
            }
            case "latency-spike" -> {
                impl(FAULT_TARGET).setLatencyOffsetMs(20);
                traffic.setTargetSessions(90);
            }
            case "packet-loss" -> {
                impl(FAULT_TARGET).setPacketLossOverride(0.1);
                traffic.setTargetSessions(90);
            }
            case "server-failure" -> {
                try {
                    registry.crashServer(FAULT_TARGET);
                    events.publish("FAULT_INJECTED", EventSeverity.CRITICAL,
                            "Scenario crashed " + FAULT_TARGET + " (RMI unexported)", FAULT_TARGET);
                } catch (Exception e) {
                    log.warn("Scenario crash failed: {}", e.getMessage());
                }
                traffic.setTargetSessions(90);
            }
            case "server-recovery" -> {
                String target = findFailedServer();
                if (target == null) {
                    // Self-contained demo: crash first, then recover.
                    try {
                        registry.crashServer(FAULT_TARGET);
                        events.publish("FAULT_INJECTED", EventSeverity.CRITICAL,
                                "Scenario crashed " + FAULT_TARGET + " to demonstrate recovery", FAULT_TARGET);
                    } catch (Exception e) {
                        log.warn("Scenario crash failed: {}", e.getMessage());
                    }
                }
                phaseStartMs = System.currentTimeMillis();
                traffic.setTargetSessions(90);
            }
            case "traffic-surge" -> traffic.setTargetSessions(20);
            default -> {
            }
        }
    }

    private void advancePhases(int steps, long elapsed, long stepMs,
                               java.util.function.IntConsumer apply) {
        int want = (int) Math.min(steps - 1, elapsed / stepMs);
        while (phase < want) {
            phase++;
            apply.accept(phase);
        }
    }

    private void restoreForRecovery() {
        String target = findFailedServer();
        if (target == null) {
            target = FAULT_TARGET;
        }
        try {
            if (registry.isCrashed(target)) {
                registry.restoreServer(target);
            }
            var impl = registry.getImpl(target);
            if (impl != null) {
                impl.clearFaults();
            }
            healthManager.markRecovering(target);
            events.info("FAULT_CLEARED",
                    target + " restored, recovery probes starting", target);
        } catch (Exception e) {
            log.warn("Recovery failed for {}: {}", target, e.getMessage());
        }
    }

    private String findFailedServer() {
        for (String id : registry.serverIds()) {
            var node = directory.get(id);
            if (node.getState() == ServerState.OFFLINE
                    || node.getState() == ServerState.UNHEALTHY
                    || registry.isCrashed(id)) {
                return id;
            }
        }
        return null;
    }

    private GameServerImpl impl(String serverId) {
        GameServerImpl impl = registry.getImpl(serverId);
        if (impl == null) {
            throw new IllegalStateException("No server impl for " + serverId);
        }
        return impl;
    }

    private void clearFaultOverrides(String serverId) {
        GameServerImpl impl = registry.getImpl(serverId);
        if (impl != null) {
            impl.setGpuTargetOverride(null);
            impl.setLatencyOffsetMs(0);
            impl.setPacketLossOverride(null);
        }
    }
}
