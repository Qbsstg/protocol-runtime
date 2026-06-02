package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.util.Objects;

public record CollectorSourceConfig(String name, SourceId sourceId, RuntimeProtocol protocol) {

    public CollectorSourceConfig(String name, SourceId sourceId) {
        this(name, sourceId, RuntimeProtocol.IEC104);
    }

    public CollectorSourceConfig {
        name = requireName(name);
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(protocol, "protocol must not be null");
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("source name must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.contains(",") || containsWhitespace(trimmed)) {
            throw new IllegalArgumentException("source name must not contain whitespace or comma: " + value);
        }
        return trimmed;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
