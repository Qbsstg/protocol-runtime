package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class TcpNettyIngressHandler<T> extends SimpleChannelInboundHandler<ByteBuf> {

    private final RuntimePipelineRunner<T> runner;
    private final TcpSourceIdResolver sourceIdResolver;
    private final Clock clock;
    private final TcpConnectionRegistry connectionRegistry;
    private TcpConnectionSession session;

    public TcpNettyIngressHandler(
            RuntimePipelineRunner<T> runner,
            TcpSourceIdResolver sourceIdResolver) {
        this(runner, sourceIdResolver, Clock.systemUTC());
    }

    public TcpNettyIngressHandler(
            RuntimePipelineRunner<T> runner,
            TcpSourceIdResolver sourceIdResolver,
            Clock clock) {
        this(runner, sourceIdResolver, clock, new TcpConnectionRegistry());
    }

    public TcpNettyIngressHandler(
            RuntimePipelineRunner<T> runner,
            TcpSourceIdResolver sourceIdResolver,
            Clock clock,
            TcpConnectionRegistry connectionRegistry) {
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.sourceIdResolver = Objects.requireNonNull(sourceIdResolver, "sourceIdResolver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry, "connectionRegistry must not be null");
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        TcpConnectionSession activeSession = currentSession(context);
        connectionRegistry.register(context.channel(), activeSession);
        runner.start();
        context.fireUserEventTriggered(TcpConnectionLifecycleEvent.active(activeSession));
        context.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        TcpConnectionSession inactiveSession = session;
        try {
            runner.stop();
        } finally {
            connectionRegistry.unregister(inactiveSession);
            if (inactiveSession != null) {
                context.fireUserEventTriggered(TcpConnectionLifecycleEvent.inactive(
                        inactiveSession,
                        Instant.now(clock)));
            }
            session = null;
            context.fireChannelInactive();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
        TcpConnectionSession activeSession = currentSession(context);
        byte[] payload = copyPayload(message);
        IngressEnvelope envelope = new IngressEnvelope(
                activeSession.sourceId(),
                TcpNettyIngressModule.TRANSPORT,
                payload,
                Instant.now(clock),
                activeSession.attributes());

        BackpressureDecision decision = runner.accept(envelope);
        if (decision == BackpressureDecision.ACCEPT) {
            return;
        }

        if (decision == BackpressureDecision.RETRY_LATER) {
            context.channel().config().setAutoRead(false);
        }
        context.fireUserEventTriggered(new TcpNettyBackpressureEvent(
                activeSession.sourceId(),
                decision,
                activeSession.channelId(),
                payload.length));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        TcpConnectionSession activeSession = currentSession(context);
        Instant observedAt = Instant.now(clock);
        runner.reportFailure(new ParseFailure(
                activeSession.sourceId(),
                runner.protocol(),
                failureMessage(cause),
                new byte[0],
                observedAt,
                cause,
                activeSession.attributes()));
        context.fireUserEventTriggered(TcpConnectionLifecycleEvent.exception(
                activeSession,
                observedAt,
                cause));
        context.close();
    }

    public TcpConnectionSession session() {
        return session;
    }

    private TcpConnectionSession currentSession(ChannelHandlerContext context) {
        if (session == null) {
            SourceId sourceId = sourceIdResolver.resolve(context);
            session = TcpConnectionSession.from(context.channel(), sourceId, Instant.now(clock));
        }
        return session;
    }

    private static String failureMessage(Throwable cause) {
        if (cause == null) {
            return "TCP connection exception";
        }
        if (cause.getMessage() == null || cause.getMessage().isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return cause.getMessage();
    }

    private static byte[] copyPayload(ByteBuf message) {
        byte[] payload = new byte[message.readableBytes()];
        message.getBytes(message.readerIndex(), payload);
        return payload;
    }
}
