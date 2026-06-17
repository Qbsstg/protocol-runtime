package io.github.qbsstg.protocol.runtime.app;

import java.time.Instant;
import java.util.List;
import java.util.Map;

final class CollectorStatusJson {

    private CollectorStatusJson() {
    }

    static String health(CollectorStatusSnapshot snapshot) {
        CollectorHealthSnapshot health = snapshot.health();
        JsonWriter json = new JsonWriter();
        json.beginObject();
        json.name("health").value(health.health().name());
        json.name("readiness").value(health.readiness().name());
        json.name("state").value(snapshot.state().name());
        json.name("reasons").strings(health.reasons());
        json.endObject();
        return json.toString();
    }

    static String readiness(CollectorStatusSnapshot snapshot) {
        CollectorHealthSnapshot health = snapshot.health();
        JsonWriter json = new JsonWriter();
        json.beginObject();
        json.name("readiness").value(health.readiness().name());
        json.name("health").value(health.health().name());
        json.name("state").value(snapshot.state().name());
        json.name("reasons").strings(health.reasons());
        json.endObject();
        return json.toString();
    }

    static String status(CollectorStatusSnapshot snapshot) {
        CollectorHealthSnapshot health = snapshot.health();
        CollectorRuntimeMetrics metrics = snapshot.metrics();
        JsonWriter json = new JsonWriter();
        json.beginObject();
        json.name("lifecycle").value(snapshot.state().name());
        json.name("health").value(health.health().name());
        json.name("readiness").value(health.readiness().name());
        json.name("healthReasons").strings(health.reasons());
        json.name("startedAt").value(snapshot.startedAt());
        json.name("stoppedAt").value(snapshot.stoppedAt());
        json.name("startupFailureReason").value(snapshot.startupFailureReason());
        json.name("lastExceptionType").value(snapshot.lastExceptionType());
        json.name("lastExceptionMessage").value(snapshot.lastExceptionMessage());
        sources(json, snapshot.sources());
        listeners(json, snapshot);
        sink(json, snapshot);
        backpressure(json, snapshot);
        metrics(json, metrics);
        management(json, snapshot.management());
        json.name("strictAsduParsing").value(snapshot.strictAsduParsing());
        json.endObject();
        return json.toString();
    }

    static String error(int status, String code, String message, String path) {
        JsonWriter json = new JsonWriter();
        json.beginObject();
        json.name("error").beginObject();
        json.name("code").value(code);
        json.name("message").value(message);
        json.name("status").value(status);
        json.name("path").value(path);
        json.endObject();
        json.endObject();
        return json.toString();
    }

    private static void sources(JsonWriter json, List<CollectorSourceStatus> sources) {
        json.name("sources").beginArray();
        for (CollectorSourceStatus source : sources) {
            json.beginObject();
            json.name("name").value(source.name());
            json.name("sourceId").value(source.sourceId());
            json.name("protocol").value(source.protocol());
            json.endObject();
        }
        json.endArray();
    }

