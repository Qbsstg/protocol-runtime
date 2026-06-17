package io.github.qbsstg.protocol.runtime.app;

import java.nio.file.Path;
import java.util.Objects;

public record SinkFailureIsolationConfig(
        boolean enabled,
        Path directory,
        int maxSamples) {

    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_MAX_SAMPLES = 32;

    public SinkFailureIsolationConfig {
        directory = Objects.requireNonNull(directory, "directory must not be null").normalize();
        if (maxSamples < 0) {
            throw new IllegalArgumentException("maxSamples must not be negative");
        }
    }

    public static SinkFailureIsolationConfig defaults(Path dataDir) {
        Path base = dataDir == null ? Path.of("data") : dataDir;
        return new SinkFailureIsolationConfig(
                DEFAULT_ENABLED,
                base.resolve("failed-records"),
                DEFAULT_MAX_SAMPLES);
    }
}
