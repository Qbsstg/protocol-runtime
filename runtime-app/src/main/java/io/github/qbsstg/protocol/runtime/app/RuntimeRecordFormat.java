package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;

import java.time.Instant;
import java.util.Map;

final class RuntimeRecordFormat {

    static final String RECORD_SCHEMA_VERSION = "protocol-runtime.record.v1";
    static final String PARSE_FAILURE_SCHEMA_VERSION = "protocol-runtime.parse-failure.v1";
    static final String FAILED_RECORD_SCHEMA_VERSION = "protocol-runtime.failed-record.v1";

    private RuntimeRecordFormat() {
    }

    static String formatRecord(ParsedRecord<?> record) {
        String value = safeValue(record.value());
        StringBuilder line = new StringBuilder();
        line.append('{');
        appendField(line, "schemaVersion", RECORD_SCHEMA_VERSION);
        appendField(line, "kind", "record");
        appendField(line, "sourceId", record.sourceId().qualifiedValue());
        appendField(line, "protocol", record.protocol());
        appendNullableField(line, "receivedAt", receivedAt(record.attributes()));
        appendField(line, "parsedAt", record.observedAt().toString());
        appendField(line, "recordType", record.recordType());
        appendField(line, "observedAt", record.observedAt().toString());
        appendField(line, "rawPayloadHex", hex(record.rawPayload()));
        appendField(line, "value", value);
        appendQuality(line, "PARSED");
        appendPayload(line, value, record.rawPayload());
        appendRaw(line, record.rawPayload(), record.attributes());
        appendParser(line, null, null);
        appendSink(line, "PENDING", RECORD_SCHEMA_VERSION);
        appendAttributes(line, record.attributes());
        appendExtensions(line);
        line.append('}');
        return line.toString();
    }

    static String formatFailure(ParseFailure failure) {
        StringBuilder line = new StringBuilder();
        line.append('{');
        appendField(line, "schemaVersion", PARSE_FAILURE_SCHEMA_VERSION);
        appendField(line, "kind", "failure");
        appendField(line, "sourceId", failure.sourceId().qualifiedValue());
        appendField(line, "protocol", failure.protocol());
        appendNullableField(line, "receivedAt", receivedAt(failure.attributes()));
        appendField(line, "parsedAt", failure.observedAt().toString());
        appendNullableField(line, "recordType", null);
        appendField(line, "message", failure.message());
        appendField(line, "observedAt", failure.observedAt().toString());
        appendField(line, "rawPayloadHex", hex(failure.rawPayload()));
        if (failure.cause() != null) {
            appendField(line, "cause", failure.cause().getClass().getName());
        }
        appendQuality(line, "PARSE_FAILED");
        appendPayload(line, null, failure.rawPayload());
        appendRaw(line, failure.rawPayload(), failure.attributes());
        appendParser(line, failure.message(), failure.cause() == null ? null : failure.cause().getClass().getName());
        appendSink(line, "PENDING", PARSE_FAILURE_SCHEMA_VERSION);
        appendAttributes(line, failure.attributes());
        appendExtensions(line);
        line.append('}');
        return line.toString();
    }

    static String formatFailedRecord(ParsedRecord<?> record, SinkDeliveryFailure failure) {
        StringBuilder line = failedRecordPrefix(failure, record.sourceId().qualifiedValue(), record.protocol());
        appendField(line, "recordKind", "record");
        appendField(line, "recordType", record.recordType());
        appendField(line, "observedAt", record.observedAt().toString());
        appendNullableField(line, "receivedAt", receivedAt(record.attributes()));
        appendField(line, "rawPayloadHex", hex(record.rawPayload()));
        appendField(line, "value", safeValuePreview(record.value()));
        appendRaw(line, record.rawPayload(), record.attributes());
        appendSinkDeliveryFailure(line, failure);
        appendExtensions(line);
        line.append('}');
        return line.toString();
    }

