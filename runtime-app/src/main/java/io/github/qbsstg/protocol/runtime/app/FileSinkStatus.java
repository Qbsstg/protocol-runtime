package io.github.qbsstg.protocol.runtime.app;

import java.nio.file.Path;
import java.util.Objects;

public record FileSinkStatus(
        Path output,
        boolean open,
        long activeBytes,
        int retainedHistoryCount,
        long rotationCount,
        FileSinkRotationConfig rotation) {

    public FileSinkStatus {
        output = Objects.requireNonNull(output, "output must not be null");
        rotation = Objects.requireNonNull(rotation, "rotation must not be null");
        if (activeBytes < 0) {
            throw new IllegalArgumentException("activeBytes must not be negative");
        }
        if (retainedHistoryCount < 0) {
            throw new IllegalArgumentException("retainedHistoryCount must not be negative");
        }
        if (rotationCount < 0) {
            throw new IllegalArgumentException("rotationCount must not be negative");
        }
    }
}