    private static void listeners(JsonWriter json, CollectorStatusSnapshot snapshot) {
        json.name("listeners").beginObject();
        json.name("activeConnectionCount").value(snapshot.activeConnectionCount());
        json.name("tcp").beginArray();
        for (TcpListenerStatus listener : snapshot.tcpListeners()) {
            json.beginObject();
            commonListener(json, listener.name(), listener.sourceName(), listener.sourceId(), listener.protocol());
            json.name("configuredHost").value(listener.configuredHost());
            json.name("configuredPort").value(listener.configuredPort());
            json.name("boundHost").value(listener.boundHost());
            json.name("boundPort").value(listener.boundPort());
            json.name("running").value(listener.running());
            json.name("activeConnectionCount").value(listener.activeConnectionCount());
            json.endObject();
        }
        json.endArray();
        json.name("http").beginArray();
        for (HttpListenerStatus listener : snapshot.httpListeners()) {
            json.beginObject();
            commonListener(json, listener.name(), listener.sourceName(), listener.sourceId(), listener.protocol());
            json.name("configuredHost").value(listener.configuredHost());
            json.name("configuredPort").value(listener.configuredPort());
            json.name("path").value(listener.path());
            json.name("sourceIdMode").value(listener.sourceIdMode().name());
            json.name("sourceIdHeader").value(listener.sourceIdHeader());
            json.name("maxPayloadBytes").value(listener.maxPayloadBytes());
            json.name("responseMode").value(listener.responseMode().name());
            json.name("backlog").value(listener.backlog());
            json.name("workerThreads").value(listener.workerThreads());
            json.name("boundHost").value(listener.boundHost());
            json.name("boundPort").value(listener.boundPort());
            json.name("running").value(listener.running());
            json.endObject();
        }
        json.endArray();
        json.name("kafka").beginArray();
        for (KafkaConsumerStatus consumer : snapshot.kafkaConsumers()) {
            json.beginObject();
            commonListener(json, consumer.name(), consumer.sourceName(), consumer.sourceId(), consumer.protocol());
            json.name("bootstrapServers").value(consumer.bootstrapServers());
            json.name("groupId").value(consumer.groupId());
            json.name("topics").strings(consumer.topics());
            json.name("topicPattern").value(consumer.topicPattern());
            json.name("sourceIdMode").value(consumer.sourceIdMode().name());
            json.name("sourceIdHeader").value(consumer.sourceIdHeader());
            json.name("commitMode").value(consumer.commitMode().name());
            json.name("autoOffsetReset").value(consumer.autoOffsetReset());
            json.name("maxPollRecords").value(consumer.maxPollRecords());
            json.name("pollTimeoutMillis").value(consumer.pollTimeoutMillis());
            json.name("running").value(consumer.running());
            json.endObject();
        }
        json.endArray();
        json.name("mqtt").beginArray();
        for (MqttClientStatus client : snapshot.mqttClients()) {
            json.beginObject();
            commonListener(json, client.name(), client.sourceName(), client.sourceId(), client.protocol());
            json.name("brokerUri").value(client.brokerUri());
            json.name("clientId").value(client.clientId());
            json.name("topicFilters").strings(client.topicFilters());
            json.name("qos").value(client.qos());
            json.name("sourceIdMode").value(client.sourceIdMode().name());
            json.name("cleanSession").value(client.cleanSession());
            json.name("automaticReconnect").value(client.automaticReconnect());
            json.name("connectionTimeoutSeconds").value(client.connectionTimeoutSeconds());
            json.name("keepAliveSeconds").value(client.keepAliveSeconds());
            json.name("running").value(client.running());
            json.endObject();
        }
        json.endArray();
        json.endObject();
    }

    private static void commonListener(
            JsonWriter json,
            String name,
            String sourceName,
            String sourceId,
            String protocol) {
        json.name("name").value(name);
        json.name("sourceName").value(sourceName);
        json.name("sourceId").value(sourceId);
        json.name("protocol").value(protocol);
    }

    private static void sink(JsonWriter json, CollectorStatusSnapshot snapshot) {
        json.name("sink").beginObject();
        json.name("type").value(snapshot.sinkType().configValue());
        json.name("schemaVersion").value(RuntimeRecordFormat.RECORD_SCHEMA_VERSION);
        json.name("format").value("jsonl");
        sinkAdapter(json, snapshot);
        FileSinkStatus fileSink = snapshot.fileSinkStatus();
        if (fileSink == null) {
            json.name("file").nullValue();
        } else {
            json.name("file").beginObject();
            json.name("output").value(fileSink.output().toString());
            json.name("open").value(fileSink.open());
            json.name("activeBytes").value(fileSink.activeBytes());
            json.name("retainedHistoryCount").value(fileSink.retainedHistoryCount());
            json.name("rotationCount").value(fileSink.rotationCount());
            json.name("maxBytes").value(fileSink.rotation().maxBytes());
            json.name("maxHistory").value(fileSink.rotation().maxHistory());
            json.endObject();
        }
        failedRecords(json, snapshot.failedRecordIsolationStatus());
        json.endObject();
    }

