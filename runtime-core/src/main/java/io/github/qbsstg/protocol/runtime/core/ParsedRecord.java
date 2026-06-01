package io.github.qbsstg.protocol.runtime.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ParsedRecord<T>(
        SourceId sourceId,
        String protocol,
        String recordType,
        T value,
        byte[] rawPayload,
        Instant observedAt,
        Map<String, String> attributes) {

    public ParsedRecord {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        protocol = requireNonBlank(protocol, "protocol");
        recordType = requireNonBlank(recordType, "recordType");
        Objects.requireNonNull(value, "value must not be null");
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
