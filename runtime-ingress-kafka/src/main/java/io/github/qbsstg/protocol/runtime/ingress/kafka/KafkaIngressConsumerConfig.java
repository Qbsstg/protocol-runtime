package io.github.qbsstg.protocol.runtime.ingress.kafka;

import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.util.List;

public record KafkaIngressConsumerConfig(
        String consumerName,
        String bootstrapServers,
        String groupId,
        List<String> topics,
        String topicPattern,
        KafkaSourceIdMode sourceIdMode,
        String sourceIdHeader,
        SourceId configuredSourceId,
        String protocol,
        KafkaCommitMode commitMode,
        String autoOffsetReset,
        int maxPollRecords,
        long pollTimeoutMillis) {

    public KafkaIngressConsumerConfig {
        consumerName = requireNonBlank(consumerName == null ? "kafka" : consumerName, "consumerName");
        bootstrapServers = requireNonBlank(bootstrapServers, "bootstrapServers");
        groupId = requireNonBlank(groupId, "groupId");
        topics = topics == null ? List.of() : List.copyOf(topics);
        topicPattern = blankToNull(topicPattern);
        sourceIdMode = sourceIdMode == null ? KafkaSourceIdMode.CONFIGURED : sourceIdMode;
        protocol = requireNonBlank(protocol, "protocol");
        commitMode = commitMode == null ? KafkaCommitMode.MANUAL : commitMode;
        autoOffsetReset = requireNonBlank(autoOffsetReset == null ? "latest" : autoOffsetReset, "autoOffsetReset");
        if (topics.stream().anyMatch(topic -> topic == null || topic.isBlank())) {
            throw new IllegalArgumentException("topics must not contain blank values");
        }
        if (topics.isEmpty() == (topicPattern == null)) {
            throw new IllegalArgumentException("exactly one of topics or topicPattern must be configured");
        }
        if (sourceIdMode == KafkaSourceIdMode.HEADER) {
            sourceIdHeader = requireNonBlank(sourceIdHeader, "sourceIdHeader");
        }
        if (sourceIdMode == KafkaSourceIdMode.CONFIGURED && configuredSourceId == null) {
            throw new IllegalArgumentException("configuredSourceId is required for CONFIGURED source id mode");
        }
        if (maxPollRecords < 1) {
            throw new IllegalArgumentException("maxPollRecords must be positive");
        }
        if (pollTimeoutMillis < 1) {
            throw new IllegalArgumentException("pollTimeoutMillis must be positive");
        }
    }

    public static KafkaIngressConsumerConfig configured(
            String bootstrapServers,
            String groupId,
            String topic,
            SourceId sourceId,
            String protocol) {
        return new KafkaIngressConsumerConfig(
                "kafka",
                bootstrapServers,
                groupId,
                List.of(topic),
                null,
                KafkaSourceIdMode.CONFIGURED,
                null,
                sourceId,
                protocol,
                KafkaCommitMode.MANUAL,
                "latest",
                100,
                1000);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
