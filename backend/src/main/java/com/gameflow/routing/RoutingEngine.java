package com.gameflow.routing;

import com.gameflow.events.EventBus;
import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.ServerNode;
import com.gameflow.rmi.GameServerRegistry;
import com.gameflow.simulation.LatencyMatrix;
import com.gameflow.simulation.ServerDirectory;
import com.gameflow.websocket.WsEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs routing strategies and records every decision. For each request it
 * scores every server (player-specific latency from the latency matrix plus
 * any injected fault offset), applies exclusion rules, and picks the
 * lowest eligible score.
 */
@Component
public class RoutingEngine {

    private static final int DECISION_HISTORY = 200;

    private final ServerDirectory directory;
    private final GameServerRegistry registry;
    private final EventBus events;
    private final WsEventPublisher ws;
    private final com.gameflow.simulation.GameCatalog gameCatalog;

    private final Map<com.gameflow.model.RoutingStrategy, RoutingStrategy> strategies = new HashMap<>();
    private volatile com.gameflow.model.RoutingStrategy activeStrategy =
            com.gameflow.model.RoutingStrategy.WEIGHTED_GAMING;

    private final Deque<RoutingDecision> decisions = new ArrayDeque<>();
    private final AtomicLong decisionSeq = new AtomicLong(0);

    public RoutingEngine(ServerDirectory directory,
                         GameServerRegistry registry,
                         EventBus events,
                         WsEventPublisher ws,
                         com.gameflow.simulation.GameCatalog gameCatalog,
                         List<RoutingStrategy> strategyBeans) {
        this.directory = directory;
        this.registry = registry;
        this.events = events;
        this.ws = ws;
        this.gameCatalog = gameCatalog;
        for (RoutingStrategy s : strategyBeans) {
            strategies.put(s.getType(), s);
        }
    }

    public com.gameflow.model.RoutingStrategy getActiveStrategy() {
        return activeStrategy;
    }

    public void setActiveStrategy(com.gameflow.model.RoutingStrategy strategy) {
        if (!strategies.containsKey(strategy)) {
            throw new IllegalArgumentException("Unknown routing strategy: " + strategy);
        }
        com.gameflow.model.RoutingStrategy old = this.activeStrategy;
        this.activeStrategy = strategy;
        if (old != strategy) {
            events.info("STRATEGY_CHANGED",
                    "Routing strategy changed: " + old + " → " + strategy, null);
        }
    }

    /** Route a request. excludedServerIds are force-ineligible (e.g. failed server). */
    public synchronized RoutingDecision route(PlayerRequest request, Set<String> excluded) {
        RoutingStrategy strategy = strategies.get(activeStrategy);
        List<ServerNode> nodes = new ArrayList<>(directory.all());

        com.gameflow.model.GameProfile gameProfile = gameCatalog.byName(request.getGame());
        String reqHardware = gameProfile != null ? gameProfile.getRequiredHardwareTier() : null;

        List<CandidateScore> candidates = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            ServerNode node = nodes.get(i);
            ServerMetrics metrics = node.getMetrics();
            double playerLatency = playerLatencyMs(request.getPlayerRegion(), node.getId());
            CandidateScore candidate = strategy.evaluate(request, node, playerLatency, i, nodes.size());
            if (excluded.contains(node.getId())) {
                candidate.setEligible(false);
                candidate.setPenaltyReason("SERVER OFFLINE");
                candidate.setScore(round1(candidate.getScore()
                        + WeightedGamingStrategy.INELIGIBLE_PENALTY));
                candidate.getScoreBreakdown().merge("penalty",
                        WeightedGamingStrategy.INELIGIBLE_PENALTY, Double::sum);
            } else if (reqHardware != null && !reqHardware.equals(node.getHardwareTier())) {
                candidate.setEligible(false);
                candidate.setPenaltyReason("HARDWARE MISMATCH");
                candidate.setScore(round1(candidate.getScore() + WeightedGamingStrategy.INELIGIBLE_PENALTY));
                candidate.getScoreBreakdown().merge("penalty", WeightedGamingStrategy.INELIGIBLE_PENALTY, Double::sum);
            }
            // Eligibility for ineligible candidates is already set by the strategy;
            // keep the metrics snapshot on the candidate honest.
            candidate.setCpu(metrics.getCpu());
            candidate.setGpu(metrics.getGpu());
            candidate.setPacketLoss(metrics.getPacketLoss());
            candidate.setSessions(metrics.getSessions());
            candidates.add(candidate);
        }

        CandidateScore best = candidates.stream()
                .filter(CandidateScore::isEligible)
                .min(Comparator.comparingDouble(CandidateScore::getScore))
                .orElse(null);

        RoutingDecision decision = new RoutingDecision();
        decision.setId(String.format("RD-%05d", decisionSeq.incrementAndGet()));
        decision.setTimestamp(System.currentTimeMillis());
        decision.setPlayerId(request.getPlayerId());
        decision.setGame(request.getGame());
        decision.setPlayerRegion(request.getPlayerRegion());
        decision.setResolution(request.getResolution());
        decision.setFps(request.getFps());
        decision.setStrategy(activeStrategy);
        decision.setCandidates(candidates);

        if (best == null) {
            decision.setSelectedServerId(null);
            decision.setReason("No eligible game servers available.");
            events.error("NO_ROUTING_CANDIDATES",
                    "No eligible server for " + request.getPlayerId()
                            + " (" + request.getGame() + ", " + request.getPlayerRegion() + ")", null);
        } else {
            decision.setSelectedServerId(best.getServerId());
            decision.setReason("Lowest eligible composite gaming score (" + best.getScore() + ").");
            events.info("ROUTING_DECISION",
                    request.getPlayerId() + " → " + best.getServerId()
                            + " [" + activeStrategy + "]", best.getServerId());
        }

        strategy.afterDecision();
        record(decision);
        ws.publish("ROUTING_DECISION", Map.of("decision", decision));
        return decision;
    }

    public RoutingDecision route(PlayerRequest request) {
        return route(request, Set.of());
    }

    /** Player-specific latency: geography + injected fault offset + jitter. */
    private double playerLatencyMs(String playerCity, String serverId) {
        double base = LatencyMatrix.baseLatency(playerCity, serverId);
        double offset = 0;
        var impl = registry.getImpl(serverId);
        if (impl != null) {
            offset = impl.getLatencyOffsetMs();
        }
        double jitter = ThreadLocalRandom.current().nextDouble(-2.5, 2.5);
        return Math.max(1, base + offset + jitter);
    }

    private void record(RoutingDecision decision) {
        decisions.addLast(decision);
        while (decisions.size() > DECISION_HISTORY) {
            decisions.removeFirst();
        }
    }

    /** Newest first. */
    public synchronized List<RoutingDecision> recentDecisions(int limit) {
        List<RoutingDecision> result = new ArrayList<>();
        var it = decisions.descendingIterator();
        while (it.hasNext() && result.size() < limit) {
            result.add(it.next());
        }
        return result;
    }

    public synchronized void clearDecisions() {
        decisions.clear();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
