package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;

import java.util.Map;

final class RuntimeRecordFormat {

    private RuntimeRecordFormat() {
    }

    static String formatRecord(ParsedRecord<?> record) {
        StringBuilder line = new StringBuilder();
        line.append('{');
        appendField(line, "kind", "record");
        appendField(line, "sourceId", record.sourceId().qualifiedValue());
        appendField(line, "protocol", record.protocol());
        appendField(line, "recordType", record.recordType());
        appendField(line, "observedAt", record.observedAt().toString());
        appendField(line, "rawPayloadHex", hex(record.rawPayload()));
        appendField(line, "value", String.valueOf(record.value()));
        appendAttributes(line, record.attributes());
        line.append('}');
        return line.toString();
    }

    static String formatFailure(ParseFailure failure) {
        StringBuilder line = new StringBuilder();
        line.append('{');
        appendField(line, "kind", "failure");
        appendField(line, "sourceId", failure.sourceId().qualifiedValue());
        appendField(line, "protocol", failure.protocol());
        appendField(line, "message", failure.message());
        appendField(line, "observedAt", failure.observedAt().toString());
        appendField(line, "rawPayloadHex", hex(failure.rawPayload()));
        if (failure.cause() != null) {
            appendField(line, "cause", failure.cause().getClass().getName());
        }
        appendAttributes(line, failure.attributes());
        line.append('}');
        return line.toString();
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

    private static void appendField(StringBuilder line, String name, String value) {
        if (line.length() > 1) {
            line.append(',');
        }
        appendQuoted(line, name);
        line.append(':');
        appendQuoted(line, value);
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
