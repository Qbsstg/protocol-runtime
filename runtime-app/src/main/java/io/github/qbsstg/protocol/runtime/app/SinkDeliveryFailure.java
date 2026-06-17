package io.github.qbsstg.protocol.runtime.app;

import java.time.Instant;
import java.util.Objects;

record SinkDeliveryFailure(
        String target,
        String sourceId,
        Instant observedAt,
        SinkDeliveryFailureType failureType,
        String exceptionType,
        String message,
        boolean retryable) {

    SinkDeliveryFailure {
        target = requireNonBlank(target, "target");
        sourceId = sourceId == null || sourceId.isBlank() ? "unknown" : sourceId;
        observedAt = observedAt == null ? Instant.now() : observedAt;
        failureType = Objects.requireNonNullElse(failureType, SinkDeliveryFailureType.UNKNOWN_FAILURE);
        exceptionType = exceptionType == null || exceptionType.isBlank() ? "unknown" : exceptionType;
        message = message == null ? "" : message;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
