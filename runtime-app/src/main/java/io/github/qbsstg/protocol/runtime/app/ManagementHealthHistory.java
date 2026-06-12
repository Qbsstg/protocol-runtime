package io.github.qbsstg.protocol.runtime.app;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class ManagementHealthHistory {

    private final int maxEntries;
    private final Deque<HealthHistoryEntry> entries = new ArrayDeque<>();
    private HealthKey lastKey;

    ManagementHealthHistory(int maxEntries) {
        if (maxEntries < 0) {
            throw new IllegalArgumentException("maxEntries must not be negative");
        }
        this.maxEntries = maxEntries;
    }

    synchronized List<HealthHistoryEntry> recordAndSnapshot(Instant observedAt, CollectorStatusSnapshot snapshot) {
        if (maxEntries == 0) {
            return List.of();
        }
        CollectorHealthSnapshot health = snapshot.health();
        HealthKey current = new HealthKey(
                snapshot.state(),
                health.health(),
                health.readiness(),
                health.reasons());
        if (!current.equals(lastKey)) {
            entries.addLast(new HealthHistoryEntry(
                    observedAt,
                    current.lifecycle(),
                    current.health(),
                    current.readiness(),
                    transition(lastKey, current),
                    current.reasons()));
            lastKey = current;
            while (entries.size() > maxEntries) {
                entries.removeFirst();
            }
        }
        return List.copyOf(entries);
    }

    synchronized List<HealthHistoryEntry> snapshot() {
        return List.copyOf(entries);
    }

    private static String transition(HealthKey previous, HealthKey current) {
        if (previous == null) {
            return "initial";
        }
        if (previous.lifecycle() != current.lifecycle()) {
            return "lifecycle";
        }
        if (previous.health() != CollectorHealthState.FAILED && current.health() == CollectorHealthState.FAILED) {
            return "failed";
        }
        if (previous.health() == CollectorHealthState.DEGRADED && current.health() == CollectorHealthState.HEALTHY) {
            return "recovered";
        }
        if (previous.health() != CollectorHealthState.DEGRADED && current.health() == CollectorHealthState.DEGRADED) {
            return "degraded";
        }
        if (previous.readiness() != current.readiness()) {
            return "readiness";
        }
        return "updated";
    }

    private record HealthKey(
            CollectorLifecycleState lifecycle,
            CollectorHealthState health,
            CollectorReadinessState readiness,
            List<String> reasons) {

        private HealthKey {
            reasons = List.copyOf(new ArrayList<>(reasons));
        }
    }
}
