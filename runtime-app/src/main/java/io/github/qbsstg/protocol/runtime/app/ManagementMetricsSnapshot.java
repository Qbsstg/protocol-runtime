package io.github.qbsstg.protocol.runtime.app;

import java.time.Instant;
import java.util.Map;

public record ManagementMetricsSnapshot(
        long requestCount,
        long rejectedRequestCount,
        long errorResponseCount,
        Map<Integer, Long> statusCounts,
        String lastMethod,
        String lastPath,
        int lastStatus,
        long lastDurationMillis,
        String lastRemoteAddress,
        String lastRejectionReason,
        Instant lastRequestAt) {

    public ManagementMetricsSnapshot {
        statusCounts = statusCounts == null ? Map.of() : Map.copyOf(statusCounts);
    }

    public static ManagementMetricsSnapshot empty() {
        return new ManagementMetricsSnapshot(
                0,
                0,
                0,
                Map.of(),
                null,
                null,
                0,
                0,
                null,
                null,
                null);
    }
}
