package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.ingress.mqtt.MqttSourceIdMode;

import java.util.List;

public record MqttClientStatus(
        String name,
        String sourceName,
        String sourceId,
        String protocol,
        String brokerUri,
        String clientId,
        List<String> topicFilters,
        int qos,
        MqttSourceIdMode sourceIdMode,
        boolean cleanSession,
        boolean automaticReconnect,
        int connectionTimeoutSeconds,
        int keepAliveSeconds,
        boolean running) {

    public MqttClientStatus {
        topicFilters = List.copyOf(topicFilters);
    }
}
