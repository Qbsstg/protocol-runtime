package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;

import java.time.Instant;
import java.util.List;

public record CollectorStatusSnapshot(
        CollectorLifecycleState state,
        Instant startedAt,
        Instant stoppedAt,
        String startupFailureReason,
        String lastExceptionType,
        String lastExceptionMessage,
        List<CollectorSourceStatus> sources,
        List<TcpListenerStatus> tcpListeners,
        int activeConnectionCount,
        SinkType sinkType,
        BackpressureDecision backpressureDecision,
        boolean strictAsduParsing) {

    public CollectorStatusSnapshot {
        sources = List.copyOf(sources);
        tcpListeners = List.copyOf(tcpListeners);
    }
}
