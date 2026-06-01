package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import org.junit.jupiter.api.Test;

class TcpNettyIngressModuleTest {

    @Test
    void exposesBootstrapModuleIdentity() {
        assertEquals("runtime-ingress-tcp-netty", TcpNettyIngressModule.MODULE_NAME);
        assertEquals("tcp", TcpNettyIngressModule.TRANSPORT);
        assertEquals(BackpressureDecision.ACCEPT, TcpNettyIngressModule.defaultBackpressureDecision());
        assertNotNull(TcpNettyIngressModule.defaultSourceIdResolver());
        assertNotNull(TcpNettyIngressModule.server(
                TcpNettyServerConfig.loopback(0),
                channel -> null));
    }
}
