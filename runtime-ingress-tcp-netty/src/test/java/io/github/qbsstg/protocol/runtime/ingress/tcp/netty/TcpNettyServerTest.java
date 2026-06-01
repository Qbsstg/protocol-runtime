package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RuntimeParseResult;
import io.github.qbsstg.protocol.runtime.core.RuntimeParserBinding;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TcpNettyServerTest {

    private static final Instant CONNECTED_AT = Instant.parse("2026-06-01T00:00:00Z");
    private static final SourceId SOURCE_ID = SourceId.of("tcp", "station-1");

    @Test
    void bindsEphemeralPortAndReceivesBytesFromNettyClient() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        List<ParsedRecord<byte[]>> records = new CopyOnWriteArrayList<>();

        try (TcpNettyServer<byte[]> server = new TcpNettyServer<>(
                TcpNettyServerConfig.loopback(0),
                channel -> runner(records, received)).bind()) {
            assertTrue(server.isRunning());
            assertTrue(server.port() > 0);

            writeWithNettyClient(server.localAddress(), new byte[] {1, 2, 3});

            assertTrue(received.await(3, TimeUnit.SECONDS));
            assertEquals(1, records.size());
            assertArrayEquals(new byte[] {1, 2, 3}, records.get(0).value());
            assertEquals("tcp", records.get(0).sourceId().namespace());
        }
    }

    @Test
    void exposesActiveSessionWithStableSourceAndSocketAttributes() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);

        try (TcpNettyServer<byte[]> server = new TcpNettyServer<>(
                TcpNettyServerConfig.loopback(0),
                channel -> lifecycleRunner(started, stopped),
                context -> SOURCE_ID,
                Clock.fixed(CONNECTED_AT, ZoneOffset.UTC)).bind();
                ClientConnection client = connectClient(server.localAddress())) {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            awaitActiveConnections(server, 1);

            TcpConnectionSession session = server.activeSessions().get(0);
            assertEquals(SOURCE_ID, session.sourceId());
            assertEquals(session.channelId(), session.attributes().get(TcpConnectionAttributes.CHANNEL_ID));
            assertEquals(session.sessionId(), session.attributes().get(TcpConnectionAttributes.SESSION_ID));
            assertEquals(SOURCE_ID.namespace(), session.attributes().get(TcpConnectionAttributes.SOURCE_NAMESPACE));
            assertEquals(SOURCE_ID.value(), session.attributes().get(TcpConnectionAttributes.SOURCE_VALUE));
            assertEquals(CONNECTED_AT.toString(), session.attributes().get(TcpConnectionAttributes.CONNECTED_AT));
            assertFalse(session.localAddress().isBlank());
            assertFalse(session.remoteAddress().isBlank());

            client.close();
            assertTrue(stopped.await(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void createsRunnerForEachAcceptedConnection() throws Exception {
        CountDownLatch received = new CountDownLatch(2);
        AtomicInteger createdRunners = new AtomicInteger();
        List<ParsedRecord<byte[]>> records = new CopyOnWriteArrayList<>();

        try (TcpNettyServer<byte[]> server = new TcpNettyServer<>(
                TcpNettyServerConfig.loopback(0),
                channel -> {
                    createdRunners.incrementAndGet();
                    return runner(records, received);
                }).bind()) {
            writeWithNettyClient(server.localAddress(), new byte[] {1});
            writeWithNettyClient(server.localAddress(), new byte[] {2});

            assertTrue(received.await(3, TimeUnit.SECONDS));
            assertEquals(2, createdRunners.get());
            assertEquals(2, records.size());
        }
    }

    @Test
    void stopClosesActiveClientsAndClearsRegistry() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        ClientConnection client = null;
        try (TcpNettyServer<byte[]> server = new TcpNettyServer<>(
                TcpNettyServerConfig.loopback(0),
                channel -> lifecycleRunner(started, stopped)).bind()) {
            client = connectClient(server.localAddress());

            assertTrue(started.await(3, TimeUnit.SECONDS));
            awaitActiveConnections(server, 1);

            server.stop();

            assertTrue(stopped.await(3, TimeUnit.SECONDS));
            assertTrue(client.channel().closeFuture().await(3, TimeUnit.SECONDS));
            assertFalse(client.channel().isOpen());
            assertEquals(0, server.activeConnectionCount());
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    @Test
    void shutsDownGracefullyAndRejectsUnboundAddressAccess() {
        TcpNettyServer<byte[]> server = new TcpNettyServer<>(
                TcpNettyServerConfig.loopback(0),
                channel -> runner(new CopyOnWriteArrayList<>(), new CountDownLatch(0)));

        assertFalse(server.isRunning());
        assertThrows(IllegalStateException.class, server::localAddress);

        server.bind();
        assertTrue(server.isRunning());

        server.stop();
        assertFalse(server.isRunning());
        assertThrows(IllegalStateException.class, server::localAddress);

        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void validatesServerConfig() {
        assertThrows(IllegalArgumentException.class, () -> TcpNettyServerConfig.loopback(-1));
        assertThrows(IllegalArgumentException.class, () -> TcpNettyServerConfig.loopback(65536));
        assertThrows(IllegalArgumentException.class, () -> new TcpNettyServerConfig(" ", 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TcpNettyServerConfig("127.0.0.1", 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TcpNettyServerConfig("127.0.0.1", 0, 1, 0));
    }

    private static RuntimePipelineRunner<byte[]> runner(
            List<ParsedRecord<byte[]>> records,
            CountDownLatch received) {
        return new RuntimePipelineRunner<>(
                new EchoBinding(),
                record -> {
                    records.add(record);
                    received.countDown();
                },
                FailureSink.noop(),
                BackpressureStrategy.acceptAll());
    }

    private static RuntimePipelineRunner<byte[]> lifecycleRunner(
            CountDownLatch started,
            CountDownLatch stopped) {
        return new RuntimePipelineRunner<>(
                new LifecycleBinding(started, stopped),
                ignored -> {
                },
                FailureSink.noop(),
                BackpressureStrategy.acceptAll());
    }

    private static void writeWithNettyClient(InetSocketAddress address, byte[] payload) {
        try (ClientConnection client = connectClient(address)) {
            client.channel().writeAndFlush(Unpooled.wrappedBuffer(payload)).syncUninterruptibly();
        }
    }

    private static ClientConnection connectClient(InetSocketAddress address) {
        EventLoopGroup group = new NioEventLoopGroup(1);
        Channel channel = null;
        try {
            channel = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .connect(address)
                    .syncUninterruptibly()
                    .channel();
            return new ClientConnection(group, channel);
        } catch (RuntimeException ex) {
            if (channel != null) {
                channel.close().syncUninterruptibly();
            }
            group.shutdownGracefully().syncUninterruptibly();
            throw ex;
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

    private record ClientConnection(EventLoopGroup group, Channel channel) implements AutoCloseable {

        @Override
        public void close() {
            try {
                if (channel != null && channel.isOpen()) {
                    channel.close().syncUninterruptibly();
                }
            } finally {
                group.shutdownGracefully().syncUninterruptibly();
            }
        }
    }

    private static final class LifecycleBinding implements RuntimeParserBinding<byte[]> {

        private final CountDownLatch started;
        private final CountDownLatch stopped;

        private LifecycleBinding(CountDownLatch started, CountDownLatch stopped) {
            this.started = started;
            this.stopped = stopped;
        }

        @Override
        public String protocol() {
            return "test";
        }

        @Override
        public void start() {
            started.countDown();
        }

        @Override
        public void stop() {
            stopped.countDown();
        }

        @Override
        public List<RuntimeParseResult<byte[]>> parse(IngressEnvelope envelope) {
            return List.of();
        }
    }

    private static final class EchoBinding implements RuntimeParserBinding<byte[]> {

        @Override
        public String protocol() {
            return "test";
        }

        @Override
        public List<RuntimeParseResult<byte[]>> parse(IngressEnvelope envelope) {
            return List.of(RuntimeParseResult.success(new ParsedRecord<>(
                    envelope.sourceId(),
                    protocol(),
                    "bytes",
                    envelope.payload(),
                    envelope.payload(),
                    Instant.EPOCH,
                    Map.of())));
        }
    }
}
