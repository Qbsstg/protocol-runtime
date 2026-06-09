package io.github.qbsstg.protocol.runtime.ingress.kafka;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;

public record KafkaIngressResult(
        KafkaIngressStatus status,
        BackpressureDecision decision,
        boolean commitAllowed,
        String reason) {

    public KafkaIngressResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
    }

    static KafkaIngressResult accepted(boolean commitAllowed) {
        return new KafkaIngressResult(KafkaIngressStatus.ACCEPTED, BackpressureDecision.ACCEPT, commitAllowed, null);
    }

    static KafkaIngressResult dropped(boolean commitAllowed) {
        return new KafkaIngressResult(KafkaIngressStatus.DROPPED, BackpressureDecision.DROP, commitAllowed, "dropped");
    }

    static KafkaIngressResult retryLater() {
        return new KafkaIngressResult(
                KafkaIngressStatus.RETRY_LATER,
                BackpressureDecision.RETRY_LATER,
                false,
                "retry_later");
    }

    static KafkaIngressResult invalidSource(String reason) {
        return new KafkaIngressResult(
                KafkaIngressStatus.INVALID_SOURCE,
                BackpressureDecision.DROP,
                false,
                reason);
    }
}
