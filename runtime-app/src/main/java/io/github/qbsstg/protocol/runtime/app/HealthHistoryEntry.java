package io.github.qbsstg.protocol.runtime.app;

import java.time.Instant;
import java.util.List;

public record HealthHistoryEntry(
        Instant observedAt,
        CollectorLifecycleState lifecycle,
        CollectorHealthState health,
        CollectorReadinessState readiness,
        String transition,
        List<String> reasons) {

    public HealthHistoryEntry {
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt must not be null");
        }
        if (lifecycle == null) {
            throw new IllegalArgumentException("lifecycle must not be null");
        }
        if (health == null) {
            throw new IllegalArgumentException("health must not be null");
        }
        if (readiness == null) {
            throw new IllegalArgumentException("readiness must not be null");
        }
        if (transition == null || transition.isBlank()) {
            throw new IllegalArgumentException("transition must not be blank");
        }
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
