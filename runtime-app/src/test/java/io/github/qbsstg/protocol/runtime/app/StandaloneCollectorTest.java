package io.github.qbsstg.protocol.runtime.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.iec104.Iec104Frame;
import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpConnectionAttributes;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServerConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
            CollectorStatusSnapshot configured = collector.statusSnapshot();
            assertEquals(CollectorLifecycleState.CONFIGURED, configured.state());
            assertNull(configured.startedAt());
            assertEquals(SinkType.IN_MEMORY, configured.sinkType());
            assertEquals(FileSinkRotationConfig.defaults(), configured.fileSinkRotation());
            assertEquals(CollectorRuntimeMetrics.empty(), configured.metrics());
            assertEquals(BackpressureDecision.ACCEPT, configured.backpressureDecision());
            assertEquals(0, configured.backpressureMaxPayloadBytes());
            assertEquals(BackpressureDecision.DROP, configured.oversizedPayloadDecision());
            assertFalse(configured.strictAsduParsing());

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

            CollectorStatusSnapshot running = collector.statusSnapshot();
            assertEquals(CollectorLifecycleState.RUNNING, running.state());
            assertNotNull(running.startedAt());
            assertNull(running.stoppedAt());
            assertEquals(1, running.sources().size());
            assertEquals("iec104:station-1", running.sources().get(0).sourceId());
            assertEquals(1, running.tcpListeners().size());
            assertEquals("default", running.tcpListeners().get(0).name());
            assertEquals("127.0.0.1", running.tcpListeners().get(0).configuredHost());
            assertEquals(0, running.tcpListeners().get(0).configuredPort());
            assertTrue(running.tcpListeners().get(0).running());
            assertNotNull(running.tcpListeners().get(0).boundPort());
            assertEquals(1, running.metrics().parsedRecordCount());
            assertEquals(0, running.metrics().parseFailureCount());
            assertNull(running.metrics().lastParseFailureMessage());
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
    void rotatesConfiguredFileSinkBeforeItGrowsWithoutBound() throws Exception {
        Path output = tempDir.resolve("rotating-records.ndjson");
        FileRuntimeSink<String> sink = new FileRuntimeSink<>(output, new FileSinkRotationConfig(1, 2));

        sink.accept(record("record-1"));
        sink.accept(record("record-2"));
        sink.accept(record("record-3"));
        sink.accept(record("record-4"));
        sink.stop();

        assertFileContains(output, "record-4");
        assertFileContains(rotatedPath(output, 1), "record-3");
        assertFileContains(rotatedPath(output, 2), "record-2");
        assertFalse(Files.exists(rotatedPath(output, 3)));
        assertFalse(Files.readString(output).contains("record-1"));
        assertFalse(Files.readString(rotatedPath(output, 1)).contains("record-1"));
        assertFalse(Files.readString(rotatedPath(output, 2)).contains("record-1"));
    }

    @Test
    void routesMalformedFramesToFailureSink() throws Exception {
        try (StandaloneCollector collector = StandaloneCollector.create(config(0, "in-memory"))) {
            collector.start();

            byte[] malformed = bytes(0x68, 0x03, 0x00, 0x00, 0x00);
            writeFrame(collector, malformed);

            InMemoryRuntimeSink<Iec104Frame> sink = collector.inMemorySink().orElseThrow();
            List<ParseFailure> failures = awaitFailures(sink, 1);
            assertTrue(sink.records().isEmpty());
            assertEquals("iec104:station-1", failures.get(0).sourceId().qualifiedValue());
            assertTrue(failures.get(0).message().contains("Invalid IEC104 APDU length"));

            writeFrame(collector, SINGLE_POINT_FRAME);
            awaitRecords(sink, 1);

            CollectorStatusSnapshot snapshot = collector.statusSnapshot();
            assertEquals(1, snapshot.metrics().parsedRecordCount());
            assertEquals(1, snapshot.metrics().parseFailureCount());
            assertEquals("iec104:station-1", snapshot.metrics().lastParseFailureSourceId());
            assertTrue(snapshot.metrics().lastParseFailureMessage().contains("Invalid IEC104 APDU length"));
            assertNotNull(snapshot.metrics().lastParseFailureAt());
            assertNull(snapshot.metrics().lastParseFailureCauseType());
            assertEquals(malformed.length, snapshot.metrics().lastParseFailurePayloadSize());
            assertEquals("6803000000", snapshot.metrics().lastParseFailurePayloadPreviewHex());
            assertEquals("iec104", snapshot.metrics().lastParseFailureAttributes().get(
                    TcpConnectionAttributes.SOURCE_NAMESPACE));
            assertEquals("station-1", snapshot.metrics().lastParseFailureAttributes().get(
                    TcpConnectionAttributes.SOURCE_VALUE));
            assertTrue(snapshot.metrics().lastParseFailureAttributes().containsKey(
                    TcpConnectionAttributes.SESSION_ID));
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
            CollectorRuntimeMetrics metrics = awaitRetryLaterBackpressure(collector, 1);
            assertEquals(0, metrics.parsedRecordCount());
            assertEquals(0, metrics.parseFailureCount());
            assertEquals(1, metrics.backpressureRetryLaterCount());
            assertEquals(0, metrics.backpressureDropCount());
            assertEquals(BackpressureDecision.RETRY_LATER, metrics.lastBackpressureDecision());
            assertEquals(SINGLE_POINT_FRAME.length, metrics.lastBackpressurePayloadSize());
            assertEquals("iec104:station-1", metrics.lastBackpressureSourceId());
        }
    }

    @Test
    void oversizedPayloadBackpressureDropsBeforeParsing() throws Exception {
        Properties properties = baseProperties(0, "in-memory");
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE_MAX_PAYLOAD_BYTES, "1");
        properties.setProperty(
                StandaloneCollectorConfig.BACKPRESSURE_OVERSIZED_PAYLOAD_DECISION,
                BackpressureDecision.DROP.name());

        try (StandaloneCollector collector = StandaloneCollector.create(
                StandaloneCollectorConfig.fromProperties(properties))) {
            CollectorStatusSnapshot configured = collector.statusSnapshot();
            assertEquals(1, configured.backpressureMaxPayloadBytes());
            assertEquals(BackpressureDecision.DROP, configured.oversizedPayloadDecision());

            collector.start();

            writeFrame(collector, SINGLE_POINT_FRAME);

            CollectorRuntimeMetrics metrics = awaitDropBackpressure(collector, 1);
            InMemoryRuntimeSink<Iec104Frame> sink = collector.inMemorySink().orElseThrow();
            assertTrue(sink.records().isEmpty());
            assertTrue(sink.failures().isEmpty());
            assertEquals(0, metrics.parsedRecordCount());
            assertEquals(0, metrics.parseFailureCount());
            assertEquals(0, metrics.backpressureRetryLaterCount());
            assertEquals(1, metrics.backpressureDropCount());
            assertEquals(BackpressureDecision.DROP, metrics.lastBackpressureDecision());
            assertEquals(SINGLE_POINT_FRAME.length, metrics.lastBackpressurePayloadSize());
            assertEquals("iec104:station-1", metrics.lastBackpressureSourceId());
            assertNotNull(metrics.lastBackpressureAt());
        }
    }

    @Test
    void preservesLegacySingleSourceConfigurationAsAppModel() {
        StandaloneCollectorAppConfig appConfig = StandaloneCollectorConfig.appConfigFromProperties(
                baseProperties(2404, "logging"));

        assertEquals(1, appConfig.sources().size());
        assertEquals(1, appConfig.tcpListeners().size());
        assertEquals("default", appConfig.sources().get(0).name());
        assertEquals("iec104:station-1", appConfig.sources().get(0).sourceId().qualifiedValue());
        assertEquals("default", appConfig.tcpListeners().get(0).name());
        assertEquals("default", appConfig.tcpListeners().get(0).sourceName());
        assertEquals(2404, appConfig.tcpListeners().get(0).tcp().port());
    }

    @Test
    void parsesMultiSourceAndMultiListenerAppConfiguration() {
        Properties properties = multiSourceProperties();

        CollectorConfigValidation validation = StandaloneCollectorConfig.validateProperties(properties);
        StandaloneCollectorAppConfig appConfig = StandaloneCollectorConfig.appConfigFromProperties(properties);

        assertTrue(validation.isValid());
        assertEquals(2, appConfig.sources().size());
        assertEquals(2, appConfig.tcpListeners().size());
        assertEquals("station-a", appConfig.tcpListeners().get(0).sourceName());
        assertEquals("iec104:station-a", appConfig.tcpListeners().get(0).sourceId().qualifiedValue());
        assertEquals("station-b", appConfig.tcpListeners().get(1).sourceName());
        assertEquals("iec104:station-b", appConfig.tcpListeners().get(1).sourceId().qualifiedValue());
    }

    @Test
    void startsMultiListenerCollectorAndRoutesSourceSpecificRecords() throws Exception {
        StandaloneCollectorAppConfig appConfig = StandaloneCollectorConfig.appConfigFromProperties(
                multiSourceProperties());

        try (StandaloneCollector collector = StandaloneCollector.create(appConfig)) {
            collector.start();
            CollectorStatusSnapshot running = collector.statusSnapshot();
            assertEquals(CollectorLifecycleState.RUNNING, running.state());
            assertEquals(2, running.sources().size());
            assertEquals(2, running.tcpListeners().size());
            assertTrue(running.tcpListeners().stream().allMatch(TcpListenerStatus::running));
            assertTrue(running.tcpListeners().stream().allMatch(listener -> listener.boundPort() != null));

            List<InetSocketAddress> addresses = collector.localAddresses();

            writeFrame(addresses.get(0), SINGLE_POINT_FRAME);
            writeFrame(addresses.get(1), SINGLE_POINT_FRAME);

            InMemoryRuntimeSink<Iec104Frame> sink = collector.inMemorySink().orElseThrow();
            List<ParsedRecord<Iec104Frame>> records = awaitRecords(sink, 2);
            Set<String> sourceIds = new HashSet<>();
            for (ParsedRecord<Iec104Frame> record : records) {
                sourceIds.add(record.sourceId().qualifiedValue());
            }
            assertEquals(Set.of("iec104:station-a", "iec104:station-b"), sourceIds);
            assertTrue(sink.failures().isEmpty());
            assertEquals(2, collector.statusSnapshot().metrics().parsedRecordCount());
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
            CollectorStatusSnapshot connected = collector.statusSnapshot();
            assertEquals(1, connected.activeConnectionCount());
            assertEquals(1, connected.tcpListeners().get(0).activeConnectionCount());

            collector.stop();

            assertFalse(collector.isRunning());
            CollectorStatusSnapshot stopped = collector.statusSnapshot();
            assertEquals(CollectorLifecycleState.STOPPED, stopped.state());
            assertNotNull(stopped.startedAt());
            assertNotNull(stopped.stoppedAt());
            assertNull(stopped.startupFailureReason());
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
            CollectorStatusSnapshot failed = collector.statusSnapshot();
            assertEquals(CollectorLifecycleState.FAILED, failed.state());
            assertNotNull(failed.startupFailureReason());
            assertNotNull(failed.lastExceptionType());
            assertNotNull(failed.lastExceptionMessage());
            assertNull(failed.startedAt());
            assertNotNull(failed.stoppedAt());
        }
    }

    @Test
    void partialMultiListenerStartupFailureRollsBackStartedListeners() throws Exception {
        int port = freePort();
        StandaloneCollectorAppConfig appConfig = duplicatePortAppConfig(port);
        StandaloneCollector collector = StandaloneCollector.create(appConfig);

        assertThrows(RuntimeException.class, collector::start);

        CollectorStatusSnapshot failed = collector.statusSnapshot();
        assertEquals(CollectorLifecycleState.FAILED, failed.state());
        assertTrue(failed.startupFailureReason().contains("south"));
        assertNotNull(failed.lastExceptionType());
        assertNotNull(failed.lastExceptionMessage());
        assertEquals(0, failed.activeConnectionCount());
        assertTrue(failed.tcpListeners().stream().noneMatch(TcpListenerStatus::running));
        assertTrue(failed.tcpListeners().stream().allMatch(listener -> listener.boundPort() == null));
    }

    @Test
    void stopIsIdempotentBeforeAndAfterStart() {
        StandaloneCollector configured = StandaloneCollector.create(config(0, "in-memory"));
        configured.stop();
        configured.stop();
        assertEquals(CollectorLifecycleState.STOPPED, configured.state());
        assertNotNull(configured.statusSnapshot().stoppedAt());

        StandaloneCollector running = StandaloneCollector.create(config(0, "in-memory"));
        running.start();
        running.stop();
        running.stop();
        assertEquals(CollectorLifecycleState.STOPPED, running.state());
        assertFalse(running.isRunning());
    }

    @Test
    void restartAfterStopIsRejectedAndRecordedAsLastException() {
        StandaloneCollector collector = StandaloneCollector.create(config(0, "in-memory"));
        collector.start();
        collector.stop();

        IllegalStateException failure = assertThrows(IllegalStateException.class, collector::start);

        CollectorStatusSnapshot snapshot = collector.statusSnapshot();
        assertEquals(CollectorLifecycleState.STOPPED, snapshot.state());
        assertEquals(IllegalStateException.class.getName(), snapshot.lastExceptionType());
        assertTrue(snapshot.lastExceptionMessage().contains("cannot be restarted"));
        assertTrue(failure.getMessage().contains("cannot be restarted"));
    }

    @Test
    void validatesConfiguration() {
        Properties properties = baseProperties(0, "file");

        assertThrows(IllegalArgumentException.class, () -> StandaloneCollectorConfig.fromProperties(properties));

        properties.setProperty(StandaloneCollectorConfig.SOURCE_ID, "missing-separator");
        properties.setProperty(StandaloneCollectorConfig.SINK_FILE, tempDir.resolve("records.ndjson").toString());
        assertThrows(IllegalArgumentException.class, () -> StandaloneCollectorConfig.fromProperties(properties));
    }

    @Test
    void reportsConfigurationValidationErrorsWithoutStarting() {
        Properties properties = baseProperties(70000, "unknown");
        properties.setProperty(StandaloneCollectorConfig.SOURCE_ID, "missing-separator");
        properties.setProperty(StandaloneCollectorConfig.TCP_BOSS_THREADS, "0");
        properties.setProperty(StandaloneCollectorConfig.TCP_WORKER_THREADS, "-1");
        properties.setProperty(StandaloneCollectorConfig.SINK_FILE, tempDir.toString());
        properties.setProperty(StandaloneCollectorConfig.SINK_FILE_MAX_BYTES, "0");
        properties.setProperty(StandaloneCollectorConfig.SINK_FILE_MAX_HISTORY, "0");
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE_MAX_PAYLOAD_BYTES, "-1");
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE_OVERSIZED_PAYLOAD_DECISION, "ACCEPT");

        CollectorConfigValidation validation = StandaloneCollectorConfig.validateProperties(properties);

        assertFalse(validation.isValid());
        assertContainsError(validation, StandaloneCollectorConfig.SOURCE_ID + " must use namespace:value format");
        assertContainsError(validation, StandaloneCollectorConfig.TCP_PORT + " must be between 0 and 65535");
        assertContainsError(validation, StandaloneCollectorConfig.TCP_BOSS_THREADS + " must be positive");
        assertContainsError(validation, StandaloneCollectorConfig.TCP_WORKER_THREADS + " must be positive");
        assertContainsError(validation, StandaloneCollectorConfig.SINK_TYPE + " must be logging, file, or in-memory");
        assertContainsError(validation, StandaloneCollectorConfig.SINK_FILE + " must point to a file");
        assertContainsError(validation, StandaloneCollectorConfig.SINK_FILE_MAX_BYTES + " must be positive");
        assertContainsError(validation, StandaloneCollectorConfig.SINK_FILE_MAX_HISTORY + " must be positive");
        assertContainsError(validation, StandaloneCollectorConfig.BACKPRESSURE_MAX_PAYLOAD_BYTES + " must be between");
        assertContainsError(
                validation,
                StandaloneCollectorConfig.BACKPRESSURE_OVERSIZED_PAYLOAD_DECISION + " must be RETRY_LATER or DROP");
    }

    @Test
    void parsesCustomBackpressurePayloadPolicy() {
        Properties properties = baseProperties(0, "in-memory");
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE_MAX_PAYLOAD_BYTES, "64");
        properties.setProperty(
                StandaloneCollectorConfig.BACKPRESSURE_OVERSIZED_PAYLOAD_DECISION,
                BackpressureDecision.RETRY_LATER.name());

        StandaloneCollectorAppConfig appConfig = StandaloneCollectorConfig.appConfigFromProperties(properties);

        assertEquals(BackpressureDecision.ACCEPT, appConfig.backpressureDecision());
        assertEquals(64, appConfig.backpressureMaxPayloadBytes());
        assertEquals(BackpressureDecision.RETRY_LATER, appConfig.oversizedPayloadDecision());
    }

    @Test
    void parsesCustomFileSinkRotationPolicy() {
        Path output = tempDir.resolve("custom-records.ndjson");
        Properties properties = baseProperties(0, "file");
        properties.setProperty(StandaloneCollectorConfig.SINK_FILE, output.toString());
        properties.setProperty(StandaloneCollectorConfig.SINK_FILE_MAX_BYTES, "4096");
        properties.setProperty(StandaloneCollectorConfig.SINK_FILE_MAX_HISTORY, "3");

        StandaloneCollectorAppConfig appConfig = StandaloneCollectorConfig.appConfigFromProperties(properties);

        assertEquals(output, appConfig.sinkFile());
        assertEquals(new FileSinkRotationConfig(4096, 3), appConfig.fileSinkRotation());
    }

    @Test
    void reportsDuplicateSourcesAndListeners() {
        Properties properties = multiSourceProperties();
        properties.setProperty(StandaloneCollectorConfig.SOURCES, "station-a,station-b,station-b");
        properties.setProperty(
                StandaloneCollectorConfig.SOURCE_PREFIX + "station-b" + StandaloneCollectorConfig.SOURCE_ID_SUFFIX,
                "iec104:station-a");
        properties.setProperty(StandaloneCollectorConfig.TCP_LISTENERS, "north,south,north");
        properties.setProperty(
                StandaloneCollectorConfig.TCP_LISTENER_PREFIX
                        + "south"
                        + StandaloneCollectorConfig.TCP_LISTENER_PORT_SUFFIX,
                "2404");
        properties.setProperty(
                StandaloneCollectorConfig.TCP_LISTENER_PREFIX
                        + "north"
                        + StandaloneCollectorConfig.TCP_LISTENER_PORT_SUFFIX,
                "2404");

        CollectorConfigValidation validation = StandaloneCollectorConfig.validateProperties(properties);

        assertFalse(validation.isValid());
        assertContainsError(validation, StandaloneCollectorConfig.SOURCES + " contains duplicate name: station-b");
        assertContainsError(validation, StandaloneCollectorConfig.TCP_LISTENERS + " contains duplicate name: north");
        assertContainsError(validation, "duplicate source id iec104:station-a");
        assertContainsError(validation, "duplicate TCP listener endpoint 127.0.0.1:2404");
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

    private static Properties multiSourceProperties() {
        Properties properties = new Properties();
        properties.setProperty(StandaloneCollectorConfig.SOURCES, "station-a,station-b");
        properties.setProperty(
                StandaloneCollectorConfig.SOURCE_PREFIX + "station-a" + StandaloneCollectorConfig.SOURCE_ID_SUFFIX,
                "iec104:station-a");
        properties.setProperty(
                StandaloneCollectorConfig.SOURCE_PREFIX + "station-b" + StandaloneCollectorConfig.SOURCE_ID_SUFFIX,
                "iec104:station-b");
        properties.setProperty(StandaloneCollectorConfig.TCP_LISTENERS, "north,south");
        setListener(properties, "north", "station-a", 0);
        setListener(properties, "south", "station-b", 0);
        properties.setProperty(StandaloneCollectorConfig.SINK_TYPE, "in-memory");
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE, BackpressureDecision.ACCEPT.name());
        return properties;
    }

    private static StandaloneCollectorAppConfig duplicatePortAppConfig(int port) {
        return new StandaloneCollectorAppConfig(
                List.of(
                        new CollectorSourceConfig("station-a", SourceId.of("iec104", "station-a")),
                        new CollectorSourceConfig("station-b", SourceId.of("iec104", "station-b"))),
                List.of(
                        new TcpListenerConfig(
                                "north",
                                new TcpNettyServerConfig("127.0.0.1", port, 1, 1),
                                "station-a",
                                SourceId.of("iec104", "station-a")),
                        new TcpListenerConfig(
                                "south",
                                new TcpNettyServerConfig("127.0.0.1", port, 1, 1),
                                "station-b",
                                SourceId.of("iec104", "station-b"))),
                BackpressureDecision.ACCEPT,
                0,
                BackpressureDecision.DROP,
                SinkType.IN_MEMORY,
                null,
                FileSinkRotationConfig.defaults(),
                false);
    }

    private static void setListener(Properties properties, String name, String sourceName, int port) {
        String prefix = StandaloneCollectorConfig.TCP_LISTENER_PREFIX + name;
        properties.setProperty(prefix + StandaloneCollectorConfig.TCP_LISTENER_HOST_SUFFIX, "127.0.0.1");
        properties.setProperty(prefix + StandaloneCollectorConfig.TCP_LISTENER_PORT_SUFFIX, Integer.toString(port));
        properties.setProperty(prefix + StandaloneCollectorConfig.TCP_LISTENER_SOURCE_SUFFIX, sourceName);
    }

    private static void writeFrame(StandaloneCollector collector, byte[] frame) throws IOException {
        writeFrame(collector.localAddress(), frame);
    }

    private static void writeFrame(InetSocketAddress address, byte[] frame) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(address);
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

    private static CollectorRuntimeMetrics awaitRetryLaterBackpressure(
            StandaloneCollector collector,
            int expected) throws InterruptedException {
        return awaitBackpressure(
                collector,
                expected,
                metrics -> metrics.backpressureRetryLaterCount());
    }

    private static CollectorRuntimeMetrics awaitDropBackpressure(
            StandaloneCollector collector,
            int expected) throws InterruptedException {
        return awaitBackpressure(
                collector,
                expected,
                metrics -> metrics.backpressureDropCount());
    }

    private static CollectorRuntimeMetrics awaitBackpressure(
            StandaloneCollector collector,
            int expected,
            java.util.function.ToLongFunction<CollectorRuntimeMetrics> counter) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            CollectorRuntimeMetrics metrics = collector.statusSnapshot().metrics();
            if (counter.applyAsLong(metrics) >= expected) {
                return metrics;
            }
            Thread.sleep(10);
        }
        CollectorRuntimeMetrics metrics = collector.statusSnapshot().metrics();
        assertEquals(expected, counter.applyAsLong(metrics));
        return metrics;
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

    private static void assertContainsError(CollectorConfigValidation validation, String expected) {
        assertTrue(
                validation.errors().stream().anyMatch(error -> error.contains(expected)),
                () -> "Expected validation error containing '" + expected + "' but got " + validation.errors());
    }

    private static ParsedRecord<String> record(String value) {
        return new ParsedRecord<>(
                SourceId.of("test", "source"),
                "test",
                "record",
                value,
                value.getBytes(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of());
    }

    private static void assertFileContains(Path output, String expected) throws Exception {
        assertTrue(Files.exists(output), () -> "Expected file to exist: " + output);
        String content = Files.readString(output);
        assertTrue(content.contains(expected), () -> "Expected " + output + " to contain " + expected);
    }

    private static Path rotatedPath(Path output, int index) {
        return output.resolveSibling(output.getFileName() + "." + index);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) (values[i] & 0xFF);
        }
        return bytes;
    }
}
