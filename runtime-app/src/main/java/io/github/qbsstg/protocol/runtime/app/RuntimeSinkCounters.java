package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;

import java.util.concurrent.atomic.AtomicLong;

final class RuntimeSinkCounters {

    private final AtomicLong parsedRecordCount = new AtomicLong();
    private final AtomicLong parseFailureCount = new AtomicLong();
    private volatile ParseFailure lastParseFailure;

    void recordParsedRecord(ParsedRecord<?> record) {
        parsedRecordCount.incrementAndGet();
    }

    void recordParseFailure(ParseFailure failure) {
        parseFailureCount.incrementAndGet();
        lastParseFailure = failure;
    }

    CollectorRuntimeMetrics snapshot() {
        ParseFailure failure = lastParseFailure;
        if (failure == null) {
            return new CollectorRuntimeMetrics(parsedRecordCount.get(), parseFailureCount.get(), null, null, null);
        }
        return new CollectorRuntimeMetrics(
                parsedRecordCount.get(),
                parseFailureCount.get(),
                failure.sourceId().qualifiedValue(),
                failure.message(),
                failure.observedAt());
    }
}
