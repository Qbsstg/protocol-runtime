package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaIngressConsumerConfig;

import java.util.Objects;

public record KafkaConsumerConfig(
        String name,
        KafkaIngressConsumerConfig kafka,
        String sourceName,
        SourceId sourceId,
        RuntimeProtocol protocol) {

    public KafkaConsumerConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(kafka, "kafka must not be null");
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(protocol, "protocol must not be null");
    }
}
