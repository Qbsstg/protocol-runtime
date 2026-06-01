package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.iec104.Iec104Frame;
import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.iec104.Iec104RuntimeBinding;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServer;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

public final class StandaloneCollector implements RuntimeLifecycle {

    private final StandaloneCollectorConfig config;
    private final RuntimeSinks sinks;
    private final TcpNettyServer<Iec104Frame> server;
    private final CountDownLatch stopped = new CountDownLatch(1);

    private StandaloneCollector(StandaloneCollectorConfig config, RuntimeSinks sinks) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.sinks = Objects.requireNonNull(sinks, "sinks must not be null");
        this.server = new TcpNettyServer<>(
                config.tcp(),
                channel -> new RuntimePipelineRunner<>(
                        new Iec104RuntimeBinding(config.strictAsduParsing()),
                        sinks.runnerRecordSink(),
                        sinks.runnerFailureSink(),
                        BackpressureStrategy.fixed(config.backpressureDecision())),
                context -> config.sourceId(),
                Clock.systemUTC());
    }

    public static StandaloneCollector create(StandaloneCollectorConfig config) {
        return new StandaloneCollector(config, RuntimeSinks.from(config));
    }

    public StandaloneCollectorConfig config() {
        return config;
    }

    public Optional<InMemoryRuntimeSink<Iec104Frame>> inMemorySink() {
        return sinks.inMemory();
    }

    public boolean isRunning() {
        return server.isRunning();
    }

    public InetSocketAddress localAddress() {
        return server.localAddress();
    }

    public int port() {
        return server.port();
    }

    public int activeConnectionCount() {
        return server.activeConnectionCount();
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        RuntimeException failure = null;
        try {
            server.stop();
        } catch (RuntimeException ex) {
            failure = ex;
        }
        try {
            sinks.stop();
        } catch (RuntimeException ex) {
            if (failure == null) {
                failure = ex;
            } else {
                failure.addSuppressed(ex);
            }
        } finally {
            stopped.countDown();
        }
        if (failure != null) {
            throw failure;
        }
    }

    public void awaitShutdown() throws InterruptedException {
        stopped.await();
    }
}
