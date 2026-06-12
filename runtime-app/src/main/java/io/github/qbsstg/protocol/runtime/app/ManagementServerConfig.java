package io.github.qbsstg.protocol.runtime.app;

import java.util.Objects;

public record ManagementServerConfig(
        boolean enabled,
        String host,
        int port,
        String healthPath,
        String readinessPath,
        String statusPath,
        ManagementAccessMode accessMode,
        String token,
        boolean requestLoggingEnabled,
        int healthHistoryMaxEntries) {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8081;
    public static final String DEFAULT_HEALTH_PATH = "/health";
    public static final String DEFAULT_READINESS_PATH = "/readiness";
    public static final String DEFAULT_STATUS_PATH = "/status";
    public static final ManagementAccessMode DEFAULT_ACCESS_MODE = ManagementAccessMode.LOCAL;
    public static final boolean DEFAULT_REQUEST_LOGGING_ENABLED = true;
    public static final int DEFAULT_HEALTH_HISTORY_MAX_ENTRIES = 32;

    public ManagementServerConfig {
        host = normalizeHost(host);
        healthPath = normalizePath("healthPath", healthPath);
        readinessPath = normalizePath("readinessPath", readinessPath);
        statusPath = normalizePath("statusPath", statusPath);
        Objects.requireNonNull(accessMode, "accessMode must not be null");
        token = normalizeToken(token);
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (healthPath.equals(readinessPath)
                || healthPath.equals(statusPath)
                || readinessPath.equals(statusPath)) {
            throw new IllegalArgumentException("management paths must be distinct");
        }
        if (accessMode == ManagementAccessMode.TOKEN && token == null) {
            throw new IllegalArgumentException("token is required when accessMode is token");
        }
        if (healthHistoryMaxEntries < 0) {
            throw new IllegalArgumentException("healthHistoryMaxEntries must not be negative");
        }
    }

    public static ManagementServerConfig disabled() {
        return new ManagementServerConfig(
                false,
                DEFAULT_HOST,
                DEFAULT_PORT,
                DEFAULT_HEALTH_PATH,
                DEFAULT_READINESS_PATH,
                DEFAULT_STATUS_PATH,
                DEFAULT_ACCESS_MODE,
                null,
                DEFAULT_REQUEST_LOGGING_ENABLED,
                DEFAULT_HEALTH_HISTORY_MAX_ENTRIES);
    }

    public static ManagementServerConfig defaults() {
        return disabled();
    }

    private static String normalizeHost(String host) {
        Objects.requireNonNull(host, "host must not be null");
        String trimmed = host.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        return trimmed;
    }

    private static String normalizePath(String field, String path) {
        Objects.requireNonNull(path, field + " must not be null");
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (!trimmed.startsWith("/")) {
            throw new IllegalArgumentException(field + " must start with /");
        }
        return trimmed;
    }

    private static String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
