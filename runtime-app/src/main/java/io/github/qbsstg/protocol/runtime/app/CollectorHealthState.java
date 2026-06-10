package io.github.qbsstg.protocol.runtime.app;

public enum CollectorHealthState {
    CONFIGURED,
    STARTING,
    HEALTHY,
    DEGRADED,
    STOPPING,
    STOPPED,
    FAILED
}
