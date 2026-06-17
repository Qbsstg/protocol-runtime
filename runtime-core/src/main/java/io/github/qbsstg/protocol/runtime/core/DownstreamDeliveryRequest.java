package io.github.qbsstg.protocol.runtime.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record DownstreamDeliveryRequest<T>(
        DownstreamRecordKind kind,
        ParsedRecord<T> record,
        ParseFailure failure,
        Instant requestedAt,
        Map<String, String> attributes) {

    public DownstreamDeliveryRequest {
        kind = Objects.requireNonNull(kind, "kind must not be null");
        if (kind == DownstreamRecordKind.RECORD && record == null) {
            throw new IllegalArgumentException("record must not be null for RECORD delivery");
        }
        if (kind == DownstreamRecordKind.PARSE_FAILURE && failure == null) {
            throw new IllegalArgumentException("failure must not be null for PARSE_FAILURE delivery");
        }
        if (kind == DownstreamRecordKind.RECORD && failure != null) {
            throw new IllegalArgumentException("failure must be null for RECORD delivery");
        }
        if (kind == DownstreamRecordKind.PARSE_FAILURE && record != null) {
            throw new IllegalArgumentException("record must be null for PARSE_FAILURE delivery");
        }
        requestedAt = requestedAt == null ? Instant.now() : requestedAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static <T> DownstreamDeliveryRequest<T> forRecord(ParsedRecord<T> record) {
        Objects.requireNonNull(record, "record must not be null");
        return new DownstreamDeliveryRequest<>(
                DownstreamRecordKind.RECORD,
                record,
                null,
                Instant.now(),
                record.attributes());
    }

    public static <T> DownstreamDeliveryRequest<T> forFailure(ParseFailure failure) {
        Objects.requireNonNull(failure, "failure must not be null");
        return new DownstreamDeliveryRequest<>(
                DownstreamRecordKind.PARSE_FAILURE,
                null,
                failure,
                Instant.now(),
                failure.attributes());
    }

    public SourceId sourceId() {
        return kind == DownstreamRecordKind.RECORD ? record.sourceId() : failure.sourceId();
    }

    public String protocol() {
        return kind == DownstreamRecordKind.RECORD ? record.protocol() : failure.protocol();
    }

    public Instant observedAt() {
        return kind == DownstreamRecordKind.RECORD ? record.observedAt() : failure.observedAt();
    }

    public String recordType() {
        return kind == DownstreamRecordKind.RECORD ? record.recordType() : null;
    }
}
