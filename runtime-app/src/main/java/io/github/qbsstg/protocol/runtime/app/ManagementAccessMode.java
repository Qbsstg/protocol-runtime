package io.github.qbsstg.protocol.runtime.app;

import java.util.Locale;

public enum ManagementAccessMode {
    LOCAL("local"),
    OPEN("open"),
    TOKEN("token");

    private final String configValue;

    ManagementAccessMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    static ManagementAccessMode parse(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "local" -> LOCAL;
            case "open" -> OPEN;
            case "token" -> TOKEN;
            default -> throw new IllegalArgumentException("unknown management access mode: " + value);
        };
    }
}
