package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.github.qbsstg.protocol.runtime.ingress.mqtt.MqttIngressClientConfig;

import java.util.Objects;

public record MqttClientConfig(
        String name,
        MqttIngressClientConfig mqtt,
        String sourceName,
        SourceId sourceId,
        RuntimeProtocol protocol) {

    public MqttClientConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(mqtt, "mqtt must not be null");
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(protocol, "protocol must not be null");
    }
}
