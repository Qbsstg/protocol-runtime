package io.github.qbsstg.protocol.runtime.ingress.mqtt;

public enum MqttIngressStatus {
    ACCEPTED,
    DROPPED,
    RETRY_LATER,
    INVALID_SOURCE
}
