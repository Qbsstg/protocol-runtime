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
    private volatile ParseFailure lastParseFailure;
    private volatile BackpressureEvent lastBackpressure;

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

    CollectorRuntimeMetrics snapshot() {
        ParseFailure failure = lastParseFailure;
        BackpressureEvent backpressure = lastBackpressure;
        if (failure == null) {
            return snapshotWithoutParseFailure(backpressure);
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
                backpressure == null ? 0 : backpressure.payloadSize());
    }

    private CollectorRuntimeMetrics snapshotWithoutParseFailure(BackpressureEvent backpressure) {
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
                backpressure == null ? 0 : backpressure.payloadSize());
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
