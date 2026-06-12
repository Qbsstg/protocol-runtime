package io.github.qbsstg.protocol.runtime.app;

import java.util.List;

public record ManagementStatusSnapshot(
        boolean enabled,
        boolean running,
        String configuredHost,
        int configuredPort,
        String boundHost,
        Integer boundPort,
        String healthPath,
        String readinessPath,
        String statusPath,
        ManagementAccessMode accessMode,
        boolean requestLoggingEnabled,
        int healthHistoryMaxEntries,
        ManagementMetricsSnapshot metrics,
        List<HealthHistoryEntry> healthHistory) {

    public ManagementStatusSnapshot {
        if (configuredHost == null || configuredHost.isBlank()) {
            throw new IllegalArgumentException("configuredHost must not be blank");
        }
        if (configuredPort < 0 || configuredPort > 65535) {
            throw new IllegalArgumentException("configuredPort must be between 0 and 65535");
        }
        if (healthPath == null || healthPath.isBlank()) {
            throw new IllegalArgumentException("healthPath must not be blank");
        }
        if (readinessPath == null || readinessPath.isBlank()) {
            throw new IllegalArgumentException("readinessPath must not be blank");
        }
        if (statusPath == null || statusPath.isBlank()) {
            throw new IllegalArgumentException("statusPath must not be blank");
        }
        if (accessMode == null) {
            throw new IllegalArgumentException("accessMode must not be null");
        }
        if (healthHistoryMaxEntries < 0) {
            throw new IllegalArgumentException("healthHistoryMaxEntries must not be negative");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }
        healthHistory = healthHistory == null ? List.of() : List.copyOf(healthHistory);
    }

    ManagementStatusSnapshot withHealthHistory(List<HealthHistoryEntry> history) {
        return new ManagementStatusSnapshot(
                enabled,
                running,
                configuredHost,
                configuredPort,
                boundHost,
                boundPort,
                healthPath,
                readinessPath,
                statusPath,
                accessMode,
                requestLoggingEnabled,
                healthHistoryMaxEntries,
                metrics,
                history);
    }
}
