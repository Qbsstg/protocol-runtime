package io.github.qbsstg.protocol.runtime.app;

final class CollectorStatusFormatter {

    private CollectorStatusFormatter() {
    }

    static String format(CollectorStatusSnapshot snapshot) {
        CollectorRuntimeMetrics metrics = snapshot.metrics();
        CollectorHealthSnapshot health = snapshot.health();
        return "Protocol Runtime collector status"
                + " state=" + snapshot.state()
                + " health=" + health.health()
                + " readiness=" + health.readiness()
                + " healthReasons=" + healthReasons(health)
                + " sources=" + snapshot.sources().size()
                + " listeners=" + (snapshot.tcpListeners().size()
                        + snapshot.httpListeners().size()
                        + snapshot.kafkaConsumers().size()
                        + snapshot.mqttClients().size())
                + " activeConnections=" + snapshot.activeConnectionCount()
                + " parsedRecords=" + metrics.parsedRecordCount()
                + " parseFailures=" + metrics.parseFailureCount()
                + " backpressureRetryLater=" + metrics.backpressureRetryLaterCount()
                + " backpressureDrop=" + metrics.backpressureDropCount()
                + " sinkFailures=" + metrics.sinkFailureCount()
                + " lastSinkFailure=" + lastSinkFailure(metrics)
                + " sink=" + snapshot.sinkType().configValue()
                + " fileSink=" + fileSink(snapshot.fileSinkStatus())
                + " failedRecords=" + failedRecords(snapshot.failedRecordIsolationStatus())
                + " backpressure=" + snapshot.backpressureDecision()
                + " maxPayloadBytes=" + snapshot.backpressureMaxPayloadBytes()
                + " oversizedPayloadDecision=" + snapshot.oversizedPayloadDecision()
                + " sinkFailureThreshold=" + snapshot.sinkFailureBackpressureThreshold()
                + " sinkFailureDecision=" + snapshot.sinkFailureBackpressureDecision()
                + " strictAsdu=" + snapshot.strictAsduParsing()
                + " management=" + management(snapshot.management())
                + " tcpListeners=" + tcpListeners(snapshot)
                + " httpListeners=" + httpListeners(snapshot)
                + " kafkaConsumers=" + kafkaConsumers(snapshot)
                + " mqttClients=" + mqttClients(snapshot);
    }

    private static String healthReasons(CollectorHealthSnapshot health) {
        if (health.reasons().isEmpty()) {
            return "[]";
        }
        return "[" + String.join(",", health.reasons()) + "]";
    }

    private static String tcpListeners(CollectorStatusSnapshot snapshot) {
        StringBuilder value = new StringBuilder("[");
        boolean first = true;
        for (TcpListenerStatus listener : snapshot.tcpListeners()) {
            if (!first) {
                value.append(',');
            }
            first = false;
            value.append(listener.name())
                    .append('@')
                    .append(listener.configuredHost())
                    .append(':')
                    .append(listener.configuredPort());
            if (listener.boundPort() != null) {
                value.append("->")
                        .append(listener.boundHost())
                        .append(':')
                        .append(listener.boundPort());
            }
            value.append("/running=")
                    .append(listener.running())
                    .append("/active=")
                    .append(listener.activeConnectionCount())
                    .append("/protocol=")
                    .append(listener.protocol());
        }
        value.append(']');
        return value.toString();
    }

    private static String lastSinkFailure(CollectorRuntimeMetrics metrics) {
        if (metrics.lastSinkFailureTarget() == null) {
            return "none";
        }
        return metrics.lastSinkFailureTarget()
                + '@'
                + metrics.lastSinkFailureSourceId()
                + '/'
                + metrics.lastSinkDeliveryFailureType()
                + '/'
                + metrics.lastSinkFailureType()
                + ':'
                + metrics.lastSinkFailureMessage();
    }

    private static String fileSink(FileSinkStatus status) {
        if (status == null) {
            return "none";
        }
        return status.output()
                + "/open=" + status.open()
                + "/activeBytes=" + status.activeBytes()
                + "/history=" + status.retainedHistoryCount()
                + "/rotations=" + status.rotationCount()
                + "/maxBytes=" + status.rotation().maxBytes()
                + "/maxHistory=" + status.rotation().maxHistory();
    }

    private static String failedRecords(FailedRecordIsolationStatus status) {
        return "enabled=" + status.enabled()
                + "/directory=" + status.directory()
                + "/samples=" + status.sampleCount()
                + "/retained=" + status.retainedSampleCount()
                + "/isolationFailures=" + status.isolationFailureCount()
                + "/lastSample=" + (status.lastSampleFile() == null ? "none" : status.lastSampleFile());
    }

    private static String management(ManagementStatusSnapshot management) {
        return "enabled=" + management.enabled()
                + "/running=" + management.running()
                + "/access=" + management.accessMode().configValue()
                + "/requests=" + management.metrics().requestCount()
                + "/rejected=" + management.metrics().rejectedRequestCount()
                + "/errors=" + management.metrics().errorResponseCount()
                + "/history=" + management.healthHistory().size();
    }

    private static String httpListeners(CollectorStatusSnapshot snapshot) {
        StringBuilder value = new StringBuilder("[");
        boolean first = true;
        for (HttpListenerStatus listener : snapshot.httpListeners()) {
            if (!first) {
                value.append(',');
            }
            first = false;
            value.append(listener.name())
                    .append('@')
                    .append(listener.configuredHost())
                    .append(':')
                    .append(listener.configuredPort())
                    .append(listener.path());
            if (listener.boundPort() != null) {
                value.append("->")
                        .append(listener.boundHost())
                        .append(':')
                        .append(listener.boundPort());
            }
            value.append("/running=")
                    .append(listener.running())
                    .append("/sourceIdMode=")
                    .append(listener.sourceIdMode())
                    .append("/protocol=")
                    .append(listener.protocol());
        }
        value.append(']');
        return value.toString();
    }

    private static String kafkaConsumers(CollectorStatusSnapshot snapshot) {
        StringBuilder value = new StringBuilder("[");
        boolean first = true;
        for (KafkaConsumerStatus consumer : snapshot.kafkaConsumers()) {
            if (!first) {
                value.append(',');
            }
            first = false;
            value.append(consumer.name())
                    .append('@')
                    .append(consumer.bootstrapServers())
                    .append("/group=")
                    .append(consumer.groupId())
                    .append("/running=")
                    .append(consumer.running())
                    .append("/sourceIdMode=")
                    .append(consumer.sourceIdMode())
                    .append("/protocol=")
                    .append(consumer.protocol());
        }
        value.append(']');
        return value.toString();
    }

    private static String mqttClients(CollectorStatusSnapshot snapshot) {
        StringBuilder value = new StringBuilder("[");
        boolean first = true;
        for (MqttClientStatus client : snapshot.mqttClients()) {
            if (!first) {
                value.append(',');
            }
            first = false;
            value.append(client.name())
                    .append('@')
                    .append(client.brokerUri())
                    .append("/clientId=")
                    .append(client.clientId())
                    .append("/running=")
                    .append(client.running())
                    .append("/sourceIdMode=")
                    .append(client.sourceIdMode())
                    .append("/protocol=")
                    .append(client.protocol());
        }
        value.append(']');
        return value.toString();
    }
}
