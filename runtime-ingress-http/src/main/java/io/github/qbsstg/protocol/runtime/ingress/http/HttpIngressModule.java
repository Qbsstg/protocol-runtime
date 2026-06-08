package io.github.qbsstg.protocol.runtime.ingress.http;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.core.SourceId;

public final class HttpIngressModule {

    public static final String MODULE_NAME = "runtime-ingress-http";
    public static final String TRANSPORT = "http";

    private HttpIngressModule() {
    }

    public static BackpressureDecision defaultBackpressureDecision() {
        return BackpressureDecision.ACCEPT;
    }

    public static HttpIngressServerConfig loopbackConfig(int port, SourceId sourceId) {
        return HttpIngressServerConfig.loopback(port, sourceId);
    }

    public static HttpIngressServerConfig pathSourceConfig(int port) {
        return HttpIngressServerConfig.pathSource(port);
    }

    public static <T> HttpIngressServer<T> server(
            HttpIngressServerConfig config,
            RuntimePipelineRunner<T> runner) {
        return new HttpIngressServer<>(config, runner);
    }
}
