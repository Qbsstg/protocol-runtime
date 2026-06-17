package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryOutcome;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryRequest;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryResult;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class RuntimeSinkCounters {

    private static final int FAILURE_PAYLOAD_PREVIEW_BYTES = 64;

    private final AtomicLong parsedRecordCount = new AtomicLong();
    private final AtomicLong parseFailureCount = new AtomicLong();
    private final AtomicLong backpressureRetryLaterCount = new AtomicLong();
    private final AtomicLong backpressureDropCount = new AtomicLong();
    private final AtomicLong sinkFailureCount = new AtomicLong();
    private final AtomicLong sinkDeliveredCount = new AtomicLong();
    private final EnumMap<SinkDeliveryFailureType, AtomicLong> sinkFailureTypeCounts =
            new EnumMap<>(SinkDeliveryFailureType.class);
    private final EnumMap<DownstreamDeliveryOutcome, AtomicLong> sinkDeliveryOutcomeCounts =
            new EnumMap<>(DownstreamDeliveryOutcome.class);
    private volatile ParseFailure lastParseFailure;
    private volatile BackpressureEvent lastBackpressure;
    private volatile SinkDeliveryFailure lastSinkFailure;
    private volatile DownstreamDeliveryResult lastDeliveryResult;

    RuntimeSinkCounters() {
        for (SinkDeliveryFailureType type : SinkDeliveryFailureType.values()) {
            sinkFailureTypeCounts.put(type, new AtomicLong());
        }
        for (DownstreamDeliveryOutcome outcome : DownstreamDeliveryOutcome.values()) {
            sinkDeliveryOutcomeCounts.put(outcome, new AtomicLong());
        }
    }

    void recordParsedRecord(ParsedRecord<?> record) {
        parsedRecordCount.incrementAndGet();
    }

    void recordParseFailure(ParseFailure failure) {
        parseFailureCount.incrementAndGet();
        lastParseFailure = failure;
    }

    void recordSuccessfulDelivery(DownstreamDeliveryRequest<?> request, DownstreamDeliveryResult result) {
        DownstreamDeliveryResult effective = result == null ? DownstreamDeliveryResult.success() : result;
        sinkDeliveredCount.incrementAndGet();
        recordDeliveryOutcome(effective);
        if (request.kind() == io.github.qbsstg.protocol.runtime.core.DownstreamRecordKind.RECORD) {
            recordParsedRecord(request.record());
        } else {
            recordParseFailure(request.failure());
        }
    }

    void recordBackpressure(IngressEnvelope envelope, BackpressureDecision decision) {
        if (decision == BackpressureDecision.RETRY_LATER) {
            backpressureRetryLaterCount.incrementAndGet();
        } else if (decision == BackpressureDecision.DROP) {
            backpressureDropCount.incrementAndGet();
        }
        lastBackpressure = new BackpressureEvent(
                envelope.sourceId().qualifiedValue(),
                decision,
                envelope.receivedAt(),
                envelope.payload().length);
    }

    SinkDeliveryFailure recordSinkFailure(
            String target,
            String sourceId,
            java.time.Instant observedAt,
            RuntimeException failure) {
        DownstreamDeliveryResult result = DownstreamDeliveryResult.failure(
                outcomeOf(SinkDeliveryFailures.typeOf(failure)),
                failure.getMessage(),
                failure.getClass().getName(),
                java.util.Map.of("target", target));
        recordDeliveryOutcome(result);
        sinkFailureCount.incrementAndGet();
        SinkDeliveryFailureType deliveryFailureType = SinkDeliveryFailures.typeOf(failure);
        sinkFailureTypeCounts.get(deliveryFailureType).incrementAndGet();
        SinkDeliveryFailure event = new SinkDeliveryFailure(
                target,
                sourceId,
                observedAt == null ? java.time.Instant.now() : observedAt,
                deliveryFailureType,
                failure.getClass().getName(),
                failure.getMessage(),
                SinkDeliveryFailures.retryable(failure));
        lastSinkFailure = event;
        return event;
    }

    SinkDeliveryFailure recordDeliveryFailure(
            String target,
            DownstreamDeliveryRequest<?> request,
            DownstreamDeliveryResult result) {
        DownstreamDeliveryResult effective = result == null
                ? DownstreamDeliveryResult.failure(
                        DownstreamDeliveryOutcome.UNKNOWN_FAILURE,
                        "downstream sink returned no result",
                        null,
                        java.util.Map.of("target", target))
                : result;
        recordDeliveryOutcome(effective);
        sinkFailureCount.incrementAndGet();
        SinkDeliveryFailureType deliveryFailureType = failureTypeOf(effective.outcome());
        sinkFailureTypeCounts.get(deliveryFailureType).incrementAndGet();
        SinkDeliveryFailure event = new SinkDeliveryFailure(
                target,
                request.sourceId().qualifiedValue(),
                request.observedAt(),
                deliveryFailureType,
                effective.exceptionType() == null ? effective.outcome().name() : effective.exceptionType(),
                effective.message(),
                effective.retryable());
        lastSinkFailure = event;
        return event;
    }

    long sinkFailureCount() {
        return sinkFailureCount.get();
    }

    CollectorRuntimeMetrics snapshot() {
        ParseFailure failure = lastParseFailure;
        BackpressureEvent backpressure = lastBackpressure;
        SinkDeliveryFailure sinkFailure = lastSinkFailure;
        if (failure == null) {
            return snapshotWithoutParseFailure(backpressure, sinkFailure);
        }
        return new CollectorRuntimeMetrics(
                parsedRecordCount.get(),
                parseFailureCount.get(),
                failure.sourceId().qualifiedValue(),
                failure.message(),
                failure.observedAt(),
                failure.cause() == null ? null : failure.cause().getClass().getName(),
                failure.rawPayload().length,
                hexPreview(failure.rawPayload(), FAILURE_PAYLOAD_PREVIEW_BYTES),
                failure.attributes(),
                backpressureRetryLaterCount.get(),
                backpressureDropCount.get(),
                backpressure == null ? null : backpressure.sourceId(),
                backpressure == null ? null : backpressure.decision(),
                backpressure == null ? null : backpressure.observedAt(),
                backpressure == null ? 0 : backpressure.payloadSize(),
                sinkFailureCount.get(),
                sinkFailure == null ? null : sinkFailure.target(),
                sinkFailure == null ? null : sinkFailure.sourceId(),
                sinkFailure == null ? null : sinkFailure.observedAt(),
                sinkFailure == null ? null : sinkFailure.exceptionType(),
                sinkFailure == null ? null : sinkFailure.message(),
                sinkFailure == null ? null : sinkFailure.failureType().name(),
                sinkFailure != null && sinkFailure.retryable(),
                sinkFailureTypeCountsSnapshot(),
                sinkDeliveredCount.get(),
                lastDeliveryResult == null ? null : lastDeliveryResult.outcome().name(),
                lastDeliveryResult,
                sinkDeliveryOutcomeCountsSnapshot());
    }

    private CollectorRuntimeMetrics snapshotWithoutParseFailure(
            BackpressureEvent backpressure,
            SinkDeliveryFailure sinkFailure) {
        return new CollectorRuntimeMetrics(
                parsedRecordCount.get(),
                parseFailureCount.get(),
                null,
                null,
                null,
                null,
                0,
                "",
                java.util.Map.of(),
                backpressureRetryLaterCount.get(),
                backpressureDropCount.get(),
                backpressure == null ? null : backpressure.sourceId(),
                backpressure == null ? null : backpressure.decision(),
                backpressure == null ? null : backpressure.observedAt(),
                backpressure == null ? 0 : backpressure.payloadSize(),
                sinkFailureCount.get(),
                sinkFailure == null ? null : sinkFailure.target(),
                sinkFailure == null ? null : sinkFailure.sourceId(),
                sinkFailure == null ? null : sinkFailure.observedAt(),
                sinkFailure == null ? null : sinkFailure.exceptionType(),
                sinkFailure == null ? null : sinkFailure.message(),
                sinkFailure == null ? null : sinkFailure.failureType().name(),
                sinkFailure != null && sinkFailure.retryable(),
                sinkFailureTypeCountsSnapshot(),
                sinkDeliveredCount.get(),
                lastDeliveryResult == null ? null : lastDeliveryResult.outcome().name(),
                lastDeliveryResult,
                sinkDeliveryOutcomeCountsSnapshot());
    }

    private Map<String, Long> sinkFailureTypeCountsSnapshot() {
        java.util.LinkedHashMap<String, Long> snapshot = new java.util.LinkedHashMap<>();
        for (SinkDeliveryFailureType type : SinkDeliveryFailureType.values()) {
            snapshot.put(type.name(), sinkFailureTypeCounts.get(type).get());
        }
        return snapshot;
    }

    private void recordDeliveryOutcome(DownstreamDeliveryResult result) {
        lastDeliveryResult = result;
        sinkDeliveryOutcomeCounts.get(result.outcome()).incrementAndGet();
    }

    private Map<String, Long> sinkDeliveryOutcomeCountsSnapshot() {
        java.util.LinkedHashMap<String, Long> snapshot = new java.util.LinkedHashMap<>();
        for (DownstreamDeliveryOutcome outcome : DownstreamDeliveryOutcome.values()) {
            snapshot.put(outcome.name(), sinkDeliveryOutcomeCounts.get(outcome).get());
        }
        return snapshot;
    }

    private static DownstreamDeliveryOutcome outcomeOf(SinkDeliveryFailureType type) {
        return switch (type) {
            case CONFIGURATION_ERROR -> DownstreamDeliveryOutcome.CONFIGURATION_REJECTED;
            case SERIALIZATION_ERROR -> DownstreamDeliveryOutcome.SERIALIZATION_FAILURE;
            case BACKPRESSURE_REJECTED -> DownstreamDeliveryOutcome.BACKPRESSURE_REJECTED;
            case TRANSPORT_FAILURE -> DownstreamDeliveryOutcome.TRANSPORT_FAILURE;
            case TIMEOUT -> DownstreamDeliveryOutcome.TIMEOUT;
            case DEAD_LETTER_ROUTED -> DownstreamDeliveryOutcome.DEAD_LETTER_ROUTED;
            case RETRYABLE_FAILURE, FILESYSTEM_ERROR, WRITE_ERROR, FLUSH_ERROR -> DownstreamDeliveryOutcome.RETRYABLE_FAILURE;
            case PERMANENT_FAILURE -> DownstreamDeliveryOutcome.PERMANENT_FAILURE;
            case UNKNOWN_FAILURE -> DownstreamDeliveryOutcome.UNKNOWN_FAILURE;
        };
    }

    private static SinkDeliveryFailureType failureTypeOf(DownstreamDeliveryOutcome outcome) {
        return switch (outcome) {
            case DELIVERED -> SinkDeliveryFailureType.UNKNOWN_FAILURE;
            case RETRYABLE_FAILURE -> SinkDeliveryFailureType.RETRYABLE_FAILURE;
            case PERMANENT_FAILURE -> SinkDeliveryFailureType.PERMANENT_FAILURE;
            case BACKPRESSURE_REJECTED -> SinkDeliveryFailureType.BACKPRESSURE_REJECTED;
            case CONFIGURATION_REJECTED -> SinkDeliveryFailureType.CONFIGURATION_ERROR;
            case SERIALIZATION_FAILURE -> SinkDeliveryFailureType.SERIALIZATION_ERROR;
            case TRANSPORT_FAILURE -> SinkDeliveryFailureType.TRANSPORT_FAILURE;
            case TIMEOUT -> SinkDeliveryFailureType.TIMEOUT;
            case DEAD_LETTER_ROUTED -> SinkDeliveryFailureType.DEAD_LETTER_ROUTED;
            case UNKNOWN_FAILURE -> SinkDeliveryFailureType.UNKNOWN_FAILURE;
        };
    }

    private static String hexPreview(byte[] payload, int maxBytes) {
        int length = Math.min(payload.length, maxBytes);
        StringBuilder hex = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            int unsigned = payload[i] & 0xFF;
            if (unsigned < 0x10) {
                hex.append('0');
            }
            hex.append(Integer.toHexString(unsigned).toUpperCase());
        }
        return hex.toString();
    }

    private record BackpressureEvent(
            String sourceId,
            BackpressureDecision decision,
            java.time.Instant observedAt,
            int payloadSize) {
    }

}
