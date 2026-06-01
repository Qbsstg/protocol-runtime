package io.github.qbsstg.protocol.runtime.app;

import java.util.Locale;

public enum SinkType {
    LOGGING("logging"),
    FILE("file"),
    IN_MEMORY("in-memory");

    private final String configValue;

    SinkType(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static SinkType parse(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (SinkType type : values()) {
            if (type.configValue.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("collector.sink.type must be logging, file, or in-memory");
    }
}
