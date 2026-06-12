package io.github.qbsstg.protocol.runtime.app;

import com.sun.net.httpserver.HttpExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

final class ManagementAccessController {

    ManagementAccessDecision authorize(ManagementServerConfig config, HttpExchange exchange) {
        InetSocketAddress remote = exchange.getRemoteAddress();
        InetAddress remoteAddress = remote == null ? null : remote.getAddress();
        return authorize(config, remoteAddress, exchange.getRequestHeaders().get("Authorization"),
                exchange.getRequestHeaders().get("X-Management-Token"));
    }

    ManagementAccessDecision authorize(
            ManagementServerConfig config,
            InetAddress remoteAddress,
            List<String> authorizationHeaders,
            List<String> tokenHeaders) {
        return switch (config.accessMode()) {
            case OPEN -> ManagementAccessDecision.granted();
            case LOCAL -> authorizeLocal(remoteAddress);
            case TOKEN -> authorizeToken(config.token(), authorizationHeaders, tokenHeaders);
        };
    }

    private ManagementAccessDecision authorizeLocal(InetAddress remoteAddress) {
        if (remoteAddress != null && remoteAddress.isLoopbackAddress()) {
            return ManagementAccessDecision.granted();
        }
        return ManagementAccessDecision.forbidden("non_local_remote");
    }

    private ManagementAccessDecision authorizeToken(
            String expectedToken,
            List<String> authorizationHeaders,
            List<String> tokenHeaders) {
        String presentedToken = bearerToken(authorizationHeaders);
        if (presentedToken == null) {
            presentedToken = firstHeader(tokenHeaders);
        }
        if (presentedToken == null || presentedToken.isBlank()) {
            return ManagementAccessDecision.unauthorized("missing_token");
        }
        if (!constantTimeEquals(expectedToken, presentedToken.trim())) {
            return ManagementAccessDecision.unauthorized("invalid_token");
        }
        return ManagementAccessDecision.granted();
    }

    private static String bearerToken(List<String> headers) {
        String value = firstHeader(headers);
        if (value == null) {
            return null;
        }
        String prefix = "Bearer ";
        if (!value.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        return value.substring(prefix.length()).trim();
    }

    private static String firstHeader(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
