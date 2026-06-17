package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.DownstreamSinkIdentity;

import java.util.Locale;

public record DownstreamSinkAdapterConfig(
        String type,
        String endpoint,
        String topic,
        String authenticationReference,
        long timeoutMillis,
        String batchingPosture,
        String retryPosture,
        String deadLetterOutput) {

    public static final String DEFAULT_TYPE = "app-local";
    public static final long DEFAULT_TIMEOUT_MILLIS = 5000L;
    public static final String DEFAULT_BATCHING_POSTURE = "none";
    public static final String DEFAULT_RETRY_POSTURE = "app-local";
    public static final String DEFAULT_DEAD_LETTER_OUTPUT = "failed-records";

    public DownstreamSinkAdapterConfig {
        type = normalizeType(type);
        endpoint = normalizeOptional(endpoint);
        topic = normalizeOptional(topic);
        authenticationReference = normalizeOptional(authenticationReference);
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("timeoutMillis must not be negative");
        }
        batchingPosture = normalizePosture(batchingPosture, "batchingPosture");
        retryPosture = normalizePosture(retryPosture, "retryPosture");
        deadLetterOutput = normalizePosture(deadLetterOutput, "deadLetterOutput");
    }

    public static DownstreamSinkAdapterConfig defaults() {
        return new DownstreamSinkAdapterConfig(
                DEFAULT_TYPE,
                null,
                null,
                null,
                DEFAULT_TIMEOUT_MILLIS,
                DEFAULT_BATCHING_POSTURE,
                DEFAULT_RETRY_POSTURE,
                DEFAULT_DEAD_LETTER_OUTPUT);
    }

    DownstreamSinkIdentity identity(SinkType sinkType) {
        String name = sinkType == null ? "unknown" : sinkType.configValue();
        return new DownstreamSinkIdentity(type, name);
    }

    boolean authenticationReferenceConfigured() {
        return authenticationReference != null;
    }

    private static String normalizeType(String value) {
        String normalized = normalizePosture(value, "type").toLowerCase(Locale.ROOT).replace('_', '-');
        if (!"app-local".equals(normalized) && !"fake-no-network".equals(normalized)) {
            throw new IllegalArgumentException("type must be app-local or fake-no-network in 0.18.0");
        }
        return normalized;
    }

    private static String normalizePosture(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
