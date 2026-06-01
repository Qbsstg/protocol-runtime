package io.github.qbsstg.protocol.runtime.ingress.tcp.netty;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
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

    public TcpNettyIngressHandler(
            RuntimePipelineRunner<T> runner,
            TcpSourceIdResolver sourceIdResolver) {
        this(runner, sourceIdResolver, Clock.systemUTC());
    }

    public TcpNettyIngressHandler(
            RuntimePipelineRunner<T> runner,
            TcpSourceIdResolver sourceIdResolver,
            Clock clock) {
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.sourceIdResolver = Objects.requireNonNull(sourceIdResolver, "sourceIdResolver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        runner.start();
        context.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        try {
            runner.stop();
        } finally {
            context.fireChannelInactive();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
        SourceId sourceId = sourceIdResolver.resolve(context);
        byte[] payload = copyPayload(message);
        IngressEnvelope envelope = new IngressEnvelope(
                sourceId,
                TcpNettyIngressModule.TRANSPORT,
                payload,
                Instant.now(clock),
                TcpConnectionAttributes.from(context.channel()));

        BackpressureDecision decision = runner.accept(envelope);
        if (decision == BackpressureDecision.ACCEPT) {
            return;
        }

        if (decision == BackpressureDecision.RETRY_LATER) {
            context.channel().config().setAutoRead(false);
        }
        context.fireUserEventTriggered(new TcpNettyBackpressureEvent(
                sourceId,
                decision,
                context.channel().id().asShortText(),
                payload.length));
    }

    private static byte[] copyPayload(ByteBuf message) {
        byte[] payload = new byte[message.readableBytes()];
        message.getBytes(message.readerIndex(), payload);
        return payload;
    }
}
