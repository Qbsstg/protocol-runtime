package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryOutcome;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryRequest;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryResult;
import io.github.qbsstg.protocol.runtime.core.DownstreamSink;
import io.github.qbsstg.protocol.runtime.core.DownstreamSinkStatus;
import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.logging.Logger;

record RuntimeSinks(
        RecordSink<Object> recordSink,
        FailureSink failureSink,
        DownstreamSink<Object> downstreamSink,
        InMemoryRuntimeSink<Object> inMemorySink,
        RuntimeSinkCounters counters,
        FailedRecordIsolation failedRecords,
        DownstreamSinkAdapterConfig adapterConfig) {

    RuntimeSinks {
        Objects.requireNonNull(recordSink, "recordSink must not be null");
        Objects.requireNonNull(failureSink, "failureSink must not be null");
        counters = Objects.requireNonNullElseGet(counters, RuntimeSinkCounters::new);
        failedRecords = Objects.requireNonNull(failedRecords, "failedRecords must not be null");
        adapterConfig = Objects.requireNonNullElseGet(adapterConfig, DownstreamSinkAdapterConfig::defaults);
        if (downstreamSink == null) {
            downstreamSink = new RecordFailureDownstreamSink<>(
                    adapterConfig.identity(SinkType.LOGGING),
                    recordSink,
                    failureSink);
        }
    }

    RuntimeSinks(
            RecordSink<Object> recordSink,
            FailureSink failureSink,
            InMemoryRuntimeSink<Object> inMemorySink,
            RuntimeSinkCounters counters,
            FailedRecordIsolation failedRecords) {
        this(
                recordSink,
                failureSink,
                null,
                inMemorySink,
                counters,
                failedRecords,
                DownstreamSinkAdapterConfig.defaults());
    }

    static RuntimeSinks from(StandaloneCollectorConfig config) {
        return from(StandaloneCollectorAppConfig.fromSingle(config));
    }

    static RuntimeSinks from(StandaloneCollectorAppConfig config) {
        return switch (config.sinkType()) {
            case LOGGING -> {
                LoggingRuntimeSink<Object> sink = new LoggingRuntimeSink<>(
                        Logger.getLogger("io.github.qbsstg.protocol.runtime.collector"));
                RuntimeSinkCounters counters = new RuntimeSinkCounters();
                FailedRecordIsolation failedRecords = new FailedRecordIsolation(config.failedRecords());
                yield new RuntimeSinks(
                        sink,
                        sink,
                        new RecordFailureDownstreamSink<>(config.sinkAdapter().identity(config.sinkType()), sink, sink),
                        null,
                        counters,
                        failedRecords,
                        config.sinkAdapter());
            }
            case FILE -> {
                FileRuntimeSink<Object> sink = new FileRuntimeSink<>(
                        config.sinkFile(),
                        config.fileSinkRotation());
                RuntimeSinkCounters counters = new RuntimeSinkCounters();
                FailedRecordIsolation failedRecords = new FailedRecordIsolation(config.failedRecords());
                yield new RuntimeSinks(
                        sink,
                        sink,
                        new RecordFailureDownstreamSink<>(config.sinkAdapter().identity(config.sinkType()), sink, sink),
                        null,
                        counters,
                        failedRecords,
                        config.sinkAdapter());
            }
            case IN_MEMORY -> {
                InMemoryRuntimeSink<Object> sink = new InMemoryRuntimeSink<>();
                RuntimeSinkCounters counters = new RuntimeSinkCounters();
                FailedRecordIsolation failedRecords = new FailedRecordIsolation(config.failedRecords());
                yield new RuntimeSinks(
                        sink,
                        sink,
                        new RecordFailureDownstreamSink<>(config.sinkAdapter().identity(config.sinkType()), sink, sink),
                        sink,
                        counters,
                        failedRecords,
                        config.sinkAdapter());
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
                deliver(DownstreamDeliveryRequest.forRecord(widen(record)));
            }
        };
    }

    FailureSink runnerFailureSink() {
        return new FailureSink() {
            @Override
            public void accept(ParseFailure failure) {
                deliver(DownstreamDeliveryRequest.forFailure(failure));
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

    DownstreamSinkStatus downstreamSinkStatus() {
        DownstreamSinkStatus base = downstreamSink.status();
        CollectorRuntimeMetrics metrics = counters.snapshot();
        DownstreamDeliveryResult lastResult = metrics.lastSinkDeliveryResult();
        boolean ready = base.ready()
                && (lastResult == null || lastResult.backpressureDecision().isAccepted())
                && (lastResult == null || lastResult.outcome() != DownstreamDeliveryOutcome.CONFIGURATION_REJECTED);
        boolean healthy = base.healthy()
                && (lastResult == null
                        || lastResult.delivered()
                        || lastResult.retryable()
                        || lastResult.outcome() == DownstreamDeliveryOutcome.BACKPRESSURE_REJECTED);
        return new DownstreamSinkStatus(
                base.identity(),
                base.running(),
                healthy,
                ready,
                lastResult == null ? base.backpressureDecision() : lastResult.backpressureDecision(),
                metrics.sinkDeliveredCount(),
                metrics.sinkFailureCount(),
                lastResult,
                diagnostics(base.diagnostics(), metrics));
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

    void start() {
        downstreamSink.start();
    }

    void stop() {
        downstreamSink.stop();
    }

    private void deliver(DownstreamDeliveryRequest<Object> request) {
        try {
            DownstreamDeliveryResult result = downstreamSink.deliver(request);
            if (result == null || !result.delivered()) {
                SinkDeliveryFailure failure = counters.recordDeliveryFailure(target(request), request, result);
                isolate(request, failure);
                return;
            }
            counters.recordSuccessfulDelivery(request, result);
        } catch (RuntimeException ex) {
            SinkDeliveryFailure failure = counters.recordSinkFailure(
                    target(request),
                    request.sourceId().qualifiedValue(),
                    request.observedAt(),
                    ex);
            isolate(request, failure);
        }
    }

    private void isolate(DownstreamDeliveryRequest<Object> request, SinkDeliveryFailure failure) {
        if (request.kind() == io.github.qbsstg.protocol.runtime.core.DownstreamRecordKind.RECORD) {
            failedRecords.isolate(request.record(), failure);
        } else {
            failedRecords.isolate(request.failure(), failure);
        }
    }

    private static String target(DownstreamDeliveryRequest<?> request) {
        return request.kind() == io.github.qbsstg.protocol.runtime.core.DownstreamRecordKind.RECORD
                ? "record"
                : "failure";
    }

    private Map<String, String> diagnostics(Map<String, String> base, CollectorRuntimeMetrics metrics) {
        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.putAll(adapterDiagnostics());
        diagnostics.putAll(base);
        diagnostics.put("deliveryOutcomeCounts", metrics.sinkDeliveryOutcomeCounts().toString());
        if (metrics.lastSinkDeliveryOutcome() != null) {
            diagnostics.put("lastDeliveryOutcome", metrics.lastSinkDeliveryOutcome());
        }
        if (metrics.lastSinkDeliveryFailureType() != null) {
            diagnostics.put("lastFailureType", metrics.lastSinkDeliveryFailureType());
        }
        return diagnostics;
    }

    private Map<String, String> adapterDiagnostics() {
        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("adapterType", adapterConfig.type());
        diagnostics.put("endpointConfigured", Boolean.toString(adapterConfig.endpoint() != null));
        diagnostics.put("topicConfigured", Boolean.toString(adapterConfig.topic() != null));
        diagnostics.put("authRefConfigured", Boolean.toString(adapterConfig.authenticationReferenceConfigured()));
        diagnostics.put("timeoutMillis", Long.toString(adapterConfig.timeoutMillis()));
        diagnostics.put("batchingPosture", adapterConfig.batchingPosture());
        diagnostics.put("retryPosture", adapterConfig.retryPosture());
        diagnostics.put("deadLetterOutput", adapterConfig.deadLetterOutput());
        return diagnostics;
    }

    @SuppressWarnings("unchecked")
    private static ParsedRecord<Object> widen(ParsedRecord<?> record) {
        return (ParsedRecord<Object>) record;
    }
}
