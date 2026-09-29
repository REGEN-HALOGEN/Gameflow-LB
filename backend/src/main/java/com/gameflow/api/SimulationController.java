package com.gameflow.api;

import com.gameflow.simulation.SimulationEngine;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationEngine simulation;

    public SimulationController(SimulationEngine simulation) {
        this.simulation = simulation;
    }

    @PostMapping("/start")
    public Map<String, String> start() {
        simulation.start();
        return Map.of("state", simulation.getState().name());
    }

    @PostMapping("/pause")
    public Map<String, String> pause() {
        simulation.pause();
        return Map.of("state", simulation.getState().name());
    }

    @PostMapping("/resume")
    public Map<String, String> resume() {
        simulation.resume();
        return Map.of("state", simulation.getState().name());
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        simulation.reset();
        return Map.of("state", simulation.getState().name());
    }

    @PutMapping("/speed")
    public Map<String, Double> speed(@RequestBody SpeedRequest body) {
        simulation.setSpeed(body.speed());
        return Map.of("speed", simulation.getSpeed());
    }

    /** Interactive traffic dial: target concurrent player sessions (0..1000). */
    @PutMapping("/traffic")
    public Map<String, Integer> traffic(@RequestBody TrafficRequest body) {
        simulation.setTrafficTarget(body.targetSessions());
        return Map.of("targetSessions", simulation.getTrafficTarget());
    }

    public record SpeedRequest(double speed) {
    }

    public record TrafficRequest(int targetSessions) {
    }
}
