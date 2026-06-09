package io.github.qbsstg.protocol.runtime.ingress.kafka;

public interface KafkaRecordSource {

    void start(KafkaRecordReceiver receiver);

    void stop();

    boolean isRunning();
}
