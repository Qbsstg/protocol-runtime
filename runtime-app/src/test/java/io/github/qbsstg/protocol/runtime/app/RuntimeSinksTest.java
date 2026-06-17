package io.github.qbsstg.protocol.runtime.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryOutcome;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryRequest;
import io.github.qbsstg.protocol.runtime.core.DownstreamDeliveryResult;
import io.github.qbsstg.protocol.runtime.core.DownstreamSink;
import io.github.qbsstg.protocol.runtime.core.DownstreamSinkIdentity;
import io.github.qbsstg.protocol.runtime.core.DownstreamSinkStatus;
import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class RuntimeSinksTest {

    @TempDir
    Path tempDir;

    @Test
    void isolatesRecordSinkFailuresAndReportsStatus() throws Exception {
        FailedRecordIsolation failedRecords = failedRecords();
        RuntimeSinks sinks = new RuntimeSinks(
                throwingRecordSink("disk full"),
                FailureSink.noop(),
                null,
                new RuntimeSinkCounters(),
                failedRecords);
        ParsedRecord<Object> record = parsedRecord();

        assertDoesNotThrow(() -> sinks.runnerRecordSink().accept(record));

        CollectorRuntimeMetrics metrics = sinks.metricsSnapshot();
        assertEquals(0, metrics.parsedRecordCount());
        assertEquals(0, metrics.parseFailureCount());
        assertEquals(1, metrics.sinkFailureCount());
        assertEquals("record", metrics.lastSinkFailureTarget());
        assertEquals("iec104:station-1", metrics.lastSinkFailureSourceId());
        assertEquals(Instant.parse("2026-06-10T08:00:00Z"), metrics.lastSinkFailureAt());
        assertEquals(IllegalStateException.class.getName(), metrics.lastSinkFailureType());
        assertEquals("disk full", metrics.lastSinkFailureMessage());
        assertEquals("PERMANENT_FAILURE", metrics.lastSinkDeliveryFailureType());
        assertFalse(metrics.lastSinkFailureRetryable());
        assertEquals(1, metrics.sinkFailureTypeCounts().get("PERMANENT_FAILURE"));
        assertEquals(0, metrics.sinkFailureTypeCounts().get("WRITE_ERROR"));

        CollectorStatusSnapshot snapshot = snapshot(metrics);
        assertEquals(CollectorHealthState.DEGRADED, snapshot.health().health());
        assertEquals(CollectorReadinessState.NOT_READY, snapshot.health().readiness());
        assertTrue(snapshot.health().reasons().contains("sinkFailures=1"));
        assertEquals(1, failedRecords.status().sampleCount());
        assertEquals(1, failedRecords.status().retainedSampleCount());
        assertTrue(Files.exists(failedRecords.status().lastSampleFile()));
        String sample = Files.readString(failedRecords.status().lastSampleFile());
        assertTrue(sample.contains("\"schemaVersion\":\"protocol-runtime.failed-record.v1\""));
        assertTrue(sample.contains("\"kind\":\"failedRecord\""));
        assertTrue(sample.contains("\"failureType\":\"PERMANENT_FAILURE\""));

        String status = CollectorStatusFormatter.format(snapshot);
        assertTrue(status.contains("health=DEGRADED"));
        assertTrue(status.contains("readiness=NOT_READY"));
        assertTrue(status.contains("sinkFailures=1"));
        assertTrue(status.contains("sinkAdapter=app-local:file/"));
        assertTrue(status.contains("lastSinkFailure=record@iec104:station-1/"));
        assertTrue(status.contains(IllegalStateException.class.getName() + ":disk full"));
    }

    @Test
    void isolatesFailureSinkFailures() {
        RuntimeSinks sinks = new RuntimeSinks(
                ignored -> {
                },
                throwingFailureSink("failure sink down"),
                null,
                new RuntimeSinkCounters(),
                failedRecords());
        ParseFailure failure = parseFailure();

        assertDoesNotThrow(() -> sinks.runnerFailureSink().accept(failure));

        CollectorRuntimeMetrics metrics = sinks.metricsSnapshot();
        assertEquals(0, metrics.parsedRecordCount());
        assertEquals(0, metrics.parseFailureCount());
        assertEquals(1, metrics.sinkFailureCount());
        assertEquals("failure", metrics.lastSinkFailureTarget());
        assertEquals("iec104:station-1", metrics.lastSinkFailureSourceId());
        assertEquals(Instant.parse("2026-06-10T08:00:01Z"), metrics.lastSinkFailureAt());
        assertEquals(IllegalStateException.class.getName(), metrics.lastSinkFailureType());
        assertEquals("failure sink down", metrics.lastSinkFailureMessage());
        assertEquals("PERMANENT_FAILURE", metrics.lastSinkDeliveryFailureType());
    }

    @Test
    void classifiesSerializationFailuresAndStillWritesFailedRecordSample() throws Exception {
        FailedRecordIsolation failedRecords = failedRecords();
        RuntimeSinks sinks = new RuntimeSinks(
                new FileRuntimeSink<>(tempDir.resolve("records.ndjson")),
                FailureSink.noop(),
                null,
                new RuntimeSinkCounters(),
                failedRecords);
        ParsedRecord<Object> record = new ParsedRecord<>(
                SourceId.of("iec104", "station-1"),
                "iec104",
                "single-point",
                new BadValue(),
                new byte[] {0x68, 0x04},
                Instant.parse("2026-06-10T08:00:03Z"),
                Map.of("transport", "test"));

        assertDoesNotThrow(() -> sinks.runnerRecordSink().accept(record));

        CollectorRuntimeMetrics metrics = sinks.metricsSnapshot();
        assertEquals(1, metrics.sinkFailureCount());
        assertEquals("SERIALIZATION_ERROR", metrics.lastSinkDeliveryFailureType());
        assertFalse(metrics.lastSinkFailureRetryable());
        assertEquals(1, metrics.sinkFailureTypeCounts().get("SERIALIZATION_ERROR"));
        assertEquals(1, failedRecords.status().sampleCount());
        String sample = Files.readString(failedRecords.status().lastSampleFile());
        assertTrue(sample.contains("\"failureType\":\"SERIALIZATION_ERROR\""));
        assertTrue(sample.contains("<unavailable:java.lang.IllegalStateException>"));
    }

    @Test
    void recordsSuccessfulFakeAdapterDeliveryEvidence() {
        FakeNoNetworkSink adapter = new FakeNoNetworkSink(DownstreamDeliveryResult.success());
        RuntimeSinks sinks = fakeAdapterSinks(adapter);
        sinks.start();

        assertDoesNotThrow(() -> sinks.runnerRecordSink().accept(parsedRecord()));

        CollectorRuntimeMetrics metrics = sinks.metricsSnapshot();
        assertEquals(1, metrics.parsedRecordCount());
        assertEquals(1, metrics.sinkDeliveredCount());
        assertEquals("DELIVERED", metrics.lastSinkDeliveryOutcome());
        assertEquals(1, metrics.sinkDeliveryOutcomeCounts().get("DELIVERED"));
        assertEquals("iec104:station-1", adapter.lastRequest().sourceId().qualifiedValue());
        DownstreamSinkStatus status = sinks.downstreamSinkStatus();
        assertEquals("fake-no-network:unit", status.identity().qualifiedName());
        assertTrue(status.ready());
        assertEquals(1, status.deliveredCount());
        assertTrue(status.diagnostics().containsKey("deliveryOutcomeCounts"));
    }

    @Test
    void routesFakeAdapterBackpressureToFailedRecordIsolationAndStatus() throws Exception {
        FakeNoNetworkSink adapter = new FakeNoNetworkSink(DownstreamDeliveryResult.failure(
                DownstreamDeliveryOutcome.BACKPRESSURE_REJECTED,
                "fake adapter queue full",
                "FakeBackpressure",
                Map.of("endpoint", "redacted")));
        RuntimeSinks sinks = fakeAdapterSinks(adapter);
        sinks.start();

        assertDoesNotThrow(() -> sinks.runnerRecordSink().accept(parsedRecord()));

        CollectorRuntimeMetrics metrics = sinks.metricsSnapshot();
        assertEquals(0, metrics.parsedRecordCount());
        assertEquals(0, metrics.sinkDeliveredCount());
        assertEquals(1, metrics.sinkFailureCount());
        assertEquals("BACKPRESSURE_REJECTED", metrics.lastSinkDeliveryOutcome());
        assertEquals("BACKPRESSURE_REJECTED", metrics.lastSinkDeliveryFailureType());
        assertTrue(metrics.lastSinkFailureRetryable());
        assertEquals(1, metrics.sinkDeliveryOutcomeCounts().get("BACKPRESSURE_REJECTED"));
        assertEquals(1, sinks.failedRecordIsolationStatus().sampleCount());
        String sample = Files.readString(sinks.failedRecordIsolationStatus().lastSampleFile());
        assertTrue(sample.contains("\"failureType\":\"BACKPRESSURE_REJECTED\""));
        assertTrue(sample.contains("\"adapterContract\":\"downstream-sink-spi.v1\""));
        DownstreamSinkStatus status = sinks.downstreamSinkStatus();
        assertFalse(status.ready());
        assertEquals(BackpressureDecision.RETRY_LATER, status.backpressureDecision());
        assertEquals("true", status.diagnostics().get("authRefConfigured"));
        assertFalse(status.diagnostics().containsValue("vault://runtime/sink-token"));
    }

    private static final class BadValue {
        @Override
        public String toString() {
            throw new IllegalStateException("cannot render value");
        }
    }

    @Test
    void sinkFailureThresholdAppliesBackpressureBeforeParsing() {
        RuntimeSinkCounters counters = new RuntimeSinkCounters();
        RuntimeAppBackpressureStrategy strategy = new RuntimeAppBackpressureStrategy(
                BackpressureDecision.ACCEPT,
                0,
                BackpressureDecision.DROP,
                1,
                BackpressureDecision.RETRY_LATER,
                counters);
        IngressEnvelope envelope = envelope();

        assertEquals(BackpressureDecision.ACCEPT, strategy.evaluate(envelope));

        counters.recordSinkFailure(
                "record",
                "iec104:station-1",
                Instant.parse("2026-06-10T08:00:02Z"),
                new IllegalStateException("sink unavailable"));

        assertEquals(BackpressureDecision.RETRY_LATER, strategy.evaluate(envelope));
        CollectorRuntimeMetrics metrics = counters.snapshot();
        assertEquals(1, metrics.backpressureRetryLaterCount());
        assertEquals(0, metrics.backpressureDropCount());
        assertEquals("iec104:station-1", metrics.lastBackpressureSourceId());
        assertEquals(BackpressureDecision.RETRY_LATER, metrics.lastBackpressureDecision());
    }

    private static RecordSink<Object> throwingRecordSink(String message) {
        return ignored -> {
            throw new IllegalStateException(message);
        };
    }

    private RuntimeSinks fakeAdapterSinks(FakeNoNetworkSink adapter) {
        return new RuntimeSinks(
                ignored -> {
                },
                FailureSink.noop(),
                adapter,
                null,
                new RuntimeSinkCounters(),
                failedRecords(),
                new DownstreamSinkAdapterConfig(
                        "fake-no-network",
                        "memory://fake",
                        "unit-topic",
                        "vault://runtime/sink-token",
                        1000,
                        "single",
                        "no-retry",
                        "failed-records"));
    }

    private static FailureSink throwingFailureSink(String message) {
        return ignored -> {
            throw new IllegalStateException(message);
        };
    }

    private static ParsedRecord<Object> parsedRecord() {
        return new ParsedRecord<>(
                SourceId.of("iec104", "station-1"),
                "iec104",
                "single-point",
                "ON",
                new byte[] {0x68, 0x04},
                Instant.parse("2026-06-10T08:00:00Z"),
                Map.of("transport", "test"));
    }

    private static ParseFailure parseFailure() {
        return new ParseFailure(
                SourceId.of("iec104", "station-1"),
                "iec104",
                "bad frame",
                new byte[] {0x01},
                Instant.parse("2026-06-10T08:00:01Z"),
                null,
                Map.of("transport", "test"));
    }

    private static IngressEnvelope envelope() {
        return new IngressEnvelope(
                SourceId.of("iec104", "station-1"),
                "tcp",
                new byte[] {0x68, 0x04},
                Instant.parse("2026-06-10T08:00:02Z"),
                Map.of("transport", "test"));
    }

    private CollectorStatusSnapshot snapshot(CollectorRuntimeMetrics metrics) {
        return new CollectorStatusSnapshot(
                CollectorLifecycleState.RUNNING,
                Instant.parse("2026-06-10T07:59:00Z"),
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0,
                metrics,
                SinkType.FILE,
                null,
                FileSinkRotationConfig.defaults(),
                DownstreamSinkAdapterConfig.defaults(),
                DownstreamSinkStatus.unknown(DownstreamSinkAdapterConfig.defaults().identity(SinkType.FILE)),
                failedRecords().status(),
                BackpressureDecision.ACCEPT,
                0,
                BackpressureDecision.DROP,
                0,
                BackpressureDecision.RETRY_LATER,
                false,
                managementStatus());
    }

    private FailedRecordIsolation failedRecords() {
        return new FailedRecordIsolation(new SinkFailureIsolationConfig(
                true,
                tempDir.resolve("failed-records"),
                4));
    }

    private static ManagementStatusSnapshot managementStatus() {
        return new ManagementStatusSnapshot(
                false,
                false,
                ManagementServerConfig.DEFAULT_HOST,
                ManagementServerConfig.DEFAULT_PORT,
                null,
                null,
                ManagementServerConfig.DEFAULT_HEALTH_PATH,
                ManagementServerConfig.DEFAULT_READINESS_PATH,
                ManagementServerConfig.DEFAULT_STATUS_PATH,
                ManagementServerConfig.DEFAULT_ACCESS_MODE,
                ManagementServerConfig.DEFAULT_REQUEST_LOGGING_ENABLED,
                ManagementServerConfig.DEFAULT_HEALTH_HISTORY_MAX_ENTRIES,
                ManagementMetricsSnapshot.empty(),
                List.of());
    }

    private static final class FakeNoNetworkSink implements DownstreamSink<Object> {
        private final DownstreamSinkIdentity identity = new DownstreamSinkIdentity("fake-no-network", "unit");
        private final DownstreamDeliveryResult result;
        private boolean running;
        private DownstreamDeliveryRequest<Object> lastRequest;

        private FakeNoNetworkSink(DownstreamDeliveryResult result) {
            this.result = result;
        }

        @Override
        public DownstreamSinkIdentity identity() {
            return identity;
        }

        @Override
        public void start() {
            running = true;
        }

        @Override
        public DownstreamDeliveryResult deliver(DownstreamDeliveryRequest<Object> request) {
            lastRequest = request;
            return result;
        }

        @Override
        public DownstreamSinkStatus status() {
            return new DownstreamSinkStatus(
                    identity,
                    running,
                    result == null || result.delivered() || result.retryable(),
                    result == null || result.delivered(),
                    result == null ? BackpressureDecision.ACCEPT : result.backpressureDecision(),
                    0,
                    0,
                    result,
                    Map.of("adapter", "fake-no-network"));
        }

        private DownstreamDeliveryRequest<Object> lastRequest() {
            return lastRequest;
        }
    }
}
