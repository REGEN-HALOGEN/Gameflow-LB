package com.gameflow.api;

import com.gameflow.simulation.CustomScenario;
import com.gameflow.simulation.ScenarioEngine;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** Create a user-defined scenario: {name, description?, steps:[...]}. */
    @PostMapping("/custom")
    public ScenarioEngine.ScenarioDescriptor createCustom(@RequestBody CustomScenarioRequest body) {
        if (body == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return scenarios.createCustom(body.name(), body.description(), body.steps());
    }

    /** Delete a user-defined scenario (stops it first if running). */
    @DeleteMapping("/custom/{id}")
    public Map<String, Object> deleteCustom(@PathVariable String id) {
        scenarios.deleteCustom(id);
        return Map.of("deleted", true, "scenarioId", id);
    }

    public record CustomScenarioRequest(String name, String description,
                                        java.util.List<CustomScenario.Step> steps) {
    }
}
