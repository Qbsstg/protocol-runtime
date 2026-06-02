package io.github.qbsstg.protocol.runtime.app;

final class CollectorStatusFormatter {

    private CollectorStatusFormatter() {
    }

    static String format(CollectorStatusSnapshot snapshot) {
        CollectorRuntimeMetrics metrics = snapshot.metrics();
        return "Protocol Runtime collector status"
                + " state=" + snapshot.state()
                + " sources=" + snapshot.sources().size()
                + " listeners=" + snapshot.tcpListeners().size()
                + " activeConnections=" + snapshot.activeConnectionCount()
                + " parsedRecords=" + metrics.parsedRecordCount()
                + " parseFailures=" + metrics.parseFailureCount()
                + " backpressureRetryLater=" + metrics.backpressureRetryLaterCount()
                + " backpressureDrop=" + metrics.backpressureDropCount()
                + " sink=" + snapshot.sinkType().configValue()
                + " backpressure=" + snapshot.backpressureDecision()
                + " maxPayloadBytes=" + snapshot.backpressureMaxPayloadBytes()
                + " oversizedPayloadDecision=" + snapshot.oversizedPayloadDecision()
                + " strictAsdu=" + snapshot.strictAsduParsing()
                + " tcpListeners=" + tcpListeners(snapshot);
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
                    .append(listener.activeConnectionCount());
        }
        value.append(']');
        return value.toString();
    }
}
