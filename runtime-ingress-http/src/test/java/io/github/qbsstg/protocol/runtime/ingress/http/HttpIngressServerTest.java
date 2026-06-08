package io.github.qbsstg.protocol.runtime.ingress.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RuntimeParseResult;
import io.github.qbsstg.protocol.runtime.core.RuntimeParserBinding;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HttpIngressServerTest {

    private static final SourceId SOURCE_ID = SourceId.of("iec104", "station-1");

    @Test
    void acceptsConfiguredSourcePostAndMapsRequestToIngressEnvelope() throws Exception {
        Capture capture = new Capture();

        try (HttpIngressServer<byte[]> server = new HttpIngressServer<>(
                HttpIngressServerConfig.loopback(0, SOURCE_ID),
                runner(capture, BackpressureStrategy.acceptAll())).bind()) {
            HttpResponse<String> response = post(server, "/ingress?trace=1", new byte[] {1, 2, 3});

            assertEquals(202, response.statusCode());
            assertEquals("{\"status\":\"accepted\"}", response.body());
            assertTrue(capture.received.await(3, TimeUnit.SECONDS));
            assertEquals(1, capture.records.size());
            ParsedRecord<byte[]> record = capture.records.get(0);
            assertEquals(SOURCE_ID, record.sourceId());
            assertEquals("test-http", record.protocol());
            assertEquals("bytes", record.recordType());
            assertArrayEquals(new byte[] {1, 2, 3}, record.value());

            IngressEnvelope envelope = capture.envelopes.get(0);
            assertEquals("http", envelope.transport());
            assertEquals("trace=1", envelope.attributes().get(HttpIngressAttributes.QUERY));
            assertEquals("application/octet-stream", envelope.attributes().get(HttpIngressAttributes.CONTENT_TYPE));
            assertEquals("http", envelope.attributes().get(HttpIngressAttributes.LISTENER_NAME));
            assertEquals("CONFIGURED", envelope.attributes().get(HttpIngressAttributes.SOURCE_ID_MODE));
            assertEquals("iec104", envelope.attributes().get(HttpIngressAttributes.SOURCE_NAMESPACE));
            assertEquals("station-1", envelope.attributes().get(HttpIngressAttributes.SOURCE_VALUE));
        }
    }

    @Test
    void acceptsPathSourcePost() throws Exception {
        Capture capture = new Capture();

        try (HttpIngressServer<byte[]> server = new HttpIngressServer<>(
                HttpIngressServerConfig.pathSource(0),
                runner(capture, BackpressureStrategy.acceptAll())).bind()) {
            HttpResponse<String> response = post(server, "/ingress/iec104%3Astation-2", new byte[] {4});

            assertEquals(202, response.statusCode());
            assertTrue(capture.received.await(3, TimeUnit.SECONDS));
            assertEquals(SourceId.of("iec104", "station-2"), capture.records.get(0).sourceId());
            assertEquals("PATH", capture.envelopes.get(0).attributes().get(HttpIngressAttributes.SOURCE_ID_MODE));
        }
    }

    @Test
    void acceptsHeaderSourcePost() throws Exception {
        Capture capture = new Capture();
        HttpIngressServerConfig config = new HttpIngressServerConfig(
                "127.0.0.1",
                0,
                "/ingress",
                HttpIngressSourceIdMode.HEADER,
                "X-Source-Id",
                null,
                0,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                0,
                1,
                "http-header");

        try (HttpIngressServer<byte[]> server = new HttpIngressServer<>(
                config,
                runner(capture, BackpressureStrategy.acceptAll())).bind()) {
            HttpResponse<String> response = post(
                    server,
                    "/ingress",
                    new byte[] {5},
                    Map.of("X-Source-Id", "modbus:device-1"));

            assertEquals(202, response.statusCode());
            assertTrue(capture.received.await(3, TimeUnit.SECONDS));
            assertEquals(SourceId.of("modbus", "device-1"), capture.records.get(0).sourceId());
            assertEquals("http-header", capture.envelopes.get(0).attributes().get(HttpIngressAttributes.LISTENER_NAME));
            assertEquals("HEADER", capture.envelopes.get(0).attributes().get(HttpIngressAttributes.SOURCE_ID_MODE));
        }
    }

    @Test
    void rejectsUnsupportedMethodsWithoutParsing() throws Exception {
        Capture capture = new Capture();

        try (HttpIngressServer<byte[]> server = new HttpIngressServer<>(
                HttpIngressServerConfig.loopback(0, SOURCE_ID),
                runner(capture, BackpressureStrategy.acceptAll())).bind()) {
            HttpResponse<String> response = client().send(
                    HttpRequest.newBuilder(uri(server, "/ingress")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(405, response.statusCode());
            assertEquals("POST", response.headers().firstValue("Allow").orElse(""));
            assertFalse(capture.received.await(150, TimeUnit.MILLISECONDS));
            assertTrue(capture.records.isEmpty());
        }
    }

    @Test
    void rejectsInvalidSourceWithoutParsing() throws Exception {
        Capture capture = new Capture();

        try (HttpIngressServer<byte[]> server = new HttpIngressServer<>(
                HttpIngressServerConfig.pathSource(0),
                runner(capture, BackpressureStrategy.acceptAll())).bind()) {
            HttpResponse<String> response = post(server, "/ingress/not-qualified", new byte[] {1});

            assertEquals(400, response.statusCode());
            assertFalse(capture.received.await(150, TimeUnit.MILLISECONDS));
            assertTrue(capture.records.isEmpty());
        }
    }

    @Test
    void rejectsPayloadsOverLimitWithoutParsing() throws Exception {
        Capture capture = new Capture();
        HttpIngressServerConfig config = new HttpIngressServerConfig(
                "127.0.0.1",
                0,
                "/ingress",
                HttpIngressSourceIdMode.CONFIGURED,
                null,
                SOURCE_ID,
                2,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                0,
                1,
                "http");

        try (HttpIngressServer<byte[]> server = new HttpIngressServer<>(
                config,
                runner(capture, BackpressureStrategy.acceptAll())).bind()) {
            HttpResponse<String> response = post(server, "/ingress", new byte[] {1, 2, 3});

            assertEquals(413, response.statusCode());
            assertFalse(capture.received.await(150, TimeUnit.MILLISECONDS));
            assertTrue(capture.records.isEmpty());
        }
    }

    @Test
    void mapsRetryLaterBackpressureToUnavailableWithoutParsing() throws Exception {
        Capture capture = new Capture();

        try (HttpIngressServer<byte[]> server = new HttpIngressServer<>(
                HttpIngressServerConfig.loopback(0, SOURCE_ID),
                runner(capture, BackpressureStrategy.fixed(BackpressureDecision.RETRY_LATER))).bind()) {
            HttpResponse<String> response = post(server, "/ingress", new byte[] {1});

            assertEquals(503, response.statusCode());
            assertEquals("1", response.headers().firstValue("Retry-After").orElse(""));
            assertEquals("{\"status\":\"retry_later\"}", response.body());
            assertFalse(capture.received.await(150, TimeUnit.MILLISECONDS));
            assertTrue(capture.records.isEmpty());
        }
    }

    @Test
    void mapsDropBackpressureToAcceptedDropWithoutParsing() throws Exception {
        Capture capture = new Capture();

        try (HttpIngressServer<byte[]> server = new HttpIngressServer<>(
                HttpIngressServerConfig.loopback(0, SOURCE_ID),
                runner(capture, BackpressureStrategy.fixed(BackpressureDecision.DROP))).bind()) {
            HttpResponse<String> response = post(server, "/ingress", new byte[] {1});

            assertEquals(202, response.statusCode());
            assertEquals("{\"status\":\"dropped\"}", response.body());
            assertFalse(capture.received.await(150, TimeUnit.MILLISECONDS));
            assertTrue(capture.records.isEmpty());
        }
    }

    @Test
    void supportsNoBodyResponseMode() throws Exception {
        Capture capture = new Capture();
        HttpIngressServerConfig config = new HttpIngressServerConfig(
                "127.0.0.1",
                0,
                "/ingress",
                HttpIngressSourceIdMode.CONFIGURED,
                null,
                SOURCE_ID,
                0,
                HttpIngressResponseMode.NO_BODY,
                0,
                1,
                "http");

        try (HttpIngressServer<byte[]> server = new HttpIngressServer<>(
                config,
                runner(capture, BackpressureStrategy.acceptAll())).bind()) {
            HttpResponse<String> response = post(server, "/ingress", new byte[] {1});

            assertEquals(202, response.statusCode());
            assertEquals("", response.body());
            assertTrue(capture.received.await(3, TimeUnit.SECONDS));
            assertEquals(1, capture.records.size());
        }
    }

    @Test
    void startsAndStopsIdempotently() {
        Capture capture = new Capture();
        HttpIngressServer<byte[]> server = new HttpIngressServer<>(
                HttpIngressServerConfig.loopback(0, SOURCE_ID),
                runner(capture, BackpressureStrategy.acceptAll()));

        assertFalse(server.isRunning());
        assertThrows(IllegalStateException.class, server::localAddress);

        server.start();
        assertTrue(server.isRunning());
        assertTrue(server.port() > 0);
        server.start();
        assertTrue(server.isRunning());

        server.stop();
        assertFalse(server.isRunning());
        assertThrows(IllegalStateException.class, server::localAddress);
        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void validatesServerConfig() {
        assertThrows(IllegalArgumentException.class, () -> HttpIngressServerConfig.loopback(-1, SOURCE_ID));
        assertThrows(IllegalArgumentException.class, () -> HttpIngressServerConfig.loopback(65536, SOURCE_ID));
        assertThrows(IllegalArgumentException.class, () -> HttpIngressServerConfig.loopback(0, null));
        assertThrows(IllegalArgumentException.class, () -> new HttpIngressServerConfig(
                " ",
                0,
                "/ingress",
                HttpIngressSourceIdMode.CONFIGURED,
                null,
                SOURCE_ID,
                0,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                0,
                1,
                "http"));
        assertThrows(IllegalArgumentException.class, () -> new HttpIngressServerConfig(
                "127.0.0.1",
                0,
                "ingress",
                HttpIngressSourceIdMode.CONFIGURED,
                null,
                SOURCE_ID,
                0,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                0,
                1,
                "http"));
        assertThrows(IllegalArgumentException.class, () -> new HttpIngressServerConfig(
                "127.0.0.1",
                0,
                "/ingress",
                HttpIngressSourceIdMode.PATH,
                null,
                null,
                0,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                0,
                1,
                "http"));
        assertThrows(IllegalArgumentException.class, () -> new HttpIngressServerConfig(
                "127.0.0.1",
                0,
                "/ingress",
                HttpIngressSourceIdMode.HEADER,
                " ",
                null,
                0,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                0,
                1,
                "http"));
        assertThrows(IllegalArgumentException.class, () -> new HttpIngressServerConfig(
                "127.0.0.1",
                0,
                "/ingress",
                HttpIngressSourceIdMode.CONFIGURED,
                null,
                SOURCE_ID,
                -1,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                0,
                1,
                "http"));
        assertThrows(IllegalArgumentException.class, () -> new HttpIngressServerConfig(
                "127.0.0.1",
                0,
                "/ingress",
                HttpIngressSourceIdMode.CONFIGURED,
                null,
                SOURCE_ID,
                0,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                -1,
                1,
                "http"));
        assertThrows(IllegalArgumentException.class, () -> new HttpIngressServerConfig(
                "127.0.0.1",
                0,
                "/ingress",
                HttpIngressSourceIdMode.CONFIGURED,
                null,
                SOURCE_ID,
                0,
                HttpIngressResponseMode.ACK_ON_ACCEPT,
                0,
                0,
                "http"));
    }

    @Test
    void exposesModuleFactories() {
        Capture capture = new Capture();
        RuntimePipelineRunner<byte[]> runner = runner(capture, BackpressureStrategy.acceptAll());

        HttpIngressServerConfig config = HttpIngressModule.loopbackConfig(0, SOURCE_ID);
        HttpIngressServer<byte[]> server = HttpIngressModule.server(config, runner);

        assertEquals("runtime-ingress-http", HttpIngressModule.MODULE_NAME);
        assertEquals("http", HttpIngressModule.TRANSPORT);
        assertSame(BackpressureDecision.ACCEPT, HttpIngressModule.defaultBackpressureDecision());
        assertEquals(HttpIngressSourceIdMode.CONFIGURED, config.sourceIdMode());
        assertEquals(HttpIngressSourceIdMode.PATH, HttpIngressModule.pathSourceConfig(0).sourceIdMode());
        assertFalse(server.isRunning());
    }

    private static RuntimePipelineRunner<byte[]> runner(Capture capture, BackpressureStrategy backpressureStrategy) {
        return new RuntimePipelineRunner<>(
                new EchoBinding(capture),
                capture.records::add,
                FailureSink.noop(),
                backpressureStrategy);
    }

    private static HttpResponse<String> post(
            HttpIngressServer<?> server,
            String path,
            byte[] payload) throws Exception {
        return post(server, path, payload, Map.of());
    }

    private static HttpResponse<String> post(
            HttpIngressServer<?> server,
            String path,
            byte[] payload,
            Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(server, path))
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .header("Content-Type", "application/octet-stream");
        headers.forEach(builder::header);
        return client().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(HttpIngressServer<?> server, String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private static HttpClient client() {
        return HttpClient.newHttpClient();
    }

    private static final class Capture {
        private final CountDownLatch received = new CountDownLatch(1);
        private final List<IngressEnvelope> envelopes = new CopyOnWriteArrayList<>();
        private final List<ParsedRecord<byte[]>> records = new CopyOnWriteArrayList<>();
    }

    private static final class EchoBinding implements RuntimeParserBinding<byte[]> {

        private final Capture capture;

        private EchoBinding(Capture capture) {
            this.capture = capture;
        }

        @Override
        public String protocol() {
            return "test-http";
        }

        @Override
        public List<RuntimeParseResult<byte[]>> parse(IngressEnvelope envelope) {
            capture.envelopes.add(envelope);
            capture.received.countDown();
            return List.of(RuntimeParseResult.success(new ParsedRecord<>(
                    envelope.sourceId(),
                    protocol(),
                    "bytes",
                    envelope.payload(),
                    envelope.payload(),
                    Instant.EPOCH,
                    envelope.attributes())));
        }
    }
}
