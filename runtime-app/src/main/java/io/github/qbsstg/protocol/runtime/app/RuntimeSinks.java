package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.iec104.Iec104Frame;
import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;
import io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle;

import java.util.Optional;
import java.util.logging.Logger;

record RuntimeSinks(
        RecordSink<Iec104Frame> recordSink,
        FailureSink failureSink,
        InMemoryRuntimeSink<Iec104Frame> inMemorySink) {

    static RuntimeSinks from(StandaloneCollectorConfig config) {
        return from(StandaloneCollectorAppConfig.fromSingle(config));
    }

    static RuntimeSinks from(StandaloneCollectorAppConfig config) {
        return switch (config.sinkType()) {
            case LOGGING -> {
                LoggingRuntimeSink<Iec104Frame> sink = new LoggingRuntimeSink<>(
                        Logger.getLogger("io.github.qbsstg.protocol.runtime.collector"));
                yield new RuntimeSinks(sink, sink, null);
            }
            case FILE -> {
                FileRuntimeSink<Iec104Frame> sink = new FileRuntimeSink<>(
                        config.sinkFile(),
                        config.fileSinkRotation());
                yield new RuntimeSinks(sink, sink, null);
            }
            case IN_MEMORY -> {
                InMemoryRuntimeSink<Iec104Frame> sink = new InMemoryRuntimeSink<>();
                yield new RuntimeSinks(sink, sink, sink);
            }
        };
    }

    Optional<InMemoryRuntimeSink<Iec104Frame>> inMemory() {
        return Optional.ofNullable(inMemorySink);
    }

    RecordSink<Iec104Frame> runnerRecordSink() {
        return new RecordSink<>() {
            @Override
            public void accept(ParsedRecord<Iec104Frame> record) {
                recordSink.accept(record);
            }
        };
    }

    FailureSink runnerFailureSink() {
        return new FailureSink() {
            @Override
            public void accept(ParseFailure failure) {
                failureSink.accept(failure);
            }
        };
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
}
