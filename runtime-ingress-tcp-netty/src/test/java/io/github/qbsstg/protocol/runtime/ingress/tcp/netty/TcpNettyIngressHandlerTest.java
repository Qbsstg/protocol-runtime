package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;
import io.github.qbsstg.protocol.runtime.core.RuntimeParseResult;
import io.github.qbsstg.protocol.runtime.core.RuntimeParserBinding;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TcpNettyIngressHandlerTest {

    private static final SourceId SOURCE_ID = SourceId.of("tcp", "station-1");
    private static final Instant RECEIVED_AT = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void convertsByteBufToIngressEnvelopeAndRoutesRecords() {
        CopyingBinding binding = new CopyingBinding();
        List<ParsedRecord<byte[]>> records = new ArrayList<>();
        RuntimePipelineRunner<byte[]> runner = new RuntimePipelineRunner<>(
                binding,
                records::add,
                FailureSink.noop(),
                BackpressureStrategy.acceptAll());
        EmbeddedChannel channel = channel(runner, new ArrayList<>());

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {1, 2, 3}));

        assertEquals(1, binding.parseCalls);
        assertArrayEquals(new byte[] {1, 2, 3}, binding.lastEnvelope.payload());
        assertEquals(SOURCE_ID, binding.lastEnvelope.sourceId());
        assertEquals("tcp", binding.lastEnvelope.transport());
        assertEquals(RECEIVED_AT, binding.lastEnvelope.receivedAt());
        assertTrue(binding.lastEnvelope.attributes().containsKey(TcpConnectionAttributes.CHANNEL_ID));
        assertTrue(binding.lastEnvelope.attributes().containsKey(TcpConnectionAttributes.SESSION_ID));

        assertEquals(1, records.size());
        assertArrayEquals(new byte[] {1, 2, 3}, records.get(0).value());
        assertEquals(SOURCE_ID, records.get(0).sourceId());
        assertEquals("sample-bytes", records.get(0).recordType());

        channel.finishAndReleaseAll();
    }

    @Test
    void retryLaterPausesAutoReadAndPublishesBackpressureEvent() {
        CountingBinding binding = new CountingBinding();
        List<Object> events = new ArrayList<>();
        RuntimePipelineRunner<byte[]> runner = new RuntimePipelineRunner<>(
                binding,
                RecordSink.noop(),
                FailureSink.noop(),
                BackpressureStrategy.fixed(BackpressureDecision.RETRY_LATER));
        EmbeddedChannel channel = channel(runner, events);

        assertTrue(channel.config().isAutoRead());
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {9, 8}));

        assertEquals(0, binding.parseCalls);
        assertFalse(channel.config().isAutoRead());
        assertEquals(1, events.size());
        TcpNettyBackpressureEvent event = assertInstanceOf(TcpNettyBackpressureEvent.class, events.get(0));
        assertEquals(SOURCE_ID, event.sourceId());
        assertEquals(BackpressureDecision.RETRY_LATER, event.decision());
        assertEquals(2, event.payloadSize());

        channel.finishAndReleaseAll();
    }

    @Test
    void dropPublishesEventWithoutPausingAutoRead() {
        CountingBinding binding = new CountingBinding();
        List<Object> events = new ArrayList<>();
        RuntimePipelineRunner<byte[]> runner = new RuntimePipelineRunner<>(
                binding,
                RecordSink.noop(),
                FailureSink.noop(),
                BackpressureStrategy.fixed(BackpressureDecision.DROP));
        EmbeddedChannel channel = channel(runner, events);

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {7}));

        assertEquals(0, binding.parseCalls);
        assertTrue(channel.config().isAutoRead());
        assertEquals(1, events.size());
        TcpNettyBackpressureEvent event = assertInstanceOf(TcpNettyBackpressureEvent.class, events.get(0));
        assertEquals(BackpressureDecision.DROP, event.decision());
        assertEquals(1, event.payloadSize());

        channel.finishAndReleaseAll();
    }

    @Test
    void channelLifecycleStartsAndStopsRunner() {
        RuntimePipelineRunner<byte[]> runner = new RuntimePipelineRunner<>(
                new CountingBinding(),
                RecordSink.noop(),
                FailureSink.noop(),
                BackpressureStrategy.acceptAll());
        EmbeddedChannel channel = channel(runner, new ArrayList<>());

        assertTrue(runner.isRunning());
        channel.close();

        assertFalse(runner.isRunning());
        channel.finishAndReleaseAll();
    }

    @Test
    void resolvesSourceValueFromInetSocketAddress() {
        assertEquals("127.0.0.1:2404", TcpSourceIdResolver.sourceValue(
                new InetSocketAddress("127.0.0.1", 2404),
                "fallback"));
        assertEquals("station.local:2404", TcpSourceIdResolver.sourceValue(
                InetSocketAddress.createUnresolved("station.local", 2404),
                "fallback"));
    }

    private static EmbeddedChannel channel(
            RuntimePipelineRunner<byte[]> runner,
            List<Object> events) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new TcpNettyIngressHandler<>(
                        runner,
                        context -> SOURCE_ID,
                        Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)),
                new UserEventCapture(events));
        channel.pipeline().fireChannelActive();
        return channel;
    }

    private static final class CopyingBinding implements RuntimeParserBinding<byte[]> {

        private int parseCalls;
        private IngressEnvelope lastEnvelope;

        @Override
        public String protocol() {
            return "test";
        }

        @Override
        public List<RuntimeParseResult<byte[]>> parse(IngressEnvelope envelope) {
            parseCalls++;
            lastEnvelope = envelope;
            return List.of(RuntimeParseResult.success(new ParsedRecord<>(
                    envelope.sourceId(),
                    protocol(),
                    "sample-bytes",
                    envelope.payload(),
                    envelope.payload(),
                    envelope.receivedAt(),
                    envelope.attributes())));
        }
    }

    private static final class CountingBinding implements RuntimeParserBinding<byte[]> {

        private int parseCalls;

        @Override
        public String protocol() {
            return "test";
        }

        @Override
        public List<RuntimeParseResult<byte[]>> parse(IngressEnvelope envelope) {
            parseCalls++;
            return List.of();
        }
    }

    private static final class UserEventCapture extends ChannelInboundHandlerAdapter {

        private final List<Object> events;

        private UserEventCapture(List<Object> events) {
            this.events = events;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) {
            events.add(event);
        }
    }
}
