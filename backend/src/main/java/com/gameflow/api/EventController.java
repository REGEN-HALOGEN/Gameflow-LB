package com.gameflow.api;

import com.gameflow.events.EventBus;
import com.gameflow.model.EventSeverity;
import com.gameflow.model.SystemEvent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventBus events;

    public EventController(EventBus events) {
        this.events = events;
    }

    @GetMapping
    public List<SystemEvent> events(@RequestParam(defaultValue = "100") int limit,
                                   @RequestParam(required = false) String severity) {
        EventSeverity filter = null;
        if (severity != null && !severity.isBlank()) {
            try {
                filter = EventSeverity.valueOf(severity.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown severity: " + severity);
            }
        }
        return events.recent(Math.min(500, Math.max(1, limit)), filter);
    }
}
