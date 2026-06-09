package io.github.qbsstg.protocol.runtime.ingress.mqtt;

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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

class MqttMessageHandlerTest {

    private static final SourceId SOURCE_ID = SourceId.of("iec104", "station-1");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void mapsConfiguredSourceMessageToIngressEnvelope() {
        Capture capture = new Capture();
        RuntimePipelineRunner<byte[]> runner = startRunner(capture, BackpressureStrategy.acceptAll());
        MqttIngressClientConfig config = MqttIngressClientConfig.configured(
                "tcp://localhost:1883",
                "runtime-client",
                "devices/+/raw",
                SOURCE_ID,
                "iec104");
        MqttMessageHandler<byte[]> handler = handler(config, runner);

        MqttIngressResult result = handler.accept("devices/station-1/raw", message(
                new byte[] {1, 2, 3},
                1,
                true,
                false));

        assertEquals(MqttIngressStatus.ACCEPTED, result.status());
        assertEquals(BackpressureDecision.ACCEPT, result.decision());
        assertTrue(result.acknowledgeAllowed());
        assertEquals(1, capture.records.size());
        ParsedRecord<byte[]> parsed = capture.records.get(0);
        assertEquals(SOURCE_ID, parsed.sourceId());
        assertArrayEquals(new byte[] {1, 2, 3}, parsed.value());

        IngressEnvelope envelope = capture.envelopes.get(0);
        assertEquals("mqtt", envelope.transport());
        assertEquals(CLOCK.instant(), envelope.receivedAt());
        assertEquals("mqtt", envelope.attributes().get(MqttIngressAttributes.CLIENT_NAME));
        assertEquals("runtime-client", envelope.attributes().get(MqttIngressAttributes.CLIENT_ID));
        assertEquals("tcp://localhost:1883", envelope.attributes().get(MqttIngressAttributes.BROKER_URI));
        assertEquals("devices/station-1/raw", envelope.attributes().get(MqttIngressAttributes.TOPIC));
        assertEquals("77", envelope.attributes().get(MqttIngressAttributes.PACKET_ID));
        assertEquals("1", envelope.attributes().get(MqttIngressAttributes.QOS));
        assertEquals("true", envelope.attributes().get(MqttIngressAttributes.RETAINED));
        assertEquals("false", envelope.attributes().get(MqttIngressAttributes.DUPLICATE));
        assertEquals("CONFIGURED", envelope.attributes().get(MqttIngressAttributes.SOURCE_ID_MODE));
        assertEquals("iec104", envelope.attributes().get(MqttIngressAttributes.PROTOCOL));
    }

    @Test
    void resolvesSourceFromTopic() {
        MqttIngressClientConfig config = new MqttIngressClientConfig(
                "mqtt-main",
                "tcp://localhost:1883",
                "runtime-client",
                List.of("iec104:+"),
                0,
                MqttSourceIdMode.TOPIC,
                null,
                "iec104",
                true,
                true,
                30,
                60);

        IngressEnvelope envelope = new MqttMessageEnvelopeMapper(config, CLOCK)
                .toEnvelope("iec104:topic-source", message(new byte[] {1}, 0, false, false));

        assertEquals(SourceId.of("iec104", "topic-source"), envelope.sourceId());
        assertEquals("TOPIC", envelope.attributes().get(MqttIngressAttributes.SOURCE_ID_MODE));
    }

    @Test
    void mapsBackpressureToMqttIngressResult() {
        MqttIngressClientConfig config = MqttIngressClientConfig.configured(
                "tcp://localhost:1883",
                "runtime-client",
                "raw",
                SOURCE_ID,
                "iec104");

        MqttIngressResult dropped = handler(config, startRunner(new Capture(), BackpressureStrategy.fixed(
                BackpressureDecision.DROP))).accept("raw", message(new byte[] {1}, 0, false, false));
        assertEquals(MqttIngressStatus.DROPPED, dropped.status());
        assertTrue(dropped.acknowledgeAllowed());

        MqttIngressResult retry = handler(config, startRunner(new Capture(), BackpressureStrategy.fixed(
                BackpressureDecision.RETRY_LATER))).accept("raw", message(new byte[] {1}, 0, false, false));
        assertEquals(MqttIngressStatus.RETRY_LATER, retry.status());
        assertFalse(retry.acknowledgeAllowed());
    }

