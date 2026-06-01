package io.github.qbsstg.protocol.runtime.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ParseFailure(
        SourceId sourceId,
        String protocol,
        String message,
        byte[] rawPayload,
        Instant observedAt,
        Throwable cause,
        Map<String, String> attributes) {

    public ParseFailure {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        protocol = requireNonBlank(protocol, "protocol");
        message = requireNonBlank(message, "message");
        rawPayload = rawPayload == null ? new byte[0] : rawPayload.clone();
        observedAt = observedAt == null ? Instant.now() : observedAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public byte[] rawPayload() {
        return rawPayload.clone();
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
