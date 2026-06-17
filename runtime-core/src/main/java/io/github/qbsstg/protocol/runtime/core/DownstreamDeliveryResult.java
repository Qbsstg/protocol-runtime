package io.github.qbsstg.protocol.runtime.core;

import java.util.Map;
import java.util.Objects;

public record DownstreamDeliveryResult(
        DownstreamDeliveryOutcome outcome,
        String message,
        String exceptionType,
        boolean retryable,
        BackpressureDecision backpressureDecision,
        Map<String, String> diagnostics) {

    public DownstreamDeliveryResult {
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        message = message == null ? "" : message;
        exceptionType = exceptionType == null || exceptionType.isBlank() ? null : exceptionType;
        retryable = retryable && !outcome.delivered();
        backpressureDecision = backpressureDecision == null
                ? (outcome == DownstreamDeliveryOutcome.BACKPRESSURE_REJECTED
                        ? BackpressureDecision.RETRY_LATER
                        : BackpressureDecision.ACCEPT)
                : backpressureDecision;
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }

    public boolean delivered() {
        return outcome.delivered();
    }

    public static DownstreamDeliveryResult success() {
        return new DownstreamDeliveryResult(
                DownstreamDeliveryOutcome.DELIVERED,
                "",
                null,
                false,
                BackpressureDecision.ACCEPT,
                Map.of());
    }

    public static DownstreamDeliveryResult failure(
            DownstreamDeliveryOutcome outcome,
            String message,
            String exceptionType,
            Map<String, String> diagnostics) {
        if (outcome == DownstreamDeliveryOutcome.DELIVERED) {
            return success();
        }
        return new DownstreamDeliveryResult(
                outcome,
                message,
                exceptionType,
                outcome.retryable(),
                outcome == DownstreamDeliveryOutcome.BACKPRESSURE_REJECTED
                        ? BackpressureDecision.RETRY_LATER
                        : BackpressureDecision.ACCEPT,
                diagnostics);
    }
}
