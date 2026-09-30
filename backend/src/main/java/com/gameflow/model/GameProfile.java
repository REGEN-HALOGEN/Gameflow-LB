package com.gameflow.model;

import java.io.Serializable;

/**
 * Resource profile of a game workload. Drives how much load each
 * session adds to its host server's simulated metrics.
 */
public class GameProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;               // NEON_STRIKE
    private String name;             // NEON STRIKE
    private String genre;
    private boolean latencySensitive;
    private double gpuLoad;          // 0..1
    private double vramLoad;         // 0..1
    private double networkLoad;      // 0..1
    private double cpuLoad;          // 0..1
    private String requiredHardwareTier; // Phase 3: Hardware Affinity

    public GameProfile() {
    }

    public GameProfile(String id, String name, String genre, boolean latencySensitive,
                       double gpuLoad, double vramLoad, double networkLoad, double cpuLoad, String requiredHardwareTier) {
        this.id = id;
        this.name = name;
        this.genre = genre;
        this.latencySensitive = latencySensitive;
        this.gpuLoad = gpuLoad;
        this.vramLoad = vramLoad;
        this.networkLoad = networkLoad;
        this.cpuLoad = cpuLoad;
        this.requiredHardwareTier = requiredHardwareTier;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public boolean isLatencySensitive() { return latencySensitive; }
    public void setLatencySensitive(boolean latencySensitive) { this.latencySensitive = latencySensitive; }
    public double getGpuLoad() { return gpuLoad; }
    public void setGpuLoad(double gpuLoad) { this.gpuLoad = gpuLoad; }
    public double getVramLoad() { return vramLoad; }
    public void setVramLoad(double vramLoad) { this.vramLoad = vramLoad; }
    public double getNetworkLoad() { return networkLoad; }
    public void setNetworkLoad(double networkLoad) { this.networkLoad = networkLoad; }
    public double getCpuLoad() { return cpuLoad; }
    public void setCpuLoad(double cpuLoad) { this.cpuLoad = cpuLoad; }
    public String getRequiredHardwareTier() { return requiredHardwareTier; }
    public void setRequiredHardwareTier(String requiredHardwareTier) { this.requiredHardwareTier = requiredHardwareTier; }
}
