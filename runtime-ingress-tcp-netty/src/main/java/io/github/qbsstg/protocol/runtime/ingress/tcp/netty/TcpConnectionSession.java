package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.netty.channel.Channel;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record TcpConnectionSession(
        SourceId sourceId,
        String channelId,
        String sessionId,
        String localAddress,
        String remoteAddress,
        Instant connectedAt,
        Map<String, String> attributes) {

    public TcpConnectionSession {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        channelId = requireNonBlank(channelId, "channelId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        localAddress = localAddress == null ? "" : localAddress;
        remoteAddress = remoteAddress == null ? "" : remoteAddress;
        connectedAt = Objects.requireNonNull(connectedAt, "connectedAt must not be null");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static TcpConnectionSession from(Channel channel, SourceId sourceId, Instant connectedAt) {
        Objects.requireNonNull(channel, "channel must not be null");
        Map<String, String> attributes = TcpConnectionAttributes.from(channel, sourceId, connectedAt);
        return new TcpConnectionSession(
                sourceId,
                attributes.get(TcpConnectionAttributes.CHANNEL_ID),
                attributes.get(TcpConnectionAttributes.SESSION_ID),
                attributes.get(TcpConnectionAttributes.LOCAL_ADDRESS),
                attributes.get(TcpConnectionAttributes.REMOTE_ADDRESS),
                connectedAt,
                attributes);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
