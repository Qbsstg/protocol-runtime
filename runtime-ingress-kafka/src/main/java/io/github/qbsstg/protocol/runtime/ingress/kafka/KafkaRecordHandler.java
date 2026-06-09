package io.github.qbsstg.protocol.runtime.ingress.kafka;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;

import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class KafkaRecordHandler<T> {

    private final KafkaRecordEnvelopeMapper mapper;
    private final KafkaIngressConsumerConfig config;
    private final RuntimePipelineRunner<T> runner;

    public KafkaRecordHandler(
            KafkaRecordEnvelopeMapper mapper,
            KafkaIngressConsumerConfig config,
            RuntimePipelineRunner<T> runner) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
    }

    public KafkaIngressResult accept(ConsumerRecord<byte[], byte[]> record) {
        IngressEnvelope envelope;
        try {
            envelope = mapper.toEnvelope(record);
        } catch (IllegalArgumentException ex) {
            return KafkaIngressResult.invalidSource(ex.getMessage());
        }

        BackpressureDecision decision = runner.accept(envelope);
        return switch (decision) {
            case ACCEPT -> KafkaIngressResult.accepted(commitAllowedAfterAccept());
            case DROP -> KafkaIngressResult.dropped(commitAllowedAfterAccept());
            case RETRY_LATER -> KafkaIngressResult.retryLater();
        };
    }

    private boolean commitAllowedAfterAccept() {
        return config.commitMode() == KafkaCommitMode.AFTER_ACCEPT;
    }
}
