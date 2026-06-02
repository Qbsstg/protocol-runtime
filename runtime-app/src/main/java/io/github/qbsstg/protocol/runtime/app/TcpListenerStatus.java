package io.github.qbsstg.protocol.runtime.app;

public record TcpListenerStatus(
        String name,
        String sourceName,
        String sourceId,
        String configuredHost,
        int configuredPort,
        String boundHost,
        Integer boundPort,
        boolean running,
        int activeConnectionCount) {
}
