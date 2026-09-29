package com.gameflow.api;

import com.gameflow.model.RoutingDecision;
import com.gameflow.model.RoutingStrategy;
import com.gameflow.routing.RoutingEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {

    private final RoutingEngine routing;

    public RoutingController(RoutingEngine routing) {
        this.routing = routing;
    }

    @GetMapping("/decisions")
    public List<RoutingDecision> decisions(
            @RequestParam(defaultValue = "50") int limit) {
        return routing.recentDecisions(Math.min(200, Math.max(1, limit)));
    }

    @GetMapping("/strategy")
    public Map<String, String> getStrategy() {
        return Map.of("strategy", routing.getActiveStrategy().name());
    }

    @PutMapping("/strategy")
    public Map<String, String> setStrategy(@RequestBody StrategyRequest body) {
        routing.setActiveStrategy(body.strategy());
        return Map.of("strategy", routing.getActiveStrategy().name());
    }

    public record StrategyRequest(RoutingStrategy strategy) {
    }
}
