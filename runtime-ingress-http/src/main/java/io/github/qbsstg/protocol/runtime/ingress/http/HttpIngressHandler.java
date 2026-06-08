package io.github.qbsstg.protocol.runtime.ingress.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class HttpIngressHandler<T> implements HttpHandler {

    private final HttpIngressServerConfig config;
    private final RuntimePipelineRunner<T> runner;
    private final Clock clock;

    HttpIngressHandler(
            HttpIngressServerConfig config,
            RuntimePipelineRunner<T> runner,
            Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                send(exchange, 405, "method_not_allowed");
                return;
            }

            SourceId sourceId;
            try {
                sourceId = resolveSourceId(exchange);
            } catch (IllegalArgumentException ex) {
                send(exchange, 400, "invalid_source_id");
                return;
            }

            byte[] payload;
            try {
                payload = readPayload(exchange.getRequestBody());
            } catch (PayloadTooLargeException ex) {
                send(exchange, 413, "payload_too_large");
                return;
            }

            IngressEnvelope envelope = new IngressEnvelope(
                    sourceId,
                    HttpIngressModule.TRANSPORT,
                    payload,
                    clock.instant(),
                    attributes(exchange, sourceId));
            BackpressureDecision decision = runner.accept(envelope);
            respond(exchange, decision);
        }
    }

    private SourceId resolveSourceId(HttpExchange exchange) {
        return switch (config.sourceIdMode()) {
            case CONFIGURED -> config.configuredSourceId();
            case HEADER -> parseSourceId(exchange.getRequestHeaders().getFirst(config.sourceIdHeader()));
            case PATH -> parseSourceId(pathSourceValue(exchange.getRequestURI()));
        };
    }

    private String pathSourceValue(URI uri) {
        String rawPath = uri.getRawPath();
        String contextPath = config.contextPath();
        if (!rawPath.startsWith(contextPath)) {
            throw new IllegalArgumentException("request path does not match configured context");
        }
        String rawSource = rawPath.substring(contextPath.length());
        if (rawSource.isBlank() || rawSource.contains("/")) {
            throw new IllegalArgumentException("path source id must be a single segment");
        }
        return URLDecoder.decode(rawSource, StandardCharsets.UTF_8);
    }

    private SourceId parseSourceId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("source id must not be blank");
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("source id must use namespace:value");
        }
        return SourceId.of(value.substring(0, separator), value.substring(separator + 1));
    }

    private byte[] readPayload(InputStream input) throws IOException {
        int maxPayloadBytes = config.maxPayloadBytes();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (maxPayloadBytes > 0 && total > maxPayloadBytes) {
                throw new PayloadTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private Map<String, String> attributes(HttpExchange exchange, SourceId sourceId) {
        Map<String, String> attributes = new LinkedHashMap<>();
        URI uri = exchange.getRequestURI();
        put(attributes, HttpIngressAttributes.LISTENER_NAME, config.listenerName());
        put(attributes, HttpIngressAttributes.METHOD, exchange.getRequestMethod());
        put(attributes, HttpIngressAttributes.PATH, uri.getPath());
        put(attributes, HttpIngressAttributes.QUERY, uri.getRawQuery());
        put(attributes, HttpIngressAttributes.CONTENT_TYPE, exchange.getRequestHeaders().getFirst("Content-Type"));
        put(attributes, HttpIngressAttributes.REMOTE_ADDRESS, String.valueOf(exchange.getRemoteAddress()));
        put(attributes, HttpIngressAttributes.REQUEST_ID, UUID.randomUUID().toString());
        put(attributes, HttpIngressAttributes.RESPONSE_MODE, config.responseMode().name());
        put(attributes, HttpIngressAttributes.SOURCE_ID_MODE, config.sourceIdMode().name());
        put(attributes, HttpIngressAttributes.SOURCE_NAMESPACE, sourceId.namespace());
        put(attributes, HttpIngressAttributes.SOURCE_VALUE, sourceId.value());
        put(attributes, HttpIngressAttributes.PROTOCOL, runner.protocol());
        return attributes;
    }

    private void respond(HttpExchange exchange, BackpressureDecision decision) throws IOException {
        if (decision == BackpressureDecision.RETRY_LATER) {
            exchange.getResponseHeaders().set("Retry-After", "1");
            send(exchange, 503, "retry_later");
            return;
        }
        if (decision == BackpressureDecision.DROP) {
            send(exchange, 202, "dropped");
            return;
        }
        send(exchange, 202, "accepted");
    }

    private void send(HttpExchange exchange, int status, String message) throws IOException {
        if (config.responseMode() == HttpIngressResponseMode.NO_BODY || status == 204) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] body = ("{\"status\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void put(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    private static final class PayloadTooLargeException extends IOException {
    }
}
