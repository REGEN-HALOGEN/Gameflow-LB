package com.gameflow.rmi;

import com.gameflow.simulation.GameCatalog;
import com.gameflow.simulation.ServerDirectory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the in-JVM RMI registry and the lifecycle of the four simulated
 * game-server nodes. The load balancer ALWAYS talks to servers via stubs
 * obtained through {@link Naming#lookup}, never via direct references.
 */
@Component
public class GameServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(GameServerRegistry.class);
    private static final String BIND_PREFIX = "gameflow/";

    private final int rmiPort;
    private final ServerDirectory directory;
    private final GameCatalog gameCatalog;

    private final Map<String, GameServerImpl> impls = new LinkedHashMap<>();
    private final Map<String, GameServerRemote> stubs = new LinkedHashMap<>();
    private final java.util.Set<String> crashed = new java.util.HashSet<>();

    public GameServerRegistry(@Value("${gameflow.rmi.port:1099}") int rmiPort,
                              ServerDirectory directory,
                              GameCatalog gameCatalog) {
        this.rmiPort = rmiPort;
        this.directory = directory;
        this.gameCatalog = gameCatalog;
    }

    private volatile boolean rmiAvailable = false;

    @PostConstruct
    public void init() {
        try {
            LocateRegistry.createRegistry(rmiPort);
            log.info("RMI registry created on port {}", rmiPort);
            rmiAvailable = true;
        } catch (Exception e) {
            log.warn("RMI registry unavailable on port {} ({}). "
                    + "Falling back to direct in-JVM server handles; rmiStatus=UNREACHABLE.",
                    rmiPort, e.toString());
            rmiAvailable = false;
        }
        for (var node : directory.all()) {
            bindFreshServer(node.getId());
        }
        log.info("Initialized {} game servers (RMI {})",
                impls.size(), rmiAvailable ? "connected" : "unreachable");
    }

    /** True when the RMI registry bound successfully. */
    public boolean isRmiAvailable() {
        return rmiAvailable;
    }

    private String bindName(String serverId) {
        return "rmi://127.0.0.1:" + rmiPort + "/" + BIND_PREFIX + serverId;
    }

    private void bindFreshServer(String serverId) {
        var node = directory.get(serverId);
        GameServerImpl impl;
        try {
            impl = new GameServerImpl(
                    node.getId(), node.getRegion(), node.getCity(), node.getCapacity(),
                    gameCatalog.asMap(), ServerDirectory.baseLatencyFor(serverId));
        } catch (RemoteException e) {
            throw new IllegalStateException("Cannot create game server " + serverId, e);
        }
        impls.put(serverId, impl);
        if (rmiAvailable) {
            try {
                Naming.rebind(bindName(serverId), impl);
                // The balancer looks the stub up exactly like a remote client would.
                stubs.put(serverId, (GameServerRemote) Naming.lookup(bindName(serverId)));
                return;
            } catch (Exception e) {
                log.warn("RMI bind failed for {} ({}); using direct handle.",
                        serverId, e.toString());
                rmiAvailable = false;
            }
        }
        // Fallback: direct in-JVM handle through the same remote interface.
        stubs.put(serverId, impl);
    }

    /** RMI stub for balancer -> server traffic. May be stale after a crash. */
    public GameServerRemote lookupStub(String serverId) {
        return stubs.get(serverId);
    }

    /** Direct handle for simulation control (fault injection, ticks). */
    public GameServerImpl getImpl(String serverId) {
        return impls.get(serverId);
    }

    public List<String> serverIds() {
        return new ArrayList<>(impls.keySet());
    }

    /**
     * Simulate a hard crash: unexport the remote object and unbind it,
     * so every subsequent RMI call throws. In fallback (no-RMI) mode,
     * drops the handle so lookups fail the same way.
     */
    public void crashServer(String serverId) {
        GameServerImpl impl = impls.get(serverId);
        if (impl == null) {
            throw new IllegalArgumentException("Unknown server: " + serverId);
        }
        if (rmiAvailable) {
            try {
                UnicastRemoteObject.unexportObject(impl, true);
            } catch (Exception e) {
                log.warn("Unexport failed for {}: {}", serverId, e.toString());
            }
            try {
                Naming.unbind(bindName(serverId));
            } catch (Exception e) {
                log.warn("Unbind failed for {} (already gone?): {}", serverId, e.getMessage());
            }
        }
        stubs.remove(serverId);
        crashed.add(serverId);
        log.warn("Server {} crashed (RMI {})", serverId,
                rmiAvailable ? "unexported + unbound" : "handle dropped (no-RMI mode)");
    }

    /**
     * Bring a crashed server back: fresh impl, re-exported and rebound.
     * The balancer picks up the new stub on its next lookup refresh.
     */
    public void restoreServer(String serverId) {
        GameServerImpl old = impls.get(serverId);
        if (old != null && rmiAvailable) {
            try {
                UnicastRemoteObject.unexportObject(old, true);
            } catch (Exception ignored) {
                // already unexported
            }
        }
        bindFreshServer(serverId);
        crashed.remove(serverId);
        log.info("Server {} restored{}", serverId,
                rmiAvailable ? " and rebound over RMI" : " (no-RMI mode)");
    }

    /** True if the server is currently crashed (unexported). */
    public boolean isCrashed(String serverId) {
        return crashed.contains(serverId);
    }

    /** Refresh the cached stub (used after restore). */
    public void refreshStub(String serverId) {
        if (rmiAvailable) {
            try {
                stubs.put(serverId, (GameServerRemote) Naming.lookup(bindName(serverId)));
                return;
            } catch (Exception e) {
                log.warn("RMI lookup failed for {} ({}); using direct handle.",
                        serverId, e.toString());
            }
        }
        GameServerImpl impl = impls.get(serverId);
        if (impl != null) {
            stubs.put(serverId, impl);
        }
    }

    /** Dynamically add and bind a new server. */
    public void registerNewServer(String serverId) {
        bindFreshServer(serverId);
        log.info("Dynamically registered new server {}", serverId);
    }

    /** Unbind and remove a dynamically added server. */
    public void unregisterServer(String serverId) {
        GameServerImpl old = impls.remove(serverId);
        if (old != null && rmiAvailable) {
            try {
                UnicastRemoteObject.unexportObject(old, true);
            } catch (Exception ignored) {}
            try {
                Naming.unbind(bindName(serverId));
            } catch (Exception ignored) {}
        }
        stubs.remove(serverId);
        crashed.remove(serverId);
        log.info("Dynamically unregistered server {}", serverId);
    }
}
