package io.github.qbsstg.protocol.runtime.ingress.http;

import com.sun.net.httpserver.HttpServer;
import io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HttpIngressServer<T> implements RuntimeLifecycle {

    private final HttpIngressServerConfig config;
    private final RuntimePipelineRunner<T> runner;
    private final Clock clock;
    private HttpServer server;
    private ExecutorService executor;

    public HttpIngressServer(
            HttpIngressServerConfig config,
            RuntimePipelineRunner<T> runner) {
        this(config, runner, Clock.systemUTC());
    }

    public HttpIngressServer(
            HttpIngressServerConfig config,
            RuntimePipelineRunner<T> runner,
            Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public HttpIngressServer<T> bind() {
        start();
        return this;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        try {
            HttpServer created = HttpServer.create(
                    new InetSocketAddress(config.host(), config.port()),
                    config.backlog());
            ExecutorService createdExecutor = Executors.newFixedThreadPool(config.workerThreads());
            created.createContext(config.contextPath(), new HttpIngressHandler<>(config, runner, clock));
            created.setExecutor(createdExecutor);
            runner.start();
            created.start();
            server = created;
            executor = createdExecutor;
        } catch (IOException | RuntimeException ex) {
            stop();
            throw new IllegalStateException("failed to start HTTP ingress server", ex);
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized InetSocketAddress localAddress() {
        if (server == null) {
            throw new IllegalStateException("server is not bound");
        }
        return server.getAddress();
    }

    public synchronized int port() {
        return localAddress().getPort();
    }

    @Override
    public synchronized void stop() {
        HttpServer current = server;
        ExecutorService currentExecutor = executor;
        server = null;
        executor = null;
        if (current != null) {
            current.stop(0);
        }
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
        runner.stop();
    }
}
