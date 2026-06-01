package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.netty.channel.Channel;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TcpConnectionRegistry {

    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TcpConnectionSession> sessions = new ConcurrentHashMap<>();

    public void register(Channel channel, TcpConnectionSession session) {
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(session, "session must not be null");
        channels.put(session.sessionId(), channel);
        sessions.put(session.sessionId(), session);
        channel.closeFuture().addListener(ignored -> unregister(session));
    }

    public void unregister(TcpConnectionSession session) {
        if (session == null) {
            return;
        }
        channels.remove(session.sessionId());
        sessions.remove(session.sessionId());
    }

    public int activeCount() {
        return sessions.size();
    }

    public boolean contains(String sessionId) {
        return sessionId != null && sessions.containsKey(sessionId);
    }

    public List<TcpConnectionSession> activeSessions() {
        return List.copyOf(sessions.values());
    }

    public void closeAll() {
        for (Channel channel : List.copyOf(channels.values())) {
            if (channel.isOpen()) {
                channel.close().syncUninterruptibly();
            }
        }
    }
}
