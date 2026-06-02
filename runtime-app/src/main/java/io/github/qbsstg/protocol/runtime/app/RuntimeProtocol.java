package io.github.qbsstg.protocol.runtime.app;

import java.util.Locale;
import java.util.Objects;

public enum RuntimeProtocol {
    IEC104("iec104"),
    IEC101("iec101"),
    IEC103("iec103"),
    MODBUS("modbus");

    private final String configValue;

    RuntimeProtocol(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static RuntimeProtocol parse(String value) {
        String normalized = Objects.requireNonNull(value, "protocol must not be null")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
        for (RuntimeProtocol protocol : values()) {
            if (protocol.configValue.equals(normalized)) {
                return protocol;
            }
        }
        throw new IllegalArgumentException("Unsupported runtime protocol: " + value);
    }
}
