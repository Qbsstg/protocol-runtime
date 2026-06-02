package io.github.qbsstg.protocol.runtime.smoke;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.iec101.Iec101Frame;
import io.github.qbsstg.protocol.iec103.Iec103Frame;
import io.github.qbsstg.protocol.modbus.ModbusTcpAdu;
import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;
import io.github.qbsstg.protocol.runtime.core.RuntimeParserBinding;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.github.qbsstg.protocol.runtime.iec101.Iec101RuntimeBinding;
import io.github.qbsstg.protocol.runtime.iec103.Iec103RuntimeBinding;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpConnectionAttributes;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyIngressHandler;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServer;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServerConfig;
import io.github.qbsstg.protocol.runtime.modbus.ModbusRuntimeBinding;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class MultiProtocolTcpRuntimeSmokeTest {

    private static final SourceId SOURCE_ID = SourceId.of("tcp", "station-1");
    private static final Instant RECEIVED_AT = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void parsesIec101TcpBytesThroughRuntimePipeline() {
        assertEmbeddedTcpPath(
                "iec101",
                Iec101RuntimeBinding::new,
                variableIec101SinglePointFrame(),
                Iec101Frame.class,
                "VARIABLE_LENGTH");
    }

    @Test
    void parsesIec103TcpBytesThroughRuntimePipeline() {
        assertEmbeddedTcpPath(
                "iec103",
                Iec103RuntimeBinding::new,
                variableIec103ProtectionEventFrame(),
                Iec103Frame.class,
                "VARIABLE_LENGTH");
    }

    @Test
    void parsesModbusTcpBytesThroughRuntimePipeline() {
        assertEmbeddedTcpPath(
                "modbus",
                ModbusRuntimeBinding::tcpStream,
                modbusReadHoldingRegistersRequest(),
                ModbusTcpAdu.class,
                "MODBUS_TCP_ADU");
    }

    @Test
    void parsesAdditionalProtocolsFromRealTcpSocket() throws Exception {
        assertRealSocketPath(
                "iec101",
                Iec101RuntimeBinding::new,
                variableIec101SinglePointFrame(),
                Iec101Frame.class);
        assertRealSocketPath(
                "iec103",
                Iec103RuntimeBinding::new,
                variableIec103ProtectionEventFrame(),
                Iec103Frame.class);
        assertRealSocketPath(
                "modbus",
                ModbusRuntimeBinding::tcpStream,
                modbusReadHoldingRegistersRequest(),
                ModbusTcpAdu.class);
    }

    private static <T> void assertEmbeddedTcpPath(
            String protocol,
            Supplier<RuntimeParserBinding<T>> bindingFactory,
            byte[] frame,
            Class<T> expectedValueType,
            String expectedRecordType) {
        List<ParsedRecord<T>> records = new ArrayList<>();
        List<ParseFailure> failures = new ArrayList<>();
        RuntimePipelineRunner<T> runner = new RuntimePipelineRunner<>(
                bindingFactory.get(),
                records::add,
                failures::add,
                BackpressureStrategy.acceptAll());
        EmbeddedChannel channel = new EmbeddedChannel(new TcpNettyIngressHandler<>(
                runner,
                context -> SOURCE_ID,
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)));

        channel.writeInbound(Unpooled.wrappedBuffer(frame));

        assertEquals(1, records.size());
        assertTrue(failures.isEmpty());
        ParsedRecord<T> record = records.get(0);
        assertEquals(SOURCE_ID, record.sourceId());
        assertEquals(protocol, record.protocol());
        assertEquals(expectedRecordType, record.recordType());
        assertEquals(RECEIVED_AT, record.observedAt());
        assertArrayEquals(frame, record.rawPayload());
        assertInstanceOf(expectedValueType, record.value());
        assertTrue(record.attributes().containsKey(TcpConnectionAttributes.CHANNEL_ID));
        assertTrue(record.attributes().containsKey(TcpConnectionAttributes.SESSION_ID));

        channel.finishAndReleaseAll();
    }

    private static <T> void assertRealSocketPath(
            String protocol,
            Supplier<RuntimeParserBinding<T>> bindingFactory,
            byte[] frame,
            Class<T> expectedValueType) throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        List<ParsedRecord<T>> records = new CopyOnWriteArrayList<>();
        List<ParseFailure> failures = new CopyOnWriteArrayList<>();
        TrackingRecordSink<T> recordSink = new TrackingRecordSink<>(records, received, stopped);

        try (TcpNettyServer<T> server = new TcpNettyServer<>(
                TcpNettyServerConfig.loopback(0),
                channel -> new RuntimePipelineRunner<>(
                        bindingFactory.get(),
                        recordSink,
                        failures::add,
                        BackpressureStrategy.acceptAll()),
                context -> SOURCE_ID,
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)).bind()) {
            try (Socket socket = new Socket()) {
                socket.connect(server.localAddress());
                socket.getOutputStream().write(frame);
                socket.getOutputStream().flush();

                assertTrue(received.await(3, TimeUnit.SECONDS));
                awaitActiveConnections(server, 1);
            }

            assertTrue(stopped.await(3, TimeUnit.SECONDS));
            awaitActiveConnections(server, 0);
            assertTrue(failures.isEmpty());
            assertEquals(1, records.size());
            assertEquals(protocol, records.get(0).protocol());
            assertInstanceOf(expectedValueType, records.get(0).value());
            assertArrayEquals(frame, records.get(0).rawPayload());
        }
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

    private static final class TrackingRecordSink<T> implements RecordSink<T> {

        private final List<ParsedRecord<T>> records;
        private final CountDownLatch received;
        private final CountDownLatch stopped;

        private TrackingRecordSink(
                List<ParsedRecord<T>> records,
                CountDownLatch received,
                CountDownLatch stopped) {
            this.records = records;
            this.received = received;
            this.stopped = stopped;
        }

        @Override
        public void accept(ParsedRecord<T> record) {
            records.add(record);
            received.countDown();
        }

        @Override
        public void stop() {
            stopped.countDown();
        }
    }
}
