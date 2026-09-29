package com.gameflow.simulation;

import com.gameflow.events.EventBus;
import com.gameflow.health.HealthManager;
import com.gameflow.model.EventSeverity;
import com.gameflow.model.FaultType;
import com.gameflow.rmi.GameServerImpl;
import com.gameflow.rmi.GameServerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fault injection for demos: manipulates real simulation state on the
 * target server. Recovery clears faults, restores RMI, and hands the
 * server to the health manager for the RECOVERING → HEALTHY path.
 */
@Component
public class FaultInjector {

    private static final Logger log = LoggerFactory.getLogger(FaultInjector.class);

    private final GameServerRegistry registry;
    private final ServerDirectory directory;
    private final HealthManager healthManager;
    private final EventBus events;

    public FaultInjector(GameServerRegistry registry,
                         ServerDirectory directory,
                         HealthManager healthManager,
                         EventBus events) {
        this.registry = registry;
        this.directory = directory;
        this.healthManager = healthManager;
        this.events = events;
    }

    public void inject(String serverId, FaultType type) {
        if (directory.get(serverId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown server: " + serverId);
        }
        try {
            switch (type) {
                case GPU_OVERLOAD -> impl(serverId).setGpuTargetOverride(97.0);
                case LATENCY_SPIKE -> impl(serverId).setLatencyOffsetMs(165.0);
                case PACKET_LOSS -> impl(serverId).setPacketLossOverride(12.0);
                case RMI_FAILURE -> impl(serverId).setRmiFailure(true);
                case CRASH -> registry.crashServer(serverId);
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Fault injection failed: " + e.getMessage());
        }
        EventSeverity severity =
                (type == FaultType.CRASH || type == FaultType.RMI_FAILURE)
                        ? EventSeverity.CRITICAL : EventSeverity.WARN;
        events.publish("FAULT_INJECTED", severity,
                "Fault injected on " + serverId + ": " + type, serverId);
        log.info("Fault {} injected on {}", type, serverId);
    }

    public void recover(String serverId) {
        if (directory.get(serverId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown server: " + serverId);
        }
        try {
            if (registry.isCrashed(serverId)) {
                registry.restoreServer(serverId);
            }
            GameServerImpl impl = registry.getImpl(serverId);
            if (impl != null) {
                impl.clearFaults();
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Recovery failed: " + e.getMessage());
        }
        healthManager.markRecovering(serverId);
        events.info("FAULT_CLEARED", serverId + " faults cleared, recovery started", serverId);
        log.info("Recovery started for {}", serverId);
    }

    private GameServerImpl impl(String serverId) {
        GameServerImpl impl = registry.getImpl(serverId);
        if (impl == null || registry.isCrashed(serverId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Server " + serverId + " is crashed; recover it first");
        }
        return impl;
    }
}
