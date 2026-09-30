package com.gameflow.routing;

import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.ServerNode;
import com.gameflow.model.ServerState;
import com.gameflow.model.CircuitState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeightedGamingStrategyTest {

    private WeightedGamingStrategy strategy;
    private PlayerRequest request;

    @BeforeEach
    void setUp() {
        strategy = new WeightedGamingStrategy();
        request = new PlayerRequest("P-1042", "Mumbai", "NEON STRIKE", "1080p", 60);
    }

    private ServerNode node(double cpu, double gpu, double packetLoss, int sessions, double jitter) {
        ServerNode node = new ServerNode("GS-MUM-01", "MUMBAI", "Mumbai", 60, 2.50, "RTX_3080");
        ServerMetrics m = new ServerMetrics();
        m.setCpu(cpu);
        m.setGpu(gpu);
        m.setPacketLoss(packetLoss);
        m.setSessions(sessions);
        m.setJitterMs(jitter);
        node.setMetrics(m);
        return node;
    }

    @Test
    void scoreCalculationMatchesWeightedFormula() {
        // cpu 42, gpu 51, loss 0.2, sessions 31/60, jitter 2, latency 18
        // 0.18*35 + 0.42*20 + 0.51*20 + 0.04*10 + 0.5167*10 + 0.10*5
        // = 6.3 + 8.4 + 10.2 + 0.4 + 5.2 + 0.5 = 31.0
        ServerNode node = node(42.0, 51.0, 0.2, 31, 2.0);

        CandidateScore c = strategy.evaluate(request, node, 18.0, 0, 4);

        assertTrue(c.isEligible());
        assertNull(c.getPenaltyReason());
        assertEquals(6.3, c.getScoreBreakdown().get("latency"), 0.0001);
        assertEquals(8.4, c.getScoreBreakdown().get("cpu"), 0.0001);
        assertEquals(10.2, c.getScoreBreakdown().get("gpu"), 0.0001);
        assertEquals(0.4, c.getScoreBreakdown().get("packetLoss"), 0.0001);
        assertEquals(5.2, c.getScoreBreakdown().get("sessions"), 0.0001);
        assertEquals(0.5, c.getScoreBreakdown().get("jitter"), 0.0001);
        assertEquals(0.0, c.getScoreBreakdown().get("penalty"), 0.0001);
        assertEquals(31.0, c.getScore(), 0.0001);
    }

    @Test
    void breakdownSumsToScore() {
        ServerNode node = node(77.0, 63.0, 1.4, 45, 6.0);
        CandidateScore c = strategy.evaluate(request, node, 55.0, 0, 4);
        double sum = c.getScoreBreakdown().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        assertEquals(sum, c.getScore(), 0.0001);
    }

    @Test
    void latencyDominatesIdenticalServers() {
        ServerNode near = node(40.0, 50.0, 0.2, 30, 2.0);
        ServerNode far = node(40.0, 50.0, 0.2, 30, 2.0);
        double nearScore = strategy.evaluate(request, near, 18.0, 0, 4).getScore();
        double farScore = strategy.evaluate(request, far, 65.0, 0, 4).getScore();
        assertTrue(nearScore < farScore,
                "lower latency must win: " + nearScore + " vs " + farScore);
        // latency weight (0.35) outweighs any single other factor
        assertTrue(farScore - nearScore > 10.0);
    }

    @Test
    void gpuOverloadExcludesServer() {
        ServerNode node = node(42.0, 94.0, 0.2, 31, 2.0);
        CandidateScore c = strategy.evaluate(request, node, 18.0, 0, 4);
        assertFalse(c.isEligible());
        assertEquals("GPU SATURATION (94%)", c.getPenaltyReason());
        assertEquals(WeightedGamingStrategy.INELIGIBLE_PENALTY,
                c.getScoreBreakdown().get("penalty"), 0.0001);
    }

    @Test
    void packetLossOverThresholdExcludesServer() {
        ServerNode node = node(42.0, 51.0, 7.2, 31, 2.0);
        CandidateScore c = strategy.evaluate(request, node, 18.0, 0, 4);
        assertFalse(c.isEligible());
        assertEquals("PACKET LOSS 7.2% > 5%", c.getPenaltyReason());
    }

    @Test
    void highLatencyExcludesServer() {
        ServerNode node = node(42.0, 51.0, 0.2, 31, 2.0);
        CandidateScore c = strategy.evaluate(request, node, 142.0, 0, 4);
        assertFalse(c.isEligible());
        assertEquals("LATENCY 142ms > 100ms", c.getPenaltyReason());
    }

    @Test
    void openCircuitExcludesServer() {
        ServerNode node = node(42.0, 51.0, 0.2, 31, 2.0);
        node.setCircuitState(CircuitState.OPEN);
        CandidateScore c = strategy.evaluate(request, node, 18.0, 0, 4);
        assertFalse(c.isEligible());
        assertEquals("CIRCUIT OPEN", c.getPenaltyReason());
    }

    @Test
    void offlineServerExcluded() {
        ServerNode node = node(42.0, 51.0, 0.2, 31, 2.0);
        node.setState(ServerState.OFFLINE);
        CandidateScore c = strategy.evaluate(request, node, 18.0, 0, 4);
        assertFalse(c.isEligible());
        assertEquals("SERVER OFFLINE", c.getPenaltyReason());
    }

    @Test
    void recoveringServerExcluded() {
        ServerNode node = node(42.0, 51.0, 0.2, 31, 2.0);
        node.setState(ServerState.RECOVERING);
        CandidateScore c = strategy.evaluate(request, node, 18.0, 0, 4);
        assertFalse(c.isEligible());
        assertEquals("SERVER RECOVERING", c.getPenaltyReason());
    }

    @Test
    void metricsAreNormalizedAndCapped() {
        // absurd values must not produce a score above 100 + penalty
        ServerNode node = node(400.0, 500.0, 60.0, 600, 200.0);
        CandidateScore c = strategy.evaluate(request, node, 900.0, 0, 4);
        assertEquals(100.0 + WeightedGamingStrategy.INELIGIBLE_PENALTY, c.getScore(), 0.0001);
    }
}
