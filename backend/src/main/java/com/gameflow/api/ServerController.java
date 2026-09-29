package com.gameflow.api;

import com.gameflow.model.FaultType;
import com.gameflow.model.ServerNode;
import com.gameflow.simulation.FaultInjector;
import com.gameflow.simulation.ServerDirectory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    private final ServerDirectory directory;
    private final FaultInjector faults;

    public ServerController(ServerDirectory directory, FaultInjector faults) {
        this.directory = directory;
        this.faults = faults;
    }

    @GetMapping
    public List<ServerNode> servers() {
        return new ArrayList<>(directory.all());
    }

    @GetMapping("/{id}")
    public ServerNode server(@PathVariable String id) {
        ServerNode node = directory.get(id);
        if (node == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown server: " + id);
        }
        return node;
    }

    @PostMapping("/{id}/fault")
    public Map<String, Object> injectFault(@PathVariable String id,
                                           @RequestBody FaultRequest body) {
        if (body == null || body.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Body must be {\"type\": \"GPU_OVERLOAD|LATENCY_SPIKE|PACKET_LOSS|RMI_FAILURE|CRASH\"}");
        }
        faults.inject(id, body.type());
        return Map.of("injected", true, "type", body.type().name());
    }

    @PostMapping("/{id}/recover")
    public Map<String, Object> recover(@PathVariable String id) {
        faults.recover(id);
        return Map.of("recovering", true);
    }

    public record FaultRequest(FaultType type) {
    }
}
