package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;

import java.util.Objects;

final class RuntimeAppBackpressureStrategy implements BackpressureStrategy {

    private final BackpressureDecision fixedDecision;
    private final long maxPayloadBytes;
    private final BackpressureDecision oversizedPayloadDecision;
    private final RuntimeSinkCounters counters;

    RuntimeAppBackpressureStrategy(
            BackpressureDecision fixedDecision,
            long maxPayloadBytes,
            BackpressureDecision oversizedPayloadDecision,
            RuntimeSinkCounters counters) {
        this.fixedDecision = Objects.requireNonNull(fixedDecision, "fixedDecision must not be null");
        if (maxPayloadBytes < 0) {
            throw new IllegalArgumentException("maxPayloadBytes must not be negative");
        }
        this.maxPayloadBytes = maxPayloadBytes;
        this.oversizedPayloadDecision = Objects.requireNonNull(
                oversizedPayloadDecision,
                "oversizedPayloadDecision must not be null");
        if (oversizedPayloadDecision == BackpressureDecision.ACCEPT) {
            throw new IllegalArgumentException("oversizedPayloadDecision must be RETRY_LATER or DROP");
        }
        this.counters = Objects.requireNonNull(counters, "counters must not be null");
    }

    @Override
    public BackpressureDecision evaluate(IngressEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        BackpressureDecision decision = decisionFor(envelope);
        if (decision != BackpressureDecision.ACCEPT) {
            counters.recordBackpressure(envelope, decision);
        }
        return decision;
    }

    private BackpressureDecision decisionFor(IngressEnvelope envelope) {
        if (fixedDecision != BackpressureDecision.ACCEPT) {
            return fixedDecision;
        }
        if (maxPayloadBytes > 0 && envelope.payload().length > maxPayloadBytes) {
            return oversizedPayloadDecision;
        }
        return BackpressureDecision.ACCEPT;
    }
}
