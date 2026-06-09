package io.github.qbsstg.protocol.runtime.ingress.mqtt;

import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.util.List;

public record MqttIngressClientConfig(
        String clientName,
        String brokerUri,
        String clientId,
        List<String> topicFilters,
        int qos,
        MqttSourceIdMode sourceIdMode,
        SourceId configuredSourceId,
        String protocol,
        boolean cleanSession,
        boolean automaticReconnect,
        int connectionTimeoutSeconds,
        int keepAliveSeconds) {

    public MqttIngressClientConfig {
        clientName = requireNonBlank(clientName == null ? "mqtt" : clientName, "clientName");
        brokerUri = requireNonBlank(brokerUri, "brokerUri");
        clientId = requireNonBlank(clientId, "clientId");
        topicFilters = topicFilters == null ? List.of() : List.copyOf(topicFilters);
        sourceIdMode = sourceIdMode == null ? MqttSourceIdMode.CONFIGURED : sourceIdMode;
        protocol = requireNonBlank(protocol, "protocol");
        if (topicFilters.isEmpty()) {
            throw new IllegalArgumentException("at least one topic filter must be configured");
        }
        if (topicFilters.stream().anyMatch(topic -> topic == null || topic.isBlank())) {
            throw new IllegalArgumentException("topicFilters must not contain blank values");
        }
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("qos must be between 0 and 2");
        }
        if (sourceIdMode == MqttSourceIdMode.CONFIGURED && configuredSourceId == null) {
            throw new IllegalArgumentException("configuredSourceId is required for CONFIGURED source id mode");
        }
        if (connectionTimeoutSeconds < 1) {
            throw new IllegalArgumentException("connectionTimeoutSeconds must be positive");
        }
        if (keepAliveSeconds < 1) {
            throw new IllegalArgumentException("keepAliveSeconds must be positive");
        }
    }

    public static MqttIngressClientConfig configured(
            String brokerUri,
            String clientId,
            String topicFilter,
            SourceId sourceId,
            String protocol) {
        return new MqttIngressClientConfig(
                "mqtt",
                brokerUri,
                clientId,
                List.of(topicFilter),
                0,
                MqttSourceIdMode.CONFIGURED,
                sourceId,
                protocol,
                true,
                true,
                30,
                60);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
