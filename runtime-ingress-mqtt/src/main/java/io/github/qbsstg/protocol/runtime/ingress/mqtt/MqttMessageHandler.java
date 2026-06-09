package io.github.qbsstg.protocol.runtime.ingress.mqtt;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;

import java.util.Objects;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public final class MqttMessageHandler<T> {

    private final MqttMessageEnvelopeMapper mapper;
    private final RuntimePipelineRunner<T> runner;

    public MqttMessageHandler(
            MqttMessageEnvelopeMapper mapper,
            RuntimePipelineRunner<T> runner) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
    }

    public MqttIngressResult accept(String topic, MqttMessage message) {
        IngressEnvelope envelope;
        try {
            envelope = mapper.toEnvelope(topic, message);
        } catch (IllegalArgumentException ex) {
            return MqttIngressResult.invalidSource(ex.getMessage());
        }

        BackpressureDecision decision = runner.accept(envelope);
        return switch (decision) {
            case ACCEPT -> MqttIngressResult.accepted();
            case DROP -> MqttIngressResult.dropped();
            case RETRY_LATER -> MqttIngressResult.retryLater();
        };
    }
}
