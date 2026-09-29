package com.gameflow.health;

import com.gameflow.events.EventBus;
import com.gameflow.model.CircuitState;
import com.gameflow.websocket.WsEventPublisher;

import java.util.Map;

/**
 * Classic circuit breaker, one per game server.
 *
 * CLOSED → (3 consecutive failures) → OPEN → (10s timeout) →
 * HALF_OPEN → probe success → CLOSED / probe failure → OPEN.
 */
public class CircuitBreaker {

    static final int FAILURE_THRESHOLD = 3;
    /** Recovery timeout; non-final so tests can shrink it. */
    static long OPEN_TIMEOUT_MS = 10_000;

    private final String serverId;
    private final EventBus events;
    private final WsEventPublisher ws;

    private volatile CircuitState state = CircuitState.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAt = 0;

    public CircuitBreaker(String serverId, EventBus events, WsEventPublisher ws) {
        this.serverId = serverId;
        this.events = events;
        this.ws = ws;
    }

    public CircuitState getState() {
        return state;
    }

    public synchronized void recordSuccess() {
        if (state == CircuitState.HALF_OPEN) {
            transitionTo(CircuitState.CLOSED);
            consecutiveFailures = 0;
        } else if (state == CircuitState.CLOSED) {
            consecutiveFailures = 0;
        }
        // In OPEN state, successes are not expected (no traffic allowed).
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (state == CircuitState.CLOSED && consecutiveFailures >= FAILURE_THRESHOLD) {
            openedAt = System.currentTimeMillis();
            transitionTo(CircuitState.OPEN);
        } else if (state == CircuitState.HALF_OPEN) {
            openedAt = System.currentTimeMillis();
            consecutiveFailures = 0;
            transitionTo(CircuitState.OPEN);
        }
    }

    /**
     * Advance time-based transitions. Call before each health check:
     * moves OPEN → HALF_OPEN once the recovery timeout has elapsed.
     */
    public synchronized CircuitState pollState() {
        if (state == CircuitState.OPEN
                && System.currentTimeMillis() - openedAt >= OPEN_TIMEOUT_MS) {
            transitionTo(CircuitState.HALF_OPEN);
        }
        return state;
    }

    public synchronized void reset() {
        CircuitState old = state;
        state = CircuitState.CLOSED;
        consecutiveFailures = 0;
        openedAt = 0;
        if (old != CircuitState.CLOSED) {
            emitChange(old, CircuitState.CLOSED);
        }
    }

    private void transitionTo(CircuitState next) {
        CircuitState old = this.state;
        if (old == next) {
            return;
        }
        this.state = next;
        emitChange(old, next);
    }

    private void emitChange(CircuitState old, CircuitState next) {
        String type = switch (next) {
            case OPEN -> "CIRCUIT_OPEN";
            case HALF_OPEN -> "CIRCUIT_HALF_OPEN";
            case CLOSED -> "CIRCUIT_CLOSED";
        };
        String message = switch (next) {
            case OPEN -> serverId + " removed from routing (circuit open)";
            case HALF_OPEN -> serverId + " probing recovery (half-open)";
            case CLOSED -> serverId + " back in routing (circuit closed)";
        };
        var severity = switch (next) {
            case OPEN -> com.gameflow.model.EventSeverity.ERROR;
            case HALF_OPEN -> com.gameflow.model.EventSeverity.WARN;
            case CLOSED -> com.gameflow.model.EventSeverity.INFO;
        };
        events.publish(type, severity, message, serverId);
        ws.publish("CIRCUIT_CHANGED", Map.of(
                "serverId", serverId,
                "oldState", old.name(),
                "newState", next.name()));
    }
}
