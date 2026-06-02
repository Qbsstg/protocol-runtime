package io.github.qbsstg.protocol.runtime.app;

public record FileSinkRotationConfig(long maxBytes, int maxHistory) {

    public static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L;
    public static final int DEFAULT_MAX_HISTORY = 5;

    public FileSinkRotationConfig {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        if (maxHistory <= 0) {
            throw new IllegalArgumentException("maxHistory must be positive");
        }
    }

    public static FileSinkRotationConfig defaults() {
        return new FileSinkRotationConfig(DEFAULT_MAX_BYTES, DEFAULT_MAX_HISTORY);
    }
}
