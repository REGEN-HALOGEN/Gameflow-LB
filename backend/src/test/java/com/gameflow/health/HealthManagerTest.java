package com.gameflow.health;

import com.gameflow.events.EventBus;
import com.gameflow.model.CircuitState;
import com.gameflow.model.RmiStatus;
import com.gameflow.model.ServerNode;
import com.gameflow.model.ServerState;
import com.gameflow.rmi.GameServerRegistry;
import com.gameflow.rmi.GameServerRemote;
import com.gameflow.session.SessionManager;
import com.gameflow.simulation.ServerDirectory;
import com.gameflow.websocket.WsEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthManagerTest {

    private ServerDirectory directory;
    private GameServerRegistry registry;
    private GameServerRemote stub;
    private SessionManager sessionManager;
    private HealthManager health;

    @BeforeEach
    void setUp() throws Exception {
        directory = new ServerDirectory();
        registry = mock(GameServerRegistry.class);
        stub = mock(GameServerRemote.class);
        when(registry.lookupStub(anyString())).thenReturn(stub);
        EventBus events = mock(EventBus.class);
        WsEventPublisher ws = mock(WsEventPublisher.class);
        sessionManager = mock(SessionManager.class);
        CircuitBreakerRegistry breakers = new CircuitBreakerRegistry(directory, events, ws);
        health = new HealthManager(directory, registry, breakers, events, ws, sessionManager);
    }

    private ServerNode node() {
        return directory.get("GS-MUM-01");
    }

    @Test
    void rmiExceptionMarksServerOffline() throws Exception {
        when(stub.healthCheck()).thenThrow(new RemoteException("connection refused"));

        health.checkHealth();

        assertEquals(ServerState.OFFLINE, node().getState());
        assertEquals(RmiStatus.UNREACHABLE, node().getRmiStatus());
        verify(sessionManager, times(1)).migrateSessionsFrom("GS-MUM-01");
    }

    @Test
    void consecutiveFailuresDegradeThenMarkUnhealthy() throws Exception {
        when(stub.healthCheck()).thenReturn(false);

        health.checkHealth();
        assertEquals(ServerState.DEGRADED, node().getState());
        verify(sessionManager, never()).migrateSessionsFrom(anyString());

        health.checkHealth();
        assertEquals(ServerState.DEGRADED, node().getState());

        health.checkHealth();
        assertEquals(ServerState.UNHEALTHY, node().getState());
        verify(sessionManager, times(1)).migrateSessionsFrom("GS-MUM-01");

        // further failures must not re-trigger migration
        health.checkHealth();
        verify(sessionManager, times(1)).migrateSessionsFrom("GS-MUM-01");
    }

    @Test
    void threeRmiFailuresOpenCircuitBreaker() throws Exception {
        when(stub.healthCheck()).thenThrow(new RemoteException("boom"));
        CircuitBreakerRegistry breakers = new CircuitBreakerRegistry(
                directory, mock(EventBus.class), mock(WsEventPublisher.class));
        HealthManager hm = new HealthManager(directory, registry, breakers,
                mock(EventBus.class), mock(WsEventPublisher.class), sessionManager);

        hm.checkHealth();
        hm.checkHealth();
        hm.checkHealth();

        assertEquals(CircuitState.OPEN, breakers.get("GS-MUM-01").getState());
    }

    @Test
    void recoveringServerBecomesHealthyAfterSuccessStreak() throws Exception {
        when(stub.healthCheck()).thenReturn(true);

        health.markRecovering("GS-MUM-01");
        assertEquals(ServerState.RECOVERING, node().getState());

        health.checkHealth();
        assertEquals(ServerState.RECOVERING, node().getState());

        health.checkHealth();
        assertEquals(ServerState.HEALTHY, node().getState());
        assertTrue(node().isEligible());
    }

    @Test
    void degradedServerRecoversToHealthyOnSuccess() throws Exception {
        when(stub.healthCheck()).thenReturn(false);
        health.checkHealth();
        assertEquals(ServerState.DEGRADED, node().getState());

        when(stub.healthCheck()).thenReturn(true);
        health.checkHealth();
        assertEquals(ServerState.HEALTHY, node().getState());
    }
}
