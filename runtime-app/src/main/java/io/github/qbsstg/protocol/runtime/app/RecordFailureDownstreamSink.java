package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryRequest;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryResult;
import io.github.qbsstg.protocol.runtime.core.DownstreamRecordKind;
import io.github.qbsstg.protocol.runtime.core.DownstreamSink;
import io.github.qbsstg.protocol.runtime.core.DownstreamSinkIdentity;
import io.github.qbsstg.protocol.runtime.core.DownstreamSinkStatus;
import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.RecordSink;

import java.util.Map;
import java.util.Objects;

final class RecordFailureDownstreamSink<T> implements DownstreamSink<T> {

    private final DownstreamSinkIdentity identity;
    private final RecordSink<T> recordSink;
    private final FailureSink failureSink;
    private volatile boolean running;

    RecordFailureDownstreamSink(
            DownstreamSinkIdentity identity,
            RecordSink<T> recordSink,
            FailureSink failureSink) {
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.recordSink = Objects.requireNonNull(recordSink, "recordSink must not be null");
        this.failureSink = Objects.requireNonNull(failureSink, "failureSink must not be null");
    }

    @Override
    public DownstreamSinkIdentity identity() {
        return identity;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public DownstreamDeliveryResult deliver(DownstreamDeliveryRequest<T> request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.kind() == DownstreamRecordKind.RECORD) {
            recordSink.accept(request.record());
        } else {
            failureSink.accept(request.failure());
        }
        return DownstreamDeliveryResult.success();
    }

    @Override
    public void stop() {
        RuntimeException failure = null;
        failure = stopComponent(failureSink, failure);
        if ((Object) recordSink != failureSink) {
            failure = stopComponent(recordSink, failure);
        }
        running = false;
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public DownstreamSinkStatus status() {
        return new DownstreamSinkStatus(
                identity,
                running,
                true,
                true,
                null,
                0,
                0,
                null,
                Map.of("bridge", "record-failure"));
    }

    private static RuntimeException stopComponent(
            io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle component,
            RuntimeException failure) {
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