    private static void sinkAdapter(JsonWriter json, CollectorStatusSnapshot snapshot) {
        io.github.qbsstg.protocol.runtime.core.DownstreamSinkStatus status = snapshot.downstreamSinkStatus();
        DownstreamSinkAdapterConfig config = snapshot.sinkAdapter();
        json.name("adapter").beginObject();
        json.name("type").value(config.type());
        json.name("endpointConfigured").value(config.endpoint() != null);
        json.name("topicConfigured").value(config.topic() != null);
        json.name("authRefConfigured").value(config.authenticationReferenceConfigured());
        json.name("timeoutMillis").value(config.timeoutMillis());
        json.name("batchingPosture").value(config.batchingPosture());
        json.name("retryPosture").value(config.retryPosture());
        json.name("deadLetterOutput").value(config.deadLetterOutput());
        json.name("identity").beginObject();
        json.name("type").value(status.identity().type());
        json.name("name").value(status.identity().name());
        json.name("qualifiedName").value(status.identity().qualifiedName());
        json.endObject();
        json.name("running").value(status.running());
        json.name("healthy").value(status.healthy());
        json.name("ready").value(status.ready());
        json.name("backpressureDecision").value(status.backpressureDecision().name());
        json.name("deliveredCount").value(status.deliveredCount());
        json.name("failureCount").value(status.failureCount());
        json.name("lastResult").beginObject();
        if (status.lastResult() == null) {
            json.name("outcome").nullValue();
            json.name("message").nullValue();
            json.name("exceptionType").nullValue();
            json.name("retryable").value(false);
            json.name("backpressureDecision").nullValue();
            json.name("diagnostics").beginObject().endObject();
        } else {
            json.name("outcome").value(status.lastResult().outcome().name());
            json.name("message").value(status.lastResult().message());
            json.name("exceptionType").value(status.lastResult().exceptionType());
            json.name("retryable").value(status.lastResult().retryable());
            json.name("backpressureDecision").value(status.lastResult().backpressureDecision().name());
            json.name("diagnostics").stringMap(status.lastResult().diagnostics());
        }
        json.endObject();
        json.name("diagnostics").stringMap(status.diagnostics());
        json.endObject();
    }

    private static void failedRecords(JsonWriter json, FailedRecordIsolationStatus status) {
        json.name("failedRecords").beginObject();
        json.name("enabled").value(status.enabled());
        json.name("directory").value(status.directory().toString());
        json.name("maxSamples").value(status.maxSamples());
        json.name("sampleCount").value(status.sampleCount());
        json.name("retainedSampleCount").value(status.retainedSampleCount());
        json.name("lastSampleFile").value(status.lastSampleFile() == null ? null : status.lastSampleFile().toString());
        json.name("lastSampleAt").value(status.lastSampleAt());
        json.name("lastFailureTarget").value(status.lastFailureTarget());
        json.name("lastFailureSourceId").value(status.lastFailureSourceId());
        json.name("lastFailureType").value(status.lastFailureType());
        json.name("lastFailureMessage").value(status.lastFailureMessage());
        json.name("isolationFailureCount").value(status.isolationFailureCount());
        json.name("lastIsolationFailureMessage").value(status.lastIsolationFailureMessage());
        json.endObject();
    }

    private static void backpressure(JsonWriter json, CollectorStatusSnapshot snapshot) {
        json.name("backpressure").beginObject();
        json.name("decision").value(snapshot.backpressureDecision().name());
        json.name("maxPayloadBytes").value(snapshot.backpressureMaxPayloadBytes());
        json.name("oversizedPayloadDecision").value(snapshot.oversizedPayloadDecision().name());
        json.name("sinkFailureThreshold").value(snapshot.sinkFailureBackpressureThreshold());
        json.name("sinkFailureDecision").value(snapshot.sinkFailureBackpressureDecision().name());
        json.endObject();
    }

