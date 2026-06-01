package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class TcpNettyServer<T> implements RuntimeLifecycle {

    private final TcpNettyServerConfig config;
    private final TcpNettyPipelineRunnerFactory<T> runnerFactory;
    private final TcpSourceIdResolver sourceIdResolver;
    private final Clock clock;
    private final TcpConnectionRegistry connectionRegistry;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public TcpNettyServer(
            TcpNettyServerConfig config,
            TcpNettyPipelineRunnerFactory<T> runnerFactory) {
        this(config, runnerFactory, TcpNettyIngressModule.defaultSourceIdResolver(), Clock.systemUTC());
    }

    public TcpNettyServer(
            TcpNettyServerConfig config,
            TcpNettyPipelineRunnerFactory<T> runnerFactory,
            TcpSourceIdResolver sourceIdResolver,
            Clock clock) {
        this(config, runnerFactory, sourceIdResolver, clock, new TcpConnectionRegistry());
    }

    public TcpNettyServer(
            TcpNettyServerConfig config,
            TcpNettyPipelineRunnerFactory<T> runnerFactory,
            TcpSourceIdResolver sourceIdResolver,
            Clock clock,
            TcpConnectionRegistry connectionRegistry) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.runnerFactory = Objects.requireNonNull(runnerFactory, "runnerFactory must not be null");
        this.sourceIdResolver = Objects.requireNonNull(sourceIdResolver, "sourceIdResolver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry, "connectionRegistry must not be null");
    }

    public TcpNettyServer<T> bind() {
        start();
        return this;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        bossGroup = new NioEventLoopGroup(config.bossThreads());
        workerGroup = new NioEventLoopGroup(config.workerThreads());
        try {
            serverChannel = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childHandler(new TcpNettyChannelInitializer<>(
                            runnerFactory,
                            sourceIdResolver,
                            clock,
                            connectionRegistry))
                    .bind(config.host(), config.port())
                    .syncUninterruptibly()
                    .channel();
        } catch (RuntimeException ex) {
            shutdownGroups();
            throw ex;
        }
    }

    public synchronized boolean isRunning() {
        return serverChannel != null && serverChannel.isOpen();
    }

    public synchronized InetSocketAddress localAddress() {
        if (serverChannel == null || serverChannel.localAddress() == null) {
            throw new IllegalStateException("server is not bound");
        }
        return (InetSocketAddress) serverChannel.localAddress();
    }

    public synchronized int port() {
        return localAddress().getPort();
    }

    public int activeConnectionCount() {
        return connectionRegistry.activeCount();
    }

    public List<TcpConnectionSession> activeSessions() {
        return connectionRegistry.activeSessions();
    }

    @Override
    public synchronized void stop() {
        Channel channel = serverChannel;
        serverChannel = null;
        try {
            if (channel != null) {
                channel.close().syncUninterruptibly();
            }
            connectionRegistry.closeAll();
        } finally {
            shutdownGroups();
        }
    }

    private void shutdownGroups() {
        EventLoopGroup workers = workerGroup;
        EventLoopGroup bosses = bossGroup;
        workerGroup = null;
        bossGroup = null;
        if (workers != null) {
            workers.shutdownGracefully().syncUninterruptibly();
        }
        if (bosses != null) {
            bosses.shutdownGracefully().syncUninterruptibly();
        }
    }
}
