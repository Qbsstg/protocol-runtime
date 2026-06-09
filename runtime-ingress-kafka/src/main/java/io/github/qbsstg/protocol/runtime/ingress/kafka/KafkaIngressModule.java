package io.github.qbsstg.protocol.runtime.ingress.kafka;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;

import java.time.Clock;

public final class KafkaIngressModule {

    public static final String MODULE_NAME = "runtime-ingress-kafka";
    public static final String TRANSPORT = "kafka";

    private KafkaIngressModule() {
    }

    public static KafkaRecordEnvelopeMapper envelopeMapper(KafkaIngressConsumerConfig config) {
        return new KafkaRecordEnvelopeMapper(config, Clock.systemUTC());
    }

    public static <T> KafkaRecordHandler<T> handler(
            KafkaIngressConsumerConfig config,
            RuntimePipelineRunner<T> runner) {
        return new KafkaRecordHandler<>(envelopeMapper(config), config, runner);
    }

    public static BackpressureDecision defaultBackpressureDecision() {
        return BackpressureDecision.ACCEPT;
    }
}
