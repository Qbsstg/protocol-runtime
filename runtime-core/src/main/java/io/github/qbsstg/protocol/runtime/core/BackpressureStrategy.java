package io.github.qbsstg.protocol.runtime.core;

import java.util.Objects;

@FunctionalInterface
public interface BackpressureStrategy {

    BackpressureDecision evaluate(IngressEnvelope envelope);

    static BackpressureStrategy acceptAll() {
        return ignored -> BackpressureDecision.ACCEPT;
    }

    static BackpressureStrategy fixed(BackpressureDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        return ignored -> decision;
    }
}
