package com.gameflow.model;

/** Circuit breaker states, per server. */
public enum CircuitState {
    /** Normal traffic. */
    CLOSED,
    /** Server removed from routing; waiting for recovery timeout. */
    OPEN,
    /** Allowing a limited health probe to test recovery. */
    HALF_OPEN
}
