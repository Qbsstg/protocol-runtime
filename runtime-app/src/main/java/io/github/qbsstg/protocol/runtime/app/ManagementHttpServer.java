package io.github.qbsstg.protocol.runtime.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

final class ManagementHttpServer implements RuntimeLifecycle {

    private static final String CONTENT_TYPE = "application/json; charset=utf-8";

    private final ManagementServerConfig config;
    private final Supplier<CollectorStatusSnapshot> snapshotSupplier;
    private HttpServer server;
    private ExecutorService executor;
    private InetSocketAddress localAddress;

    ManagementHttpServer(
            ManagementServerConfig config,
            Supplier<CollectorStatusSnapshot> snapshotSupplier) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier must not be null");
    }

    @Override
    public synchronized void start() {
        if (!config.enabled() || server != null) {
            return;
        }
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
            httpServer.createContext(config.healthPath(), exchange -> handleHealth(exchange, config.healthPath()));
            httpServer.createContext(config.readinessPath(), exchange -> handleReadiness(exchange, config.readinessPath()));
            httpServer.createContext(config.statusPath(), exchange -> handleStatus(exchange, config.statusPath()));
            executor = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "protocol-runtime-management");
                thread.setDaemon(true);
                return thread;
            });
            httpServer.setExecutor(executor);
            httpServer.start();
            server = httpServer;
            localAddress = httpServer.getAddress();
        } catch (IOException ex) {
            throw new IllegalStateException("management HTTP server failed to bind "
                    + config.host() + ":" + config.port(), ex);
        }
    }

    @Override
    public synchronized void stop() {
        HttpServer current = server;
        server = null;
        localAddress = null;
        if (current != null) {
            current.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    synchronized boolean isRunning() {
        return server != null;
    }

    synchronized InetSocketAddress localAddress() {
        if (localAddress == null) {
            throw new IllegalStateException("management HTTP server is not running");
        }
        return localAddress;
    }

    synchronized int port() {
        return localAddress().getPort();
    }

    private void handleHealth(HttpExchange exchange, String expectedPath) throws IOException {
        if (!validateGet(exchange, expectedPath)) {
            return;
        }
        CollectorStatusSnapshot snapshot = snapshotSupplier.get();
        int statusCode = snapshot.health().health() == CollectorHealthState.FAILED ? 503 : 200;
        writeJson(exchange, statusCode, CollectorStatusJson.health(snapshot));
    }

    private void handleReadiness(HttpExchange exchange, String expectedPath) throws IOException {
        if (!validateGet(exchange, expectedPath)) {
            return;
        }
        CollectorStatusSnapshot snapshot = snapshotSupplier.get();
        int statusCode = snapshot.health().readiness() == CollectorReadinessState.READY ? 200 : 503;
        writeJson(exchange, statusCode, CollectorStatusJson.readiness(snapshot));
    }

    private void handleStatus(HttpExchange exchange, String expectedPath) throws IOException {
        if (!validateGet(exchange, expectedPath)) {
            return;
        }
        writeJson(exchange, 200, CollectorStatusJson.status(snapshotSupplier.get()));
    }

    private boolean validateGet(HttpExchange exchange, String expectedPath) throws IOException {
        if (!expectedPath.equals(exchange.getRequestURI().getPath())) {
            writeJson(exchange, 404, "{\"error\":\"not_found\"}");
            return false;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            writeJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return false;
        }
        return true;
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
