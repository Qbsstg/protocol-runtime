package io.github.qbsstg.protocol.runtime.ingress.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

@FunctionalInterface
public interface KafkaRecordReceiver {

    KafkaIngressResult accept(ConsumerRecord<byte[], byte[]> record);
}
