package io.github.qbsstg.protocol.runtime.smoke;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.iec104.Iec104Frame;
import io.github.qbsstg.protocol.iec104.Iec104FrameType;
import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.github.qbsstg.protocol.runtime.iec104.Iec104RuntimeBinding;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpConnectionAttributes;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyBackpressureEvent;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyIngressHandler;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServer;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServerConfig;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class Iec104TcpRuntimeSmokeTest {

    private static final SourceId SOURCE_ID = SourceId.of("tcp", "station-1");
    private static final Instant RECEIVED_AT = Instant.parse("2026-06-01T00:00:00Z");
    private static final byte[] SINGLE_POINT_FRAME = bytes(
            0x68, 0x0E, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x01, 0x03, 0x00, 0x01, 0x00,
            0x01, 0x00, 0x00, 0x01);

    @Test
    void parsesIec104TcpBytesThroughRuntimePipeline() {
        List<ParsedRecord<Iec104Frame>> records = new ArrayList<>();
        List<ParseFailure> failures = new ArrayList<>();
        EmbeddedChannel channel = channel(records, failures, BackpressureStrategy.acceptAll(), new ArrayList<>());

        channel.writeInbound(Unpooled.wrappedBuffer(SINGLE_POINT_FRAME));

        assertEquals(1, records.size());
        assertTrue(failures.isEmpty());
        ParsedRecord<Iec104Frame> record = records.get(0);
        assertEquals(SOURCE_ID, record.sourceId());
        assertEquals("iec104", record.protocol());
        assertEquals("I_FORMAT", record.recordType());
        assertEquals(RECEIVED_AT, record.observedAt());
        assertArrayEquals(SINGLE_POINT_FRAME, record.rawPayload());
        assertEquals(Iec104FrameType.I_FORMAT, record.value().getType());
        assertEquals(1, record.value().getAsduType());
        assertEquals(3, record.value().getCauseOfTransmission());
        assertEquals(1, record.value().getAsdu().getInformationObjects().size());
        assertTrue(record.attributes().containsKey(TcpConnectionAttributes.CHANNEL_ID));
        assertTrue(record.attributes().containsKey(TcpConnectionAttributes.SESSION_ID));

        channel.finishAndReleaseAll();
    }

    @Test
    void buffersSplitIec104TcpFramesAcrossInboundReads() {
        List<ParsedRecord<Iec104Frame>> records = new ArrayList<>();
        List<ParseFailure> failures = new ArrayList<>();
        EmbeddedChannel channel = channel(records, failures, BackpressureStrategy.acceptAll(), new ArrayList<>());

        channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOfRange(SINGLE_POINT_FRAME, 0, 3)));

        assertTrue(records.isEmpty());
        assertTrue(failures.isEmpty());

        channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOfRange(
                SINGLE_POINT_FRAME,
                3,
                SINGLE_POINT_FRAME.length)));

        assertEquals(1, records.size());
        assertTrue(failures.isEmpty());
        assertArrayEquals(SINGLE_POINT_FRAME, records.get(0).rawPayload());

        channel.finishAndReleaseAll();
    }

    @Test
    void backpressurePreventsIec104Parsing() {
        List<ParsedRecord<Iec104Frame>> records = new ArrayList<>();
        List<ParseFailure> failures = new ArrayList<>();
        List<Object> events = new ArrayList<>();
        EmbeddedChannel channel = channel(
                records,
                failures,
                BackpressureStrategy.fixed(BackpressureDecision.RETRY_LATER),
                events);

        channel.writeInbound(Unpooled.wrappedBuffer(SINGLE_POINT_FRAME));

        assertTrue(records.isEmpty());
        assertTrue(failures.isEmpty());
        assertFalse(channel.config().isAutoRead());
        List<TcpNettyBackpressureEvent> backpressureEvents = eventsOfType(events, TcpNettyBackpressureEvent.class);
        assertEquals(1, backpressureEvents.size());
        TcpNettyBackpressureEvent event = backpressureEvents.get(0);
        assertEquals(SOURCE_ID, event.sourceId());
        assertEquals(BackpressureDecision.RETRY_LATER, event.decision());
        assertEquals(SINGLE_POINT_FRAME.length, event.payloadSize());

        channel.finishAndReleaseAll();
    }

    @Test
    void routesIec104ParseFailuresToFailureSink() {
        byte[] malformedFrame = bytes(0x68, 0x03, 0x00, 0x00, 0x00);
        List<ParsedRecord<Iec104Frame>> records = new ArrayList<>();
        List<ParseFailure> failures = new ArrayList<>();
        EmbeddedChannel channel = channel(records, failures, BackpressureStrategy.acceptAll(), new ArrayList<>());

        channel.writeInbound(Unpooled.wrappedBuffer(malformedFrame));

        assertTrue(records.isEmpty());
        assertEquals(1, failures.size());
        ParseFailure failure = failures.get(0);
        assertEquals(SOURCE_ID, failure.sourceId());
        assertEquals("iec104", failure.protocol());
        assertTrue(failure.message().contains("Invalid IEC104 APDU length"));
        assertArrayEquals(malformedFrame, failure.rawPayload());
        assertEquals(RECEIVED_AT, failure.observedAt());

        channel.finishAndReleaseAll();
    }

    @Test
    void parsesIec104BytesFromRealTcpSocketAndStopsRunnerOnDisconnect() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        List<ParsedRecord<Iec104Frame>> records = new CopyOnWriteArrayList<>();
        List<ParseFailure> failures = new CopyOnWriteArrayList<>();
        TrackingRecordSink recordSink = new TrackingRecordSink(records, received, stopped);

        try (TcpNettyServer<Iec104Frame> server = new TcpNettyServer<>(
                TcpNettyServerConfig.loopback(0),
                channel -> new RuntimePipelineRunner<>(
                        new Iec104RuntimeBinding(),
                        recordSink,
                        failures::add,
                        BackpressureStrategy.acceptAll()),
                context -> SOURCE_ID,
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)).bind()) {
            try (Socket socket = new Socket()) {
                socket.connect(server.localAddress());
                socket.getOutputStream().write(SINGLE_POINT_FRAME);
                socket.getOutputStream().flush();

                assertTrue(received.await(3, TimeUnit.SECONDS));
                awaitActiveConnections(server, 1);
            }

            assertTrue(stopped.await(3, TimeUnit.SECONDS));
            awaitActiveConnections(server, 0);
            assertTrue(failures.isEmpty());
            assertEquals(1, records.size());
            assertEquals("iec104", records.get(0).protocol());
            assertEquals(Iec104FrameType.I_FORMAT, records.get(0).value().getType());
            assertArrayEquals(SINGLE_POINT_FRAME, records.get(0).rawPayload());
        }
    }

    private static EmbeddedChannel channel(
            List<ParsedRecord<Iec104Frame>> records,
            List<ParseFailure> failures,
            BackpressureStrategy backpressureStrategy,
            List<Object> events) {
        RuntimePipelineRunner<Iec104Frame> runner = new RuntimePipelineRunner<>(
                new Iec104RuntimeBinding(),
                records::add,
                failures::add,
                backpressureStrategy);
        EmbeddedChannel channel = new EmbeddedChannel(
                new TcpNettyIngressHandler<>(
                        runner,
                        context -> SOURCE_ID,
                        Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)),
                new UserEventCapture(events));
        return channel;
    }

    private static void awaitActiveConnections(TcpNettyServer<?> server, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (server.activeConnectionCount() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, server.activeConnectionCount());
    }

    private static <E> List<E> eventsOfType(List<Object> events, Class<E> type) {
        return events.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) (values[i] & 0xFF);
        }
        return bytes;
    }

    private static final class TrackingRecordSink implements RecordSink<Iec104Frame> {

        private final List<ParsedRecord<Iec104Frame>> records;
        private final CountDownLatch received;
        private final CountDownLatch stopped;

        private TrackingRecordSink(
                List<ParsedRecord<Iec104Frame>> records,
                CountDownLatch received,
                CountDownLatch stopped) {
            this.records = records;
            this.received = received;
            this.stopped = stopped;
        }

        @Override
        public void accept(ParsedRecord<Iec104Frame> record) {
            records.add(record);
            received.countDown();
        }

        @Override
        public void stop() {
            stopped.countDown();
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
