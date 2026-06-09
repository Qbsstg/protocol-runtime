package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.ingress.http.HttpIngressResponseMode;
import io.github.qbsstg.protocol.runtime.ingress.http.HttpIngressSourceIdMode;

public record HttpListenerStatus(
        String name,
        String sourceName,
        String sourceId,
        String protocol,
        String configuredHost,
        int configuredPort,
        String path,
        HttpIngressSourceIdMode sourceIdMode,
        String sourceIdHeader,
        int maxPayloadBytes,
        HttpIngressResponseMode responseMode,
        int backlog,
        int workerThreads,
        String boundHost,
        Integer boundPort,
        boolean running) {
}
