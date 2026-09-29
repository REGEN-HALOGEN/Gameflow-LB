package com.gameflow.simulation;

import com.gameflow.model.ServerMetrics;

import java.util.Random;

/**
 * Bounded random-walk metric simulator living inside each game server.
 * Values drift smoothly toward load-based targets — no wild jumps —
 * unless a fault override is installed (scenarios / fault injection).
 */
public class MetricSimulator {

    private final int capacity;
    private final double baseLatencyMs;
    private final Random random;

    private double cpu = 12;
    private double gpu = 15;
    private double ram = 28;
    private double vram = 12;
    private double encoding = 8;
    private double latencyMs;
    private double jitterMs = 1.5;
    private double packetLoss = 0.1;
    private double networkMbps = 0;
    private double requestsPerSec = 0;
    private double temperatureC = 48;

    // Fault overrides (null = normal behavior)
    private volatile Double gpuTargetOverride;
    private volatile double latencyOffsetMs;
    private volatile Double packetLossOverride;

    public MetricSimulator(int capacity, double baseLatencyMs) {
        this(capacity, baseLatencyMs, new Random());
    }

    public MetricSimulator(int capacity, double baseLatencyMs, Random random) {
        this.capacity = capacity;
        this.baseLatencyMs = baseLatencyMs;
        this.random = random;
        this.latencyMs = baseLatencyMs;
    }

    /**
     * Advance the simulation one tick (~1s). Average game resource loads
     * (0..1) shape how hard the active sessions push each resource.
     */
    public synchronized void tick(int sessionCount, double avgCpuLoad,
                                  double avgGpuLoad, double avgVramLoad,
                                  double avgNetworkLoad) {
        double util = capacity == 0 ? 0 : Math.min(1.0, (double) sessionCount / capacity);

        double gpuTarget = gpuTargetOverride != null ? gpuTargetOverride
                : 10 + 78 * util * (0.35 + avgGpuLoad);
        double cpuTarget = 8 + 62 * util * (0.35 + avgCpuLoad);
        double ramTarget = 20 + 48 * util;
        double vramTarget = 8 + 72 * util * (0.35 + avgVramLoad);
        double encodingTarget = 5 + 82 * util;
        double latencyTarget = baseLatencyMs + 22 * util * util + latencyOffsetMs;
        double jitterTarget = 1 + 6 * util + (latencyOffsetMs > 0 ? 9 : 0);
        double packetLossTarget = packetLossOverride != null ? packetLossOverride
                : 0.05 + 1.4 * util * util * util;

        gpu = drift(gpu, gpuTarget, 2.2, 0.9, 0, 100);
        cpu = drift(cpu, cpuTarget, 2.6, 1.1, 0, 100);
        ram = drift(ram, ramTarget, 1.5, 0.6, 0, 100);
        vram = drift(vram, vramTarget, 1.8, 0.7, 0, 100);
        encoding = drift(encoding, encodingTarget, 2.4, 1.0, 0, 100);
        latencyMs = drift(latencyMs, latencyTarget, 4.0, 1.6, 0, 500);
        jitterMs = drift(jitterMs, jitterTarget, 1.2, 0.5, 0, 60);
        packetLoss = drift(packetLoss, packetLossTarget, 0.5, 0.18, 0, 40);

        networkMbps = Math.max(0, sessionCount * (6 + 30 * avgNetworkLoad)
                + (random.nextDouble() - 0.5) * 18);
        requestsPerSec = Math.max(0, sessionCount * 0.12 + (random.nextDouble() - 0.5) * 1.2);
        temperatureC = 42 + gpu * 0.32 + (random.nextDouble() - 0.5) * 1.5;
    }

    private double drift(double current, double target, double maxStep,
                         double noise, double min, double max) {
        double delta = target - current;
        double step = Math.max(-maxStep, Math.min(maxStep, delta));
        double next = current + step + (random.nextDouble() - 0.5) * 2 * noise;
        return Math.max(min, Math.min(max, next));
    }

    public synchronized ServerMetrics toMetrics(int sessionCount) {
        ServerMetrics m = new ServerMetrics();
        m.setCpu(round1(cpu));
        m.setGpu(round1(gpu));
        m.setRam(round1(ram));
        m.setVram(round1(vram));
        m.setEncoding(round1(encoding));
        m.setNetworkMbps(round1(networkMbps));
        m.setLatencyMs(round1(latencyMs));
        m.setJitterMs(round1(jitterMs));
        m.setPacketLoss(round1(packetLoss));
        m.setSessions(sessionCount);
        m.setRequestsPerSec(round1(requestsPerSec));
        m.setTemperatureC(round1(temperatureC));
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ---- fault overrides (scenarios / fault injection) ----

    public void setGpuTargetOverride(Double target) { this.gpuTargetOverride = target; }
    public void setLatencyOffsetMs(double offset) { this.latencyOffsetMs = offset; }
    public void setPacketLossOverride(Double packetLoss) { this.packetLossOverride = packetLoss; }
    public double getLatencyOffsetMs() { return latencyOffsetMs; }

    public void clearOverrides() {
        this.gpuTargetOverride = null;
        this.latencyOffsetMs = 0;
        this.packetLossOverride = null;
    }
}
