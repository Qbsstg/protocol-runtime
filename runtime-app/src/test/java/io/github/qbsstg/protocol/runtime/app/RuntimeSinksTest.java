package io.github.qbsstg.protocol.runtime.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class RuntimeSinksTest {

    @Test
    void isolatesRecordSinkFailuresAndReportsStatus() {
        RuntimeSinks sinks = new RuntimeSinks(
                throwingRecordSink("disk full"),
                FailureSink.noop(),
                null,
                new RuntimeSinkCounters());
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

        String status = CollectorStatusFormatter.format(snapshot(metrics));
        assertTrue(status.contains("sinkFailures=1"));
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
                new RuntimeSinkCounters());
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

    private static CollectorStatusSnapshot snapshot(CollectorRuntimeMetrics metrics) {
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
                BackpressureDecision.ACCEPT,
                0,
                BackpressureDecision.DROP,
                0,
                BackpressureDecision.RETRY_LATER,
                false);
    }
}
