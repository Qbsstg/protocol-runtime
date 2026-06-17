package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.DownstreamSinkStatus;

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
        List<HttpListenerStatus> httpListeners,
        List<KafkaConsumerStatus> kafkaConsumers,
        List<MqttClientStatus> mqttClients,
        int activeConnectionCount,
        CollectorRuntimeMetrics metrics,
        SinkType sinkType,
        FileSinkStatus fileSinkStatus,
        FileSinkRotationConfig fileSinkRotation,
        DownstreamSinkAdapterConfig sinkAdapter,
        DownstreamSinkStatus downstreamSinkStatus,
        FailedRecordIsolationStatus failedRecordIsolationStatus,
        BackpressureDecision backpressureDecision,
        long backpressureMaxPayloadBytes,
        BackpressureDecision oversizedPayloadDecision,
        long sinkFailureBackpressureThreshold,
        BackpressureDecision sinkFailureBackpressureDecision,
        boolean strictAsduParsing,
        ManagementStatusSnapshot management) {

    public CollectorStatusSnapshot {
        sources = List.copyOf(sources);
        tcpListeners = List.copyOf(tcpListeners);
        httpListeners = List.copyOf(httpListeners);
        kafkaConsumers = List.copyOf(kafkaConsumers);
        mqttClients = List.copyOf(mqttClients);
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }
        if (fileSinkRotation == null) {
            throw new IllegalArgumentException("fileSinkRotation must not be null");
        }
        if (sinkAdapter == null) {
            throw new IllegalArgumentException("sinkAdapter must not be null");
        }
        if (downstreamSinkStatus == null) {
            throw new IllegalArgumentException("downstreamSinkStatus must not be null");
        }
        if (failedRecordIsolationStatus == null) {
            throw new IllegalArgumentException("failedRecordIsolationStatus must not be null");
        }
        if (backpressureMaxPayloadBytes < 0) {
            throw new IllegalArgumentException("backpressureMaxPayloadBytes must not be negative");
        }
        if (oversizedPayloadDecision == null) {
            throw new IllegalArgumentException("oversizedPayloadDecision must not be null");
        }
        if (sinkFailureBackpressureThreshold < 0) {
            throw new IllegalArgumentException("sinkFailureBackpressureThreshold must not be negative");
        }
        if (sinkFailureBackpressureDecision == null) {
            throw new IllegalArgumentException("sinkFailureBackpressureDecision must not be null");
        }
        if (management == null) {
            throw new IllegalArgumentException("management must not be null");
        }
    }

    public CollectorHealthSnapshot health() {
        return CollectorHealthSnapshot.from(this);
    }

    CollectorStatusSnapshot withManagement(ManagementStatusSnapshot management) {
        return new CollectorStatusSnapshot(
                state,
                startedAt,
                stoppedAt,
                startupFailureReason,
                lastExceptionType,
                lastExceptionMessage,
                sources,
                tcpListeners,
                httpListeners,
                kafkaConsumers,
                mqttClients,
                activeConnectionCount,
                metrics,
                sinkType,
                fileSinkStatus,
                fileSinkRotation,
                sinkAdapter,
                downstreamSinkStatus,
                failedRecordIsolationStatus,
                backpressureDecision,
                backpressureMaxPayloadBytes,
                oversizedPayloadDecision,
                sinkFailureBackpressureThreshold,
                sinkFailureBackpressureDecision,
                strictAsduParsing,
                management);
    }
}
