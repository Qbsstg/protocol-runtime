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
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TcpNettyServerTest {

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

    private static void writeWithNettyClient(InetSocketAddress address, byte[] payload) {
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
            channel.writeAndFlush(Unpooled.wrappedBuffer(payload)).syncUninterruptibly();
        } finally {
            if (channel != null) {
                channel.close().syncUninterruptibly();
            }
            group.shutdownGracefully().syncUninterruptibly();
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
