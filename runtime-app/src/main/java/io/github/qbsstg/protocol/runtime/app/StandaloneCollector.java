package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.iec104.Iec104Frame;
import io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.iec104.Iec104RuntimeBinding;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServer;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

public final class StandaloneCollector implements RuntimeLifecycle {

    private final StandaloneCollectorAppConfig appConfig;
    private final RuntimeSinks sinks;
    private final List<ListenerRuntime> listeners;
    private final Clock clock;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private CollectorLifecycleState state = CollectorLifecycleState.CONFIGURED;
    private Instant startedAt;
    private Instant stoppedAt;
    private String startupFailureReason;
    private Throwable lastException;

    private StandaloneCollector(StandaloneCollectorAppConfig appConfig, RuntimeSinks sinks) {
        this(appConfig, sinks, Clock.systemUTC());
    }

    private StandaloneCollector(StandaloneCollectorAppConfig appConfig, RuntimeSinks sinks, Clock clock) {
        this.appConfig = Objects.requireNonNull(appConfig, "appConfig must not be null");
        this.sinks = Objects.requireNonNull(sinks, "sinks must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.listeners = appConfig.tcpListeners().stream()
                .map(listener -> new ListenerRuntime(
                        listener,
                        new TcpNettyServer<Iec104Frame>(
                                listener.tcp(),
                                channel -> new RuntimePipelineRunner<>(
                                        new Iec104RuntimeBinding(appConfig.strictAsduParsing()),
                                        sinks.runnerRecordSink(),
                                        sinks.runnerFailureSink(),
                                        sinks.backpressureStrategy(appConfig)),
                                context -> listener.sourceId(),
                                clock)))
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

    public synchronized CollectorLifecycleState state() {
        return state;
    }

    public synchronized CollectorStatusSnapshot statusSnapshot() {
        List<CollectorSourceStatus> sourceStatuses = appConfig.sources().stream()
                .map(source -> new CollectorSourceStatus(source.name(), source.sourceId().qualifiedValue()))
                .toList();
        List<TcpListenerStatus> listenerStatuses = listeners.stream()
                .map(this::listenerStatus)
                .toList();
        return new CollectorStatusSnapshot(
                state,
                startedAt,
                stoppedAt,
                startupFailureReason,
                lastException == null ? null : lastException.getClass().getName(),
                lastException == null ? null : lastException.getMessage(),
                sourceStatuses,
                listenerStatuses,
                listenerStatuses.stream().mapToInt(TcpListenerStatus::activeConnectionCount).sum(),
                sinks.metricsSnapshot(),
                appConfig.sinkType(),
                appConfig.fileSinkRotation(),
                appConfig.backpressureDecision(),
                appConfig.backpressureMaxPayloadBytes(),
                appConfig.oversizedPayloadDecision(),
                appConfig.strictAsduParsing());
    }

    public synchronized boolean isRunning() {
        return state == CollectorLifecycleState.RUNNING
                && !listeners.isEmpty()
                && listeners.stream().allMatch(listener -> listener.server().isRunning());
    }

    public synchronized InetSocketAddress localAddress() {
        return singleServer().localAddress();
    }

    public synchronized List<InetSocketAddress> localAddresses() {
        return listeners.stream().map(listener -> listener.server().localAddress()).toList();
    }

    public synchronized int port() {
        return singleServer().port();
    }

    public synchronized List<Integer> ports() {
        return listeners.stream().map(listener -> listener.server().port()).toList();
    }

    public synchronized int activeConnectionCount() {
        return listeners.stream().mapToInt(listener -> listener.server().activeConnectionCount()).sum();
    }

    @Override
    public synchronized void start() {
        if (state == CollectorLifecycleState.RUNNING || state == CollectorLifecycleState.STARTING) {
            return;
        }
        if (state == CollectorLifecycleState.STOPPING) {
            IllegalStateException failure = new IllegalStateException("collector is stopping");
            lastException = failure;
            throw failure;
        }
        if (state == CollectorLifecycleState.STOPPED || state == CollectorLifecycleState.FAILED) {
            IllegalStateException failure = new IllegalStateException("collector cannot be restarted after " + state);
            lastException = failure;
            throw failure;
        }
        state = CollectorLifecycleState.STARTING;
        stoppedAt = null;
        startupFailureReason = null;
        lastException = null;
        Exception failure = null;
        ListenerRuntime failedListener = null;
        for (ListenerRuntime listener : listeners) {
            try {
                listener.server().start();
            } catch (Exception ex) {
                failure = ex;
                failedListener = listener;
                break;
            }
        }
        if (failure != null) {
            recordStartupFailure(failedListener, failure);
            rollbackAfterStartFailure(failure);
            throw asRuntimeException(failure);
        }
        startedAt = clock.instant();
        state = CollectorLifecycleState.RUNNING;
    }

    @Override
    public synchronized void stop() {
        if (state == CollectorLifecycleState.STOPPED) {
            stopped.countDown();
            return;
        }
        if (state == CollectorLifecycleState.CONFIGURED) {
            stoppedAt = clock.instant();
            state = CollectorLifecycleState.STOPPED;
            stopped.countDown();
            return;
        }
        boolean wasFailed = state == CollectorLifecycleState.FAILED;
        if (!wasFailed) {
            state = CollectorLifecycleState.STOPPING;
        }
        RuntimeException failure = null;
        for (ListenerRuntime listener : listeners) {
            try {
                listener.server().stop();
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
            stoppedAt = clock.instant();
            if (failure != null) {
                lastException = failure;
                state = CollectorLifecycleState.FAILED;
            } else if (wasFailed) {
                state = CollectorLifecycleState.FAILED;
            } else {
                state = CollectorLifecycleState.STOPPED;
            }
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
        if (listeners.size() != 1) {
            throw new IllegalStateException("single listener API requires exactly one TCP listener");
        }
        return listeners.get(0).server();
    }

    private TcpListenerStatus listenerStatus(ListenerRuntime runtime) {
        TcpListenerConfig config = runtime.config();
        TcpNettyServer<Iec104Frame> server = runtime.server();
        boolean running = server.isRunning();
        String boundHost = null;
        Integer boundPort = null;
        if (running) {
            InetSocketAddress localAddress = server.localAddress();
            boundHost = localAddress.getHostString();
            boundPort = localAddress.getPort();
        }
        return new TcpListenerStatus(
                config.name(),
                config.sourceName(),
                config.sourceId().qualifiedValue(),
                config.tcp().host(),
                config.tcp().port(),
                boundHost,
                boundPort,
                running,
                server.activeConnectionCount());
    }

    private void recordStartupFailure(ListenerRuntime failedListener, Exception failure) {
        String listenerName = failedListener == null ? "unknown" : failedListener.config().name();
        startupFailureReason = "Failed to start TCP listener " + listenerName + ": " + failure.getMessage();
        lastException = failure;
        state = CollectorLifecycleState.FAILED;
    }

    private void rollbackAfterStartFailure(Exception failure) {
        for (ListenerRuntime listener : listeners) {
            try {
                listener.server().stop();
            } catch (RuntimeException ex) {
                failure.addSuppressed(ex);
            }
        }
        try {
            sinks.stop();
        } catch (RuntimeException ex) {
            failure.addSuppressed(ex);
        } finally {
            stoppedAt = clock.instant();
            stopped.countDown();
        }
    }

    private static RuntimeException asRuntimeException(Exception failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("collector start failed", failure);
    }

    private record ListenerRuntime(
            TcpListenerConfig config,
            TcpNettyServer<Iec104Frame> server) {
    }
}
