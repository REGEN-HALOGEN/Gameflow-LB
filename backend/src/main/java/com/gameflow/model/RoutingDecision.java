package com.gameflow.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A recorded routing decision: every candidate, its score breakdown,
 * and the selected server. Broadcast in full over the WebSocket.
 */
public class RoutingDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;                       // RD-18421
    private long timestamp;
    private String playerId;
    private String game;
    private String playerRegion;
    private String resolution;
    private int fps;
    private RoutingStrategy strategy;
    private List<CandidateScore> candidates;
    private String selectedServerId;         // null when no eligible candidate
    private String reason;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getGame() { return game; }
    public void setGame(String game) { this.game = game; }
    public String getPlayerRegion() { return playerRegion; }
    public void setPlayerRegion(String playerRegion) { this.playerRegion = playerRegion; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }
    public RoutingStrategy getStrategy() { return strategy; }
    public void setStrategy(RoutingStrategy strategy) { this.strategy = strategy; }
    public List<CandidateScore> getCandidates() { return candidates; }
    public void setCandidates(List<CandidateScore> candidates) { this.candidates = candidates; }
    public String getSelectedServerId() { return selectedServerId; }
    public void setSelectedServerId(String selectedServerId) { this.selectedServerId = selectedServerId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    /** Per-server scorecard inside a routing decision. */
    public static class CandidateScore implements Serializable {

        private static final long serialVersionUID = 1L;

        private String serverId;
        private double latencyMs;
        private double cpu;
        private double gpu;
        private double packetLoss;
        private int sessions;
        private double jitterMs;
        private double score;                                  // lower is better
        private Map<String, Double> scoreBreakdown = new LinkedHashMap<>();
        private boolean eligible;
        private String penaltyReason;

        public String getServerId() { return serverId; }
        public void setServerId(String serverId) { this.serverId = serverId; }
        public double getLatencyMs() { return latencyMs; }
        public void setLatencyMs(double latencyMs) { this.latencyMs = latencyMs; }
        public double getCpu() { return cpu; }
        public void setCpu(double cpu) { this.cpu = cpu; }
        public double getGpu() { return gpu; }
        public void setGpu(double gpu) { this.gpu = gpu; }
        public double getPacketLoss() { return packetLoss; }
        public void setPacketLoss(double packetLoss) { this.packetLoss = packetLoss; }
        public int getSessions() { return sessions; }
        public void setSessions(int sessions) { this.sessions = sessions; }
        public double getJitterMs() { return jitterMs; }
        public void setJitterMs(double jitterMs) { this.jitterMs = jitterMs; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public Map<String, Double> getScoreBreakdown() { return scoreBreakdown; }
        public void setScoreBreakdown(Map<String, Double> scoreBreakdown) {
            this.scoreBreakdown = scoreBreakdown;
        }
        public boolean isEligible() { return eligible; }
        public void setEligible(boolean eligible) { this.eligible = eligible; }
        public String getPenaltyReason() { return penaltyReason; }
        public void setPenaltyReason(String penaltyReason) { this.penaltyReason = penaltyReason; }
    }
}
