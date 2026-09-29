package com.gameflow.health;

import com.gameflow.events.EventBus;
import com.gameflow.model.CircuitState;
import com.gameflow.model.EventSeverity;
import com.gameflow.websocket.WsEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CircuitBreakerTest {

    private EventBus events;
    private WsEventPublisher ws;
    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        events = mock(EventBus.class);
        ws = mock(WsEventPublisher.class);
        breaker = new CircuitBreaker("GS-MUM-01", events, ws);
    }

    @AfterEach
    void restoreTimeout() {
        CircuitBreaker.OPEN_TIMEOUT_MS = 10_000;
    }

    @Test
    void closedToOpenAfterThreeConsecutiveFailures() {
        breaker.recordFailure();
        assertEquals(CircuitState.CLOSED, breaker.getState());
        breaker.recordFailure();
        assertEquals(CircuitState.CLOSED, breaker.getState());
        breaker.recordFailure();
        assertEquals(CircuitState.OPEN, breaker.getState());

        verify(events).publish(eq("CIRCUIT_OPEN"), eq(EventSeverity.ERROR),
                anyString(), eq("GS-MUM-01"));
        verify(ws).publish(eq("CIRCUIT_CHANGED"), anyMap());
    }

    @Test
    void successResetsConsecutiveFailureCount() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(CircuitState.CLOSED, breaker.getState(),
                "two failures after a success must not open the circuit");
    }

    @Test
    void openToHalfOpenAfterTimeoutThenClosesOnProbeSuccess() throws Exception {
        openCircuit();
        CircuitBreaker.OPEN_TIMEOUT_MS = 50;
        Thread.sleep(80);

        assertEquals(CircuitState.HALF_OPEN, breaker.pollState());
        verify(events).publish(eq("CIRCUIT_HALF_OPEN"), eq(EventSeverity.WARN),
                anyString(), eq("GS-MUM-01"));

        breaker.recordSuccess();
        assertEquals(CircuitState.CLOSED, breaker.getState());
        verify(events).publish(eq("CIRCUIT_CLOSED"), eq(EventSeverity.INFO),
                anyString(), eq("GS-MUM-01"));
    }

    @Test
    void failedProbeReopensCircuit() throws Exception {
        openCircuit();
        CircuitBreaker.OPEN_TIMEOUT_MS = 50;
        Thread.sleep(80);
        assertEquals(CircuitState.HALF_OPEN, breaker.pollState());

        breaker.recordFailure();
        assertEquals(CircuitState.OPEN, breaker.getState());
    }

    @Test
    void pollBeforeTimeoutStaysOpen() throws Exception {
        openCircuit();
        CircuitBreaker.OPEN_TIMEOUT_MS = 60_000;
        assertEquals(CircuitState.OPEN, breaker.pollState());
    }

    private void openCircuit() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(CircuitState.OPEN, breaker.getState());
    }
}
