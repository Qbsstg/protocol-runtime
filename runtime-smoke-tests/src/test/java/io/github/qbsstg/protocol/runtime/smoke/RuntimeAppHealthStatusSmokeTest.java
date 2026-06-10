package io.github.qbsstg.protocol.runtime.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.runtime.app.CollectorHealthState;
import io.github.qbsstg.protocol.runtime.app.CollectorLifecycleState;
import io.github.qbsstg.protocol.runtime.app.CollectorReadinessState;
import io.github.qbsstg.protocol.runtime.app.CollectorStatusSnapshot;
import io.github.qbsstg.protocol.runtime.app.InMemoryRuntimeSink;
import io.github.qbsstg.protocol.runtime.app.SinkType;
import io.github.qbsstg.protocol.runtime.app.StandaloneCollector;
import io.github.qbsstg.protocol.runtime.app.StandaloneCollectorConfig;
import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;

import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RuntimeAppHealthStatusSmokeTest {

    private static final byte[] SINGLE_POINT_FRAME = bytes(
            0x68, 0x0E, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x01, 0x03, 0x00, 0x01, 0x00,
            0x01, 0x00, 0x00, 0x01);
    private static final byte[] MALFORMED_FRAME = bytes(0x68, 0x03, 0x00, 0x00, 0x00);

    @Test
    void reportsHealthyAndDegradedStatusThroughStandaloneTcpCollector() throws Exception {
        try (StandaloneCollector collector = StandaloneCollector.create(config())) {
            CollectorStatusSnapshot configured = collector.statusSnapshot();
            assertEquals(CollectorLifecycleState.CONFIGURED, configured.state());
            assertEquals(CollectorHealthState.CONFIGURED, configured.health().health());
            assertEquals(CollectorReadinessState.NOT_READY, configured.health().readiness());

            collector.start();

            CollectorStatusSnapshot started = collector.statusSnapshot();
            assertEquals(CollectorLifecycleState.RUNNING, started.state());
            assertEquals(CollectorHealthState.HEALTHY, started.health().health());
            assertEquals(CollectorReadinessState.READY, started.health().readiness());
            assertTrue(started.health().reasons().isEmpty());
            assertEquals(1, started.tcpListeners().size());
            assertTrue(started.tcpListeners().get(0).running());

            writeFrame(collector, SINGLE_POINT_FRAME);
            awaitParsedRecords(collector, 1);

            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            assertEquals(1, sink.records().size());
            assertTrue(sink.failures().isEmpty());
            CollectorStatusSnapshot healthyAfterRecord = collector.statusSnapshot();
            assertEquals(CollectorHealthState.HEALTHY, healthyAfterRecord.health().health());
            assertEquals(CollectorReadinessState.READY, healthyAfterRecord.health().readiness());
            assertEquals(1, healthyAfterRecord.metrics().parsedRecordCount());
            assertEquals(0, healthyAfterRecord.metrics().parseFailureCount());

            writeFrame(collector, MALFORMED_FRAME);
            awaitParseFailures(collector, 1);

            CollectorStatusSnapshot degraded = collector.statusSnapshot();
            assertEquals(CollectorHealthState.DEGRADED, degraded.health().health());
            assertEquals(CollectorReadinessState.READY, degraded.health().readiness());
            assertTrue(degraded.health().reasons().contains("parseFailures=1"));
            assertEquals(1, degraded.metrics().parseFailureCount());
            assertEquals(1, sink.failures().size());
        }
    }

    private static StandaloneCollectorConfig config() {
        Properties properties = new Properties();
        properties.setProperty(StandaloneCollectorConfig.TCP_HOST, "127.0.0.1");
        properties.setProperty(StandaloneCollectorConfig.TCP_PORT, "0");
        properties.setProperty(StandaloneCollectorConfig.SOURCE_ID, "iec104:station-1");
        properties.setProperty(StandaloneCollectorConfig.PROTOCOL, "iec104");
        properties.setProperty(StandaloneCollectorConfig.SINK_TYPE, SinkType.IN_MEMORY.configValue());
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE, BackpressureDecision.ACCEPT.name());
        return StandaloneCollectorConfig.fromProperties(properties);
    }

    private static void writeFrame(StandaloneCollector collector, byte[] frame) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(collector.localAddress());
            socket.getOutputStream().write(frame);
            socket.getOutputStream().flush();
        }
    }

    private static void awaitParsedRecords(StandaloneCollector collector, long expected) throws InterruptedException {
        awaitMetric(() -> collector.statusSnapshot().metrics().parsedRecordCount(), expected);
    }

    private static void awaitParseFailures(StandaloneCollector collector, long expected) throws InterruptedException {
        awaitMetric(() -> collector.statusSnapshot().metrics().parseFailureCount(), expected);
    }

    private static void awaitMetric(MetricSupplier supplier, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (supplier.getAsLong() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, supplier.getAsLong());
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private interface MetricSupplier {
        long getAsLong();
    }
}
