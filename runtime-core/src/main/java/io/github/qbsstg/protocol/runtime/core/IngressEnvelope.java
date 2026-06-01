package io.github.qbsstg.protocol.runtime.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record IngressEnvelope(
        SourceId sourceId,
        String transport,
        byte[] payload,
        Instant receivedAt,
        Map<String, String> attributes) {

    public IngressEnvelope {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        transport = requireNonBlank(transport, "transport");
        payload = payload == null ? new byte[0] : payload.clone();
        receivedAt = receivedAt == null ? Instant.now() : receivedAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
