package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record StandaloneCollectorAppConfig(
        List<CollectorSourceConfig> sources,
        List<TcpListenerConfig> tcpListeners,
        List<HttpListenerConfig> httpListeners,
        List<KafkaConsumerConfig> kafkaConsumers,
        List<MqttClientConfig> mqttClients,
        BackpressureDecision backpressureDecision,
        long backpressureMaxPayloadBytes,
        BackpressureDecision oversizedPayloadDecision,
        long sinkFailureBackpressureThreshold,
        BackpressureDecision sinkFailureBackpressureDecision,
        SinkType sinkType,
        Path sinkFile,
        FileSinkRotationConfig fileSinkRotation,
        boolean strictAsduParsing) {

    public StandaloneCollectorAppConfig {
        sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
        tcpListeners = List.copyOf(Objects.requireNonNull(tcpListeners, "tcpListeners must not be null"));
        httpListeners = List.copyOf(Objects.requireNonNull(httpListeners, "httpListeners must not be null"));
        kafkaConsumers = List.copyOf(Objects.requireNonNull(kafkaConsumers, "kafkaConsumers must not be null"));
        mqttClients = List.copyOf(Objects.requireNonNull(mqttClients, "mqttClients must not be null"));
        Objects.requireNonNull(backpressureDecision, "backpressureDecision must not be null");
        Objects.requireNonNull(oversizedPayloadDecision, "oversizedPayloadDecision must not be null");
        Objects.requireNonNull(sinkFailureBackpressureDecision, "sinkFailureBackpressureDecision must not be null");
        Objects.requireNonNull(sinkType, "sinkType must not be null");
        Objects.requireNonNull(fileSinkRotation, "fileSinkRotation must not be null");
        if (backpressureMaxPayloadBytes < 0) {
            throw new IllegalArgumentException("backpressureMaxPayloadBytes must not be negative");
        }
        if (oversizedPayloadDecision == BackpressureDecision.ACCEPT) {
            throw new IllegalArgumentException("oversizedPayloadDecision must be RETRY_LATER or DROP");
        }
        if (sinkFailureBackpressureThreshold < 0) {
            throw new IllegalArgumentException("sinkFailureBackpressureThreshold must not be negative");
        }
        if (sinkFailureBackpressureDecision == BackpressureDecision.ACCEPT) {
            throw new IllegalArgumentException("sinkFailureBackpressureDecision must be RETRY_LATER or DROP");
        }
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        if (tcpListeners.isEmpty() && httpListeners.isEmpty() && kafkaConsumers.isEmpty() && mqttClients.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one TCP listener, HTTP listener, Kafka consumer, or MQTT client is required");
        }
        if (sinkType == SinkType.FILE && sinkFile == null) {
            throw new IllegalArgumentException("collector.sink.file is required when collector.sink.type=file");
        }
    }

    public StandaloneCollectorAppConfig(
            List<CollectorSourceConfig> sources,
            List<TcpListenerConfig> tcpListeners,
            BackpressureDecision backpressureDecision,
            long backpressureMaxPayloadBytes,
            BackpressureDecision oversizedPayloadDecision,
            long sinkFailureBackpressureThreshold,
            BackpressureDecision sinkFailureBackpressureDecision,
            SinkType sinkType,
            Path sinkFile,
            FileSinkRotationConfig fileSinkRotation,
            boolean strictAsduParsing) {
        this(
                sources,
                tcpListeners,
                List.of(),
                List.of(),
                List.of(),
                backpressureDecision,
                backpressureMaxPayloadBytes,
                oversizedPayloadDecision,
                sinkFailureBackpressureThreshold,
                sinkFailureBackpressureDecision,
                sinkType,
                sinkFile,
                fileSinkRotation,
                strictAsduParsing);
    }

    public StandaloneCollectorAppConfig(
            List<CollectorSourceConfig> sources,
            List<TcpListenerConfig> tcpListeners,
            List<HttpListenerConfig> httpListeners,
            List<KafkaConsumerConfig> kafkaConsumers,
            BackpressureDecision backpressureDecision,
            long backpressureMaxPayloadBytes,
            BackpressureDecision oversizedPayloadDecision,
            long sinkFailureBackpressureThreshold,
            BackpressureDecision sinkFailureBackpressureDecision,
            SinkType sinkType,
            Path sinkFile,
            FileSinkRotationConfig fileSinkRotation,
            boolean strictAsduParsing) {
        this(
                sources,
                tcpListeners,
                httpListeners,
                kafkaConsumers,
                List.of(),
                backpressureDecision,
                backpressureMaxPayloadBytes,
                oversizedPayloadDecision,
                sinkFailureBackpressureThreshold,
                sinkFailureBackpressureDecision,
                sinkType,
                sinkFile,
                fileSinkRotation,
                strictAsduParsing);
    }

    public static StandaloneCollectorAppConfig fromSingle(StandaloneCollectorConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        CollectorSourceConfig source = new CollectorSourceConfig("default", config.sourceId(), config.protocol());
        TcpListenerConfig listener = new TcpListenerConfig(
                "default",
                config.tcp(),
                source.name(),
                source.sourceId(),
                source.protocol());
        return new StandaloneCollectorAppConfig(
                List.of(source),
                List.of(listener),
                List.of(),
                List.of(),
                List.of(),
                config.backpressureDecision(),
                config.backpressureMaxPayloadBytes(),
                config.oversizedPayloadDecision(),
                config.sinkFailureBackpressureThreshold(),
                config.sinkFailureBackpressureDecision(),
                config.sinkType(),
                config.sinkFile(),
                config.fileSinkRotation(),
                config.strictAsduParsing());
    }

    public StandaloneCollectorConfig singleCollectorConfig() {
        if (sources.size() != 1
                || tcpListeners.size() != 1
                || !httpListeners.isEmpty()
                || !kafkaConsumers.isEmpty()
                || !mqttClients.isEmpty()) {
            throw new IllegalArgumentException(
                    "StandaloneCollectorConfig requires exactly one source, one TCP listener, and no HTTP listeners, Kafka consumers, or MQTT clients");
        }
        TcpListenerConfig listener = tcpListeners.get(0);
        return new StandaloneCollectorConfig(
                listener.tcp(),
                listener.sourceId(),
                backpressureDecision,
                backpressureMaxPayloadBytes,
                oversizedPayloadDecision,
                sinkFailureBackpressureThreshold,
                sinkFailureBackpressureDecision,
                sinkType,
                sinkFile,
                fileSinkRotation,
                listener.protocol(),
                strictAsduParsing);
    }
}
