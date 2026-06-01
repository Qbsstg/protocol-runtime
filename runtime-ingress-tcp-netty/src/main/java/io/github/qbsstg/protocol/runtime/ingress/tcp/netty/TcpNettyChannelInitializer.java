package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

import java.time.Clock;
import java.util.Objects;

public final class TcpNettyChannelInitializer<T> extends ChannelInitializer<SocketChannel> {

    private final TcpNettyPipelineRunnerFactory<T> runnerFactory;
    private final TcpSourceIdResolver sourceIdResolver;
    private final Clock clock;
    private final TcpConnectionRegistry connectionRegistry;

    public TcpNettyChannelInitializer(TcpNettyPipelineRunnerFactory<T> runnerFactory) {
        this(runnerFactory, TcpNettyIngressModule.defaultSourceIdResolver(), Clock.systemUTC());
    }

    public TcpNettyChannelInitializer(
            TcpNettyPipelineRunnerFactory<T> runnerFactory,
            TcpSourceIdResolver sourceIdResolver,
            Clock clock) {
        this(runnerFactory, sourceIdResolver, clock, new TcpConnectionRegistry());
    }

    public TcpNettyChannelInitializer(
            TcpNettyPipelineRunnerFactory<T> runnerFactory,
            TcpSourceIdResolver sourceIdResolver,
            Clock clock,
            TcpConnectionRegistry connectionRegistry) {
        this.runnerFactory = Objects.requireNonNull(runnerFactory, "runnerFactory must not be null");
        this.sourceIdResolver = Objects.requireNonNull(sourceIdResolver, "sourceIdResolver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry, "connectionRegistry must not be null");
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        RuntimePipelineRunner<T> runner = Objects.requireNonNull(
                runnerFactory.create(channel),
                "runnerFactory must not return null");
        channel.pipeline().addLast(new TcpNettyIngressHandler<>(
                runner,
                sourceIdResolver,
                clock,
                connectionRegistry));
    }
}
