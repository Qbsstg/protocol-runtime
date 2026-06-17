package io.github.qbsstg.protocol.runtime.app;

enum SinkDeliveryFailureType {
    CONFIGURATION_ERROR(false),
    FILESYSTEM_ERROR(true),
    SERIALIZATION_ERROR(false),
    WRITE_ERROR(true),
    FLUSH_ERROR(true),
    BACKPRESSURE_REJECTED(true),
    TRANSPORT_FAILURE(true),
    TIMEOUT(true),
    DEAD_LETTER_ROUTED(false),
    RETRYABLE_FAILURE(true),
    PERMANENT_FAILURE(false),
    UNKNOWN_FAILURE(true);

    private final boolean retryable;

    SinkDeliveryFailureType(boolean retryable) {
        this.retryable = retryable;
    }

    boolean retryable() {
        return retryable;
    }
}
