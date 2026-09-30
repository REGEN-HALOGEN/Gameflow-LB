package com.gameflow.session;

import com.gameflow.events.EventBus;
import com.gameflow.health.CircuitBreakerRegistry;
import com.gameflow.model.GameSession;
import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.SessionState;
import com.gameflow.rmi.GameServerRegistry;
import com.gameflow.rmi.GameServerRemote;
import com.gameflow.routing.RoutingEngine;
import com.gameflow.simulation.ServerDirectory;
import com.gameflow.websocket.WsEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionManagerTest {

    private RoutingEngine routing;
    private GameServerRegistry registry;
    private EventBus events;
    private WsEventPublisher ws;
    private SessionManager sessions;

    private GameServerRemote stub1;
    private GameServerRemote stub2;

    @BeforeEach
    void setUp() throws Exception {
        routing = mock(RoutingEngine.class);
        registry = mock(GameServerRegistry.class);
        events = mock(EventBus.class);
        ws = mock(WsEventPublisher.class);
        ServerDirectory directory = new ServerDirectory();
        CircuitBreakerRegistry breakers = new CircuitBreakerRegistry(directory, events, ws);
        sessions = new SessionManager(routing, registry, breakers, events, ws);

        stub1 = mock(GameServerRemote.class);
        stub2 = mock(GameServerRemote.class);
        when(registry.lookupStub("GS-MUM-01")).thenReturn(stub1);
        when(registry.lookupStub("GS-MUM-02")).thenReturn(stub2);
        when(stub1.createSession(any())).thenAnswer(inv -> serverSession(inv.getArgument(0), "GS-MUM-01"));
        when(stub2.createSession(any())).thenAnswer(inv -> serverSession(inv.getArgument(0), "GS-MUM-02"));
    }

    private static GameSession serverSession(PlayerRequest request, String serverId) {
        GameSession s = new GameSession();
        s.setId(request.getSessionId());
        s.setPlayerId(request.getPlayerId());
        s.setGame(request.getGame());
        s.setServerId(serverId);
        s.setPlayerRegion(request.getPlayerRegion());
        s.setResolution(request.getResolution());
        s.setFps(request.getFps());
        s.setState(SessionState.ACTIVE);
        s.setLatencyMs(18.0);
        s.setStartTime(System.currentTimeMillis());
        return s;
    }

    private static RoutingDecision decisionSelecting(String serverId) {
        CandidateScore c = new CandidateScore();
        c.setServerId(serverId);
        c.setScore(20.0);
        c.setEligible(true);
        RoutingDecision d = new RoutingDecision();
        d.setId("RD-00001");
        d.setSelectedServerId(serverId);
        d.setCandidates(List.of(c));
        return d;
    }

    private PlayerRequest request(String playerId) {
        return new PlayerRequest(playerId, "Mumbai", "CYBERPUNK 2077", "1080p", 60);
    }

    @Test
    void createSessionRoutesAndStoresSession() {
        when(routing.route(any(PlayerRequest.class)))
                .thenReturn(decisionSelecting("GS-MUM-01"));

        GameSession session = sessions.createSession(request("P-1"));

        assertNotNull(session);
        assertEquals("GS-MUM-01", session.getServerId());
        assertEquals(SessionState.ACTIVE, session.getState());
        assertNotNull(sessions.get(session.getId()));
        verify(ws).publish(eq("SESSION_CREATED"), anyMap());
    }

    @Test
    void createSessionReturnsNullWhenNoCandidate() {
        RoutingDecision d = decisionSelecting(null);
        when(routing.route(any(PlayerRequest.class))).thenReturn(d);

        assertNull(sessions.createSession(request("P-1")));
        assertTrue(sessions.all().isEmpty());
    }

    @Test
    void failedRmiCandidateFallsOverToNextCandidate() throws Exception {
        CandidateScore c1 = new CandidateScore();
        c1.setServerId("GS-MUM-01");
        c1.setScore(10.0);
        c1.setEligible(true);
        CandidateScore c2 = new CandidateScore();
        c2.setServerId("GS-MUM-02");
        c2.setScore(20.0);
        c2.setEligible(true);
        RoutingDecision d = new RoutingDecision();
        d.setSelectedServerId("GS-MUM-01");
        d.setCandidates(List.of(c1, c2));
        when(routing.route(any(PlayerRequest.class))).thenReturn(d);
        doThrow(new java.rmi.RemoteException("crashed mid-call"))
                .when(stub1).createSession(any());

        GameSession session = sessions.createSession(request("P-9"));

        assertNotNull(session);
        assertEquals("GS-MUM-02", session.getServerId(),
                "must fail over to the next eligible candidate");
    }

    @Test
    void migrateSessionsFromReassignsToReplacementServer() {
        when(routing.route(any(PlayerRequest.class)))
                .thenReturn(decisionSelecting("GS-MUM-01"));
        GameSession s1 = sessions.createSession(request("P-1"));
        GameSession s2 = sessions.createSession(request("P-2"));
        assertEquals("GS-MUM-01", s1.getServerId());

        when(routing.route(any(PlayerRequest.class), eq(Set.of("GS-MUM-01"))))
                .thenReturn(decisionSelecting("GS-MUM-02"));

        sessions.migrateSessionsFrom("GS-MUM-01");

        assertEquals("GS-MUM-02", sessions.get(s1.getId()).getServerId());
        assertEquals("GS-MUM-02", sessions.get(s2.getId()).getServerId());
        assertEquals(SessionState.ACTIVE, sessions.get(s1.getId()).getState());
        verify(ws, times(2)).publish(eq("SESSION_MIGRATING"), anyMap());
        verify(ws, times(2)).publish(eq("SESSION_MIGRATED"), anyMap());
    }

    @Test
    void migrateWithNoReplacementMarksSessionFailed() {
        when(routing.route(any(PlayerRequest.class)))
                .thenReturn(decisionSelecting("GS-MUM-01"));
        GameSession s1 = sessions.createSession(request("P-1"));

        RoutingDecision noCandidate = decisionSelecting(null);
        when(routing.route(any(PlayerRequest.class), eq(Set.of("GS-MUM-01"))))
                .thenReturn(noCandidate);

        sessions.migrateSessionsFrom("GS-MUM-01");

        assertEquals(SessionState.FAILED, sessions.get(s1.getId()).getState());
    }
}