    private static void metrics(JsonWriter json, CollectorRuntimeMetrics metrics) {
        json.name("metrics").beginObject();
        json.name("parsedRecordCount").value(metrics.parsedRecordCount());
        json.name("parseFailureCount").value(metrics.parseFailureCount());
        json.name("backpressureRetryLaterCount").value(metrics.backpressureRetryLaterCount());
        json.name("backpressureDropCount").value(metrics.backpressureDropCount());
        json.name("sinkFailureCount").value(metrics.sinkFailureCount());
        json.name("delivery").beginObject();
        json.name("schemaVersion").value(RuntimeRecordFormat.RECORD_SCHEMA_VERSION);
        json.name("deliveredCount").value(metrics.sinkDeliveredCount());
        json.name("lastOutcome").value(metrics.lastSinkDeliveryOutcome());
        json.name("outcomeCounts").stringLongMap(metrics.sinkDeliveryOutcomeCounts());
        json.name("sinkFailureTypeCounts").stringLongMap(metrics.sinkFailureTypeCounts());
        json.name("lastSinkDeliveryFailureType").value(metrics.lastSinkDeliveryFailureType());
        json.name("lastSinkFailureRetryable").value(metrics.lastSinkFailureRetryable());
        json.endObject();
        json.name("failureCounters").beginObject();
        json.name("parse").value(metrics.parseFailureCount());
        json.name("sink").value(metrics.sinkFailureCount());
        json.name("backpressureRetryLater").value(metrics.backpressureRetryLaterCount());
        json.name("backpressureDrop").value(metrics.backpressureDropCount());
        json.endObject();
        json.name("lastParseFailure").beginObject();
        json.name("sourceId").value(metrics.lastParseFailureSourceId());
        json.name("message").value(metrics.lastParseFailureMessage());
        json.name("at").value(metrics.lastParseFailureAt());
        json.name("causeType").value(metrics.lastParseFailureCauseType());
        json.name("payloadSize").value(metrics.lastParseFailurePayloadSize());
        json.name("payloadPreviewHex").value(metrics.lastParseFailurePayloadPreviewHex());
        json.name("attributes").stringMap(metrics.lastParseFailureAttributes());
        json.endObject();
        json.name("lastBackpressure").beginObject();
        json.name("sourceId").value(metrics.lastBackpressureSourceId());
        json.name("decision").value(metrics.lastBackpressureDecision() == null
                ? null
                : metrics.lastBackpressureDecision().name());
        json.name("at").value(metrics.lastBackpressureAt());
        json.name("payloadSize").value(metrics.lastBackpressurePayloadSize());
        json.endObject();
        json.name("lastSinkFailure").beginObject();
        json.name("target").value(metrics.lastSinkFailureTarget());
        json.name("sourceId").value(metrics.lastSinkFailureSourceId());
        json.name("at").value(metrics.lastSinkFailureAt());
        json.name("type").value(metrics.lastSinkFailureType());
        json.name("message").value(metrics.lastSinkFailureMessage());
        json.name("deliveryFailureType").value(metrics.lastSinkDeliveryFailureType());
        json.name("retryable").value(metrics.lastSinkFailureRetryable());
        json.endObject();
        json.endObject();
    }

    private static void management(JsonWriter json, ManagementStatusSnapshot management) {
        json.name("management").beginObject();
        json.name("enabled").value(management.enabled());
        json.name("running").value(management.running());
        json.name("configuredHost").value(management.configuredHost());
        json.name("configuredPort").value(management.configuredPort());
        json.name("boundHost").value(management.boundHost());
        json.name("boundPort").value(management.boundPort());
        json.name("healthPath").value(management.healthPath());
        json.name("readinessPath").value(management.readinessPath());
        json.name("statusPath").value(management.statusPath());
        json.name("access").beginObject();
        json.name("mode").value(management.accessMode().configValue());
        json.name("requestLoggingEnabled").value(management.requestLoggingEnabled());
        json.endObject();
        json.name("healthHistoryMaxEntries").value(management.healthHistoryMaxEntries());
        managementMetrics(json, management.metrics());
        healthHistory(json, management.healthHistory());
        json.endObject();
    }

    private static void managementMetrics(JsonWriter json, ManagementMetricsSnapshot metrics) {
        json.name("metrics").beginObject();
        json.name("requestCount").value(metrics.requestCount());
        json.name("rejectedRequestCount").value(metrics.rejectedRequestCount());
        json.name("errorResponseCount").value(metrics.errorResponseCount());
        json.name("statusCounts").longMap(metrics.statusCounts());
        json.name("lastMethod").value(metrics.lastMethod());
        json.name("lastPath").value(metrics.lastPath());
        json.name("lastStatus").value(metrics.lastStatus());
        json.name("lastDurationMillis").value(metrics.lastDurationMillis());
        json.name("lastRemoteAddress").value(metrics.lastRemoteAddress());
        json.name("lastRejectionReason").value(metrics.lastRejectionReason());
        json.name("lastRequestAt").value(metrics.lastRequestAt());
        json.endObject();
    }

