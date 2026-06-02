package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServerConfig;

import java.util.Objects;

public record TcpListenerConfig(
        String name,
        TcpNettyServerConfig tcp,
        String sourceName,
        SourceId sourceId) {

    public TcpListenerConfig {
        name = requireName(name, "listener name");
        Objects.requireNonNull(tcp, "tcp must not be null");
        sourceName = requireName(sourceName, "listener source name");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
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
