package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@FunctionalInterface
public interface TcpSourceIdResolver {

    SourceId resolve(ChannelHandlerContext context);

    static TcpSourceIdResolver remoteAddress(String namespace) {
        return context -> SourceId.of(
                namespace,
                sourceValue(context.channel().remoteAddress(), context.channel().id().asShortText()));
    }

    static TcpSourceIdResolver channelId(String namespace) {
        return context -> SourceId.of(namespace, context.channel().id().asShortText());
    }

    static String sourceValue(SocketAddress address, String fallback) {
        if (address instanceof InetSocketAddress inetAddress) {
            String host = inetAddress.getAddress() == null
                    ? inetAddress.getHostString()
                    : inetAddress.getAddress().getHostAddress();
            return host + ":" + inetAddress.getPort();
        }
        if (address != null) {
            return address.toString();
        }
        return fallback;
    }
}