    static String formatFailedParseFailure(ParseFailure parseFailure, SinkDeliveryFailure failure) {
        StringBuilder line = failedRecordPrefix(failure, parseFailure.sourceId().qualifiedValue(), parseFailure.protocol());
        appendField(line, "recordKind", "parseFailure");
        appendNullableField(line, "recordType", null);
        appendField(line, "observedAt", parseFailure.observedAt().toString());
        appendNullableField(line, "receivedAt", receivedAt(parseFailure.attributes()));
        appendField(line, "rawPayloadHex", hex(parseFailure.rawPayload()));
        appendField(line, "message", parseFailure.message());
        appendRaw(line, parseFailure.rawPayload(), parseFailure.attributes());
        appendParser(
                line,
                parseFailure.message(),
                parseFailure.cause() == null ? null : parseFailure.cause().getClass().getName());
        appendSinkDeliveryFailure(line, failure);
        appendExtensions(line);
        line.append('}');
        return line.toString();
    }

    private static StringBuilder failedRecordPrefix(
            SinkDeliveryFailure failure,
            String sourceId,
            String protocol) {
        StringBuilder line = new StringBuilder();
        line.append('{');
        appendField(line, "schemaVersion", FAILED_RECORD_SCHEMA_VERSION);
        appendField(line, "kind", "failedRecord");
        appendField(line, "failedAt", Instant.now().toString());
        appendField(line, "target", failure.target());
        appendField(line, "sourceId", sourceId);
        appendField(line, "protocol", protocol);
        return line;
    }

