package io.github.qbsstg.protocol.runtime.app;

import java.util.ArrayList;
import java.util.List;

public record CollectorHealthSnapshot(
        CollectorHealthState health,
        CollectorReadinessState readiness,
        List<String> reasons) {

    public CollectorHealthSnapshot {
        if (health == null) {
            throw new IllegalArgumentException("health must not be null");
        }
        if (readiness == null) {
            throw new IllegalArgumentException("readiness must not be null");
        }
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static CollectorHealthSnapshot from(CollectorStatusSnapshot snapshot) {
        List<String> reasons = new ArrayList<>();
        return switch (snapshot.state()) {
            case CONFIGURED -> lifecycle(CollectorHealthState.CONFIGURED, "lifecycle=CONFIGURED");
            case STARTING -> lifecycle(CollectorHealthState.STARTING, "lifecycle=STARTING");
            case STOPPING -> lifecycle(CollectorHealthState.STOPPING, "lifecycle=STOPPING");
            case STOPPED -> lifecycle(CollectorHealthState.STOPPED, "lifecycle=STOPPED");
            case FAILED -> failed(snapshot);
            case RUNNING -> running(snapshot, reasons);
        };
    }

    private static CollectorHealthSnapshot lifecycle(CollectorHealthState health, String reason) {
        return new CollectorHealthSnapshot(health, CollectorReadinessState.NOT_READY, List.of(reason));
    }

    private static CollectorHealthSnapshot failed(CollectorStatusSnapshot snapshot) {
        List<String> reasons = new ArrayList<>();
        if (snapshot.startupFailureReason() != null) {
            reasons.add("startupFailure=" + snapshot.startupFailureReason());
        }
        if (snapshot.lastExceptionType() != null) {
            reasons.add("lastException=" + snapshot.lastExceptionType() + ":" + snapshot.lastExceptionMessage());
        }
        if (reasons.isEmpty()) {
            reasons.add("lifecycle=FAILED");
        }
        return new CollectorHealthSnapshot(CollectorHealthState.FAILED, CollectorReadinessState.NOT_READY, reasons);
    }

    private static CollectorHealthSnapshot running(
            CollectorStatusSnapshot snapshot,
            List<String> reasons) {
        boolean ready = addBlockingReasons(snapshot, reasons);
        addDegradedReasons(snapshot, reasons);
        CollectorHealthState health = reasons.isEmpty() ? CollectorHealthState.HEALTHY : CollectorHealthState.DEGRADED;
        CollectorReadinessState readiness =
                ready ? CollectorReadinessState.READY : CollectorReadinessState.NOT_READY;
        return new CollectorHealthSnapshot(health, readiness, reasons);
    }

    private static boolean addBlockingReasons(CollectorStatusSnapshot snapshot, List<String> reasons) {
        int listenerCount = snapshot.tcpListeners().size()
                + snapshot.httpListeners().size()
                + snapshot.kafkaConsumers().size()
                + snapshot.mqttClients().size();
        boolean ready = true;
        if (listenerCount == 0) {
            reasons.add("listeners=0");
            ready = false;
        }
        for (TcpListenerStatus listener : snapshot.tcpListeners()) {
            if (!listener.running()) {
                reasons.add("listenerNotRunning=tcp:" + listener.name());
                ready = false;
            }
        }
        for (HttpListenerStatus listener : snapshot.httpListeners()) {
            if (!listener.running()) {
                reasons.add("listenerNotRunning=http:" + listener.name());
                ready = false;
            }
        }
        for (KafkaConsumerStatus consumer : snapshot.kafkaConsumers()) {
            if (!consumer.running()) {
                reasons.add("listenerNotRunning=kafka:" + consumer.name());
                ready = false;
            }
        }
        for (MqttClientStatus client : snapshot.mqttClients()) {
            if (!client.running()) {
                reasons.add("listenerNotRunning=mqtt:" + client.name());
                ready = false;
            }
        }
        if (snapshot.sinkType() == SinkType.FILE) {
            FileSinkStatus fileSink = snapshot.fileSinkStatus();
            if (fileSink == null) {
                reasons.add("fileSink=missing");
                ready = false;
            } else if (!fileSink.open()) {
                reasons.add("fileSinkNotOpen=" + fileSink.output());
                ready = false;
            }
        }
        return ready;
    }

    private static void addDegradedReasons(CollectorStatusSnapshot snapshot, List<String> reasons) {
        CollectorRuntimeMetrics metrics = snapshot.metrics();
        if (metrics.parseFailureCount() > 0) {
            reasons.add("parseFailures=" + metrics.parseFailureCount());
        }
        if (metrics.sinkFailureCount() > 0) {
            reasons.add("sinkFailures=" + metrics.sinkFailureCount());
        }
        if (metrics.backpressureRetryLaterCount() > 0) {
            reasons.add("backpressureRetryLater=" + metrics.backpressureRetryLaterCount());
        }
        if (metrics.backpressureDropCount() > 0) {
            reasons.add("backpressureDrop=" + metrics.backpressureDropCount());
        }
    }
}
