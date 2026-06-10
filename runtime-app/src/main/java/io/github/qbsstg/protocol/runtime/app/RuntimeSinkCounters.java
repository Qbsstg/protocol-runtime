package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;

import java.util.concurrent.atomic.AtomicLong;

final class RuntimeSinkCounters {

    private static final int FAILURE_PAYLOAD_PREVIEW_BYTES = 64;

    private final AtomicLong parsedRecordCount = new AtomicLong();
    private final AtomicLong parseFailureCount = new AtomicLong();
    private final AtomicLong backpressureRetryLaterCount = new AtomicLong();
    private final AtomicLong backpressureDropCount = new AtomicLong();
    private final AtomicLong sinkFailureCount = new AtomicLong();
    private volatile ParseFailure lastParseFailure;
    private volatile BackpressureEvent lastBackpressure;
    private volatile SinkFailureEvent lastSinkFailure;

    void recordParsedRecord(ParsedRecord<?> record) {
        parsedRecordCount.incrementAndGet();
    }

    void recordParseFailure(ParseFailure failure) {
        parseFailureCount.incrementAndGet();
        lastParseFailure = failure;
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

    void recordSinkFailure(String target, String sourceId, java.time.Instant observedAt, RuntimeException failure) {
        sinkFailureCount.incrementAndGet();
        lastSinkFailure = new SinkFailureEvent(
                target,
                sourceId,
                observedAt == null ? java.time.Instant.now() : observedAt,
                failure.getClass().getName(),
                failure.getMessage());
    }

    CollectorRuntimeMetrics snapshot() {
        ParseFailure failure = lastParseFailure;
        BackpressureEvent backpressure = lastBackpressure;
        SinkFailureEvent sinkFailure = lastSinkFailure;
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
                sinkFailure == null ? null : sinkFailure.failureType(),
                sinkFailure == null ? null : sinkFailure.message());
    }

    private CollectorRuntimeMetrics snapshotWithoutParseFailure(
            BackpressureEvent backpressure,
            SinkFailureEvent sinkFailure) {
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
                sinkFailure == null ? null : sinkFailure.failureType(),
                sinkFailure == null ? null : sinkFailure.message());
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

    private record SinkFailureEvent(
            String target,
            String sourceId,
            java.time.Instant observedAt,
            String failureType,
            String message) {
    }
}