    private static void healthHistory(JsonWriter json, List<HealthHistoryEntry> history) {
        json.name("healthHistory").beginArray();
        for (HealthHistoryEntry entry : history) {
            json.beginObject();
            json.name("observedAt").value(entry.observedAt());
            json.name("lifecycle").value(entry.lifecycle().name());
            json.name("health").value(entry.health().name());
            json.name("readiness").value(entry.readiness().name());
            json.name("transition").value(entry.transition());
            json.name("reasons").strings(entry.reasons());
            json.endObject();
        }
        json.endArray();
    }

    private static final class JsonWriter {
        private final StringBuilder value = new StringBuilder();
        private boolean[] firstStack = new boolean[32];
        private int depth;
        private boolean afterName;

        private JsonWriter() {
            firstStack[0] = true;
        }

        JsonWriter beginObject() {
            beforeValue();
            value.append('{');
            push();
            return this;
        }

        JsonWriter endObject() {
            value.append('}');
            pop();
            return this;
        }

        JsonWriter beginArray() {
            beforeValue();
            value.append('[');
            push();
            return this;
        }

        JsonWriter endArray() {
            value.append(']');
            pop();
            return this;
        }

        JsonWriter name(String name) {
            beforeName();
            string(name);
            value.append(':');
            afterName = true;
            return this;
        }

        JsonWriter value(String text) {
            if (text == null) {
                return nullValue();
            }
            beforeValue();
            string(text);
            return this;
        }

        JsonWriter value(Instant instant) {
            return instant == null ? nullValue() : value(instant.toString());
        }

        JsonWriter value(Integer number) {
            if (number == null) {
                return nullValue();
            }
            beforeValue();
            value.append(number);
            return this;
        }

        JsonWriter value(int number) {
            beforeValue();
            value.append(number);
            return this;
        }

        JsonWriter value(long number) {
            beforeValue();
            value.append(number);
            return this;
        }

        JsonWriter value(boolean bool) {
            beforeValue();
            value.append(bool);
            return this;
        }

        JsonWriter nullValue() {
            beforeValue();
            value.append("null");
            return this;
        }

        JsonWriter strings(List<String> strings) {
            beginArray();
            for (String text : strings) {
                value(text);
            }
            endArray();
            return this;
        }

        JsonWriter stringMap(Map<String, String> values) {
            beginObject();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                name(entry.getKey()).value(entry.getValue());
            }
            endObject();
            return this;
        }

        JsonWriter longMap(Map<Integer, Long> values) {
            beginObject();
            for (Map.Entry<Integer, Long> entry : values.entrySet()) {
                name(Integer.toString(entry.getKey())).value(entry.getValue());
            }
            endObject();
            return this;
        }

        JsonWriter stringLongMap(Map<String, Long> values) {
            beginObject();
            for (Map.Entry<String, Long> entry : values.entrySet()) {
                name(entry.getKey()).value(entry.getValue());
            }
            endObject();
            return this;
        }

        private void beforeName() {
            if (!firstStack[depth]) {
                value.append(',');
            }
            firstStack[depth] = false;
        }

        private void beforeValue() {
            if (afterName) {
                afterName = false;
                return;
            }
            if (!firstStack[depth]) {
                value.append(',');
            }
            firstStack[depth] = false;
        }

        private void push() {
            depth++;
            if (depth == firstStack.length) {
                boolean[] expanded = new boolean[firstStack.length * 2];
                System.arraycopy(firstStack, 0, expanded, 0, firstStack.length);
                firstStack = expanded;
            }
            firstStack[depth] = true;
            afterName = false;
        }

        private void pop() {
            depth--;
            afterName = false;
        }

        private void string(String text) {
            value.append('"');
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"' -> value.append("\\\"");
                    case '\\' -> value.append("\\\\");
                    case '\b' -> value.append("\\b");
                    case '\f' -> value.append("\\f");
                    case '\n' -> value.append("\\n");
                    case '\r' -> value.append("\\r");
                    case '\t' -> value.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            value.append(String.format("\\u%04x", (int) c));
                        } else {
                            value.append(c);
                        }
                    }
                }
            }
            value.append('"');
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
