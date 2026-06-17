package io.github.qbsstg.protocol.runtime.core;

public enum DownstreamDeliveryOutcome {
    DELIVERED(false),
    RETRYABLE_FAILURE(true),
    PERMANENT_FAILURE(false),
    BACKPRESSURE_REJECTED(true),
    CONFIGURATION_REJECTED(false),
    SERIALIZATION_FAILURE(false),
    TRANSPORT_FAILURE(true),
    TIMEOUT(true),
    DEAD_LETTER_ROUTED(false),
    UNKNOWN_FAILURE(true);

    private final boolean retryable;

    DownstreamDeliveryOutcome(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean delivered() {
        return this == DELIVERED || this == DEAD_LETTER_ROUTED;
    }

    public boolean retryable() {
        return retryable;
    }
}
