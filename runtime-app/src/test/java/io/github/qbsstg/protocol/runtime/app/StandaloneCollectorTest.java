package io.github.qbsstg.protocol.runtime.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.iec104.Iec104Frame;
import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandaloneCollectorTest {

    private static final byte[] SINGLE_POINT_FRAME = bytes(
            0x68, 0x0E, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x01, 0x03, 0x00, 0x01, 0x00,
            0x01, 0x00, 0x00, 0x01);

    @TempDir
    Path tempDir;

    @Test
    void startsStandaloneIec104CollectorAndRoutesRecordsToInMemorySink() throws Exception {
        try (StandaloneCollector collector = StandaloneCollector.create(config(0, "in-memory"))) {
            collector.start();

            writeFrame(collector, SINGLE_POINT_FRAME);

            InMemoryRuntimeSink<Iec104Frame> sink = collector.inMemorySink().orElseThrow();
            List<ParsedRecord<Iec104Frame>> records = awaitRecords(sink, 1);
            assertTrue(collector.isRunning());
            assertEquals("iec104:station-1", records.get(0).sourceId().qualifiedValue());
            assertEquals("iec104", records.get(0).protocol());
            assertEquals("I_FORMAT", records.get(0).recordType());
            assertArrayEquals(SINGLE_POINT_FRAME, records.get(0).rawPayload());
            assertTrue(sink.failures().isEmpty());
        }
    }

    @Test
    void writesRecordsToConfiguredFileSink() throws Exception {
        Path output = tempDir.resolve("records.ndjson");
        StandaloneCollectorConfig config = config(0, "file", output);

        try (StandaloneCollector collector = StandaloneCollector.create(config)) {
            collector.start();

            writeFrame(collector, SINGLE_POINT_FRAME);
            awaitFileLines(output, 1);
        }

        String line = Files.readString(output);
        assertTrue(line.contains("\"kind\":\"record\""));
        assertTrue(line.contains("\"sourceId\":\"iec104:station-1\""));
        assertTrue(line.contains("\"rawPayloadHex\":\"680E0000000001010300010001000001\""));
    }

    @Test
    void routesMalformedFramesToFailureSink() throws Exception {
        try (StandaloneCollector collector = StandaloneCollector.create(config(0, "in-memory"))) {
            collector.start();

            writeFrame(collector, bytes(0x68, 0x03, 0x00, 0x00, 0x00));

            InMemoryRuntimeSink<Iec104Frame> sink = collector.inMemorySink().orElseThrow();
            List<ParseFailure> failures = awaitFailures(sink, 1);
            assertTrue(sink.records().isEmpty());
            assertEquals("iec104:station-1", failures.get(0).sourceId().qualifiedValue());
            assertTrue(failures.get(0).message().contains("Invalid IEC104 APDU length"));
        }
    }

    @Test
    void retryLaterBackpressurePreventsParsing() throws Exception {
        Properties properties = baseProperties(0, "in-memory");
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE, "RETRY_LATER");

        try (StandaloneCollector collector = StandaloneCollector.create(
                StandaloneCollectorConfig.fromProperties(properties))) {
            collector.start();

            writeFrame(collector, SINGLE_POINT_FRAME);
            Thread.sleep(200);

            InMemoryRuntimeSink<Iec104Frame> sink = collector.inMemorySink().orElseThrow();
            assertTrue(sink.records().isEmpty());
            assertTrue(sink.failures().isEmpty());
        }
    }

    @Test
    void clientDisconnectClearsActiveSession() throws Exception {
        try (StandaloneCollector collector = StandaloneCollector.create(config(0, "in-memory"))) {
            collector.start();

            try (Socket socket = new Socket()) {
                socket.connect(collector.localAddress());
                awaitActiveConnections(collector, 1);
            }

            awaitActiveConnections(collector, 0);
        }
    }

    @Test
    void gracefulStopClosesActiveClient() throws Exception {
        Socket socket = null;
        try (StandaloneCollector collector = StandaloneCollector.create(config(0, "in-memory"))) {
            collector.start();
            socket = new Socket();
            socket.connect(collector.localAddress());
            awaitActiveConnections(collector, 1);

            collector.stop();

            assertFalse(collector.isRunning());
            assertTrue(socket.isClosed() || socket.getInputStream().read() < 0);
            awaitActiveConnections(collector, 0);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    void portConflictFailsFast() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            StandaloneCollector collector = StandaloneCollector.create(config(socket.getLocalPort(), "in-memory"));

            assertThrows(Exception.class, collector::start);
            assertFalse(collector.isRunning());
        }
    }

    @Test
    void validatesConfiguration() {
        Properties properties = baseProperties(0, "file");

        assertThrows(IllegalArgumentException.class, () -> StandaloneCollectorConfig.fromProperties(properties));

        properties.setProperty(StandaloneCollectorConfig.SOURCE_ID, "missing-separator");
        properties.setProperty(StandaloneCollectorConfig.SINK_FILE, tempDir.resolve("records.ndjson").toString());
        assertThrows(IllegalArgumentException.class, () -> StandaloneCollectorConfig.fromProperties(properties));
    }

    private static StandaloneCollectorConfig config(int port, String sinkType) {
        return StandaloneCollectorConfig.fromProperties(baseProperties(port, sinkType));
    }

    private static StandaloneCollectorConfig config(int port, String sinkType, Path output) {
        Properties properties = baseProperties(port, sinkType);
        properties.setProperty(StandaloneCollectorConfig.SINK_FILE, output.toString());
        return StandaloneCollectorConfig.fromProperties(properties);
    }

    private static Properties baseProperties(int port, String sinkType) {
        Properties properties = new Properties();
        properties.setProperty(StandaloneCollectorConfig.TCP_HOST, "127.0.0.1");
        properties.setProperty(StandaloneCollectorConfig.TCP_PORT, Integer.toString(port));
        properties.setProperty(StandaloneCollectorConfig.SOURCE_ID, "iec104:station-1");
        properties.setProperty(StandaloneCollectorConfig.SINK_TYPE, sinkType);
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE, BackpressureDecision.ACCEPT.name());
        return properties;
    }

    private static void writeFrame(StandaloneCollector collector, byte[] frame) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(collector.localAddress());
            socket.getOutputStream().write(frame);
            socket.getOutputStream().flush();
        }
    }

    private static List<ParsedRecord<Iec104Frame>> awaitRecords(
            InMemoryRuntimeSink<Iec104Frame> sink,
            int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            List<ParsedRecord<Iec104Frame>> records = sink.records();
            if (records.size() >= expected) {
                return records;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, sink.records().size());
        return sink.records();
    }

    private static List<ParseFailure> awaitFailures(
            InMemoryRuntimeSink<Iec104Frame> sink,
            int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            List<ParseFailure> failures = sink.failures();
            if (failures.size() >= expected) {
                return failures;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, sink.failures().size());
        return sink.failures();
    }

    private static void awaitFileLines(Path output, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (Files.exists(output) && Files.readAllLines(output).size() >= expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(Files.exists(output));
        assertEquals(expected, Files.readAllLines(output).size());
    }

    private static void awaitActiveConnections(StandaloneCollector collector, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (collector.activeConnectionCount() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, collector.activeConnectionCount());
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) (values[i] & 0xFF);
        }
        return bytes;
    }
}
