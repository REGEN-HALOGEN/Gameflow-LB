package com.gameflow.model;

/** Health lifecycle of a game-server node, as seen by the load balancer. */
public enum ServerState {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    OFFLINE,
    RECOVERING,
    DRAINING
}
