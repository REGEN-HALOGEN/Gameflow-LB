package com.gameflow.model;

import java.io.Serializable;

/**
 * A player's request for a cloud-gaming session.
 * Internal to the backend (REST never exposes it); crosses RMI, so Serializable.
 */
public class PlayerRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String playerId;
    private String playerRegion;   // city, e.g. "Mumbai"
    private String game;           // e.g. "NEON STRIKE"
    private String resolution;     // e.g. "1080p"
    private int fps;
    private String sessionId;      // assigned by SessionManager before the RMI call

    public PlayerRequest() {
    }

    public PlayerRequest(String playerId, String playerRegion, String game,
                         String resolution, int fps) {
        this.playerId = playerId;
        this.playerRegion = playerRegion;
        this.game = game;
        this.resolution = resolution;
        this.fps = fps;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getPlayerRegion() { return playerRegion; }
    public void setPlayerRegion(String playerRegion) { this.playerRegion = playerRegion; }
    public String getGame() { return game; }
    public void setGame(String game) { this.game = game; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
