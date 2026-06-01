package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;

public final class TcpNettyIngressModule {

    public static final String MODULE_NAME = "runtime-ingress-tcp-netty";
    public static final String TRANSPORT = "tcp";

    private TcpNettyIngressModule() {
    }

    public static BackpressureDecision defaultBackpressureDecision() {
        return BackpressureDecision.ACCEPT;
    }

    public static TcpSourceIdResolver defaultSourceIdResolver() {
        return TcpSourceIdResolver.remoteAddress(TRANSPORT);
    }

    public static <T> TcpNettyIngressHandler<T> ingressHandler(RuntimePipelineRunner<T> runner) {
        return new TcpNettyIngressHandler<>(runner, defaultSourceIdResolver());
    }

    public static <T> TcpNettyServer<T> server(
            TcpNettyServerConfig config,
            TcpNettyPipelineRunnerFactory<T> runnerFactory) {
        return new TcpNettyServer<>(config, runnerFactory);
    }
}
