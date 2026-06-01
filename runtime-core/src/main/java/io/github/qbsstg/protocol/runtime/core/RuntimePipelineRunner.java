package io.github.qbsstg.protocol.runtime.core;

import java.util.List;
import java.util.Objects;

public final class RuntimePipelineRunner<T> implements RuntimeLifecycle {

    private final RuntimeParserBinding<T> parserBinding;
    private final RecordSink<T> recordSink;
    private final FailureSink failureSink;
    private final BackpressureStrategy backpressureStrategy;
    private boolean running;

    public RuntimePipelineRunner(
            RuntimeParserBinding<T> parserBinding,
            RecordSink<T> recordSink,
            FailureSink failureSink,
            BackpressureStrategy backpressureStrategy) {
        this.parserBinding = Objects.requireNonNull(parserBinding, "parserBinding must not be null");
        this.recordSink = recordSink == null ? RecordSink.noop() : recordSink;
        this.failureSink = failureSink == null ? FailureSink.noop() : failureSink;
        this.backpressureStrategy = backpressureStrategy == null
                ? BackpressureStrategy.acceptAll()
                : backpressureStrategy;
    }

    public String protocol() {
        return parserBinding.protocol();
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        parserBinding.start();
        recordSink.start();
        failureSink.start();
        running = true;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        RuntimeException failure = null;
        failure = stopComponent(failureSink, failure);
        failure = stopComponent(recordSink, failure);
        failure = stopComponent(parserBinding, failure);
        running = false;
        if (failure != null) {
            throw failure;
        }
    }

    public BackpressureDecision accept(IngressEnvelope envelope) {
        ensureRunning();
        Objects.requireNonNull(envelope, "envelope must not be null");

        BackpressureDecision decision = Objects.requireNonNull(
                backpressureStrategy.evaluate(envelope),
                "backpressure decision must not be null");
        if (decision != BackpressureDecision.ACCEPT) {
            return decision;
        }

        List<RuntimeParseResult<T>> results;
        try {
            results = parserBinding.parse(envelope);
        } catch (RuntimeException ex) {
            failureSink.accept(new ParseFailure(
                    envelope.sourceId(),
                    parserBinding.protocol(),
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    envelope.payload(),
                    envelope.receivedAt(),
                    ex,
                    envelope.attributes()));
            return BackpressureDecision.ACCEPT;
        }

        for (RuntimeParseResult<T> result : results == null ? List.<RuntimeParseResult<T>>of() : results) {
            route(result);
        }
        return BackpressureDecision.ACCEPT;
    }

    private void route(RuntimeParseResult<T> result) {
        Objects.requireNonNull(result, "parse result must not be null");
        if (result instanceof ParsedRuntimeRecord<?> parsed) {
            recordSink.accept(castRecord(parsed.record()));
            return;
        }
        if (result instanceof FailedRuntimeParse<?> failed) {
            failureSink.accept(failed.failure());
            return;
        }
        throw new IllegalStateException("Unsupported parse result: " + result.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private ParsedRecord<T> castRecord(ParsedRecord<?> record) {
        return (ParsedRecord<T>) record;
    }

    private void ensureRunning() {
        if (!running) {
            throw new IllegalStateException("runtime pipeline is not running");
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
