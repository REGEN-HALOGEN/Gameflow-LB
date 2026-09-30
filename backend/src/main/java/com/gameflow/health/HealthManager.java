package com.gameflow.health;

import com.gameflow.events.EventBus;
import com.gameflow.model.CircuitState;
import com.gameflow.model.EventSeverity;
import com.gameflow.model.RmiStatus;
import com.gameflow.model.ServerNode;
import com.gameflow.model.ServerState;
import com.gameflow.rmi.GameServerRegistry;
import com.gameflow.rmi.GameServerRemote;
import com.gameflow.session.SessionManager;
import com.gameflow.simulation.ServerDirectory;
import com.gameflow.websocket.WsEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls every game server over RMI every 2 seconds.
 *
 * State machine:
 *   1–2 consecutive failures → DEGRADED
 *   ≥3 consecutive failures  → UNHEALTHY (also opens the circuit breaker)
 *   RemoteException          → OFFLINE (RMI unreachable)
 *   RECOVERING + success streak → HEALTHY
 *
 * Transitions to UNHEALTHY/OFFLINE trigger session migration exactly once.
 */
@Component
public class HealthManager {

    private static final Logger log = LoggerFactory.getLogger(HealthManager.class);
    private static final int RECOVERY_STREAK = 2;

    private final ServerDirectory directory;
    private final GameServerRegistry registry;
    private final CircuitBreakerRegistry breakers;
    private final EventBus events;
    private final WsEventPublisher ws;
    private final SessionManager sessionManager;

    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, Integer> recoveryStreak = new ConcurrentHashMap<>();

    public HealthManager(ServerDirectory directory,
                         GameServerRegistry registry,
                         CircuitBreakerRegistry breakers,
                         EventBus events,
                         WsEventPublisher ws,
                         SessionManager sessionManager) {
        this.directory = directory;
        this.registry = registry;
        this.breakers = breakers;
        this.events = events;
        this.ws = ws;
        this.sessionManager = sessionManager;
    }

    /** Invoked on a 2s schedule by the simulation engine. */
    public void checkHealth() {
        for (ServerNode node : directory.all()) {
            try {
                checkOne(node);
            } catch (Exception e) {
                log.warn("Health check error for {}: {}", node.getId(), e.getMessage());
            }
        }
    }

    private void checkOne(ServerNode node) {
        String id = node.getId();
        CircuitBreaker breaker = breakers.get(id);

        // Time-based breaker transition (OPEN → HALF_OPEN after timeout).
        // In HALF_OPEN this very check doubles as the recovery probe.
        CircuitState circuit = breaker.pollState();
        node.setCircuitState(circuit);
        node.refreshEligibility();

        boolean healthy;
        try {
            GameServerRemote stub = registry.lookupStub(id);
            if (stub == null) {
                throw new RemoteException("No RMI stub bound for " + id);
            }
            healthy = stub.healthCheck();
            // rmiStatus reflects the actual RMI transport, not the fallback handle.
            RmiStatus want = registry.isRmiAvailable() ? RmiStatus.CONNECTED : RmiStatus.UNREACHABLE;
            if (node.getRmiStatus() != want) {
                node.setRmiStatus(want);
                ws.publish("RMI_STATUS_CHANGED", Map.of("serverId", id, "rmiStatus", want.name()));
                if (want == RmiStatus.CONNECTED) {
                    events.info("RMI_RECONNECTED", id + " RMI reachable again", id);
                }
            }
        } catch (RemoteException e) {
            if (node.getRmiStatus() != RmiStatus.UNREACHABLE) {
                node.setRmiStatus(RmiStatus.UNREACHABLE);
                ws.publish("RMI_STATUS_CHANGED", Map.of("serverId", id, "rmiStatus", RmiStatus.UNREACHABLE.name()));
            }
            onFailure(node, breaker, true);
            return;
        }

        if (healthy) {
            onSuccess(node, breaker);
        } else {
            onFailure(node, breaker, false);
        }
    }

    private void onSuccess(ServerNode node, CircuitBreaker breaker) {
        String id = node.getId();
        breaker.recordSuccess();
        node.setCircuitState(breaker.getState());
        consecutiveFailures.put(id, 0);

        switch (node.getState()) {
            case RECOVERING -> {
                int streak = recoveryStreak.merge(id, 1, Integer::sum);
                if (streak >= RECOVERY_STREAK) {
                    recoveryStreak.put(id, 0);
                    setState(node, ServerState.HEALTHY, "recovery probes passing");
                }
            }
            case DEGRADED -> setState(node, ServerState.HEALTHY, "health checks passing");
            case UNHEALTHY, OFFLINE ->
                    setState(node, ServerState.RECOVERING, "responding again, verifying");
            default -> {
            }
        }
        node.refreshEligibility();
    }

    private void onFailure(ServerNode node, CircuitBreaker breaker, boolean rmiException) {
        String id = node.getId();
        breaker.recordFailure();
        node.setCircuitState(breaker.getState());
        recoveryStreak.put(id, 0);

        int failures = consecutiveFailures.merge(id, 1, Integer::sum);
        if (rmiException) {
            if (node.getState() != ServerState.OFFLINE) {
                events.error("HEALTH_CHECK_FAILED",
                        id + " RMI health check failed (unreachable)", id);
            }
            setState(node, ServerState.OFFLINE, "RMI unreachable");
        } else if (failures >= 3) {
            if (node.getState() != ServerState.UNHEALTHY) {
                events.error("HEALTH_CHECK_FAILED",
                        id + " failed " + failures + " consecutive health checks", id);
            }
            setState(node, ServerState.UNHEALTHY, failures + " consecutive failures");
        } else {
            setState(node, ServerState.DEGRADED, "health check failed (" + failures + "x)");
        }
        node.refreshEligibility();
    }

    private void setState(ServerNode node, ServerState next, String reason) {
        ServerState old = node.getState();
        if (old == next) {
            return;
        }
        node.setState(next);
        node.refreshEligibility();

        EventSeverity severity = switch (next) {
            case HEALTHY -> EventSeverity.INFO;
            case DEGRADED, RECOVERING -> EventSeverity.WARN;
            case UNHEALTHY, OFFLINE -> EventSeverity.ERROR;
        };
        events.publish("SERVER_STATE_CHANGED", severity,
                node.getId() + ": " + old + " → " + next + " (" + reason + ")", node.getId());
        ws.publish("SERVER_STATE_CHANGED", Map.of(
                "serverId", node.getId(),
                "oldState", old.name(),
                "newState", next.name()));

        // Losing a server triggers orchestrated session reassignment, once.
        if ((next == ServerState.UNHEALTHY || next == ServerState.OFFLINE)
                && (old == ServerState.HEALTHY || old == ServerState.DEGRADED
                    || old == ServerState.RECOVERING)) {
            sessionManager.migrateSessionsFrom(node.getId());
        }
    }

    /** Mark a server as recovering (used by the recover endpoint). */
    public void markRecovering(String serverId) {
        ServerNode node = directory.get(serverId);
        if (node != null) {
            consecutiveFailures.put(serverId, 0);
            recoveryStreak.put(serverId, 0);
            setState(node, ServerState.RECOVERING, "operator-initiated recovery");
        }
    }

    public void reset() {
        consecutiveFailures.clear();
        recoveryStreak.clear();
    }
}
