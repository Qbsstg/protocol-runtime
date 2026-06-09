package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.github.qbsstg.protocol.runtime.ingress.http.HttpIngressServerConfig;

import java.util.Objects;

public record HttpListenerConfig(
        String name,
        HttpIngressServerConfig http,
        String sourceName,
        SourceId sourceId,
        RuntimeProtocol protocol) {

    public HttpListenerConfig(String name, HttpIngressServerConfig http, String sourceName, SourceId sourceId) {
        this(name, http, sourceName, sourceId, RuntimeProtocol.IEC104);
    }

    public HttpListenerConfig {
        name = requireName(name, "listener name");
        Objects.requireNonNull(http, "http must not be null");
        sourceName = requireName(sourceName, "listener source name");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(protocol, "protocol must not be null");
    }

    private static String requireName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.contains(",") || containsWhitespace(trimmed)) {
            throw new IllegalArgumentException(label + " must not contain whitespace or comma: " + value);
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
