package io.github.qbsstg.protocol.runtime.ingress.mqtt;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;

import java.time.Clock;

public final class MqttIngressModule {

    public static final String MODULE_NAME = "runtime-ingress-mqtt";
    public static final String TRANSPORT = "mqtt";

    private MqttIngressModule() {
    }

    public static MqttMessageEnvelopeMapper envelopeMapper(MqttIngressClientConfig config) {
        return new MqttMessageEnvelopeMapper(config, Clock.systemUTC());
    }

    public static <T> MqttMessageHandler<T> handler(
            MqttIngressClientConfig config,
            RuntimePipelineRunner<T> runner) {
        return new MqttMessageHandler<>(envelopeMapper(config), runner);
    }

    public static BackpressureDecision defaultBackpressureDecision() {
        return BackpressureDecision.ACCEPT;
    }
}
