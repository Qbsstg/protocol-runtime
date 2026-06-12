package io.github.qbsstg.protocol.runtime.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

final class ManagementHttpServer implements RuntimeLifecycle {

    private static final String CONTENT_TYPE = "application/json; charset=utf-8";

    private final ManagementServerConfig config;
    private final Supplier<CollectorStatusSnapshot> snapshotSupplier;
    private final ManagementAccessController accessController = new ManagementAccessController();
    private final ManagementRuntimeMetrics metrics = new ManagementRuntimeMetrics();
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
            httpServer.createContext("/", this::handle);
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

    ManagementMetricsSnapshot metricsSnapshot() {
        return metrics.snapshot();
    }

    private void handle(HttpExchange exchange) throws IOException {
        long startNanos = System.nanoTime();
        int statusCode = 500;
        String rejectionReason = null;
        String path = path(exchange);
        try {
            ManagementAccessDecision access = accessController.authorize(config, exchange);
            if (!access.allowed()) {
                rejectionReason = access.rejectionReason();
                if (access.statusCode() == 401) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                }
                statusCode = writeError(exchange, access.statusCode(), access.errorCode(), access.message(), path);
                return;
            }
            statusCode = handleAuthorized(exchange, path);
        } catch (Exception ex) {
            rejectionReason = "internal_error";
            statusCode = writeError(
                    exchange,
                    500,
                    "internal_error",
                    "Management request failed internally.",
                    path);
        } finally {
            long durationMillis = Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
            String remoteAddress = exchange.getRemoteAddress() == null
                    ? "unknown"
                    : exchange.getRemoteAddress().getAddress().getHostAddress();
            metrics.record(
                    Instant.now(),
                    exchange.getRequestMethod(),
                    path,
                    statusCode,
                    durationMillis,
                    remoteAddress,
                    rejectionReason);
            ManagementRequestLogger.log(
                    config.requestLoggingEnabled(),
                    exchange.getRequestMethod(),
                    path,
                    statusCode,
                    durationMillis,
                    remoteAddress,
                    rejectionReason);
        }
    }

    private int handleAuthorized(HttpExchange exchange, String path) throws IOException {
        if (!isManagementPath(path)) {
            return writeError(exchange, 404, "not_found", "Management endpoint was not found.", path);
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            return writeError(exchange, 405, "method_not_allowed", "Management endpoint only supports GET.", path);
        }
        if (hasRequestBody(exchange)) {
            return writeError(exchange, 400, "malformed_request", "GET management requests must not include a body.", path);
        }
        if (config.healthPath().equals(path)) {
            return handleHealth(exchange);
        }
        if (config.readinessPath().equals(path)) {
            return handleReadiness(exchange);
        }
        return handleStatus(exchange);
    }

    private int handleHealth(HttpExchange exchange) throws IOException {
        CollectorStatusSnapshot snapshot = snapshotSupplier.get();
        int statusCode = snapshot.health().health() == CollectorHealthState.FAILED ? 503 : 200;
        writeJson(exchange, statusCode, CollectorStatusJson.health(snapshot));
        return statusCode;
    }

    private int handleReadiness(HttpExchange exchange) throws IOException {
        CollectorStatusSnapshot snapshot = snapshotSupplier.get();
        int statusCode = snapshot.health().readiness() == CollectorReadinessState.READY ? 200 : 503;
        writeJson(exchange, statusCode, CollectorStatusJson.readiness(snapshot));
        return statusCode;
    }

    private int handleStatus(HttpExchange exchange) throws IOException {
        writeJson(exchange, 200, CollectorStatusJson.status(snapshotSupplier.get()));
        return 200;
    }

    private boolean isManagementPath(String path) {
        return config.healthPath().equals(path)
                || config.readinessPath().equals(path)
                || config.statusPath().equals(path);
    }

    private boolean hasRequestBody(HttpExchange exchange) {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null && !"0".equals(contentLength.trim())) {
            return true;
        }
        return exchange.getRequestHeaders().containsKey("Transfer-Encoding");
    }

    private int writeError(
            HttpExchange exchange,
            int statusCode,
            String code,
            String message,
            String path) throws IOException {
        writeJson(exchange, statusCode, CollectorStatusJson.error(statusCode, code, message, path));
        return statusCode;
    }

    private String path(HttpExchange exchange) {
        String path = exchange.getRequestURI() == null ? null : exchange.getRequestURI().getPath();
        return path == null || path.isBlank() ? "/" : path;
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
