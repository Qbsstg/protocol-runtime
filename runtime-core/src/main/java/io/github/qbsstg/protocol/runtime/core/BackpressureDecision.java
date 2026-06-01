package io.github.qbsstg.protocol.runtime.core;

public enum BackpressureDecision {
    ACCEPT,
    RETRY_LATER,
    DROP;

    public boolean isAccepted() {
        return this == ACCEPT;
    }
}
