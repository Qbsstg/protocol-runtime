package io.github.qbsstg.protocol.runtime.ingress.kafka;

import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

public final class KafkaRecordEnvelopeMapper {

    private final KafkaIngressConsumerConfig config;
    private final Clock clock;

    public KafkaRecordEnvelopeMapper(KafkaIngressConsumerConfig config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public IngressEnvelope toEnvelope(ConsumerRecord<byte[], byte[]> record) {
        Objects.requireNonNull(record, "record must not be null");
        SourceId sourceId = resolveSourceId(record);
        return new IngressEnvelope(
                sourceId,
                KafkaIngressModule.TRANSPORT,
                record.value(),
                clock.instant(),
                attributes(record, sourceId));
    }

    private SourceId resolveSourceId(ConsumerRecord<byte[], byte[]> record) {
        return switch (config.sourceIdMode()) {
            case CONFIGURED -> config.configuredSourceId();
            case HEADER -> parseSourceId(headerValue(record, config.sourceIdHeader()));
            case TOPIC -> parseSourceId(record.topic());
            case KEY -> parseSourceId(decode(record.key()));
        };
    }

    private Map<String, String> attributes(ConsumerRecord<byte[], byte[]> record, SourceId sourceId) {
        Map<String, String> attributes = new LinkedHashMap<>();
        put(attributes, KafkaIngressAttributes.CONSUMER_NAME, config.consumerName());
        put(attributes, KafkaIngressAttributes.GROUP_ID, config.groupId());
        put(attributes, KafkaIngressAttributes.TOPIC, record.topic());
        put(attributes, KafkaIngressAttributes.PARTITION, String.valueOf(record.partition()));
        put(attributes, KafkaIngressAttributes.OFFSET, String.valueOf(record.offset()));
        if (record.timestamp() >= 0) {
            put(attributes, KafkaIngressAttributes.TIMESTAMP, String.valueOf(record.timestamp()));
        }
        if (record.timestampType() != null) {
            put(attributes, KafkaIngressAttributes.TIMESTAMP_TYPE, record.timestampType().name());
        }
        put(attributes, KafkaIngressAttributes.KEY, decode(record.key()));
        put(attributes, KafkaIngressAttributes.SOURCE_ID_MODE, config.sourceIdMode().name());
        put(attributes, KafkaIngressAttributes.SOURCE_NAMESPACE, sourceId.namespace());
        put(attributes, KafkaIngressAttributes.SOURCE_VALUE, sourceId.value());
        put(attributes, KafkaIngressAttributes.PROTOCOL, config.protocol());
        for (Header header : record.headers()) {
            put(attributes, KafkaIngressAttributes.HEADER_PREFIX + header.key(), decode(header.value()));
        }
        return attributes;
    }

    private String headerValue(ConsumerRecord<byte[], byte[]> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) {
            throw new IllegalArgumentException("source id header is missing: " + headerName);
        }
        return decode(header.value());
    }

    private SourceId parseSourceId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("source id must not be blank");
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("source id must use namespace:value");
        }
        return SourceId.of(value.substring(0, separator), value.substring(separator + 1));
    }

    private static String decode(byte[] value) {
        if (value == null || value.length == 0) {
            return null;
        }
        return new String(value, StandardCharsets.UTF_8);
    }

    private static void put(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}
