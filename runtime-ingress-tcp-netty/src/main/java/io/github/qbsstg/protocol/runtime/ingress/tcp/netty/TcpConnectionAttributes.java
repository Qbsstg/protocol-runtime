package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.netty.channel.Channel;

import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TcpConnectionAttributes {

    public static final String CHANNEL_ID = "tcp.channel.id";
    public static final String SESSION_ID = "tcp.session.id";
    public static final String LOCAL_ADDRESS = "tcp.local.address";
    public static final String REMOTE_ADDRESS = "tcp.remote.address";

    private TcpConnectionAttributes() {
    }

    public static Map<String, String> from(Channel channel) {
        Objects.requireNonNull(channel, "channel must not be null");
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put(CHANNEL_ID, channel.id().asShortText());
        attributes.put(SESSION_ID, channel.id().asLongText());
        putAddress(attributes, LOCAL_ADDRESS, channel.localAddress());
        putAddress(attributes, REMOTE_ADDRESS, channel.remoteAddress());
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