    @Test
    void invalidSourceDoesNotDispatchToRunner() {
        Capture capture = new Capture();
        MqttIngressClientConfig config = new MqttIngressClientConfig(
                "mqtt-main",
                "tcp://localhost:1883",
                "runtime-client",
                List.of("raw"),
                0,
                MqttSourceIdMode.TOPIC,
                null,
                "iec104",
                true,
                true,
                30,
                60);

        MqttIngressResult result = handler(config, startRunner(capture, BackpressureStrategy.acceptAll()))
                .accept("raw", message(new byte[] {1}, 0, false, false));

        assertEquals(MqttIngressStatus.INVALID_SOURCE, result.status());
        assertFalse(result.acknowledgeAllowed());
        assertTrue(result.reason().contains("namespace:value"));
        assertTrue(capture.envelopes.isEmpty());
        assertTrue(capture.records.isEmpty());
    }

    @Test
    void validatesConfigAndExposesModuleFactories() {
        assertThrows(IllegalArgumentException.class, () -> MqttIngressClientConfig.configured(
                " ",
                "runtime-client",
                "raw",
                SOURCE_ID,
                "iec104"));
        assertThrows(IllegalArgumentException.class, () -> new MqttIngressClientConfig(
                "mqtt",
                "tcp://localhost:1883",
                "runtime-client",
                List.of(),
                0,
                MqttSourceIdMode.CONFIGURED,
                SOURCE_ID,
                "iec104",
                true,
                true,
                30,
                60));
        assertThrows(IllegalArgumentException.class, () -> new MqttIngressClientConfig(
                "mqtt",
                "tcp://localhost:1883",
                "runtime-client",
                List.of("raw"),
                3,
                MqttSourceIdMode.CONFIGURED,
                SOURCE_ID,
                "iec104",
                true,
                true,
                30,
                60));
        assertThrows(IllegalArgumentException.class, () -> new MqttIngressClientConfig(
                "mqtt",
                "tcp://localhost:1883",
                "runtime-client",
                List.of("raw"),
                0,
                MqttSourceIdMode.CONFIGURED,
                null,
                "iec104",
                true,
                true,
                30,
                60));

        assertEquals("runtime-ingress-mqtt", MqttIngressModule.MODULE_NAME);
        assertEquals("mqtt", MqttIngressModule.TRANSPORT);
        assertSame(BackpressureDecision.ACCEPT, MqttIngressModule.defaultBackpressureDecision());
    }

    @Test
    void sourceRecordsStartupFailure() {
        MqttIngressClientConfig config = MqttIngressClientConfig.configured(
                "tcp://localhost:1883",
                "runtime-client",
                "raw",
                SOURCE_ID,
                "iec104");
        MqttPahoMessageSource source = new MqttPahoMessageSource(
                config,
                ignored -> {
                    throw new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION);
                });

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> source.start((topic, message) -> MqttIngressResult.accepted()));

        assertFalse(source.isRunning());
        assertSame(thrown, source.lastFailure());
    }

    private static MqttMessageHandler<byte[]> handler(
            MqttIngressClientConfig config,
            RuntimePipelineRunner<byte[]> runner) {
        return new MqttMessageHandler<>(new MqttMessageEnvelopeMapper(config, CLOCK), runner);
    }

    private static RuntimePipelineRunner<byte[]> startRunner(
            Capture capture,
            BackpressureStrategy backpressureStrategy) {
        RuntimePipelineRunner<byte[]> runner = new RuntimePipelineRunner<>(
                new EchoBinding(capture),
                capture.records::add,
                FailureSink.noop(),
                backpressureStrategy);
        runner.start();
        return runner;
    }

    private static MqttMessage message(byte[] payload, int qos, boolean retained, boolean duplicate) {
        MqttMessage message = new MqttMessage(payload);
        message.setQos(qos);
        message.setRetained(retained);
        message.setId(77);
        assertFalse(duplicate, "Paho MQTT v3 exposes duplicate as broker-owned metadata");
        return message;
    }

    private static final class Capture {
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
            return "test-mqtt";
        }

        @Override
        public List<RuntimeParseResult<byte[]>> parse(IngressEnvelope envelope) {
            capture.envelopes.add(envelope);
            return List.of(RuntimeParseResult.success(new ParsedRecord<>(
                    envelope.sourceId(),
                    protocol(),
                    "payload",
                    envelope.payload(),
                    envelope.payload(),
                    envelope.receivedAt(),
                    envelope.attributes())));
        }
    }
}
