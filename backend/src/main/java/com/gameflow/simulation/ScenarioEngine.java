package com.gameflow.simulation;

import com.gameflow.events.EventBus;
import com.gameflow.health.HealthManager;
import com.gameflow.model.EventSeverity;
import com.gameflow.model.FaultType;
import com.gameflow.model.RoutingStrategy;
import com.gameflow.model.ServerState;
import com.gameflow.rmi.GameServerImpl;
import com.gameflow.rmi.GameServerRegistry;
import com.gameflow.routing.RoutingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                                     boolean active, String targetServerId, boolean custom) {
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
    private final FaultInjector faults;
    private final RoutingEngine routingEngine;

    private volatile String activeScenarioId = null;
    private volatile long phaseStartMs = 0;
    private volatile int phase = 0;

    // ---- custom (user-defined) scenarios ----
    /** Dwell applied to action steps so each step's effect is visible. */
    private static final long ACTION_DWELL_MS = 3_000;
    private final Map<String, CustomScenario> customScenarios = new LinkedHashMap<>();
    private volatile CustomScenario activeCustom = null;
    private volatile int customStepIndex = -1;
    private volatile long customStepStartMs = 0;
    /** Servers faulted by the active custom scenario (cleared on stop). */
    private final java.util.Set<String> customFaultedServers = new java.util.HashSet<>();

    public ScenarioEngine(GameServerRegistry registry,
                          TrafficGenerator traffic,
                          HealthManager healthManager,
                          ServerDirectory directory,
                          EventBus events,
                          FaultInjector faults,
                          RoutingEngine routingEngine) {
        this.registry = registry;
        this.traffic = traffic;
        this.healthManager = healthManager;
        this.directory = directory;
        this.events = events;
        this.faults = faults;
        this.routingEngine = routingEngine;
    }

    public List<ScenarioDescriptor> getDescriptors() {
        List<ScenarioDescriptor> out = new ArrayList<>();
        for (ScenarioDef def : DEFS) {
            out.add(new ScenarioDescriptor(def.id(), def.name(), def.description(),
                    def.id().equals(activeScenarioId), def.targetServerId(), false));
        }
        synchronized (customScenarios) {
            for (CustomScenario sc : customScenarios.values()) {
                out.add(new ScenarioDescriptor(sc.id(), sc.name(), sc.description(),
                        sc.id().equals(activeScenarioId), null, true));
            }
        }
        return out;
    }

    public String getActiveScenarioId() {
        return activeScenarioId;
    }

    public synchronized void start(String scenarioId) {
        CustomScenario custom;
        synchronized (customScenarios) {
            custom = customScenarios.get(scenarioId);
        }
        if (custom != null) {
            startCustom(custom);
            return;
        }
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
        for (String id : customFaultedServers) {
            clearFaultOverrides(id);
        }
        customFaultedServers.clear();
        events.info("SCENARIO_STOPPED", "Scenario stopped: " + activeScenarioId, null);
        activeScenarioId = null;
        activeCustom = null;
        customStepIndex = -1;
        phase = 0;
    }

    // ---------------- custom scenarios ----------------

    /** Validate and store a user-defined scenario. Returns the descriptor. */
    public synchronized ScenarioDescriptor createCustom(String name, String description,
                                                        List<CustomScenario.Step> steps) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scenario name is required");
        }
        if (name.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scenario name too long (max 80)");
        }
        if (steps == null || steps.isEmpty() || steps.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Scenario must have 1..20 steps");
        }
        for (int i = 0; i < steps.size(); i++) {
            validateStep(i, steps.get(i));
        }
        String id = "custom-" + UUID.randomUUID().toString().substring(0, 8);
        CustomScenario sc = new CustomScenario(id, name.strip(),
                description == null ? "" : description.strip(),
                List.copyOf(steps), System.currentTimeMillis());
        synchronized (customScenarios) {
            customScenarios.put(id, sc);
        }
        events.info("SCENARIO_CREATED", "Custom scenario created: " + sc.name()
                + " (" + steps.size() + " steps)", null);
        return new ScenarioDescriptor(id, sc.name(), sc.description(), false, null, true);
    }

    public synchronized void deleteCustom(String scenarioId) {
        CustomScenario removed;
        synchronized (customScenarios) {
            removed = customScenarios.remove(scenarioId);
        }
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Unknown custom scenario: " + scenarioId);
        }
        if (scenarioId.equals(activeScenarioId)) {
            stopActive();
        }
        events.info("SCENARIO_DELETED", "Custom scenario deleted: " + removed.name(), null);
    }

    private void validateStep(int i, CustomScenario.Step step) {
        String where = "Step " + (i + 1) + ": ";
        if (step == null || step.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, where + "type is required");
        }
        switch (step.type()) {
            case WAIT -> {
                if (step.seconds() == null || step.seconds() < 1 || step.seconds() > 600) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            where + "WAIT needs seconds in 1..600");
                }
            }
            case SET_TRAFFIC -> {
                if (step.targetSessions() == null || step.targetSessions() < 0 || step.targetSessions() > 1000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            where + "SET_TRAFFIC needs targetSessions in 0..1000");
                }
            }
            case FAULT -> {
                requireServer(where, step.serverId());
                if (step.faultType() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            where + "FAULT needs faultType");
                }
            }
            case RECOVER -> requireServer(where, step.serverId());
            case STRATEGY -> {
                if (step.strategy() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            where + "STRATEGY needs strategy");
                }
            }
        }
    }

    private void requireServer(String where, String serverId) {
        if (serverId == null || directory.get(serverId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    where + "unknown serverId: " + serverId);
        }
    }

    private void startCustom(CustomScenario sc) {
        stopAll();
        activeCustom = sc;
        activeScenarioId = sc.id();
        customStepIndex = -1;
        customStepStartMs = System.currentTimeMillis();
        events.info("SCENARIO_STARTED", "Custom scenario started: " + sc.name(), null);
        advanceCustom();
    }

    /** Execute the next step's action and start its dwell timer. */
    private void advanceCustom() {
        CustomScenario sc = activeCustom;
        if (sc == null) {
            return;
        }
        customStepIndex++;
        customStepStartMs = System.currentTimeMillis();
        List<CustomScenario.Step> steps = sc.steps();
        if (customStepIndex >= steps.size()) {
            String name = sc.name();
            stopActive();
            events.info("SCENARIO_COMPLETED", "Custom scenario completed: " + name, null);
            return;
        }
        CustomScenario.Step step = steps.get(customStepIndex);
        executeCustomStep(step);
    }

    private void executeCustomStep(CustomScenario.Step step) {
        String label = "step " + (customStepIndex + 1) + "/" + activeCustom.steps().size();
        try {
            switch (step.type()) {
                case WAIT -> events.info("SCENARIO_PHASE",
                        "Waiting " + step.seconds() + "s (" + label + ")", null);
                case SET_TRAFFIC -> {
                    traffic.setTargetSessions(step.targetSessions());
                    events.info("SCENARIO_PHASE",
                            "Traffic target → " + step.targetSessions() + " sessions (" + label + ")", null);
                }
                case FAULT -> {
                    faults.inject(step.serverId(), step.faultType());
                    customFaultedServers.add(step.serverId());
                }
                case RECOVER -> {
                    faults.recover(step.serverId());
                    customFaultedServers.remove(step.serverId());
                }
                case STRATEGY -> routingEngine.setActiveStrategy(step.strategy());
            }
        } catch (ResponseStatusException e) {
            // A failed step must not kill the timeline; record and continue.
            events.warn("SCENARIO_PHASE",
                    "Step failed (" + label + "): " + e.getReason(), step.serverId());
            log.warn("Custom scenario step failed: {}", e.getReason());
        } catch (Exception e) {
            events.warn("SCENARIO_PHASE",
                    "Step failed (" + label + "): " + e.getMessage(), step.serverId());
            log.warn("Custom scenario step failed", e);
        }
    }

    /** Dwell per step: WAIT uses its seconds, actions get a fixed pause so
     *  their effects are visible before the timeline moves on. */
    private static long dwellMs(CustomScenario.Step step) {
        return step.type() == CustomScenario.StepType.WAIT
                ? (long) (step.seconds() * 1000)
                : ACTION_DWELL_MS;
    }

    /** Called on every traffic tick while a custom scenario is active. */
    private void tickCustom() {
        CustomScenario sc = activeCustom;
        if (sc == null || customStepIndex < 0 || customStepIndex >= sc.steps().size()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - customStepStartMs;
        if (elapsed >= dwellMs(sc.steps().get(customStepIndex))) {
            advanceCustom();
        }
    }

    /** Called on every traffic tick while RUNNING. Advances scripted phases. */
    public synchronized void tick() {
        if (activeCustom != null) {
            tickCustom();
            return;
        }
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
