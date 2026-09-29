package com.gameflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket endpoint at /ws/events. Every message is a JSON text frame:
 * {"type": "...", "timestamp": 1730000000000, ...payload}.
 * A multicast sink fans events out to all connected browsers.
 */
@Component
public class WsEventPublisher implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WsEventPublisher.class);

    private final Sinks.Many<String> sink =
            Sinks.many().multicast().onBackpressureBuffer(4096, false);
    private final ObjectMapper mapper;

    public WsEventPublisher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket client connected: {}", session.getId());
        return session.send(sink.asFlux().map(session::textMessage))
                .doFinally(sig -> log.info("WebSocket client disconnected: {}", session.getId()));
    }

    /** Publish an event: {"type", "timestamp", ...payload}. Thread-safe. */
    public void publish(String type, Map<String, Object> payload) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", type);
            message.put("timestamp", System.currentTimeMillis());
            message.putAll(payload);
            Sinks.EmitResult result = sink.tryEmitNext(mapper.writeValueAsString(message));
            if (result.isFailure()) {
                log.warn("WebSocket emit failed for {}: {}", type, result);
            }
        } catch (Exception e) {
            log.warn("Failed to serialize WebSocket message {}: {}", type, e.getMessage());
        }
    }
}
