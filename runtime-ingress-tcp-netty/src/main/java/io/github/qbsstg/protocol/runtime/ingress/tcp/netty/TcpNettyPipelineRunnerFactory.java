package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.netty.channel.Channel;

@FunctionalInterface
public interface TcpNettyPipelineRunnerFactory<T> {

    RuntimePipelineRunner<T> create(Channel channel);
}
