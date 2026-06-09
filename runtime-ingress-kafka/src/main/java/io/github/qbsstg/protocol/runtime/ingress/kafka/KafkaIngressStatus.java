package io.github.qbsstg.protocol.runtime.ingress.kafka;

public enum KafkaIngressStatus {
    ACCEPTED,
    DROPPED,
    RETRY_LATER,
    INVALID_SOURCE
}
