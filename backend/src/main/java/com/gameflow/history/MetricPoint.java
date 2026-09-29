package com.gameflow.history;

import com.gameflow.model.ServerMetrics;

import java.io.Serializable;

/** One sampled point in a server's metric history (~1/sec, oldest first). */
public class MetricPoint implements Serializable {

    private static final long serialVersionUID = 1L;

    private long t;
    private double cpu;
    private double gpu;
    private double ram;
    private double vram;
    private double encoding;
    private double latencyMs;
    private double jitterMs;
    private double packetLoss;
    private int sessions;
    private double networkMbps;
    private double requestsPerSec;

    public MetricPoint() {
    }

    public static MetricPoint from(ServerMetrics m) {
        MetricPoint p = new MetricPoint();
        p.t = m.getTimestamp();
        p.cpu = m.getCpu();
        p.gpu = m.getGpu();
        p.ram = m.getRam();
        p.vram = m.getVram();
        p.encoding = m.getEncoding();
        p.latencyMs = m.getLatencyMs();
        p.jitterMs = m.getJitterMs();
        p.packetLoss = m.getPacketLoss();
        p.sessions = m.getSessions();
        p.networkMbps = m.getNetworkMbps();
        p.requestsPerSec = m.getRequestsPerSec();
        return p;
    }

    public long getT() { return t; }
    public void setT(long t) { this.t = t; }
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
    public double getLatencyMs() { return latencyMs; }
    public void setLatencyMs(double latencyMs) { this.latencyMs = latencyMs; }
    public double getJitterMs() { return jitterMs; }
    public void setJitterMs(double jitterMs) { this.jitterMs = jitterMs; }
    public double getPacketLoss() { return packetLoss; }
    public void setPacketLoss(double packetLoss) { this.packetLoss = packetLoss; }
    public int getSessions() { return sessions; }
    public void setSessions(int sessions) { this.sessions = sessions; }
    public double getNetworkMbps() { return networkMbps; }
    public void setNetworkMbps(double networkMbps) { this.networkMbps = networkMbps; }
    public double getRequestsPerSec() { return requestsPerSec; }
    public void setRequestsPerSec(double requestsPerSec) { this.requestsPerSec = requestsPerSec; }
}
