package com.gameflow.api;

import com.gameflow.GameFlowApplication;
import com.gameflow.model.ServerNode;
import com.gameflow.model.ServerState;
import com.gameflow.model.SessionState;
import com.gameflow.routing.RoutingEngine;
import com.gameflow.session.SessionManager;
import com.gameflow.simulation.ServerDirectory;
import com.gameflow.simulation.SimulationEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SimulationEngine simulation;
    private final ServerDirectory directory;
    private final SessionManager sessions;
    private final RoutingEngine routing;

    public SystemController(SimulationEngine simulation, ServerDirectory directory,
                            SessionManager sessions, RoutingEngine routing) {
        this.simulation = simulation;
        this.directory = directory;
        this.sessions = sessions;
        this.routing = routing;
    }

    @GetMapping
    public Map<String, Object> system() {
        long activeSessions = sessions.all().stream()
                .filter(s -> s.getState() == SessionState.ACTIVE
                        || s.getState() == SessionState.CREATING
                        || s.getState() == SessionState.MIGRATING)
                .count();

        double rps = 0, lat = 0, gpu = 0, loss = 0;
        int healthy = 0, n = 0;
        for (ServerNode node : directory.all()) {
            if (node.getState() == ServerState.HEALTHY) {
                healthy++;
            }
            var m = node.getMetrics();
            rps += m.getRequestsPerSec();
            lat += m.getLatencyMs();
            gpu += m.getGpu();
            loss += m.getPacketLoss();
            n++;
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("activeSessions", activeSessions);
        totals.put("requestsPerSec", round1(rps));
        totals.put("avgLatencyMs", n == 0 ? 0 : round1(lat / n));
        totals.put("healthyServers", healthy);
        totals.put("avgGpu", n == 0 ? 0 : round1(gpu / n));
        totals.put("packetLoss", n == 0 ? 0 : round1(loss / n));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("simulationState", simulation.getState().name());
        body.put("speed", simulation.getSpeed());
        body.put("time", System.currentTimeMillis());
        body.put("uptimeSec", GameFlowApplication.uptimeSec());
        body.put("strategy", routing.getActiveStrategy().name());
        body.put("totals", totals);
        return body;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
