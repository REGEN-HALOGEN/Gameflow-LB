package com.gameflow.session;

import com.gameflow.events.EventBus;
import com.gameflow.health.CircuitBreakerRegistry;
import com.gameflow.model.EventSeverity;
import com.gameflow.model.GameSession;
import com.gameflow.model.PlayerRequest;
import com.gameflow.model.RoutingDecision;
import com.gameflow.model.RoutingDecision.CandidateScore;
import com.gameflow.model.SessionState;
import com.gameflow.rmi.GameServerRegistry;
import com.gameflow.rmi.GameServerRemote;
import com.gameflow.routing.RoutingEngine;
import com.gameflow.websocket.WsEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Authoritative session store (ConcurrentHashMap). Creates sessions by
 * running the routing engine and calling the chosen server over RMI;
 * on server failure, re-routes affected sessions (orchestrated
 * reassignment — not VM memory migration).
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong sessionSeq = new AtomicLong(10000);
    
    private final java.util.concurrent.PriorityBlockingQueue<PlayerRequest> waitQueue = new java.util.concurrent.PriorityBlockingQueue<>(
            100,
            Comparator.comparing((PlayerRequest r) -> !r.isVip())
                      .thenComparingLong(PlayerRequest::getQueuedAt)
    );

    private final RoutingEngine routingEngine;
    private final GameServerRegistry registry;
    private final CircuitBreakerRegistry breakers;
    private final EventBus events;
    private final WsEventPublisher ws;

    public SessionManager(RoutingEngine routingEngine,
                          GameServerRegistry registry,
                          CircuitBreakerRegistry breakers,
                          EventBus events,
                          WsEventPublisher ws) {
        this.routingEngine = routingEngine;
        this.registry = registry;
        this.breakers = breakers;
        this.events = events;
        this.ws = ws;
    }

    /**
     * Route the request and establish the session on the chosen server.
     * Returns null when no server could take the session.
     */
    public GameSession createSession(PlayerRequest request) {
        if (request.getSessionId() == null) {
            String sessionId = String.format("S-%05d", sessionSeq.incrementAndGet());
            request.setSessionId(sessionId);
        }

        RoutingDecision decision = routingEngine.route(request);
        if (decision.getSelectedServerId() != null) {
            List<CandidateScore> eligible = decision.getCandidates().stream()
                    .filter(CandidateScore::isEligible)
                    .sorted(Comparator.comparingDouble(CandidateScore::getScore))
                    .toList();

            for (CandidateScore candidate : eligible) {
                String serverId = candidate.getServerId();
                try {
                    GameServerRemote stub = registry.lookupStub(serverId);
                    if (stub == null) {
                        throw new RemoteException("No RMI stub for " + serverId);
                    }
                    GameSession session = stub.createSession(request);
                    session.setVip(request.isVip());
                    session.setState(SessionState.ACTIVE);
                    sessions.put(session.getId(), session);
                    events.info("SESSION_CREATED",
                            request.getPlayerId() + " → " + serverId + (request.isVip() ? " [VIP]" : ""), serverId);
                    ws.publish("SESSION_CREATED", Map.of("session", session));
                    return session;
                } catch (RemoteException e) {
                    log.warn("createSession RMI failed on {}: {}", serverId, e.getMessage());
                    breakers.get(serverId).recordFailure();
                    // Keep trying other eligible candidates
                }
            }
        }

        // If we exhausted all candidates or none were available, queue it.
        if (request.getQueuedAt() == 0) {
            request.setQueuedAt(System.currentTimeMillis());
        }
        waitQueue.offer(request);
        events.info("SESSION_QUEUED", 
            request.getPlayerId() + " added to wait queue (size: " + waitQueue.size() + ")", null);
        broadcastQueueState();
        return null;
    }

    public void processWaitQueue() {
        if (waitQueue.isEmpty()) return;
        
        // Try to place up to 5 queued sessions per tick to avoid overwhelming
        int attempts = Math.min(5, waitQueue.size());
        List<PlayerRequest> requeue = new ArrayList<>();
        
        for (int i = 0; i < attempts; i++) {
            PlayerRequest req = waitQueue.poll();
            if (req == null) break;
            
            GameSession s = createSession(req);
            if (s == null) {
                // Not placed, don't put back in waitQueue immediately because createSession already did!
                // Wait, createSession calls waitQueue.offer(request) again if it fails!
                // So it's already back in the queue.
            }
        }
    }

    private void broadcastQueueState() {
        int vipCount = (int) waitQueue.stream().filter(PlayerRequest::isVip).count();
        ws.publish("WAIT_QUEUE_UPDATED", Map.of(
            "size", waitQueue.size(),
            "vipCount", vipCount
        ));
    }

    public GameSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    public List<GameSession> all() {
        return new ArrayList<>(sessions.values());
    }

    public void terminateSession(String sessionId) {
        GameSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        session.setState(SessionState.TERMINATING);
        try {
            GameServerRemote stub = registry.lookupStub(session.getServerId());
            if (stub != null) {
                stub.terminateSession(sessionId);
            }
        } catch (RemoteException e) {
            log.warn("terminateSession RMI failed for {}: {}", sessionId, e.getMessage());
        }
        session.setState(SessionState.TERMINATED);
        sessions.remove(sessionId);
        events.info("SESSION_TERMINATED",
                session.getPlayerId() + " session " + sessionId + " ended", session.getServerId());
        ws.publish("SESSION_TERMINATED", Map.of(
                "sessionId", sessionId,
                "playerId", session.getPlayerId(),
                "serverId", session.getServerId()));
    }

    /**
     * Reassign every live session hosted on a failed server.
     * Marks sessions MIGRATING, re-runs routing (excluding the failed
     * server), and marks them ACTIVE on the replacement.
     */
    public void migrateSessionsFrom(String failedServerId) {
        List<GameSession> affected = sessions.values().stream()
                .filter(s -> failedServerId.equals(s.getServerId()))
                .filter(s -> s.getState() == SessionState.ACTIVE
                        || s.getState() == SessionState.CREATING)
                .toList();
        if (affected.isEmpty()) {
            return;
        }
        events.warn("SESSION_MIGRATION",
                affected.size() + " sessions being reassigned from " + failedServerId,
                failedServerId);

        for (GameSession session : affected) {
            session.setState(SessionState.MIGRATING);
            ws.publish("SESSION_MIGRATING", migrationPayload(session, failedServerId, null));

            PlayerRequest request = new PlayerRequest(
                    session.getPlayerId(), session.getPlayerRegion(),
                    session.getGame(), session.getResolution(), session.getFps());
            request.setSessionId(session.getId());

            RoutingDecision decision = routingEngine.route(request, Set.of(failedServerId));
            String target = decision.getSelectedServerId();
            if (target == null) {
                session.setState(SessionState.FAILED);
                events.error("SESSION_MIGRATION_FAILED",
                        "No replacement server for " + session.getId(), failedServerId);
                continue;
            }
            try {
                // Best-effort cleanup on the dead server; it will usually throw.
                try {
                    GameServerRemote old = registry.lookupStub(failedServerId);
                    if (old != null) {
                        old.terminateSession(session.getId());
                    }
                } catch (Exception ignored) {
                }
                GameServerRemote stub = registry.lookupStub(target);
                GameSession created = stub.createSession(request);
                session.setServerId(target);
                session.setLatencyMs(created.getLatencyMs());
                session.setState(SessionState.ACTIVE);
                events.info("SESSION_MIGRATED",
                        session.getId() + ": " + failedServerId + " → " + target, target);
                ws.publish("SESSION_MIGRATED", migrationPayload(session, failedServerId, target));
            } catch (RemoteException e) {
                session.setState(SessionState.FAILED);
                events.error("SESSION_MIGRATION_FAILED",
                        "Migration of " + session.getId() + " to " + target + " failed: "
                                + e.getMessage(), target);
            }
        }
        events.info("SESSION_MIGRATION_COMPLETE",
                "Reassignment from " + failedServerId + " finished", failedServerId);
    }

    private static Map<String, Object> migrationPayload(GameSession session,
                                                        String from, String to) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("playerId", session.getPlayerId());
        payload.put("fromServerId", from);
        payload.put("toServerId", to);
        return payload;
    }

    /** Quietly drop everything (simulation reset). */
    public void clearAll() {
        for (GameSession session : sessions.values()) {
            try {
                GameServerRemote stub = registry.lookupStub(session.getServerId());
                if (stub != null) {
                    stub.terminateSession(session.getId());
                }
            } catch (Exception ignored) {
            }
        }
        sessions.clear();
    }
}
