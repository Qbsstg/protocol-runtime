package io.github.qbsstg.protocol.runtime.app;

import java.nio.file.Path;
import java.time.Instant;

public record FailedRecordIsolationStatus(
        boolean enabled,
        Path directory,
        int maxSamples,
        long sampleCount,
        int retainedSampleCount,
        Path lastSampleFile,
        Instant lastSampleAt,
        String lastFailureTarget,
        String lastFailureSourceId,
        String lastFailureType,
        String lastFailureMessage,
        long isolationFailureCount,
        String lastIsolationFailureMessage) {
}
