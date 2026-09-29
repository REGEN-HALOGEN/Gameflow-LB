package com.gameflow.model;

/** Lifecycle of a persistent cloud-gaming session. */
public enum SessionState {
    CREATING,
    ACTIVE,
    MIGRATING,
    TERMINATING,
    TERMINATED,
    FAILED
}
