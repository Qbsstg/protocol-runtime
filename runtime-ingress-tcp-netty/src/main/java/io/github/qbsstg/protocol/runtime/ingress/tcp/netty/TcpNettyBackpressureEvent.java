package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.util.Objects;

public record TcpNettyBackpressureEvent(
        SourceId sourceId,
        BackpressureDecision decision,
        String channelId,
        int payloadSize) {

    public TcpNettyBackpressureEvent {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
        if (decision == BackpressureDecision.ACCEPT) {
            throw new IllegalArgumentException("event decision must be a backpressure decision");
        }
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId must not be blank");
        }
        if (payloadSize < 0) {
            throw new IllegalArgumentException("payloadSize must not be negative");
        }
    }
}
