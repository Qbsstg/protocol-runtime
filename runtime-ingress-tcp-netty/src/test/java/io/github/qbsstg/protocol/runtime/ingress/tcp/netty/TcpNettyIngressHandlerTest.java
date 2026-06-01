package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
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
    void convertsByteBufToSessionScopedIngressEnvelopeAndRoutesRecords() {
        CopyingBinding binding = new CopyingBinding();
        List<ParsedRecord<byte[]>> records = new ArrayList<>();
        RuntimePipelineRunner<byte[]> runner = new RuntimePipelineRunner<>(
                binding,
                records::add,
                FailureSink.noop(),
                BackpressureStrategy.acceptAll());
        TestChannel testChannel = channel(runner, new ArrayList<>());
        EmbeddedChannel channel = testChannel.channel();

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {1, 2, 3}));

        assertEquals(1, binding.parseCalls);
        assertArrayEquals(new byte[] {1, 2, 3}, binding.lastEnvelope.payload());
        assertEquals(SOURCE_ID, binding.lastEnvelope.sourceId());
        assertEquals("tcp", binding.lastEnvelope.transport());
        assertEquals(RECEIVED_AT, binding.lastEnvelope.receivedAt());
        assertEquals(SOURCE_ID.namespace(), binding.lastEnvelope.attributes().get(
                TcpConnectionAttributes.SOURCE_NAMESPACE));
        assertEquals(SOURCE_ID.value(), binding.lastEnvelope.attributes().get(
                TcpConnectionAttributes.SOURCE_VALUE));
        assertEquals(RECEIVED_AT.toString(), binding.lastEnvelope.attributes().get(
                TcpConnectionAttributes.CONNECTED_AT));
        assertTrue(binding.lastEnvelope.attributes().containsKey(TcpConnectionAttributes.CHANNEL_ID));
        assertTrue(binding.lastEnvelope.attributes().containsKey(TcpConnectionAttributes.SESSION_ID));
        assertEquals(1, testChannel.registry().activeCount());

        assertEquals(1, records.size());
        assertArrayEquals(new byte[] {1, 2, 3}, records.get(0).value());
        assertEquals(SOURCE_ID, records.get(0).sourceId());
        assertEquals("sample-bytes", records.get(0).recordType());

        channel.close();
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
        EmbeddedChannel channel = channel(runner, events).channel();

        assertTrue(channel.config().isAutoRead());
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {9, 8}));

        assertEquals(0, binding.parseCalls);
        assertFalse(channel.config().isAutoRead());
        List<TcpNettyBackpressureEvent> backpressureEvents = eventsOfType(events, TcpNettyBackpressureEvent.class);
        assertEquals(1, backpressureEvents.size());
        TcpNettyBackpressureEvent event = backpressureEvents.get(0);
        assertEquals(SOURCE_ID, event.sourceId());
        assertEquals(BackpressureDecision.RETRY_LATER, event.decision());
        assertEquals(2, event.payloadSize());

        channel.close();
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
        EmbeddedChannel channel = channel(runner, events).channel();

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {7}));

        assertEquals(0, binding.parseCalls);
        assertTrue(channel.config().isAutoRead());
        List<TcpNettyBackpressureEvent> backpressureEvents = eventsOfType(events, TcpNettyBackpressureEvent.class);
        assertEquals(1, backpressureEvents.size());
        TcpNettyBackpressureEvent event = backpressureEvents.get(0);
        assertEquals(BackpressureDecision.DROP, event.decision());
        assertEquals(1, event.payloadSize());

        channel.close();
        channel.finishAndReleaseAll();
    }

    @Test
    void channelLifecycleStartsStopsRunnerAndPublishesSessionEvents() {
        List<Object> events = new ArrayList<>();
        RuntimePipelineRunner<byte[]> runner = new RuntimePipelineRunner<>(
                new CountingBinding(),
                RecordSink.noop(),
                FailureSink.noop(),
                BackpressureStrategy.acceptAll());
        TestChannel testChannel = channel(runner, events);

        assertTrue(runner.isRunning());
        assertEquals(1, testChannel.registry().activeCount());
        List<TcpConnectionLifecycleEvent> activeEvents = lifecycleEvents(events);
        assertEquals(1, activeEvents.size());
        assertEquals(TcpConnectionLifecycleEvent.Type.ACTIVE, activeEvents.get(0).type());
        assertEquals(SOURCE_ID, activeEvents.get(0).session().sourceId());

        testChannel.channel().close();

        assertFalse(runner.isRunning());
        assertEquals(0, testChannel.registry().activeCount());
        List<TcpConnectionLifecycleEvent> lifecycleEvents = lifecycleEvents(events);
        assertEquals(2, lifecycleEvents.size());
        assertEquals(TcpConnectionLifecycleEvent.Type.INACTIVE, lifecycleEvents.get(1).type());
        assertEquals(activeEvents.get(0).session().sessionId(), lifecycleEvents.get(1).session().sessionId());
        testChannel.channel().finishAndReleaseAll();
    }

    @Test
    void exceptionCaughtRoutesFailureAndPublishesLifecycleEvent() {
        List<Object> events = new ArrayList<>();
        List<ParseFailure> failures = new ArrayList<>();
        RuntimePipelineRunner<byte[]> runner = new RuntimePipelineRunner<>(
                new CountingBinding(),
                RecordSink.noop(),
                failures::add,
                BackpressureStrategy.acceptAll());
        EmbeddedChannel channel = channel(runner, events).channel();
        IllegalStateException cause = new IllegalStateException("socket failed");

        channel.pipeline().fireExceptionCaught(cause);

        assertEquals(1, failures.size());
        ParseFailure failure = failures.get(0);
        assertEquals(SOURCE_ID, failure.sourceId());
        assertEquals("test", failure.protocol());
        assertEquals("socket failed", failure.message());
        assertSame(cause, failure.cause());
        assertEquals(RECEIVED_AT, failure.observedAt());
        assertTrue(failure.attributes().containsKey(TcpConnectionAttributes.SESSION_ID));

        List<TcpConnectionLifecycleEvent> lifecycleEvents = lifecycleEvents(events);
        assertEquals(3, lifecycleEvents.size());
        assertEquals(TcpConnectionLifecycleEvent.Type.ACTIVE, lifecycleEvents.get(0).type());
        assertEquals(TcpConnectionLifecycleEvent.Type.EXCEPTION, lifecycleEvents.get(1).type());
        assertSame(cause, lifecycleEvents.get(1).cause());
        assertEquals(TcpConnectionLifecycleEvent.Type.INACTIVE, lifecycleEvents.get(2).type());
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

    private static TestChannel channel(
            RuntimePipelineRunner<byte[]> runner,
            List<Object> events) {
        TcpConnectionRegistry registry = new TcpConnectionRegistry();
        TcpNettyIngressHandler<byte[]> handler = new TcpNettyIngressHandler<>(
                runner,
                context -> SOURCE_ID,
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
                registry);
        EmbeddedChannel channel = new EmbeddedChannel(handler, new UserEventCapture(events));
        return new TestChannel(channel, registry, handler);
    }

    private static List<TcpConnectionLifecycleEvent> lifecycleEvents(List<Object> events) {
        return eventsOfType(events, TcpConnectionLifecycleEvent.class);
    }

    private static <E> List<E> eventsOfType(List<Object> events, Class<E> type) {
        return events.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private record TestChannel(
            EmbeddedChannel channel,
            TcpConnectionRegistry registry,
            TcpNettyIngressHandler<byte[]> handler) {
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
