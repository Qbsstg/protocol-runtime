package io.github.qbsstg.protocol.runtime.ingress.http;

import io.github.qbsstg.protocol.runtime.core.SourceId;

public record HttpIngressServerConfig(
        String host,
        int port,
        String path,
        HttpIngressSourceIdMode sourceIdMode,
        String sourceIdHeader,
        SourceId configuredSourceId,
        int maxPayloadBytes,
        HttpIngressResponseMode responseMode,
        int backlog,
        int workerThreads,
        String listenerName) {

    private static final String SOURCE_ID_TOKEN = "{sourceId}";

    public HttpIngressServerConfig {
        host = requireNonBlank(host, "host");
        path = normalizePath(path);
        sourceIdMode = sourceIdMode == null ? HttpIngressSourceIdMode.CONFIGURED : sourceIdMode;
        responseMode = responseMode == null ? HttpIngressResponseMode.ACK_ON_ACCEPT : responseMode;
        listenerName = requireNonBlank(listenerName == null ? "http" : listenerName, "listenerName");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (maxPayloadBytes < 0) {
            throw new IllegalArgumentException("maxPayloadBytes must not be negative");
        }
        if (backlog < 0) {
            throw new IllegalArgumentException("backlog must not be negative");
        }
        if (workerThreads < 1) {
            throw new IllegalArgumentException("workerThreads must be positive");
        }
        if (sourceIdMode == HttpIngressSourceIdMode.PATH && !path.endsWith(SOURCE_ID_TOKEN)) {
            throw new IllegalArgumentException("path source id mode requires path to end with {sourceId}");
        }
        if (sourceIdMode == HttpIngressSourceIdMode.HEADER) {
            sourceIdHeader = requireNonBlank(sourceIdHeader, "sourceIdHeader");
        }
        if (sourceIdMode == HttpIngressSourceIdMode.CONFIGURED && configuredSourceId == null) {
            throw new IllegalArgumentException("configuredSourceId is required for CONFIGURED source id mode");
        }
    }

    public static HttpIngressServerConfig loopback(int port, SourceId sourceId) {
        return new HttpIngressServerConfig(
                "127.0.0.1",
                port,
                "/ingress",
                HttpIngressSourceIdMode.CONFIGURED,
                null,
                sourceId,
                0,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                0,
                1,
                "http");
    }

    public static HttpIngressServerConfig pathSource(int port) {
        return new HttpIngressServerConfig(
                "127.0.0.1",
                port,
                "/ingress/{sourceId}",
                HttpIngressSourceIdMode.PATH,
                null,
                null,
                0,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                0,
                1,
                "http");
    }

    public String contextPath() {
        int tokenIndex = path.indexOf(SOURCE_ID_TOKEN);
        if (tokenIndex < 0) {
            return path;
        }
        return path.substring(0, tokenIndex);
    }

    private static String normalizePath(String value) {
        String pathValue = requireNonBlank(value, "path");
        if (!pathValue.startsWith("/")) {
            throw new IllegalArgumentException("path must start with /");
        }
        return pathValue;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
