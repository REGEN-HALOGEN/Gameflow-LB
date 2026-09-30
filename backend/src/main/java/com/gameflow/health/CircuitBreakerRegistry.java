package com.gameflow.health;

import com.gameflow.events.EventBus;
import com.gameflow.simulation.ServerDirectory;
import com.gameflow.websocket.WsEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Owns one CircuitBreaker per game server. */
@Component
public class CircuitBreakerRegistry {

    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public CircuitBreakerRegistry(ServerDirectory directory, EventBus events, WsEventPublisher ws) {
        for (var node : directory.all()) {
            breakers.put(node.getId(), new CircuitBreaker(node.getId(), events, ws));
        }
    }

    public CircuitBreaker get(String serverId) {
        return breakers.get(serverId);
    }

    public void resetAll() {
        breakers.values().forEach(CircuitBreaker::reset);
    }

    public void register(String serverId, EventBus events, WsEventPublisher ws) {
        breakers.putIfAbsent(serverId, new CircuitBreaker(serverId, events, ws));
    }

    public void unregister(String serverId) {
        breakers.remove(serverId);
    }
}
