package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaCommitMode;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaSourceIdMode;

import java.util.List;

public record KafkaConsumerStatus(
        String name,
        String sourceName,
        String sourceId,
        String protocol,
        String bootstrapServers,
        String groupId,
        List<String> topics,
        String topicPattern,
        KafkaSourceIdMode sourceIdMode,
        String sourceIdHeader,
        KafkaCommitMode commitMode,
        String autoOffsetReset,
        int maxPollRecords,
        long pollTimeoutMillis,
        boolean running) {

    public KafkaConsumerStatus {
        topics = List.copyOf(topics);
    }
}
