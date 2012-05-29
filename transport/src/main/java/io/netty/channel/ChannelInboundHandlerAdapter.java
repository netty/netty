package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.util.Queue;

public abstract class ChannelInboundHandlerAdapter<I> implements ChannelInboundHandler<I> {
    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void channelRegistered(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<I> ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelInboundHandlerContext<I> ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<I> ctx) throws Exception {
        inboundBufferUpdated0(ctx);
    }

    static <I> void inboundBufferUpdated0(ChannelInboundHandlerContext<I> ctx) {
        if (ctx.inbound().isBypass()) {
            ctx.fireInboundBufferUpdated();
            return;
        }

        if (ctx.inbound().hasMessageBuffer()) {
            Queue<I> in = ctx.inbound().messageBuffer();
            Queue<Object> nextIn = ctx.nextInboundMessageBuffer();
            for (;;) {
                I msg = in.poll();
                if (msg == null) {
                    break;
                }
                nextIn.add(msg);
            }
        } else {
            ChannelBuffer in = ctx.inbound().byteBuffer();
            ChannelBuffer nextIn = ctx.nextInboundByteBuffer();
            nextIn.writeBytes(in);
            in.discardReadBytes();
        }
        ctx.fireInboundBufferUpdated();
    }
}
