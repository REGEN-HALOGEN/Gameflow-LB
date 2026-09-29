package com.gameflow.model;

import java.io.Serializable;

/**
 * A persistent cloud-gaming session. Authoritative copy lives in the
 * load balancer's SessionManager (ConcurrentHashMap); each game server
 * keeps its own local copy for the sessions it hosts.
 */
public class GameSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;               // S-10001
    private String playerId;
    private String game;
    private String serverId;
    private String playerRegion;
    private String resolution;
    private int fps;
    private SessionState state;
    private double latencyMs;
    private long startTime;

    public GameSession() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getGame() { return game; }
    public void setGame(String game) { this.game = game; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public String getPlayerRegion() { return playerRegion; }
    public void setPlayerRegion(String playerRegion) { this.playerRegion = playerRegion; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }
    public SessionState getState() { return state; }
    public void setState(SessionState state) { this.state = state; }
    public double getLatencyMs() { return latencyMs; }
    public void setLatencyMs(double latencyMs) { this.latencyMs = latencyMs; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    /** Live-computed at serialization time, so it never goes stale. */
    public long getDurationSec() {
        return Math.max(0, (System.currentTimeMillis() - startTime) / 1000);
    }
}
