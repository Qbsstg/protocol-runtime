package io.github.qbsstg.protocol.runtime.ingress.kafka;

public enum KafkaCommitMode {
    MANUAL,
    AFTER_ACCEPT,
    AFTER_PARSE_SUCCESS,
    NEVER
}
