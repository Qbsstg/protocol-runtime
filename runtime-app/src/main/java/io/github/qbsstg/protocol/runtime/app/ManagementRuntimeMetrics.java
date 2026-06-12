package io.github.qbsstg.protocol.runtime.app;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class ManagementRuntimeMetrics {

    private long requestCount;
    private long rejectedRequestCount;
    private long errorResponseCount;
    private final Map<Integer, Long> statusCounts = new LinkedHashMap<>();
    private String lastMethod;
    private String lastPath;
    private int lastStatus;
    private long lastDurationMillis;
    private String lastRemoteAddress;
    private String lastRejectionReason;
    private Instant lastRequestAt;

    synchronized void record(
            Instant observedAt,
            String method,
            String path,
            int status,
            long durationMillis,
            String remoteAddress,
            String rejectionReason) {
        requestCount++;
        if (rejectionReason != null) {
            rejectedRequestCount++;
        }
        if (status >= 400) {
            errorResponseCount++;
        }
        statusCounts.merge(status, 1L, Long::sum);
        lastMethod = method;
        lastPath = path;
        lastStatus = status;
        lastDurationMillis = durationMillis;
        lastRemoteAddress = remoteAddress;
        lastRejectionReason = rejectionReason;
        lastRequestAt = observedAt;
    }

    synchronized ManagementMetricsSnapshot snapshot() {
        return new ManagementMetricsSnapshot(
                requestCount,
                rejectedRequestCount,
                errorResponseCount,
                new LinkedHashMap<>(statusCounts),
                lastMethod,
                lastPath,
                lastStatus,
                lastDurationMillis,
                lastRemoteAddress,
                lastRejectionReason,
                lastRequestAt);
    }
}
