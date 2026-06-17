package io.github.qbsstg.protocol.runtime.app;

final class SinkDeliveryException extends RuntimeException {

    private final SinkDeliveryFailureType failureType;
    private final boolean retryable;

    SinkDeliveryException(SinkDeliveryFailureType failureType, String message, Throwable cause) {
        this(failureType, message, cause, failureType.retryable());
    }

    SinkDeliveryException(
            SinkDeliveryFailureType failureType,
            String message,
            Throwable cause,
            boolean retryable) {
        super(message, cause);
        this.failureType = failureType == null ? SinkDeliveryFailureType.UNKNOWN_FAILURE : failureType;
        this.retryable = retryable;
    }

    SinkDeliveryFailureType failureType() {
        return failureType;
    }

    boolean retryable() {
        return retryable;
    }
}
