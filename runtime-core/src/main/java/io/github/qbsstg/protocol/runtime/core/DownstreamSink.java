package io.github.qbsstg.protocol.runtime.core;

public interface DownstreamSink<T> extends RuntimeLifecycle {

    DownstreamSinkIdentity identity();

    DownstreamDeliveryResult deliver(DownstreamDeliveryRequest<T> request);

    default DownstreamDeliveryResult flush() {
        return DownstreamDeliveryResult.success();
    }

    default DownstreamSinkStatus status() {
        return DownstreamSinkStatus.unknown(identity());
    }
}
