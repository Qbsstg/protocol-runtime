package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;

import java.time.Instant;

public record CollectorRuntimeMetrics(
        long parsedRecordCount,
        long parseFailureCount,
        String lastParseFailureSourceId,
        String lastParseFailureMessage,
        Instant lastParseFailureAt,
        long backpressureRetryLaterCount,
        long backpressureDropCount,
        String lastBackpressureSourceId,
        BackpressureDecision lastBackpressureDecision,
        Instant lastBackpressureAt,
        int lastBackpressurePayloadSize) {

    public static CollectorRuntimeMetrics empty() {
        return new CollectorRuntimeMetrics(0, 0, null, null, null, 0, 0, null, null, null, 0);
    }
}
