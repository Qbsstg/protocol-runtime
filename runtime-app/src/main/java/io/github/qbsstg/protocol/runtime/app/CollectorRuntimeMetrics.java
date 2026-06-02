package io.github.qbsstg.protocol.runtime.app;

import java.time.Instant;

public record CollectorRuntimeMetrics(
        long parsedRecordCount,
        long parseFailureCount,
        String lastParseFailureSourceId,
        String lastParseFailureMessage,
        Instant lastParseFailureAt) {

    public static CollectorRuntimeMetrics empty() {
        return new CollectorRuntimeMetrics(0, 0, null, null, null);
    }
}
