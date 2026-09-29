package com.gameflow.api;

import com.gameflow.simulation.ScenarioEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioEngine scenarios;

    public ScenarioController(ScenarioEngine scenarios) {
        this.scenarios = scenarios;
    }

    @GetMapping
    public List<ScenarioEngine.ScenarioDescriptor> list() {
        return scenarios.getDescriptors();
    }

    @PostMapping("/{id}/start")
    public Map<String, Object> start(@PathVariable String id) {
        scenarios.start(id);
        return Map.of("started", true, "scenarioId", id);
    }

    @PostMapping("/{id}/stop")
    public Map<String, Object> stop(@PathVariable String id) {
        scenarios.stop(id);
        return Map.of("stopped", true);
    }
}
