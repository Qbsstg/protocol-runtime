package io.github.qbsstg.protocol.runtime.ingress.kafka;

public final class KafkaIngressAttributes {

    public static final String CONSUMER_NAME = "kafka.consumer";
    public static final String GROUP_ID = "kafka.group";
    public static final String TOPIC = "kafka.topic";
    public static final String PARTITION = "kafka.partition";
    public static final String OFFSET = "kafka.offset";
    public static final String TIMESTAMP = "kafka.timestamp";
    public static final String TIMESTAMP_TYPE = "kafka.timestampType";
    public static final String KEY = "kafka.key";
    public static final String HEADER_PREFIX = "kafka.header.";
    public static final String SOURCE_ID_MODE = "kafka.sourceIdMode";
    public static final String SOURCE_NAMESPACE = "kafka.source.namespace";
    public static final String SOURCE_VALUE = "kafka.source.value";
    public static final String PROTOCOL = "runtime.protocol";

    private KafkaIngressAttributes() {
    }
}
