package com.gameflow.rmi;

import com.gameflow.model.GameProfile;
import com.gameflow.model.GameSession;
import com.gameflow.model.PlayerRequest;
import com.gameflow.model.ServerMetrics;
import com.gameflow.model.SessionState;
import com.gameflow.simulation.MetricSimulator;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated game-server node. Exported over RMI; the load balancer only
 * ever touches it through the {@link GameServerRemote} stub.
 *
 * Fault flags let the demo inject GPU_OVERLOAD / LATENCY_SPIKE /
 * PACKET_LOSS / RMI_FAILURE without killing the process.
 */
public class GameServerImpl extends UnicastRemoteObject implements GameServerRemote {

    private final String id;
    private final String region;
    private final String city;
    private final int capacity;
    private final MetricSimulator simulator;
    private final Map<String, GameProfile> gameProfiles;

    private final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();

    private volatile boolean rmiFailure = false;

    public GameServerImpl(String id, String region, String city, int capacity,
                          Map<String, GameProfile> gameProfiles,
                          double baseLatencyMs) throws RemoteException {
        super();
        this.id = id;
        this.region = region;
        this.city = city;
        this.capacity = capacity;
        this.gameProfiles = gameProfiles;
        this.simulator = new MetricSimulator(capacity, baseLatencyMs);
    }

    private void checkRmi() throws RemoteException {
        if (rmiFailure) {
            throw new RemoteException("RMI_FAILURE fault injected on " + id);
        }
    }

    @Override
    public ServerMetrics getMetrics() throws RemoteException {
        checkRmi();
        simulator.tick(sessions.size(), avgCpuLoad(), avgGpuLoad(), avgVramLoad(), avgNetworkLoad());
        return simulator.toMetrics(sessions.size());
    }

    @Override
    public boolean healthCheck() throws RemoteException {
        checkRmi();
        return true;
    }

    @Override
    public boolean canAcceptSession() throws RemoteException {
        checkRmi();
        return sessions.size() < capacity;
    }

    @Override
    public GameSession createSession(PlayerRequest request) throws RemoteException {
        checkRmi();
        if (sessions.size() >= capacity) {
            throw new RemoteException("Server " + id + " at capacity (" + capacity + ")");
        }
        GameSession session = new GameSession();
        session.setId(request.getSessionId());
        session.setPlayerId(request.getPlayerId());
        session.setGame(request.getGame());
        session.setServerId(id);
        session.setPlayerRegion(request.getPlayerRegion());
        session.setResolution(request.getResolution());
        session.setFps(request.getFps());
        session.setState(SessionState.ACTIVE);
        session.setStartTime(System.currentTimeMillis());
        // Approximate the player's experienced latency from the sim's current value.
        session.setLatencyMs(simulator.toMetrics(sessions.size()).getLatencyMs());
        sessions.put(session.getId(), session);
        return session;
    }

    @Override
    public void terminateSession(String sessionId) throws RemoteException {
        checkRmi();
        sessions.remove(sessionId);
    }

    // ---- local (non-remote) simulation control ----

    public String getId() { return id; }
    public String getRegion() { return region; }
    public String getCity() { return city; }
    public int getCapacity() { return capacity; }
    public int getSessionCount() { return sessions.size(); }

    public void setRmiFailure(boolean rmiFailure) { this.rmiFailure = rmiFailure; }
    public boolean isRmiFailure() { return rmiFailure; }
    public double getLatencyOffsetMs() { return simulator.getLatencyOffsetMs(); }

    public void setGpuTargetOverride(Double target) { simulator.setGpuTargetOverride(target); }
    public void setLatencyOffsetMs(double offset) { simulator.setLatencyOffsetMs(offset); }
    public void setPacketLossOverride(Double packetLoss) { simulator.setPacketLossOverride(packetLoss); }

    public void clearFaults() {
        rmiFailure = false;
        simulator.clearOverrides();
    }

    private double avgCpuLoad() { return avgProfile(GameProfile::getCpuLoad, 0.55); }
    private double avgGpuLoad() { return avgProfile(GameProfile::getGpuLoad, 0.65); }
    private double avgVramLoad() { return avgProfile(GameProfile::getVramLoad, 0.60); }
    private double avgNetworkLoad() { return avgProfile(GameProfile::getNetworkLoad, 0.60); }

    private double avgProfile(java.util.function.ToDoubleFunction<GameProfile> fn, double fallback) {
        if (sessions.isEmpty()) {
            return fallback;
        }
        double sum = 0;
        int n = 0;
        for (GameSession s : sessions.values()) {
            GameProfile p = gameProfiles.get(s.getGame());
            if (p != null) {
                sum += fn.applyAsDouble(p);
                n++;
            }
        }
        return n == 0 ? fallback : sum / n;
    }
}
