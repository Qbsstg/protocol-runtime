package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import java.time.Instant;
import java.util.Objects;

public record TcpConnectionLifecycleEvent(
        Type type,
        TcpConnectionSession session,
        Instant observedAt,
        Throwable cause) {

    public enum Type {
        ACTIVE,
        INACTIVE,
        EXCEPTION
    }

    public TcpConnectionLifecycleEvent {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(session, "session must not be null");
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
    }

    public static TcpConnectionLifecycleEvent active(TcpConnectionSession session) {
        return new TcpConnectionLifecycleEvent(Type.ACTIVE, session, session.connectedAt(), null);
    }

    public static TcpConnectionLifecycleEvent inactive(TcpConnectionSession session, Instant observedAt) {
        return new TcpConnectionLifecycleEvent(Type.INACTIVE, session, observedAt, null);
    }

    public static TcpConnectionLifecycleEvent exception(
            TcpConnectionSession session,
            Instant observedAt,
            Throwable cause) {
        return new TcpConnectionLifecycleEvent(Type.EXCEPTION, session, observedAt, cause);
    }
}
