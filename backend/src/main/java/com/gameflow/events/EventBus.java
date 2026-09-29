package com.gameflow.events;

import com.gameflow.model.EventSeverity;
import com.gameflow.model.SystemEvent;
import com.gameflow.websocket.WsEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central event bus: creates SystemEvents (EVT-%06d), keeps the last 500
 * in a ring buffer, and forwards each to the WebSocket as SYSTEM_EVENT.
 */
@Component
public class EventBus {

    private static final int CAPACITY = 500;

    private final WsEventPublisher ws;
    private final Deque<SystemEvent> events = new ArrayDeque<>();
    private final AtomicLong seq = new AtomicLong(0);

    public EventBus(WsEventPublisher ws) {
        this.ws = ws;
    }

    public synchronized SystemEvent publish(String type, EventSeverity severity,
                                            String message, String serverId) {
        String id = String.format("EVT-%06d", seq.incrementAndGet());
        SystemEvent event = new SystemEvent(id, System.currentTimeMillis(),
                type, severity, message, serverId);
        events.addLast(event);
        while (events.size() > CAPACITY) {
            events.removeFirst();
        }
        ws.publish("SYSTEM_EVENT", Map.of("event", event));
        return event;
    }

    public SystemEvent info(String type, String message, String serverId) {
        return publish(type, EventSeverity.INFO, message, serverId);
    }

    public SystemEvent warn(String type, String message, String serverId) {
        return publish(type, EventSeverity.WARN, message, serverId);
    }

    public SystemEvent error(String type, String message, String serverId) {
        return publish(type, EventSeverity.ERROR, message, serverId);
    }

    public SystemEvent critical(String type, String message, String serverId) {
        return publish(type, EventSeverity.CRITICAL, message, serverId);
    }

    /** Newest first, optional severity filter. */
    public synchronized List<SystemEvent> recent(int limit, EventSeverity severity) {
        List<SystemEvent> result = new ArrayList<>();
        var it = events.descendingIterator();
        while (it.hasNext() && result.size() < limit) {
            SystemEvent e = it.next();
            if (severity == null || e.getSeverity() == severity) {
                result.add(e);
            }
        }
        return result;
    }

    public synchronized void clear() {
        events.clear();
    }
}
