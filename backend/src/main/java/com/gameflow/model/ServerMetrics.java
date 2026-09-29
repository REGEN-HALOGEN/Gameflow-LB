package com.gameflow.model;

import java.io.Serializable;

/**
 * Point-in-time resource/network metrics for one game server.
 * Transferred over RMI, so it must stay Serializable.
 */
public class ServerMetrics implements Serializable {

    private static final long serialVersionUID = 1L;

    private double cpu;            // %
    private double gpu;            // %
    private double ram;            // %
    private double vram;           // %
    private double encoding;       // % NVENC/encoder utilization
    private double networkMbps;
    private double latencyMs;
    private double jitterMs;
    private double packetLoss;     // %
    private int sessions;
    private double requestsPerSec;
    private double temperatureC;
    private long timestamp;

    public ServerMetrics() {
    }

    public ServerMetrics(ServerMetrics other) {
        this.cpu = other.cpu;
        this.gpu = other.gpu;
        this.ram = other.ram;
        this.vram = other.vram;
        this.encoding = other.encoding;
        this.networkMbps = other.networkMbps;
        this.latencyMs = other.latencyMs;
        this.jitterMs = other.jitterMs;
        this.packetLoss = other.packetLoss;
        this.sessions = other.sessions;
        this.requestsPerSec = other.requestsPerSec;
        this.temperatureC = other.temperatureC;
        this.timestamp = other.timestamp;
    }

    public double getCpu() { return cpu; }
    public void setCpu(double cpu) { this.cpu = cpu; }
    public double getGpu() { return gpu; }
    public void setGpu(double gpu) { this.gpu = gpu; }
    public double getRam() { return ram; }
    public void setRam(double ram) { this.ram = ram; }
    public double getVram() { return vram; }
    public void setVram(double vram) { this.vram = vram; }
    public double getEncoding() { return encoding; }
    public void setEncoding(double encoding) { this.encoding = encoding; }
    public double getNetworkMbps() { return networkMbps; }
    public void setNetworkMbps(double networkMbps) { this.networkMbps = networkMbps; }
    public double getLatencyMs() { return latencyMs; }
    public void setLatencyMs(double latencyMs) { this.latencyMs = latencyMs; }
    public double getJitterMs() { return jitterMs; }
    public void setJitterMs(double jitterMs) { this.jitterMs = jitterMs; }
    public double getPacketLoss() { return packetLoss; }
    public void setPacketLoss(double packetLoss) { this.packetLoss = packetLoss; }
    public int getSessions() { return sessions; }
    public void setSessions(int sessions) { this.sessions = sessions; }
    public double getRequestsPerSec() { return requestsPerSec; }
    public void setRequestsPerSec(double requestsPerSec) { this.requestsPerSec = requestsPerSec; }
    public double getTemperatureC() { return temperatureC; }
    public void setTemperatureC(double temperatureC) { this.temperatureC = temperatureC; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
