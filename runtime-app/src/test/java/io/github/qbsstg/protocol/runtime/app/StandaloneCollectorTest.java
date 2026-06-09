package io.github.qbsstg.protocol.runtime.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.iec101.Iec101Frame;
import io.github.qbsstg.protocol.iec103.Iec103Frame;
import io.github.qbsstg.protocol.modbus.ModbusTcpAdu;
import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.github.qbsstg.protocol.runtime.ingress.http.HttpIngressResponseMode;
import io.github.qbsstg.protocol.runtime.ingress.http.HttpIngressSourceIdMode;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaCommitMode;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaIngressResult;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaIngressStatus;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaRecordReceiver;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaRecordSource;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaSourceIdMode;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpConnectionAttributes;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServerConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            List<ParsedRecord<Object>> records = awaitRecords(sink, 1);
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
            assertEquals("iec104", running.sources().get(0).protocol());
            assertEquals(1, running.tcpListeners().size());
            assertEquals("default", running.tcpListeners().get(0).name());
            assertEquals("iec104", running.tcpListeners().get(0).protocol());
            assertEquals("127.0.0.1", running.tcpListeners().get(0).configuredHost());
            assertEquals(0, running.tcpListeners().get(0).configuredPort());
            assertTrue(running.tcpListeners().get(0).running());
            assertNotNull(running.tcpListeners().get(0).boundPort());
            assertEquals(1, running.metrics().parsedRecordCount());
            assertEquals(0, running.metrics().parseFailureCount());
            assertNull(running.metrics().lastParseFailureMessage());

            String status = CollectorStatusFormatter.format(running);
            assertTrue(status.contains("state=RUNNING"));
            assertTrue(status.contains("listeners=1"));
            assertTrue(status.contains("parsedRecords=1"));
            assertTrue(status.contains("parseFailures=0"));
            assertTrue(status.contains("sink=in-memory"));
            assertTrue(status.contains("tcpListeners=[default@127.0.0.1:0->"));
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

            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
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

            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
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
            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
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
        assertTrue(appConfig.httpListeners().isEmpty());
        assertEquals("default", appConfig.sources().get(0).name());
        assertEquals("iec104:station-1", appConfig.sources().get(0).sourceId().qualifiedValue());
        assertEquals(RuntimeProtocol.IEC104, appConfig.sources().get(0).protocol());
        assertEquals("default", appConfig.tcpListeners().get(0).name());
        assertEquals("default", appConfig.tcpListeners().get(0).sourceName());
        assertEquals(RuntimeProtocol.IEC104, appConfig.tcpListeners().get(0).protocol());
        assertEquals(2404, appConfig.tcpListeners().get(0).tcp().port());
    }

    @Test
    void parsesHttpOnlyAppConfigurationWithoutDefaultTcpListener() {
        Properties properties = httpProperties(8080, "logging");
        properties.setProperty(
                StandaloneCollectorConfig.HTTP_LISTENER_PREFIX
                        + "http-main"
                        + StandaloneCollectorConfig.HTTP_LISTENER_MAX_PAYLOAD_BYTES_SUFFIX,
                "65536");
        properties.setProperty(
                StandaloneCollectorConfig.HTTP_LISTENER_PREFIX
                        + "http-main"
                        + StandaloneCollectorConfig.HTTP_LISTENER_WORKER_THREADS_SUFFIX,
                "2");

        CollectorConfigValidation validation = StandaloneCollectorConfig.validateProperties(properties);
        StandaloneCollectorAppConfig appConfig = StandaloneCollectorConfig.appConfigFromProperties(properties);

        assertTrue(validation.isValid());
        assertEquals(1, appConfig.sources().size());
        assertTrue(appConfig.tcpListeners().isEmpty());
        assertEquals(1, appConfig.httpListeners().size());
        HttpListenerConfig listener = appConfig.httpListeners().get(0);
        assertEquals("http-main", listener.name());
        assertEquals("default", listener.sourceName());
        assertEquals("iec104:station-1", listener.sourceId().qualifiedValue());
        assertEquals(RuntimeProtocol.IEC104, listener.protocol());
        assertEquals("127.0.0.1", listener.http().host());
        assertEquals(8080, listener.http().port());
        assertEquals("/ingress", listener.http().path());
        assertEquals(HttpIngressSourceIdMode.CONFIGURED, listener.http().sourceIdMode());
        assertEquals(HttpIngressResponseMode.ACK_ON_ACCEPT, listener.http().responseMode());
        assertEquals(65536, listener.http().maxPayloadBytes());
        assertEquals(2, listener.http().workerThreads());
        assertThrows(IllegalArgumentException.class, appConfig::singleCollectorConfig);
    }

    @Test
    void startsHttpCollectorAndRoutesIec104PostsToInMemorySink() throws Exception {
        try (StandaloneCollector collector = StandaloneCollector.create(
                StandaloneCollectorConfig.appConfigFromProperties(httpProperties(0, "in-memory")))) {
            CollectorStatusSnapshot configured = collector.statusSnapshot();
            assertEquals(CollectorLifecycleState.CONFIGURED, configured.state());
            assertTrue(configured.tcpListeners().isEmpty());
            assertEquals(1, configured.httpListeners().size());
            assertFalse(configured.httpListeners().get(0).running());

            collector.start();

            HttpResponse<String> response = postHttp(collector, "/ingress?trace=1", SINGLE_POINT_FRAME);

            assertEquals(202, response.statusCode());
            assertEquals("{\"status\":\"accepted\"}", response.body());
            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            List<ParsedRecord<Object>> records = awaitRecords(sink, 1);
            assertEquals("iec104:station-1", records.get(0).sourceId().qualifiedValue());
            assertEquals("iec104", records.get(0).protocol());
            assertEquals("I_FORMAT", records.get(0).recordType());
            assertArrayEquals(SINGLE_POINT_FRAME, records.get(0).rawPayload());
            assertTrue(sink.failures().isEmpty());

            CollectorStatusSnapshot running = collector.statusSnapshot();
            assertTrue(collector.isRunning());
            assertEquals(CollectorLifecycleState.RUNNING, running.state());
            assertTrue(running.tcpListeners().isEmpty());
            assertEquals(1, running.httpListeners().size());
            HttpListenerStatus listener = running.httpListeners().get(0);
            assertEquals("http-main", listener.name());
            assertEquals("iec104", listener.protocol());
            assertEquals("/ingress", listener.path());
            assertEquals(HttpIngressSourceIdMode.CONFIGURED, listener.sourceIdMode());
            assertTrue(listener.running());
            assertNotNull(listener.boundPort());
            assertEquals(0, running.activeConnectionCount());
            assertEquals(1, running.metrics().parsedRecordCount());
            assertEquals(0, running.metrics().parseFailureCount());

            String status = CollectorStatusFormatter.format(running);
            assertTrue(status.contains("listeners=1"));
            assertTrue(status.contains("tcpListeners=[]"));
            assertTrue(status.contains("httpListeners=[http-main@127.0.0.1:0/ingress->"));
            assertTrue(status.contains("/sourceIdMode=CONFIGURED/protocol=iec104"));
        }
    }

    @Test
    void routesHeaderSourceHttpPostsWithConfiguredProtocol() throws Exception {
        Properties properties = httpProperties(0, "in-memory");
        String prefix = StandaloneCollectorConfig.HTTP_LISTENER_PREFIX + "http-main";
        properties.setProperty(
                prefix + StandaloneCollectorConfig.HTTP_LISTENER_SOURCE_ID_MODE_SUFFIX,
                "header");
        properties.setProperty(
                prefix + StandaloneCollectorConfig.HTTP_LISTENER_SOURCE_ID_HEADER_SUFFIX,
                "X-Protocol-Source");

        try (StandaloneCollector collector = StandaloneCollector.create(
                StandaloneCollectorConfig.appConfigFromProperties(properties))) {
            collector.start();

            HttpResponse<String> response = postHttp(
                    collector,
                    "/ingress",
                    SINGLE_POINT_FRAME,
                    Map.of("X-Protocol-Source", "iec104:station-header"));

            assertEquals(202, response.statusCode());
            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            ParsedRecord<Object> record = awaitRecords(sink, 1).get(0);
            assertEquals("iec104:station-header", record.sourceId().qualifiedValue());
            assertEquals("iec104", record.protocol());
            assertEquals(HttpIngressSourceIdMode.HEADER, collector.statusSnapshot().httpListeners().get(0).sourceIdMode());
        }
    }

    @Test
    void routesMalformedHttpPayloadsToFailureSink() throws Exception {
        try (StandaloneCollector collector = StandaloneCollector.create(
                StandaloneCollectorConfig.appConfigFromProperties(httpProperties(0, "in-memory")))) {
            collector.start();

            byte[] malformed = bytes(0x68, 0x03, 0x00, 0x00, 0x00);
            HttpResponse<String> response = postHttp(collector, "/ingress", malformed);

            assertEquals(202, response.statusCode());
            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            List<ParseFailure> failures = awaitFailures(sink, 1);
            assertTrue(sink.records().isEmpty());
            assertEquals("iec104:station-1", failures.get(0).sourceId().qualifiedValue());
            assertTrue(failures.get(0).message().contains("Invalid IEC104 APDU length"));
            assertEquals(1, collector.statusSnapshot().metrics().parseFailureCount());
        }
    }

    @Test
    void retryLaterHttpBackpressureReturnsUnavailableWithoutParsing() throws Exception {
        Properties properties = httpProperties(0, "in-memory");
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE, "RETRY_LATER");

        try (StandaloneCollector collector = StandaloneCollector.create(
                StandaloneCollectorConfig.appConfigFromProperties(properties))) {
            collector.start();

            HttpResponse<String> response = postHttp(collector, "/ingress", SINGLE_POINT_FRAME);

            assertEquals(503, response.statusCode());
            assertEquals("1", response.headers().firstValue("Retry-After").orElse(""));
            assertEquals("{\"status\":\"retry_later\"}", response.body());
            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            assertTrue(sink.records().isEmpty());
            assertTrue(sink.failures().isEmpty());
            CollectorRuntimeMetrics metrics = awaitRetryLaterBackpressure(collector, 1);
            assertEquals(0, metrics.parsedRecordCount());
            assertEquals(0, metrics.parseFailureCount());
            assertEquals("iec104:station-1", metrics.lastBackpressureSourceId());
        }
    }

    @Test
    void httpPayloadLimitRejectsBeforeRuntimeParsing() throws Exception {
        Properties properties = httpProperties(0, "in-memory");
        properties.setProperty(
                StandaloneCollectorConfig.HTTP_LISTENER_PREFIX
                        + "http-main"
                        + StandaloneCollectorConfig.HTTP_LISTENER_MAX_PAYLOAD_BYTES_SUFFIX,
                "1");

        try (StandaloneCollector collector = StandaloneCollector.create(
                StandaloneCollectorConfig.appConfigFromProperties(properties))) {
            collector.start();

            HttpResponse<String> response = postHttp(collector, "/ingress", SINGLE_POINT_FRAME);

            assertEquals(413, response.statusCode());
            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            assertTrue(sink.records().isEmpty());
            assertTrue(sink.failures().isEmpty());
            CollectorRuntimeMetrics metrics = collector.statusSnapshot().metrics();
            assertEquals(0, metrics.parsedRecordCount());
            assertEquals(0, metrics.parseFailureCount());
            assertEquals(0, metrics.backpressureRetryLaterCount());
            assertEquals(0, metrics.backpressureDropCount());
        }
    }

    @Test
    void parsesKafkaOnlyAppConfigurationWithoutDefaultTcpOrHttpListener() {
        Properties properties = kafkaProperties("in-memory");

        CollectorConfigValidation validation = StandaloneCollectorConfig.validateProperties(properties);
        StandaloneCollectorAppConfig appConfig = StandaloneCollectorConfig.appConfigFromProperties(properties);

        assertTrue(validation.isValid());
        assertEquals(1, appConfig.sources().size());
        assertTrue(appConfig.tcpListeners().isEmpty());
        assertTrue(appConfig.httpListeners().isEmpty());
        assertEquals(1, appConfig.kafkaConsumers().size());
        KafkaConsumerConfig consumer = appConfig.kafkaConsumers().get(0);
        assertEquals("kafka-main", consumer.name());
        assertEquals("default", consumer.sourceName());
        assertEquals("iec104:station-1", consumer.sourceId().qualifiedValue());
        assertEquals(RuntimeProtocol.IEC104, consumer.protocol());
        assertEquals("localhost:9092", consumer.kafka().bootstrapServers());
        assertEquals("protocol-runtime", consumer.kafka().groupId());
        assertEquals(List.of("iec104-raw"), consumer.kafka().topics());
        assertNull(consumer.kafka().topicPattern());
        assertEquals(KafkaSourceIdMode.CONFIGURED, consumer.kafka().sourceIdMode());
        assertEquals(KafkaCommitMode.MANUAL, consumer.kafka().commitMode());
        assertEquals("latest", consumer.kafka().autoOffsetReset());
        assertEquals(100, consumer.kafka().maxPollRecords());
        assertEquals(1000, consumer.kafka().pollTimeoutMillis());
        assertThrows(IllegalArgumentException.class, appConfig::singleCollectorConfig);
    }

    @Test
    void startsKafkaCollectorAndRoutesIec104RecordsToInMemorySink() throws Exception {
        FakeKafkaRecordSource source = new FakeKafkaRecordSource();
        StandaloneCollectorAppConfig appConfig = StandaloneCollectorConfig.appConfigFromProperties(
                kafkaProperties("in-memory"));

        try (StandaloneCollector collector = kafkaCollector(appConfig, source)) {
            CollectorStatusSnapshot configured = collector.statusSnapshot();
            assertEquals(CollectorLifecycleState.CONFIGURED, configured.state());
            assertTrue(configured.tcpListeners().isEmpty());
            assertTrue(configured.httpListeners().isEmpty());
            assertEquals(1, configured.kafkaConsumers().size());
            assertFalse(configured.kafkaConsumers().get(0).running());

            collector.start();

            KafkaIngressResult result = source.emit(kafkaRecord(SINGLE_POINT_FRAME));

            assertEquals(KafkaIngressStatus.ACCEPTED, result.status());
            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            List<ParsedRecord<Object>> records = awaitRecords(sink, 1);
            assertTrue(collector.isRunning());
            assertEquals("iec104:station-1", records.get(0).sourceId().qualifiedValue());
            assertEquals("iec104", records.get(0).protocol());
            assertEquals("I_FORMAT", records.get(0).recordType());
            assertArrayEquals(SINGLE_POINT_FRAME, records.get(0).rawPayload());
            assertTrue(sink.failures().isEmpty());

            CollectorStatusSnapshot running = collector.statusSnapshot();
            assertEquals(CollectorLifecycleState.RUNNING, running.state());
            assertEquals(1, running.kafkaConsumers().size());
            KafkaConsumerStatus status = running.kafkaConsumers().get(0);
            assertEquals("kafka-main", status.name());
            assertEquals("protocol-runtime", status.groupId());
            assertEquals(List.of("iec104-raw"), status.topics());
            assertEquals(KafkaSourceIdMode.CONFIGURED, status.sourceIdMode());
            assertTrue(status.running());
            assertEquals(0, running.activeConnectionCount());
            assertEquals(1, running.metrics().parsedRecordCount());
            assertEquals(0, running.metrics().parseFailureCount());

            String formatted = CollectorStatusFormatter.format(running);
            assertTrue(formatted.contains("listeners=1"));
            assertTrue(formatted.contains("tcpListeners=[]"));
            assertTrue(formatted.contains("httpListeners=[]"));
            assertTrue(formatted.contains("kafkaConsumers=[kafka-main@localhost:9092/group=protocol-runtime"));
            assertTrue(formatted.contains("/sourceIdMode=CONFIGURED/protocol=iec104"));
        }
    }

    @Test
    void routesMalformedKafkaPayloadsToFailureSink() throws Exception {
        FakeKafkaRecordSource source = new FakeKafkaRecordSource();
        StandaloneCollectorAppConfig appConfig = StandaloneCollectorConfig.appConfigFromProperties(
                kafkaProperties("in-memory"));

        try (StandaloneCollector collector = kafkaCollector(appConfig, source)) {
            collector.start();

            byte[] malformed = bytes(0x68, 0x03, 0x00, 0x00, 0x00);
            KafkaIngressResult result = source.emit(kafkaRecord(malformed));

            assertEquals(KafkaIngressStatus.ACCEPTED, result.status());
            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            List<ParseFailure> failures = awaitFailures(sink, 1);
            assertTrue(sink.records().isEmpty());
            assertEquals("iec104:station-1", failures.get(0).sourceId().qualifiedValue());
            assertTrue(failures.get(0).message().contains("Invalid IEC104 APDU length"));
            assertEquals("iec104-raw", failures.get(0).attributes().get("kafka.topic"));
            assertEquals(1, collector.statusSnapshot().metrics().parseFailureCount());
        }
    }

    @Test
    void retryLaterKafkaBackpressurePreventsParsing() throws Exception {
        FakeKafkaRecordSource source = new FakeKafkaRecordSource();
        Properties properties = kafkaProperties("in-memory");
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE, "RETRY_LATER");
        StandaloneCollectorAppConfig appConfig = StandaloneCollectorConfig.appConfigFromProperties(properties);

        try (StandaloneCollector collector = kafkaCollector(appConfig, source)) {
            collector.start();

            KafkaIngressResult result = source.emit(kafkaRecord(SINGLE_POINT_FRAME));

            assertEquals(KafkaIngressStatus.RETRY_LATER, result.status());
            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            assertTrue(sink.records().isEmpty());
            assertTrue(sink.failures().isEmpty());
            CollectorRuntimeMetrics metrics = awaitRetryLaterBackpressure(collector, 1);
            assertEquals(0, metrics.parsedRecordCount());
            assertEquals(0, metrics.parseFailureCount());
            assertEquals("iec104:station-1", metrics.lastBackpressureSourceId());
            assertEquals(SINGLE_POINT_FRAME.length, metrics.lastBackpressurePayloadSize());
        }
    }

    @Test
    void validatesKafkaConsumerConfiguration() {
        Properties properties = kafkaProperties("in-memory");
        String prefix = StandaloneCollectorConfig.KAFKA_CONSUMER_PREFIX + "kafka-main";
        properties.setProperty(StandaloneCollectorConfig.KAFKA_CONSUMERS, "kafka-main,kafka-main");
        properties.remove(prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_BOOTSTRAP_SERVERS_SUFFIX);
        properties.setProperty(prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_SOURCE_SUFFIX, "missing");
        properties.setProperty(prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_SOURCE_ID_MODE_SUFFIX, "cookie");
        properties.setProperty(prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_COMMIT_MODE_SUFFIX, "after-coffee");
        properties.setProperty(prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_MAX_POLL_RECORDS_SUFFIX, "0");
        properties.setProperty(prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_POLL_TIMEOUT_MILLIS_SUFFIX, "0");

        CollectorConfigValidation validation = StandaloneCollectorConfig.validateProperties(properties);

        assertFalse(validation.isValid());
        assertContainsError(validation, StandaloneCollectorConfig.KAFKA_CONSUMERS
                + " contains duplicate name: kafka-main");
        assertContainsError(validation, prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_BOOTSTRAP_SERVERS_SUFFIX
                + " is required");
        assertContainsError(validation, prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_SOURCE_SUFFIX
                + " references unknown source: missing");
        assertContainsError(validation, prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_SOURCE_ID_MODE_SUFFIX
                + " must be CONFIGURED, HEADER, TOPIC, or KEY");
        assertContainsError(validation, prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_COMMIT_MODE_SUFFIX
                + " must be MANUAL, AFTER_ACCEPT, AFTER_PARSE_SUCCESS, or NEVER");
        assertContainsError(validation, prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_MAX_POLL_RECORDS_SUFFIX
                + " must be positive");
        assertContainsError(validation, prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_POLL_TIMEOUT_MILLIS_SUFFIX
                + " must be positive");
    }

    @Test
    void httpPortConflictFailsFast() throws Exception {
        try (StandaloneCollector bound = StandaloneCollector.create(
                StandaloneCollectorConfig.appConfigFromProperties(httpProperties(0, "in-memory")))) {
            bound.start();
            Properties properties = httpProperties(bound.httpPorts().get(0), "in-memory");
            try (StandaloneCollector collector = StandaloneCollector.create(
                    StandaloneCollectorConfig.appConfigFromProperties(properties))) {

                assertThrows(Exception.class, collector::start);

                CollectorStatusSnapshot failed = collector.statusSnapshot();
                assertEquals(CollectorLifecycleState.FAILED, failed.state());
                assertTrue(failed.startupFailureReason().contains("HTTP listener http-main"));
                assertNotNull(failed.lastExceptionType());
                assertNotNull(failed.lastExceptionMessage());
                assertTrue(failed.tcpListeners().isEmpty());
                assertEquals(1, failed.httpListeners().size());
                assertFalse(failed.httpListeners().get(0).running());
                assertNull(failed.startedAt());
                assertNotNull(failed.stoppedAt());
            }
        }
    }

    @Test
    void validatesHttpListenerConfiguration() {
        Properties properties = httpProperties(0, "in-memory");
        String prefix = StandaloneCollectorConfig.HTTP_LISTENER_PREFIX + "http-main";
        properties.setProperty(StandaloneCollectorConfig.HTTP_LISTENERS, "http-main,http-main");
        properties.setProperty(prefix + StandaloneCollectorConfig.HTTP_LISTENER_PORT_SUFFIX, "70000");
        properties.setProperty(prefix + StandaloneCollectorConfig.HTTP_LISTENER_PATH_SUFFIX, "ingress/{sourceId}");
        properties.setProperty(prefix + StandaloneCollectorConfig.HTTP_LISTENER_SOURCE_ID_MODE_SUFFIX, "path");
        properties.setProperty(prefix + StandaloneCollectorConfig.HTTP_LISTENER_RESPONSE_MODE_SUFFIX, "body");
        properties.setProperty(prefix + StandaloneCollectorConfig.HTTP_LISTENER_WORKER_THREADS_SUFFIX, "0");

        CollectorConfigValidation validation = StandaloneCollectorConfig.validateProperties(properties);

        assertFalse(validation.isValid());
        assertContainsError(validation, StandaloneCollectorConfig.HTTP_LISTENERS + " contains duplicate name: http-main");
        assertContainsError(validation, prefix + StandaloneCollectorConfig.HTTP_LISTENER_PORT_SUFFIX + " must be between");
        assertContainsError(validation, prefix + StandaloneCollectorConfig.HTTP_LISTENER_RESPONSE_MODE_SUFFIX
                + " must be ACK_ON_ACCEPT or NO_BODY");
        assertContainsError(validation, prefix + StandaloneCollectorConfig.HTTP_LISTENER_WORKER_THREADS_SUFFIX
                + " must be positive");
    }

    @Test
    void parsesProtocolSelectionForLegacyAndNamedSources() {
        Properties legacy = baseProperties(2404, "logging");
        legacy.setProperty(StandaloneCollectorConfig.PROTOCOL, "modbus");
        legacy.setProperty(StandaloneCollectorConfig.SOURCE_ID, "modbus:station-1");

        StandaloneCollectorAppConfig legacyConfig = StandaloneCollectorConfig.appConfigFromProperties(legacy);

        assertEquals(RuntimeProtocol.MODBUS, legacyConfig.sources().get(0).protocol());
        assertEquals(RuntimeProtocol.MODBUS, legacyConfig.tcpListeners().get(0).protocol());
        assertEquals(RuntimeProtocol.MODBUS, legacyConfig.singleCollectorConfig().protocol());

        Properties named = multiSourceProperties();
        named.setProperty(
                StandaloneCollectorConfig.SOURCE_PREFIX
                        + "station-b"
                        + StandaloneCollectorConfig.SOURCE_PROTOCOL_SUFFIX,
                "iec103");

        StandaloneCollectorAppConfig namedConfig = StandaloneCollectorConfig.appConfigFromProperties(named);

        assertEquals(RuntimeProtocol.IEC104, namedConfig.sources().get(0).protocol());
        assertEquals(RuntimeProtocol.IEC104, namedConfig.tcpListeners().get(0).protocol());
        assertEquals(RuntimeProtocol.IEC103, namedConfig.sources().get(1).protocol());
        assertEquals(RuntimeProtocol.IEC103, namedConfig.tcpListeners().get(1).protocol());
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

            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            List<ParsedRecord<Object>> records = awaitRecords(sink, 2);
            Set<String> sourceIds = new HashSet<>();
            for (ParsedRecord<Object> record : records) {
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
    void reportsUnsupportedProtocolConfiguration() {
        Properties properties = baseProperties(0, "in-memory");
        properties.setProperty(StandaloneCollectorConfig.PROTOCOL, "iec102");

        CollectorConfigValidation validation = StandaloneCollectorConfig.validateProperties(properties);

        assertFalse(validation.isValid());
        assertContainsError(validation, StandaloneCollectorConfig.PROTOCOL + " must be iec104, iec101, iec103, or modbus");

        properties = multiSourceProperties();
        properties.setProperty(
                StandaloneCollectorConfig.SOURCE_PREFIX
                        + "station-a"
                        + StandaloneCollectorConfig.SOURCE_PROTOCOL_SUFFIX,
                "mqtt");

        validation = StandaloneCollectorConfig.validateProperties(properties);

        assertFalse(validation.isValid());
        assertContainsError(
                validation,
                StandaloneCollectorConfig.SOURCE_PREFIX
                        + "station-a"
                        + StandaloneCollectorConfig.SOURCE_PROTOCOL_SUFFIX
                        + " must be iec104, iec101, iec103, or modbus");
    }

    @Test
    void routesProtocolSelectedTcpFramesToInMemorySink() throws Exception {
        assertProtocolSelectedFrame(RuntimeProtocol.IEC101, "iec101:station-1", variableIec101SinglePointFrame(), Iec101Frame.class);
        assertProtocolSelectedFrame(RuntimeProtocol.IEC103, "iec103:station-1", variableIec103ProtectionEventFrame(), Iec103Frame.class);
        assertProtocolSelectedFrame(RuntimeProtocol.MODBUS, "modbus:station-1", modbusReadHoldingRegistersRequest(), ModbusTcpAdu.class);
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

    private static Properties httpProperties(int port, String sinkType) {
        Properties properties = new Properties();
        properties.setProperty(StandaloneCollectorConfig.SOURCE_ID, "iec104:station-1");
        properties.setProperty(StandaloneCollectorConfig.SINK_TYPE, sinkType);
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE, BackpressureDecision.ACCEPT.name());
        properties.setProperty(StandaloneCollectorConfig.HTTP_LISTENERS, "http-main");
        setHttpListener(properties, "http-main", "default", port);
        return properties;
    }

    private static Properties kafkaProperties(String sinkType) {
        Properties properties = new Properties();
        properties.setProperty(StandaloneCollectorConfig.SOURCE_ID, "iec104:station-1");
        properties.setProperty(StandaloneCollectorConfig.SINK_TYPE, sinkType);
        properties.setProperty(StandaloneCollectorConfig.BACKPRESSURE, BackpressureDecision.ACCEPT.name());
        properties.setProperty(StandaloneCollectorConfig.KAFKA_CONSUMERS, "kafka-main");
        setKafkaConsumer(properties, "kafka-main", "default");
        return properties;
    }

    private static Properties protocolProperties(RuntimeProtocol protocol, String sourceId) {
        Properties properties = baseProperties(0, "in-memory");
        properties.setProperty(StandaloneCollectorConfig.PROTOCOL, protocol.configValue());
        properties.setProperty(StandaloneCollectorConfig.SOURCE_ID, sourceId);
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
                List.of(),
                List.of(),
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

    private static void setHttpListener(Properties properties, String name, String sourceName, int port) {
        String prefix = StandaloneCollectorConfig.HTTP_LISTENER_PREFIX + name;
        properties.setProperty(prefix + StandaloneCollectorConfig.HTTP_LISTENER_HOST_SUFFIX, "127.0.0.1");
        properties.setProperty(prefix + StandaloneCollectorConfig.HTTP_LISTENER_PORT_SUFFIX, Integer.toString(port));
        properties.setProperty(prefix + StandaloneCollectorConfig.HTTP_LISTENER_PATH_SUFFIX, "/ingress");
        properties.setProperty(prefix + StandaloneCollectorConfig.HTTP_LISTENER_SOURCE_SUFFIX, sourceName);
    }

    private static void setKafkaConsumer(Properties properties, String name, String sourceName) {
        String prefix = StandaloneCollectorConfig.KAFKA_CONSUMER_PREFIX + name;
        properties.setProperty(
                prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_BOOTSTRAP_SERVERS_SUFFIX,
                "localhost:9092");
        properties.setProperty(
                prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_GROUP_ID_SUFFIX,
                "protocol-runtime");
        properties.setProperty(
                prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_TOPICS_SUFFIX,
                "iec104-raw");
        properties.setProperty(
                prefix + StandaloneCollectorConfig.KAFKA_CONSUMER_SOURCE_SUFFIX,
                sourceName);
    }

    private static StandaloneCollector kafkaCollector(
            StandaloneCollectorAppConfig appConfig,
            FakeKafkaRecordSource source) {
        return new StandaloneCollector(
                appConfig,
                RuntimeSinks.from(appConfig),
                Clock.systemUTC(),
                ignored -> source);
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

    private static HttpResponse<String> postHttp(
            StandaloneCollector collector,
            String path,
            byte[] payload) throws IOException, InterruptedException {
        return postHttp(collector, path, payload, Map.of());
    }

    private static HttpResponse<String> postHttp(
            StandaloneCollector collector,
            String path,
            byte[] payload,
            Map<String, String> headers) throws IOException, InterruptedException {
        InetSocketAddress address = collector.httpLocalAddresses().get(0);
        URI uri = URI.create("http://" + address.getHostString() + ":" + address.getPort() + path);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
        headers.forEach(request::header);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static ConsumerRecord<byte[], byte[]> kafkaRecord(byte[] value) {
        return new ConsumerRecord<>(
                "iec104-raw",
                0,
                42,
                "device-key".getBytes(StandardCharsets.UTF_8),
                value);
    }

    private static List<ParsedRecord<Object>> awaitRecords(
            InMemoryRuntimeSink<Object> sink,
            int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            List<ParsedRecord<Object>> records = sink.records();
            if (records.size() >= expected) {
                return records;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, sink.records().size());
        return sink.records();
    }

    private static List<ParseFailure> awaitFailures(
            InMemoryRuntimeSink<Object> sink,
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

    private static void assertProtocolSelectedFrame(
            RuntimeProtocol protocol,
            String sourceId,
            byte[] frame,
            Class<?> expectedValueType) throws Exception {
        try (StandaloneCollector collector = StandaloneCollector.create(
                StandaloneCollectorConfig.fromProperties(protocolProperties(protocol, sourceId)))) {
            collector.start();

            writeFrame(collector, frame);

            InMemoryRuntimeSink<Object> sink = collector.inMemorySink().orElseThrow();
            ParsedRecord<Object> record = awaitRecords(sink, 1).get(0);
            assertEquals(sourceId, record.sourceId().qualifiedValue());
            assertEquals(protocol.configValue(), record.protocol());
            assertInstanceOf(expectedValueType, record.value());
            assertTrue(sink.failures().isEmpty());
            CollectorStatusSnapshot snapshot = collector.statusSnapshot();
            assertEquals(protocol.configValue(), snapshot.sources().get(0).protocol());
            assertEquals(protocol.configValue(), snapshot.tcpListeners().get(0).protocol());
        }
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

    private static byte[] variableIec101SinglePointFrame() {
        return variableFrame(0x08, 0x01, bytes(
                0x01, 0x01, 0x03, 0x00,
                0x01, 0x00, 0x01, 0x00, 0x00,
                0x01));
    }

    private static byte[] variableIec103ProtectionEventFrame() {
        return variableFrame(0x08, 0x01, bytes(
                0x01, 0x01, 0x01, 0x01,
                0x10, 0x01, 0xD2, 0xE8, 0x03, 0x15, 0x10));
    }

    private static byte[] variableFrame(int control, int linkAddress, byte[] asdu) {
        byte[] payload = new byte[2 + asdu.length];
        payload[0] = (byte) control;
        payload[1] = (byte) linkAddress;
        System.arraycopy(asdu, 0, payload, 2, asdu.length);
        int checksum = checksum(payload);
        byte[] frame = new byte[6 + payload.length];
        frame[0] = 0x68;
        frame[1] = (byte) payload.length;
        frame[2] = (byte) payload.length;
        frame[3] = 0x68;
        System.arraycopy(payload, 0, frame, 4, payload.length);
        frame[4 + payload.length] = (byte) checksum;
        frame[5 + payload.length] = 0x16;
        return frame;
    }

    private static int checksum(byte[] bytes) {
        int value = 0;
        for (byte current : bytes) {
            value = (value + (current & 0xFF)) & 0xFF;
        }
        return value;
    }

    private static byte[] modbusReadHoldingRegistersRequest() {
        return bytes(
                0x00, 0x01, 0x00, 0x00, 0x00, 0x06,
                0x11, 0x03, 0x00, 0x6B, 0x00, 0x03);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) (values[i] & 0xFF);
        }
        return bytes;
    }

    private static final class FakeKafkaRecordSource implements KafkaRecordSource {

        private KafkaRecordReceiver receiver;
        private boolean running;

        @Override
        public void start(KafkaRecordReceiver receiver) {
            this.receiver = receiver;
            this.running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        private KafkaIngressResult emit(ConsumerRecord<byte[], byte[]> record) {
            if (!running || receiver == null) {
                throw new IllegalStateException("fake Kafka source is not running");
            }
            return receiver.accept(record);
        }
    }
}