    private static void appendAttributes(StringBuilder line, Map<String, String> attributes) {
        line.append(",\"attributes\":{");
        boolean first = true;
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            if (!first) {
                line.append(',');
            }
            first = false;
            appendQuoted(line, attribute.getKey());
            line.append(':');
            appendQuoted(line, attribute.getValue());
        }
        line.append('}');
    }

    private static void appendQuality(StringBuilder line, String status) {
        line.append(",\"quality\":{");
        appendQuoted(line, "status");
        line.append(':');
        appendQuoted(line, status);
        line.append('}');
    }

    private static void appendPayload(StringBuilder line, String value, byte[] rawPayload) {
        line.append(",\"payload\":{");
        appendQuoted(line, "value");
        line.append(':');
        if (value == null) {
            line.append("null");
        } else {
            appendQuoted(line, value);
        }
        line.append(',');
        appendQuoted(line, "rawHex");
        line.append(':');
        appendQuoted(line, hex(rawPayload));
        line.append(',');
        appendQuoted(line, "rawSize");
        line.append(':');
        line.append(rawPayload.length);
        line.append('}');
    }

    private static void appendRaw(StringBuilder line, byte[] rawPayload, Map<String, String> attributes) {
        line.append(",\"raw\":{");
        appendQuoted(line, "payloadHex");
        line.append(':');
        appendQuoted(line, hex(rawPayload));
        line.append(',');
        appendQuoted(line, "payloadSize");
        line.append(':');
        line.append(rawPayload.length);
        line.append(',');
        appendQuoted(line, "metadata");
        line.append(':');
        appendStringMap(line, attributes);
        line.append('}');
    }

    private static void appendParser(StringBuilder line, String message, String causeType) {
        line.append(",\"parser\":{");
        appendQuoted(line, "diagnostics");
        line.append(":{");
        appendQuoted(line, "message");
        line.append(':');
        appendNullableQuoted(line, message);
        line.append(',');
        appendQuoted(line, "causeType");
        line.append(':');
        appendNullableQuoted(line, causeType);
        line.append("}}");
    }

    private static void appendSink(StringBuilder line, String delivery, String schemaVersion) {
        line.append(",\"sink\":{");
        appendQuoted(line, "schemaVersion");
        line.append(':');
        appendQuoted(line, schemaVersion);
        line.append(',');
        appendQuoted(line, "format");
        line.append(':');
        appendQuoted(line, "jsonl");
        line.append(',');
        appendQuoted(line, "delivery");
        line.append(':');
        appendQuoted(line, delivery);
        line.append(',');
        appendQuoted(line, "adapterContract");
        line.append(':');
        appendQuoted(line, "downstream-sink-spi.v1");
        line.append(',');
        appendQuoted(line, "metadata");
        line.append(":{");
        appendQuoted(line, "result");
        line.append(':');
        appendNullableQuoted(line, null);
        line.append('}');
        line.append('}');
    }

    private static void appendSinkDeliveryFailure(StringBuilder line, SinkDeliveryFailure failure) {
        line.append(",\"sink\":{");
        appendQuoted(line, "schemaVersion");
        line.append(':');
        appendQuoted(line, FAILED_RECORD_SCHEMA_VERSION);
        line.append(',');
        appendQuoted(line, "delivery");
        line.append(':');
        appendQuoted(line, "FAILED");
        line.append(',');
        appendQuoted(line, "failureType");
        line.append(':');
        appendQuoted(line, failure.failureType().name());
        line.append(',');
        appendQuoted(line, "exceptionType");
        line.append(':');
        appendQuoted(line, failure.exceptionType());
        line.append(',');
        appendQuoted(line, "message");
        line.append(':');
        appendQuoted(line, failure.message());
        line.append(',');
        appendQuoted(line, "retryable");
        line.append(':');
        line.append(failure.retryable());
        line.append(',');
        appendQuoted(line, "adapterContract");
        line.append(':');
        appendQuoted(line, "downstream-sink-spi.v1");
        line.append('}');
    }

    private static void appendExtensions(StringBuilder line) {
        line.append(",\"extensions\":{}");
    }

    private static void appendStringMap(StringBuilder line, Map<String, String> values) {
        line.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                line.append(',');
            }
            first = false;
            appendQuoted(line, entry.getKey());
            line.append(':');
            appendQuoted(line, entry.getValue());
        }
        line.append('}');
    }

    private static void appendField(StringBuilder line, String name, String value) {
        if (line.length() > 1) {
            line.append(',');
        }
        appendQuoted(line, name);
        line.append(':');
        appendQuoted(line, value);
    }

    private static void appendNullableField(StringBuilder line, String name, String value) {
        if (line.length() > 1) {
            line.append(',');
        }
        appendQuoted(line, name);
        line.append(':');
        appendNullableQuoted(line, value);
    }

    private static void appendNullableQuoted(StringBuilder line, String value) {
        if (value == null) {
            line.append("null");
        } else {
            appendQuoted(line, value);
        }
    }

    private static void appendQuoted(StringBuilder line, String value) {
        line.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') {
                line.append('\\').append(ch);
            } else if (ch == '\n') {
                line.append("\\n");
            } else if (ch == '\r') {
                line.append("\\r");
            } else if (ch == '\t') {
                line.append("\\t");
            } else {
                line.append(ch);
            }
        }
        line.append('"');
    }

    private static String safeValue(Object value) {
        try {
            return String.valueOf(value);
        } catch (RuntimeException ex) {
            throw new SinkDeliveryException(
                    SinkDeliveryFailureType.SERIALIZATION_ERROR,
                    "Unable to serialize parsed record value",
                    ex,
                    false);
        }
    }

    private static String safeValuePreview(Object value) {
        try {
            return String.valueOf(value);
        } catch (RuntimeException ex) {
            return "<unavailable:" + ex.getClass().getName() + ">";
        }
    }

    private static String receivedAt(Map<String, String> attributes) {
        String value = attributes.get("ingress.receivedAt");
        if (value != null) {
            return value;
        }
        value = attributes.get("receivedAt");
        if (value != null) {
            return value;
        }
        value = attributes.get("kafka.timestamp");
        if (value != null) {
            return value;
        }
        return attributes.get("tcp.connected.at");
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            if (unsigned < 0x10) {
                hex.append('0');
            }
            hex.append(Integer.toHexString(unsigned).toUpperCase());
        }
        return hex.toString();
    }
}
