package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;

public final class TcpNettyIngressModule {

    public static final String MODULE_NAME = "runtime-ingress-tcp-netty";

    private TcpNettyIngressModule() {
    }

    public static BackpressureDecision defaultBackpressureDecision() {
        return BackpressureDecision.ACCEPT;
    }
}
