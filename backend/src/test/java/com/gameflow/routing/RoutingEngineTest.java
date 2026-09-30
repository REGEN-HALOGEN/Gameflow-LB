package com.gameflow.routing;

import com.gameflow.events.EventBus;
import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision;
import com.gameflow.model.ServerState;
import com.gameflow.rmi.GameServerRegistry;
import com.gameflow.simulation.ServerDirectory;
import com.gameflow.websocket.WsEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RoutingEngineTest {

    private ServerDirectory directory;
    private RoutingEngine engine;
    private EventBus events;

    @BeforeEach
    void setUp() {
        directory = new ServerDirectory();
        GameServerRegistry registry = mock(GameServerRegistry.class);
        events = mock(EventBus.class);
        WsEventPublisher ws = mock(WsEventPublisher.class);
        List<RoutingStrategy> strategies = List.of(
                new WeightedGamingStrategy(),
                new LeastSessionsStrategy(),
                new LowestLatencyStrategy(),
                new RoundRobinStrategy());
        com.gameflow.simulation.GameCatalog catalog = new com.gameflow.simulation.GameCatalog();
        engine = new RoutingEngine(directory, registry, events, ws, catalog, strategies);
    }

    private PlayerRequest request() {
        return new PlayerRequest("P-1", "Mumbai", "VALORANT", "1080p", 60);
    }

    @Test
    void noEligibleCandidatesProducesNullSelectionAndEvent() {
        for (var node : directory.all()) {
            node.setState(ServerState.OFFLINE);
        }

        RoutingDecision decision = engine.route(request());

        assertNull(decision.getSelectedServerId());
        assertEquals("No eligible game servers available.", decision.getReason());
        assertEquals(4, decision.getCandidates().size());
        assertTrue(decision.getCandidates().stream().noneMatch(c -> c.isEligible()));
        verify(events).error(eq("NO_ROUTING_CANDIDATES"), anyString(), isNull());
    }

    @Test
    void mumbaiPlayerPrefersMumbaiServers() {
        RoutingDecision decision = engine.route(request());
        assertNotNull(decision.getSelectedServerId());
        assertTrue(decision.getSelectedServerId().startsWith("GS-MUM-"),
                "expected a Mumbai server, got " + decision.getSelectedServerId());
        assertEquals(com.gameflow.model.RoutingStrategy.WEIGHTED_GAMING, decision.getStrategy());
        assertTrue(decision.getId().startsWith("RD-"));
    }

    @Test
    void decisionsAreRecordedNewestFirst() {
        engine.route(request());
        engine.route(request());
        List<RoutingDecision> recent = engine.recentDecisions(10);
        assertEquals(2, recent.size());
        assertTrue(recent.get(0).getTimestamp() >= recent.get(1).getTimestamp());
    }

    @Test
    void strategySwitchChangesScoring() {
        engine.setActiveStrategy(com.gameflow.model.RoutingStrategy.ROUND_ROBIN);
        RoutingDecision d1 = engine.route(request());
        RoutingDecision d2 = engine.route(request());
        assertEquals(com.gameflow.model.RoutingStrategy.ROUND_ROBIN, d1.getStrategy());
        // round robin must rotate across decisions
        assertNotEquals(d1.getSelectedServerId(), d2.getSelectedServerId());
    }

    @Test
    void excludedServersAreForceIneligible() {
        RoutingDecision decision = engine.route(request(), java.util.Set.of("GS-MUM-01", "GS-MUM-02"));
        assertNotNull(decision.getSelectedServerId());
        assertNotEquals("GS-MUM-01", decision.getSelectedServerId());
        assertNotEquals("GS-MUM-02", decision.getSelectedServerId());
    }
}
