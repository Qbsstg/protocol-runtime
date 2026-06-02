package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.iec104.Iec104Frame;
import io.github.qbsstg.protocol.runtime.core.BackpressureStrategy;
import io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.iec104.Iec104RuntimeBinding;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServer;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

public final class StandaloneCollector implements RuntimeLifecycle {

    private final StandaloneCollectorAppConfig appConfig;
    private final RuntimeSinks sinks;
    private final List<TcpNettyServer<Iec104Frame>> servers;
    private final CountDownLatch stopped = new CountDownLatch(1);

    private StandaloneCollector(StandaloneCollectorAppConfig appConfig, RuntimeSinks sinks) {
        this.appConfig = Objects.requireNonNull(appConfig, "appConfig must not be null");
        this.sinks = Objects.requireNonNull(sinks, "sinks must not be null");
        this.servers = appConfig.tcpListeners().stream()
                .map(listener -> new TcpNettyServer<Iec104Frame>(
                        listener.tcp(),
                        channel -> new RuntimePipelineRunner<>(
                                new Iec104RuntimeBinding(appConfig.strictAsduParsing()),
                                sinks.runnerRecordSink(),
                                sinks.runnerFailureSink(),
                                BackpressureStrategy.fixed(appConfig.backpressureDecision())),
                        context -> listener.sourceId(),
                        Clock.systemUTC()))
                .toList();
    }

    public static StandaloneCollector create(StandaloneCollectorConfig config) {
        return create(StandaloneCollectorAppConfig.fromSingle(config));
    }

    public static StandaloneCollector create(StandaloneCollectorAppConfig config) {
        return new StandaloneCollector(config, RuntimeSinks.from(config));
    }

    public StandaloneCollectorConfig config() {
        return appConfig.singleCollectorConfig();
    }

    public StandaloneCollectorAppConfig appConfig() {
        return appConfig;
    }

    public Optional<InMemoryRuntimeSink<Iec104Frame>> inMemorySink() {
        return sinks.inMemory();
    }

    public boolean isRunning() {
        return !servers.isEmpty() && servers.stream().allMatch(TcpNettyServer::isRunning);
    }

    public InetSocketAddress localAddress() {
        return singleServer().localAddress();
    }

    public List<InetSocketAddress> localAddresses() {
        return servers.stream().map(TcpNettyServer::localAddress).toList();
    }

    public int port() {
        return singleServer().port();
    }

    public List<Integer> ports() {
        return servers.stream().map(TcpNettyServer::port).toList();
    }

    public int activeConnectionCount() {
        return servers.stream().mapToInt(TcpNettyServer::activeConnectionCount).sum();
    }

    @Override
    public void start() {
        RuntimeException failure = null;
        for (TcpNettyServer<Iec104Frame> server : servers) {
            try {
                server.start();
            } catch (RuntimeException ex) {
                failure = ex;
                break;
            }
        }
        if (failure != null) {
            try {
                stop();
            } catch (RuntimeException stopFailure) {
                failure.addSuppressed(stopFailure);
            }
            throw failure;
        }
    }

    @Override
    public void stop() {
        RuntimeException failure = null;
        for (TcpNettyServer<Iec104Frame> server : servers) {
            try {
                server.stop();
            } catch (RuntimeException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
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

    private TcpNettyServer<Iec104Frame> singleServer() {
        if (servers.size() != 1) {
            throw new IllegalStateException("single listener API requires exactly one TCP listener");
        }
        return servers.get(0);
    }
}
