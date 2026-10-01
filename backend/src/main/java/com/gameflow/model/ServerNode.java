package com.gameflow.model;

/**
 * The load balancer's view of one game-server node: identity, health,
 * circuit, RMI reachability, and the latest metrics pulled over RMI.
 * This is what GET /api/servers returns.
 */
public class ServerNode {

    private final String id;
    private final String region;     // MUMBAI / SINGAPORE / BANGALORE
    private final String city;       // Mumbai / Singapore / Bangalore
    private final int capacity;
    private final double costPerHour;
    private final String hardwareTier; // e.g. "RTX_3080", "RTX_4090"

    private volatile ServerState state = ServerState.HEALTHY;
    private volatile CircuitState circuitState = CircuitState.CLOSED;
    private volatile RmiStatus rmiStatus = RmiStatus.CONNECTED;
    private volatile boolean eligible = true;
    private volatile double weight = 1.0;
    private volatile ServerMetrics metrics = new ServerMetrics();

    public ServerNode(String id, String region, String city, int capacity, double costPerHour, String hardwareTier) {
        this.id = id;
        this.region = region;
        this.city = city;
        this.capacity = capacity;
        this.costPerHour = costPerHour;
        this.hardwareTier = hardwareTier;
    }

    public String getId() { return id; }
    public String getRegion() { return region; }
    public String getCity() { return city; }
    public int getCapacity() { return capacity; }
    public double getCostPerHour() { return costPerHour; }
    public String getHardwareTier() { return hardwareTier; }
    public ServerState getState() { return state; }
    public void setState(ServerState state) { this.state = state; }
    public CircuitState getCircuitState() { return circuitState; }
    public void setCircuitState(CircuitState circuitState) { this.circuitState = circuitState; }
    public RmiStatus getRmiStatus() { return rmiStatus; }
    public void setRmiStatus(RmiStatus rmiStatus) { this.rmiStatus = rmiStatus; }
    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
    public ServerMetrics getMetrics() { return metrics; }
    public void setMetrics(ServerMetrics metrics) { this.metrics = metrics; }

    /** Recomputes routing eligibility from health + circuit state. */
    public void refreshEligibility() {
        // DRAINING is intentionally excluded: the server is being wound down
        // and must not accept new sessions even though it is technically healthy.
        boolean healthyEnough = (state == ServerState.HEALTHY || state == ServerState.DEGRADED)
                && state != ServerState.DRAINING;
        this.eligible = healthyEnough && circuitState == CircuitState.CLOSED;
    }
}
