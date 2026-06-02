package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record StandaloneCollectorAppConfig(
        List<CollectorSourceConfig> sources,
        List<TcpListenerConfig> tcpListeners,
        BackpressureDecision backpressureDecision,
        long backpressureMaxPayloadBytes,
        BackpressureDecision oversizedPayloadDecision,
        SinkType sinkType,
        Path sinkFile,
        FileSinkRotationConfig fileSinkRotation,
        boolean strictAsduParsing) {

    public StandaloneCollectorAppConfig {
        sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
        tcpListeners = List.copyOf(Objects.requireNonNull(tcpListeners, "tcpListeners must not be null"));
        Objects.requireNonNull(backpressureDecision, "backpressureDecision must not be null");
        Objects.requireNonNull(oversizedPayloadDecision, "oversizedPayloadDecision must not be null");
        Objects.requireNonNull(sinkType, "sinkType must not be null");
        Objects.requireNonNull(fileSinkRotation, "fileSinkRotation must not be null");
        if (backpressureMaxPayloadBytes < 0) {
            throw new IllegalArgumentException("backpressureMaxPayloadBytes must not be negative");
        }
        if (oversizedPayloadDecision == BackpressureDecision.ACCEPT) {
            throw new IllegalArgumentException("oversizedPayloadDecision must be RETRY_LATER or DROP");
        }
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        if (tcpListeners.isEmpty()) {
            throw new IllegalArgumentException("tcpListeners must not be empty");
        }
        if (sinkType == SinkType.FILE && sinkFile == null) {
            throw new IllegalArgumentException("collector.sink.file is required when collector.sink.type=file");
        }
    }

    public static StandaloneCollectorAppConfig fromSingle(StandaloneCollectorConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        CollectorSourceConfig source = new CollectorSourceConfig("default", config.sourceId());
        TcpListenerConfig listener = new TcpListenerConfig("default", config.tcp(), source.name(), source.sourceId());
        return new StandaloneCollectorAppConfig(
                List.of(source),
                List.of(listener),
                config.backpressureDecision(),
                config.backpressureMaxPayloadBytes(),
                config.oversizedPayloadDecision(),
                config.sinkType(),
                config.sinkFile(),
                config.fileSinkRotation(),
                config.strictAsduParsing());
    }

    public StandaloneCollectorConfig singleCollectorConfig() {
        if (sources.size() != 1 || tcpListeners.size() != 1) {
            throw new IllegalArgumentException(
                    "StandaloneCollectorConfig requires exactly one source and one TCP listener");
        }
        TcpListenerConfig listener = tcpListeners.get(0);
        return new StandaloneCollectorConfig(
                listener.tcp(),
                listener.sourceId(),
                backpressureDecision,
                backpressureMaxPayloadBytes,
                oversizedPayloadDecision,
                sinkType,
                sinkFile,
                fileSinkRotation,
                strictAsduParsing);
    }
}
