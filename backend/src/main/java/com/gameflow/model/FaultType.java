package com.gameflow.model;

/** Fault types injectable via POST /api/servers/{id}/fault. */
public enum FaultType {
    GPU_OVERLOAD,
    LATENCY_SPIKE,
    PACKET_LOSS,
    RMI_FAILURE,
    CRASH
}
