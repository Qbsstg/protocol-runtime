package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryResult;

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
        Map<String, Long> sinkFailureTypeCounts,
        long sinkDeliveredCount,
        String lastSinkDeliveryOutcome,
        DownstreamDeliveryResult lastSinkDeliveryResult,
        Map<String, Long> sinkDeliveryOutcomeCounts) {

    public CollectorRuntimeMetrics {
        lastParseFailureAttributes = lastParseFailureAttributes == null
                ? Map.of()
                : Map.copyOf(lastParseFailureAttributes);
        sinkFailureTypeCounts = sinkFailureTypeCounts == null
                ? Map.of()
                : Map.copyOf(sinkFailureTypeCounts);
        sinkDeliveryOutcomeCounts = sinkDeliveryOutcomeCounts == null
                ? Map.of()
                : Map.copyOf(sinkDeliveryOutcomeCounts);
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
                emptySinkFailureTypeCounts(),
                0,
                null,
                null,
                emptySinkDeliveryOutcomeCounts());
    }

    private static Map<String, Long> emptySinkFailureTypeCounts() {
        java.util.LinkedHashMap<String, Long> counts = new java.util.LinkedHashMap<>();
        for (SinkDeliveryFailureType type : SinkDeliveryFailureType.values()) {
            counts.put(type.name(), 0L);
        }
        return counts;
    }

    private static Map<String, Long> emptySinkDeliveryOutcomeCounts() {
        java.util.LinkedHashMap<String, Long> counts = new java.util.LinkedHashMap<>();
        for (io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryOutcome outcome
                : io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryOutcome.values()) {
            counts.put(outcome.name(), 0L);
        }
        return counts;
    }
}
