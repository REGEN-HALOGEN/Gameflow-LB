package com.gameflow.api;

import com.gameflow.model.GameSession;
import com.gameflow.model.SessionState;
import com.gameflow.session.SessionManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionManager sessions;

    public SessionController(SessionManager sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    public List<GameSession> list(@RequestParam(required = false) String state,
                                  @RequestParam(required = false) String serverId,
                                  @RequestParam(required = false) String search) {
        SessionState stateFilter = null;
        if (state != null && !state.isBlank()) {
            try {
                stateFilter = SessionState.valueOf(state.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown session state: " + state);
            }
        }
        SessionState sf = stateFilter;
        String q = search == null ? null : search.toLowerCase();
        return sessions.all().stream()
                .filter(s -> sf == null || s.getState() == sf)
                .filter(s -> serverId == null || serverId.equals(s.getServerId()))
                .filter(s -> q == null
                        || s.getId().toLowerCase().contains(q)
                        || s.getPlayerId().toLowerCase().contains(q)
                        || s.getGame().toLowerCase().contains(q))
                .sorted((a, b) -> Long.compare(b.getStartTime(), a.getStartTime()))
                .toList();
    }

    @GetMapping("/{id}")
    public GameSession get(@PathVariable String id) {
        GameSession session = sessions.get(id);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown session: " + id);
        }
        return session;
    }

    /** End a session now (player disconnect). Broadcasts SESSION_TERMINATED. */
    @DeleteMapping("/{id}")
    public Map<String, Object> terminate(@PathVariable String id) {
        if (sessions.get(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown session: " + id);
        }
        sessions.terminateSession(id);
        return Map.of("terminated", true, "sessionId", id);
    }
}
