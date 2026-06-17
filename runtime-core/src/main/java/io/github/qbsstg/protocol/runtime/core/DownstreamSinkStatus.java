package io.github.qbsstg.protocol.runtime.core;

import java.util.Map;
import java.util.Objects;

public record DownstreamSinkStatus(
        DownstreamSinkIdentity identity,
        boolean running,
        boolean healthy,
        boolean ready,
        BackpressureDecision backpressureDecision,
        long deliveredCount,
        long failureCount,
        DownstreamDeliveryResult lastResult,
        Map<String, String> diagnostics) {

    public DownstreamSinkStatus {
        identity = Objects.requireNonNull(identity, "identity must not be null");
        backpressureDecision = Objects.requireNonNullElse(backpressureDecision, BackpressureDecision.ACCEPT);
        if (deliveredCount < 0) {
            throw new IllegalArgumentException("deliveredCount must not be negative");
        }
        if (failureCount < 0) {
            throw new IllegalArgumentException("failureCount must not be negative");
        }
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }

    public static DownstreamSinkStatus unknown(DownstreamSinkIdentity identity) {
        return new DownstreamSinkStatus(
                identity,
                false,
                true,
                true,
                BackpressureDecision.ACCEPT,
                0,
                0,
                null,
                Map.of());
    }
}
