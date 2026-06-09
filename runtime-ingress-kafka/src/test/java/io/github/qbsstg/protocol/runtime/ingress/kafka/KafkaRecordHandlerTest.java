package io.github.qbsstg.protocol.runtime.ingress.kafka;

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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class KafkaRecordHandlerTest {

    private static final SourceId SOURCE_ID = SourceId.of("iec104", "station-1");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void mapsConfiguredSourceRecordToIngressEnvelope() {
        Capture capture = new Capture();
        RuntimePipelineRunner<byte[]> runner = startRunner(capture, BackpressureStrategy.acceptAll());
        KafkaIngressConsumerConfig config = KafkaIngressConsumerConfig.configured(
                "localhost:9092",
                "runtime",
                "iec104-raw",
                SOURCE_ID,
                "iec104");
        KafkaRecordHandler<byte[]> handler = handler(config, runner);

        KafkaIngressResult result = handler.accept(record(
                "iec104-raw",
                2,
                42,
                "device-key",
                new byte[] {1, 2, 3},
                Map.of("trace", "abc")));

        assertEquals(KafkaIngressStatus.ACCEPTED, result.status());
        assertEquals(BackpressureDecision.ACCEPT, result.decision());
        assertFalse(result.commitAllowed());
        assertEquals(1, capture.records.size());
        ParsedRecord<byte[]> parsed = capture.records.get(0);
        assertEquals(SOURCE_ID, parsed.sourceId());
        assertArrayEquals(new byte[] {1, 2, 3}, parsed.value());

        IngressEnvelope envelope = capture.envelopes.get(0);
        assertEquals("kafka", envelope.transport());
        assertEquals(CLOCK.instant(), envelope.receivedAt());
        assertEquals("kafka", envelope.attributes().get(KafkaIngressAttributes.CONSUMER_NAME));
        assertEquals("runtime", envelope.attributes().get(KafkaIngressAttributes.GROUP_ID));
        assertEquals("iec104-raw", envelope.attributes().get(KafkaIngressAttributes.TOPIC));
        assertEquals("2", envelope.attributes().get(KafkaIngressAttributes.PARTITION));
        assertEquals("42", envelope.attributes().get(KafkaIngressAttributes.OFFSET));
        assertEquals("device-key", envelope.attributes().get(KafkaIngressAttributes.KEY));
        assertEquals("abc", envelope.attributes().get(KafkaIngressAttributes.HEADER_PREFIX + "trace"));
        assertEquals("CONFIGURED", envelope.attributes().get(KafkaIngressAttributes.SOURCE_ID_MODE));
        assertEquals("iec104", envelope.attributes().get(KafkaIngressAttributes.PROTOCOL));
    }

    @Test
    void resolvesSourceFromHeaderTopicAndKey() {
        assertEquals(
                SourceId.of("iec104", "header-source"),
                envelope(config(KafkaSourceIdMode.HEADER, "protocol-source", null, List.of("raw"), null),
                        record("raw", 0, 1, "ignored", new byte[] {1}, Map.of(
                                "protocol-source",
                                "iec104:header-source"))).sourceId());

        assertEquals(
                SourceId.of("iec101", "topic-source"),
                envelope(config(KafkaSourceIdMode.TOPIC, null, null, List.of("iec101:topic-source"), null),
                        record("iec101:topic-source", 0, 1, "ignored", new byte[] {1}, Map.of())).sourceId());

        assertEquals(
                SourceId.of("modbus", "key-source"),
                envelope(config(KafkaSourceIdMode.KEY, null, null, List.of("raw"), null),
                        record("raw", 0, 1, "modbus:key-source", new byte[] {1}, Map.of())).sourceId());
    }

    @Test
    void mapsBackpressureToKafkaIngressResult() {
        KafkaIngressConsumerConfig config = new KafkaIngressConsumerConfig(
                "kafka",
                "localhost:9092",
                "runtime",
                List.of("raw"),
                null,
                KafkaSourceIdMode.CONFIGURED,
                null,
                SOURCE_ID,
                "iec104",
                KafkaCommitMode.AFTER_ACCEPT,
                "latest",
                100,
                1000);

        KafkaIngressResult dropped = handler(config, startRunner(new Capture(), BackpressureStrategy.fixed(
                BackpressureDecision.DROP))).accept(record("raw", 0, 1, null, new byte[] {1}, Map.of()));
        assertEquals(KafkaIngressStatus.DROPPED, dropped.status());
        assertTrue(dropped.commitAllowed());

        KafkaIngressResult retry = handler(config, startRunner(new Capture(), BackpressureStrategy.fixed(
                BackpressureDecision.RETRY_LATER))).accept(record("raw", 0, 1, null, new byte[] {1}, Map.of()));
        assertEquals(KafkaIngressStatus.RETRY_LATER, retry.status());
        assertFalse(retry.commitAllowed());
    }

    @Test
    void invalidSourceDoesNotDispatchToRunner() {
        Capture capture = new Capture();
        KafkaIngressConsumerConfig config = config(KafkaSourceIdMode.HEADER, "protocol-source", null, List.of("raw"), null);
        KafkaIngressResult result = handler(config, startRunner(capture, BackpressureStrategy.acceptAll()))
                .accept(record("raw", 0, 1, null, new byte[] {1}, Map.of()));

        assertEquals(KafkaIngressStatus.INVALID_SOURCE, result.status());
        assertFalse(result.commitAllowed());
        assertTrue(result.reason().contains("protocol-source"));
        assertTrue(capture.envelopes.isEmpty());
        assertTrue(capture.records.isEmpty());
    }

    @Test
    void validatesConfigAndExposesModuleFactories() {
        assertThrows(IllegalArgumentException.class, () -> KafkaIngressConsumerConfig.configured(
                " ",
                "runtime",
                "raw",
                SOURCE_ID,
                "iec104"));
        assertThrows(IllegalArgumentException.class, () -> new KafkaIngressConsumerConfig(
                "kafka",
                "localhost:9092",
                "runtime",
                List.of("raw"),
                "raw.*",
                KafkaSourceIdMode.CONFIGURED,
                null,
                SOURCE_ID,
                "iec104",
                KafkaCommitMode.MANUAL,
                "latest",
                100,
                1000));
        assertThrows(IllegalArgumentException.class, () -> config(
                KafkaSourceIdMode.HEADER,
                " ",
                null,
                List.of("raw"),
                null));
        assertThrows(IllegalArgumentException.class, () -> config(
                KafkaSourceIdMode.CONFIGURED,
                null,
                null,
                List.of("raw"),
                null));

        assertEquals("runtime-ingress-kafka", KafkaIngressModule.MODULE_NAME);
        assertEquals("kafka", KafkaIngressModule.TRANSPORT);
        assertSame(BackpressureDecision.ACCEPT, KafkaIngressModule.defaultBackpressureDecision());
    }

    private static KafkaRecordHandler<byte[]> handler(
            KafkaIngressConsumerConfig config,
            RuntimePipelineRunner<byte[]> runner) {
        return new KafkaRecordHandler<>(new KafkaRecordEnvelopeMapper(config, CLOCK), config, runner);
    }

    private static IngressEnvelope envelope(KafkaIngressConsumerConfig config, ConsumerRecord<byte[], byte[]> record) {
        return new KafkaRecordEnvelopeMapper(config, CLOCK).toEnvelope(record);
    }

    private static KafkaIngressConsumerConfig config(
            KafkaSourceIdMode sourceIdMode,
            String sourceIdHeader,
            SourceId configuredSourceId,
            List<String> topics,
            String topicPattern) {
        return new KafkaIngressConsumerConfig(
                "kafka-main",
                "localhost:9092",
                "runtime",
                topics,
                topicPattern,
                sourceIdMode,
                sourceIdHeader,
                configuredSourceId,
                "iec104",
                KafkaCommitMode.MANUAL,
                "latest",
                100,
                1000);
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

    private static ConsumerRecord<byte[], byte[]> record(
            String topic,
            int partition,
            long offset,
            String key,
            byte[] value,
            Map<String, String> headers) {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(
                topic,
                partition,
                offset,
                key == null ? null : key.getBytes(StandardCharsets.UTF_8),
                value);
        headers.forEach((name, headerValue) -> record.headers().add(
                name,
                headerValue.getBytes(StandardCharsets.UTF_8)));
        return record;
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
            return "test-kafka";
        }

        @Override
        public List<RuntimeParseResult<byte[]>> parse(IngressEnvelope envelope) {
            capture.envelopes.add(envelope);
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
