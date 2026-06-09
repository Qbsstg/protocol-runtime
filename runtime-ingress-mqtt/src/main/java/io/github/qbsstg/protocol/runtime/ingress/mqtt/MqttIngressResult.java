package io.github.qbsstg.protocol.runtime.ingress.mqtt;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;

public record MqttIngressResult(
        MqttIngressStatus status,
        BackpressureDecision decision,
        boolean acknowledgeAllowed,
        String reason) {

    public MqttIngressResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
    }

    static MqttIngressResult accepted() {
        return new MqttIngressResult(MqttIngressStatus.ACCEPTED, BackpressureDecision.ACCEPT, true, null);
    }

    static MqttIngressResult dropped() {
        return new MqttIngressResult(MqttIngressStatus.DROPPED, BackpressureDecision.DROP, true, "dropped");
    }

    static MqttIngressResult retryLater() {
        return new MqttIngressResult(
                MqttIngressStatus.RETRY_LATER,
                BackpressureDecision.RETRY_LATER,
                false,
                "retry_later");
    }

    static MqttIngressResult invalidSource(String reason) {
        return new MqttIngressResult(
                MqttIngressStatus.INVALID_SOURCE,
                BackpressureDecision.DROP,
                false,
                reason);
    }
}
