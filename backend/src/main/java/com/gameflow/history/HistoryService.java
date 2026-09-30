package com.gameflow.history;

import com.gameflow.model.ServerMetrics;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rolling metric history: per-server ring buffers (~600 points at 1/sec)
 * plus a per-tick aggregate series. Serves GET /api/metrics/history.
 */
@Component
public class HistoryService {

    private static final int CAPACITY = 600;

    private final Map<String, Deque<MetricPoint>> perServer = new LinkedHashMap<>();
    private final Deque<AggregatePoint> aggregate = new ArrayDeque<>();

    /** Record one tick's metrics for every server (called once per second). */
    public synchronized void recordTick(Map<String, ServerMetrics> latestByServer, long activeSessions, double burnRate) {
        long now = System.currentTimeMillis();
        double rpsSum = 0;
        double latSum = 0;
        double lossSum = 0;
        double throughputSum = 0;
        int n = 0;
        for (var entry : latestByServer.entrySet()) {
            ServerMetrics m = entry.getValue();
            if (m == null) {
                continue;
            }
            Deque<MetricPoint> buf = perServer.computeIfAbsent(entry.getKey(), k -> new ArrayDeque<>());
            buf.addLast(MetricPoint.from(m));
            while (buf.size() > CAPACITY) {
                buf.removeFirst();
            }
            rpsSum += m.getRequestsPerSec();
            latSum += m.getLatencyMs();
            lossSum += m.getPacketLoss();
            throughputSum += m.getNetworkMbps();
            n++;
        }
        if (n > 0) {
            aggregate.addLast(new AggregatePoint(now, rpsSum, latSum / n, lossSum / n, throughputSum, activeSessions, burnRate));
            while (aggregate.size() > CAPACITY) {
                aggregate.removeFirst();
            }
        }
    }

    public synchronized HistoryResponse getHistory(int windowSec) {
        long cutoff = System.currentTimeMillis() - (long) windowSec * 1000;
        Map<String, List<MetricPoint>> servers = new LinkedHashMap<>();
        for (var entry : perServer.entrySet()) {
            List<MetricPoint> points = new ArrayList<>();
            for (MetricPoint p : entry.getValue()) {
                if (p.getT() >= cutoff) {
                    points.add(p);
                }
            }
            servers.put(entry.getKey(), points);
        }
        List<Long> t = new ArrayList<>();
        List<Double> rps = new ArrayList<>();
        List<Double> avgLat = new ArrayList<>();
        List<Double> loss = new ArrayList<>();
        List<Double> throughput = new ArrayList<>();
        List<Long> activeSess = new ArrayList<>();
        List<Double> burn = new ArrayList<>();
        for (AggregatePoint a : aggregate) {
            if (a.t >= cutoff) {
                t.add(a.t);
                rps.add(a.requestsPerSec);
                avgLat.add(a.avgLatencyMs);
                loss.add(a.packetLoss);
                throughput.add(a.throughputMbps);
                activeSess.add(a.activeSessions);
                burn.add(a.burnRate);
            }
        }
        Map<String, List<? extends Number>> agg = new LinkedHashMap<>();
        agg.put("t", t);
        agg.put("requestsPerSec", rps);
        agg.put("avgLatencyMs", avgLat);
        agg.put("packetLoss", loss);
        agg.put("throughputMbps", throughput);
        agg.put("activeSessions", activeSess);
        agg.put("burnRate", burn);
        return new HistoryResponse(servers, agg);
    }

    public synchronized void clear() {
        perServer.clear();
        aggregate.clear();
    }

    private record AggregatePoint(long t, double requestsPerSec, double avgLatencyMs,
                                  double packetLoss, double throughputMbps, long activeSessions, double burnRate) {
    }

    /** Shape required by CONTRACT section 3 for /api/metrics/history. */
    public record HistoryResponse(Map<String, List<MetricPoint>> servers,
                                  Map<String, List<? extends Number>> aggregate) {
    }
}
