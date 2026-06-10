package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;

import java.time.Instant;
import java.util.Map;

public record CollectorRuntimeMetrics(
        long parsedRecordCount,
        long parseFailureCount,
        String lastParseFailureSourceId,
        String lastParseFailureMessage,
        Instant lastParseFailureAt,
        String lastParseFailureCauseType,
        int lastParseFailurePayloadSize,
        String lastParseFailurePayloadPreviewHex,
        Map<String, String> lastParseFailureAttributes,
        long backpressureRetryLaterCount,
        long backpressureDropCount,
        String lastBackpressureSourceId,
        BackpressureDecision lastBackpressureDecision,
        Instant lastBackpressureAt,
        int lastBackpressurePayloadSize,
        long sinkFailureCount,
        String lastSinkFailureTarget,
        String lastSinkFailureSourceId,
        Instant lastSinkFailureAt,
        String lastSinkFailureType,
        String lastSinkFailureMessage) {

    public CollectorRuntimeMetrics {
        lastParseFailureAttributes = lastParseFailureAttributes == null
                ? Map.of()
                : Map.copyOf(lastParseFailureAttributes);
    }

    public static CollectorRuntimeMetrics empty() {
        return new CollectorRuntimeMetrics(
                0,
                0,
                null,
                null,
                null,
                null,
                0,
                "",
                Map.of(),
                0,
                0,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                null,
                null,
                null);
    }
}
