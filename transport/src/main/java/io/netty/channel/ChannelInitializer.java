package io.netty.channel;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

@Sharable
public abstract class ChannelInitializer<C extends Channel> extends ChannelInboundHandlerAdapter<Object> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelInitializer.class);

    public abstract void initChannel(C ch) throws Exception;

    @Override
    public ChannelBufferHolder<Object> newInboundBuffer(
            ChannelInboundHandlerContext<Object> ctx) throws Exception {
        return ChannelBufferHolders.inboundBypassBuffer(ctx);
    }

    @Override
    public final void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        super.beforeAdd(ctx);
    }

    @Override
    public final void afterAdd(ChannelHandlerContext ctx) throws Exception {
        super.afterAdd(ctx);
    }

    @Override
    public final void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        super.beforeRemove(ctx);
    }

    @Override
    public final void afterRemove(ChannelHandlerContext ctx) throws Exception {
        super.afterRemove(ctx);
    }

    @Override
    public final void channelRegistered(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        try {
            initChannel((C) ctx.channel());
            ctx.pipeline().remove(this);
            // Note that we do not call ctx.fireChannelRegistered() because a user might have
            // inserted a handler before the initializer using pipeline.addFirst().
            ctx.pipeline().fireChannelRegistered();
        } catch (Throwable t) {
            logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), t);
            ctx.close();
        }
    }

    @Override
    public final void channelUnregistered(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        super.channelUnregistered(ctx);
    }

    @Override
    public final void channelActive(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public final void channelInactive(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    public final void exceptionCaught(ChannelInboundHandlerContext<Object> ctx,
            Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public final void userEventTriggered(ChannelInboundHandlerContext<Object> ctx,
            Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public final void inboundBufferUpdated(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        super.inboundBufferUpdated(ctx);
    }
}
