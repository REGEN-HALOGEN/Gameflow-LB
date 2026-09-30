package com.gameflow.simulation;

import java.util.Map;

/**
 * Approximate base latency (ms) between player cities and game servers.
 * Keeps simulated geography plausible: a Mumbai player really does get
 * ~18ms to a Mumbai server and ~65ms to Singapore.
 */
public final class LatencyMatrix {

    private static final Map<String, Map<String, Integer>> BASE = Map.of(
            "Mumbai", Map.of(
                    "GS-MUM-01", 18, "GS-MUM-02", 18, "GS-MUM-03", 18, "GS-MUM-04", 18, "GS-MUM-05", 18,
                    "GS-BLR-01", 35, "GS-BLR-02", 35,
                    "GS-SIN-01", 65, "GS-SIN-02", 65, "GS-SIN-03", 65),
            "Bangalore", Map.of(
                    "GS-MUM-01", 35, "GS-MUM-02", 35, "GS-MUM-03", 35, "GS-MUM-04", 35, "GS-MUM-05", 35,
                    "GS-BLR-01", 10, "GS-BLR-02", 10,
                    "GS-SIN-01", 60, "GS-SIN-02", 60, "GS-SIN-03", 60),
            "Delhi", Map.of(
                    "GS-MUM-01", 45, "GS-MUM-02", 45, "GS-MUM-03", 45, "GS-MUM-04", 45, "GS-MUM-05", 45,
                    "GS-BLR-01", 50, "GS-BLR-02", 50,
                    "GS-SIN-01", 80, "GS-SIN-02", 80, "GS-SIN-03", 80),
            "Singapore", Map.of(
                    "GS-MUM-01", 65, "GS-MUM-02", 65, "GS-MUM-03", 65, "GS-MUM-04", 65, "GS-MUM-05", 65,
                    "GS-BLR-01", 55, "GS-BLR-02", 55,
                    "GS-SIN-01", 12, "GS-SIN-02", 12, "GS-SIN-03", 12),
            "Chennai", Map.of(
                    "GS-MUM-01", 30, "GS-MUM-02", 30, "GS-MUM-03", 30, "GS-MUM-04", 30, "GS-MUM-05", 30,
                    "GS-BLR-01", 20, "GS-BLR-02", 20,
                    "GS-SIN-01", 55, "GS-SIN-02", 55, "GS-SIN-03", 55),
            "Hyderabad", Map.of(
                    "GS-MUM-01", 28, "GS-MUM-02", 28, "GS-MUM-03", 28, "GS-MUM-04", 28, "GS-MUM-05", 28,
                    "GS-BLR-01", 25, "GS-BLR-02", 25,
                    "GS-SIN-01", 62, "GS-SIN-02", 62, "GS-SIN-03", 62)
    );

    private LatencyMatrix() {
    }

    public static int baseLatency(String playerCity, String serverId) {
        Map<String, Integer> row = BASE.get(playerCity);
        if (row == null) {
            return 60;
        }
        return row.getOrDefault(serverId, 60);
    }

    public static java.util.Set<String> cities() {
        return BASE.keySet();
    }
}
