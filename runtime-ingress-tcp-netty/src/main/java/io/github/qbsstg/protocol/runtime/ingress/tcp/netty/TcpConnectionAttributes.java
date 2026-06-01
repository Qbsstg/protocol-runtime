package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.netty.channel.Channel;

import java.net.SocketAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TcpConnectionAttributes {

    public static final String CHANNEL_ID = "tcp.channel.id";
    public static final String SESSION_ID = "tcp.session.id";
    public static final String SOURCE_NAMESPACE = "tcp.source.namespace";
    public static final String SOURCE_VALUE = "tcp.source.value";
    public static final String LOCAL_ADDRESS = "tcp.local.address";
    public static final String REMOTE_ADDRESS = "tcp.remote.address";
    public static final String CONNECTED_AT = "tcp.connected.at";

    private TcpConnectionAttributes() {
    }

    public static Map<String, String> from(Channel channel) {
        return from(channel, null, null);
    }

    public static Map<String, String> from(Channel channel, SourceId sourceId, Instant connectedAt) {
        Objects.requireNonNull(channel, "channel must not be null");
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put(CHANNEL_ID, channel.id().asShortText());
        attributes.put(SESSION_ID, channel.id().asLongText());
        if (sourceId != null) {
            attributes.put(SOURCE_NAMESPACE, sourceId.namespace());
            attributes.put(SOURCE_VALUE, sourceId.value());
        }
        putAddress(attributes, LOCAL_ADDRESS, channel.localAddress());
        putAddress(attributes, REMOTE_ADDRESS, channel.remoteAddress());
        if (connectedAt != null) {
            attributes.put(CONNECTED_AT, connectedAt.toString());
        }
        return Map.copyOf(attributes);
    }

    private static void putAddress(
            Map<String, String> attributes,
            String key,
            SocketAddress address) {
        if (address != null) {
            attributes.put(key, TcpSourceIdResolver.sourceValue(address, ""));
        }
    }
}
