package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;
import io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle;

import java.util.Optional;
import java.util.logging.Logger;

record RuntimeSinks(
        RecordSink<Object> recordSink,
        FailureSink failureSink,
        InMemoryRuntimeSink<Object> inMemorySink,
        RuntimeSinkCounters counters,
        FailedRecordIsolation failedRecords) {

    static RuntimeSinks from(StandaloneCollectorConfig config) {
        return from(StandaloneCollectorAppConfig.fromSingle(config));
    }

    static RuntimeSinks from(StandaloneCollectorAppConfig config) {
        return switch (config.sinkType()) {
            case LOGGING -> {
                LoggingRuntimeSink<Object> sink = new LoggingRuntimeSink<>(
                        Logger.getLogger("io.github.qbsstg.protocol.runtime.collector"));
                yield new RuntimeSinks(
                        sink,
                        sink,
                        null,
                        new RuntimeSinkCounters(),
                        new FailedRecordIsolation(config.failedRecords()));
            }
            case FILE -> {
                FileRuntimeSink<Object> sink = new FileRuntimeSink<>(
                        config.sinkFile(),
                        config.fileSinkRotation());
                yield new RuntimeSinks(
                        sink,
                        sink,
                        null,
                        new RuntimeSinkCounters(),
                        new FailedRecordIsolation(config.failedRecords()));
            }
            case IN_MEMORY -> {
                InMemoryRuntimeSink<Object> sink = new InMemoryRuntimeSink<>();
                yield new RuntimeSinks(
                        sink,
                        sink,
                        sink,
                        new RuntimeSinkCounters(),
                        new FailedRecordIsolation(config.failedRecords()));
            }
        };
    }

    Optional<InMemoryRuntimeSink<Object>> inMemory() {
        return Optional.ofNullable(inMemorySink);
    }

    <T> RecordSink<T> runnerRecordSink() {
        return new RecordSink<>() {
            @Override
            public void accept(ParsedRecord<T> record) {
                try {
                    recordSink.accept(widen(record));
                    counters.recordParsedRecord(record);
                } catch (RuntimeException ex) {
                    SinkDeliveryFailure failure = counters.recordSinkFailure(
                            "record",
                            record.sourceId().qualifiedValue(),
                            record.observedAt(),
                            ex);
                    failedRecords.isolate(record, failure);
                }
            }
        };
    }

    FailureSink runnerFailureSink() {
        return new FailureSink() {
            @Override
            public void accept(ParseFailure failure) {
                try {
                    failureSink.accept(failure);
                    counters.recordParseFailure(failure);
                } catch (RuntimeException ex) {
                    SinkDeliveryFailure sinkFailure = counters.recordSinkFailure(
                            "failure",
                            failure.sourceId().qualifiedValue(),
                            failure.observedAt(),
                            ex);
                    failedRecords.isolate(failure, sinkFailure);
                }
            }
        };
    }

    CollectorRuntimeMetrics metricsSnapshot() {
        return counters.snapshot();
    }

    FileSinkStatus fileSinkStatus() {
        if (recordSink instanceof FileRuntimeSink<?> fileSink) {
            return fileSink.status();
        }
        return null;
    }

    FailedRecordIsolationStatus failedRecordIsolationStatus() {
        return failedRecords.status();
    }

    BackpressureStrategy backpressureStrategy(StandaloneCollectorAppConfig config) {
        return new RuntimeAppBackpressureStrategy(
                config.backpressureDecision(),
                config.backpressureMaxPayloadBytes(),
                config.oversizedPayloadDecision(),
                config.sinkFailureBackpressureThreshold(),
                config.sinkFailureBackpressureDecision(),
                counters);
    }

    void stop() {
        RuntimeException failure = null;
        failure = stopComponent(failureSink, failure);
        if ((Object) recordSink != failureSink) {
            failure = stopComponent(recordSink, failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException stopComponent(RuntimeLifecycle component, RuntimeException failure) {
        try {
            component.stop();
            return failure;
        } catch (RuntimeException ex) {
            if (failure == null) {
                return ex;
            }
            failure.addSuppressed(ex);
            return failure;
        }
    }

    @SuppressWarnings("unchecked")
    private static ParsedRecord<Object> widen(ParsedRecord<?> record) {
        return (ParsedRecord<Object>) record;
    }
}
