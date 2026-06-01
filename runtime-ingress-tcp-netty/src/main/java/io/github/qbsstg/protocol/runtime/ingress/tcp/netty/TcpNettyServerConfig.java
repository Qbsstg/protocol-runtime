package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

public record TcpNettyServerConfig(
        String host,
        int port,
        int bossThreads,
        int workerThreads) {

    public TcpNettyServerConfig {
        host = requireNonBlank(host, "host");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (bossThreads < 1) {
            throw new IllegalArgumentException("bossThreads must be positive");
        }
        if (workerThreads < 1) {
            throw new IllegalArgumentException("workerThreads must be positive");
        }
    }

    public static TcpNettyServerConfig loopback(int port) {
        return new TcpNettyServerConfig("127.0.0.1", port, 1, 1);
    }

    public static TcpNettyServerConfig anyAddress(int port) {
        return new TcpNettyServerConfig("0.0.0.0", port, 1, 1);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
