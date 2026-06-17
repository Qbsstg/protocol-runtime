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
        String lastSinkFailureMessage,
        String lastSinkDeliveryFailureType,
        boolean lastSinkFailureRetryable,
        Map<String, Long> sinkFailureTypeCounts) {

    public CollectorRuntimeMetrics {
        lastParseFailureAttributes = lastParseFailureAttributes == null
                ? Map.of()
                : Map.copyOf(lastParseFailureAttributes);
        sinkFailureTypeCounts = sinkFailureTypeCounts == null
                ? Map.of()
                : Map.copyOf(sinkFailureTypeCounts);
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
                null,
                null,
                false,
                emptySinkFailureTypeCounts());
    }

    private static Map<String, Long> emptySinkFailureTypeCounts() {
        java.util.LinkedHashMap<String, Long> counts = new java.util.LinkedHashMap<>();
        for (SinkDeliveryFailureType type : SinkDeliveryFailureType.values()) {
            counts.put(type.name(), 0L);
        }
        return counts;
    }
}
